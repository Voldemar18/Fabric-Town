package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.Building;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Processor;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InfoUi {
    public final Table root;
    private final Game game;
    private final Skin skin;
    private final UiStage uiStage;
    private final Consumer<String> onError;

    private final Table listTable;
    private final Label infoLabel;
    private final Table bindingsTable;
    private final TextButton autoBindButton;
    private final TextButton sellButton;

    /** Список «Мои объекты» — колесо мыши крутит именно его, когда курсор над панелью. */
    private ScrollPane listScrollPane;
    /** Список связей. */
    private ScrollPane bindScrollPane;

    private Building selected;
    private float refreshAcc;

    public InfoUi(Skin skin, Game game, UiStage uiStage, Consumer<String> onError) {
        this.skin = skin;
        this.game = game;
        this.uiStage = uiStage;
        this.onError = onError;

        root = new Table(skin);
        root.setBackground("panel");
        root.top().pad(8);

        Label title = new Label(filter("🏬 Мои объекты"), skin);
        root.add(title).left().padBottom(8).row();

        listTable = new Table(skin);
        listTable.top().left();
        listScrollPane = new ScrollPane(listTable, skin);
        listScrollPane.setFadeScrollBars(false);
        listScrollPane.setScrollingDisabled(true, false);
        root.add(listScrollPane).growX().height(180).row();

        infoLabel = new Label(filter("<i>Ничего не выбрано</i>"), skin);
        infoLabel.setWrap(true);
        infoLabel.setAlignment(Align.topLeft);
        root.add(infoLabel).growX().padTop(8).left().row();

        bindingsTable = new Table(skin);
        bindingsTable.top().left();
        bindScrollPane = new ScrollPane(bindingsTable, skin);
        bindScrollPane.setFadeScrollBars(false);
        bindScrollPane.setScrollingDisabled(true, false);
        root.add(bindScrollPane).grow().padTop(6).row();

        autoBindButton = new TextButton(filter("🔗 Связать автоматически"), skin);
        autoBindButton.setVisible(false);
        autoBindButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                onAutoBind();
            }
        });
        root.add(autoBindButton).growX().height(28).padTop(6).row();

        sellButton = new TextButton(filter("💰 Продать"), skin);
        sellButton.setDisabled(true);
        sellButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                onSell();
            }
        });
        root.add(sellButton).growX().height(32).padTop(6);

        refresh();
    }

    /** Основной скролл-список — нужен UiStage. */
    public ScrollPane getScrollPane() { return listScrollPane; }

    /** Скролл-список связей — если понадобится крутить отдельно. */
    public ScrollPane getBindingsScrollPane() { return bindScrollPane; }

    public Building getSelected() { return selected; }

    public void act(float dt) {
        refreshAcc += dt;
        if (refreshAcc >= 0.5f) {
            refreshAcc = 0;
            refresh();
        }
    }

    public void selectBuilding(Building building) {
        selected = building;
        refresh();
        updateInfo();
    }

    public void refresh() {
        Building prev = selected;
        listTable.clearChildren();
        List<Building> all = new ArrayList<>();
        all.addAll(game.getFactories());
        all.addAll(game.getShops());
        all.addAll(game.getProcessors());
        all.addAll(game.getHouses());

        boolean found = false;
        for (Building b : all) {
            TextButton row = new TextButton(filter(iconFor(b) + " " + b.getTypeName() + " " + b.getOrigin()), skin);
            row.getLabel().setAlignment(Align.left);
            if (b == prev) {
                row.setChecked(true);
                found = true;
            }
            row.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                    selected = b;
                    updateInfo();
                    refresh();
                }
            });
            listTable.add(row).growX().height(24).padBottom(2).row();
        }
        if (prev != null && !found) {
            selected = null;
            updateInfo();
        } else if (selected != null) {
            updateInfo();
        }
    }

    private void updateInfo() {
        rebuildBindings();
        if (selected == null) {
            infoLabel.setText(filter("Ничего не выбрано"));
            sellButton.setDisabled(true);
            sellButton.setText(filter("💰 Продать"));
            autoBindButton.setVisible(false);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(selected.getTypeName()).append('\n');
        sb.append("Позиция: ").append(selected.getOrigin()).append('\n');
        if (selected instanceof Factory f) {
            sb.append("Склад: ").append(f.getStorageCount()).append('/').append(f.getMaxStorage()).append('\n');
            int boundShops = game.getDispatcher().getFactoriesForShopCount(f);
            sb.append("Магазинов: ").append(boundShops).append('/').append(f.getMaxShops()).append('\n');
            sb.append("Переработчиков: ").append(game.getDispatcher().getProcessorsForFactory(f).size()).append('\n');
            sb.append("Вместимость машины: ").append(f.getCarryCapacity()).append(" шт.");
        } else if (selected instanceof Shop s) {
            sb.append("Товаров: ").append(s.totalStored()).append('/').append(s.getCapacity()).append('\n');
            int bound = game.getDispatcher().getFactoriesFor(s).size();
            int maxTotal = s.getMaxFactoriesPerType() * Product.values().length;
            sb.append("Фабрик: ").append(bound).append('/').append(maxTotal)
                    .append(" (лимит по типу: ").append(s.getMaxFactoriesPerType()).append(")\n");
            sb.append("Переработчиков: ").append(game.getDispatcher().getProcessorsForShop(s).size()).append('\n');
            sb.append("Продано на: ").append((int) s.getMoneyEarned());
        } else if (selected instanceof Processor p) {
            sb.append("Рецепт: ");
            p.getRecipe().inputs.forEach((prod, qty) -> sb.append(prod.title).append('×').append(qty).append(' '));
            sb.append("→ ").append(p.getOutputProduct().title).append('\n');
            sb.append("Ингредиентов: ").append(p.totalInputStored()).append('/').append(p.getInputCapacity()).append('\n');
            sb.append("Готово: ").append(p.getOutputCount()).append('/').append(p.getOutputCapacity()).append('\n');
            sb.append("Фабрик: ").append(game.getDispatcher().getFactoriesForProcessor(p).size()).append('\n');
            sb.append("Магазинов: ").append(game.getDispatcher().getShopsForProcessor(p).size())
                    .append('/').append(p.getMaxShops());
        } else if (selected instanceof House h) {
            sb.append("Жителей: ").append(h.getResidents());
        }
        infoLabel.setText(filter(sb.toString()));
        sellButton.setText(filter("💰 Продать за " + (int) selected.getSellPrice()));
        sellButton.setDisabled(false);
    }

    private void rebuildBindings() {
        bindingsTable.clearChildren();
        autoBindButton.setVisible(false);
        if (selected instanceof Shop shop) buildShopBindings(shop);
        else if (selected instanceof Factory factory) buildFactoryBindings(factory);
        else if (selected instanceof Processor processor) buildProcessorBindings(processor);
    }

    private void buildShopBindings(Shop shop) {
        List<Factory> bound = game.getDispatcher().getFactoriesFor(shop);
        addHeader("Фабрики для магазина (по типам):");
        int limitPerType = shop.getMaxFactoriesPerType();
        for (Product type : Product.values()) {
            if (!type.isRawMaterial()) continue;
            List<Factory> boundOfType = new ArrayList<>();
            for (Factory f : bound) if (f.getProduct() == type) boundOfType.add(f);
            int used = boundOfType.size();
            boolean full = used >= limitPerType;
            Label typeHdr = new Label(type.title + "  " + used + "/" + limitPerType, skin);
            typeHdr.setColor(full ? new Color(1f, 180 / 255f, 100 / 255f, 1f) : Color.SKY);
            bindingsTable.add(typeHdr).left().padTop(6).row();
            for (Factory f : boundOfType) {
                addRow("  ✔ " + f.getOrigin(), new Color(180 / 255f, 1f, 180 / 255f, 1f),
                        "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                        () -> doUnbindFactoryShop(f, shop));
            }
            boolean canAdd = !full;
            for (Factory f : game.getFactories()) {
                if (f.getProduct() != type || boundOfType.contains(f)) continue;
                boolean factoryBusy = game.getDispatcher().getFactoriesForShopCount(f) >= f.getMaxShops();
                String label = "  " + f.getOrigin() + (factoryBusy ? "  (занята)" : "");
                addRow(label, new Color(0.8f, 0.8f, 0.8f, 1f),
                        "+", new Color(60 / 255f, 120 / 255f, 60 / 255f, 1f),
                        canAdd && !factoryBusy,
                        () -> doBindFactoryShop(f, shop));
            }
        }
        autoBindButton.setVisible(bound.isEmpty());

        addHeader("Переработчики:");
        List<Processor> boundProcs = game.getDispatcher().getProcessorsForShop(shop);
        for (Processor p : boundProcs) {
            addRow("  ✔ " + p.getOrigin() + " (" + p.getOutputProduct().title + ")",
                    new Color(220 / 255f, 180 / 255f, 220 / 255f, 1f),
                    "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                    () -> doUnbindProcessorShop(p, shop));
        }
        for (Processor p : game.getProcessors()) {
            if (boundProcs.contains(p)) continue;
            boolean atMax = game.getDispatcher().getShopsForProcessor(p).size() >= p.getMaxShops();
            addRow("  " + p.getOrigin() + " (" + p.getOutputProduct().title + ")" + (atMax ? "  (занят)" : ""),
                    new Color(0.8f, 0.8f, 0.8f, 1f),
                    "+", new Color(120 / 255f, 60 / 255f, 120 / 255f, 1f),
                    !atMax, () -> doBindProcessorShop(p, shop));
        }
    }

    private void buildFactoryBindings(Factory factory) {
        List<Shop> bound = game.getDispatcher().getShopsFor(factory);
        addHeader("Магазины для этой фабрики (" + bound.size() + "/" + factory.getMaxShops() + "):");
        for (Shop s : bound) {
            addRow(s.getOrigin().toString(), new Color(180 / 255f, 1f, 180 / 255f, 1f),
                    "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                    () -> doUnbindFactoryShop(factory, s));
        }
        boolean canAdd = bound.size() < factory.getMaxShops();
        for (Shop s : game.getShops()) {
            if (bound.contains(s)) continue;
            boolean shopFull = !game.getDispatcher().canBind(factory, s);
            String label = s.getOrigin().toString();
            if (shopFull) {
                int used = game.getDispatcher().countFactoriesOfType(s, factory.getProduct());
                label += "  (" + used + "/" + s.getMaxFactoriesPerType() + " " + factory.getProduct().title + ")";
            }
            addRow(label, new Color(0.8f, 0.8f, 0.8f, 1f),
                    "+", new Color(60 / 255f, 120 / 255f, 60 / 255f, 1f),
                    canAdd && !shopFull, () -> doBindFactoryShop(factory, s));
        }

        addHeader("Переработчики-получатели:");
        List<Processor> boundProcs = game.getDispatcher().getProcessorsForFactory(factory);
        for (Processor p : boundProcs) {
            addRow("  ✔ " + p.getOrigin() + " (" + p.getOutputProduct().title + ")",
                    new Color(220 / 255f, 180 / 255f, 220 / 255f, 1f),
                    "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                    () -> doUnbindFactoryProcessor(factory, p));
        }
        for (Processor p : game.getProcessors()) {
            if (boundProcs.contains(p) || !p.acceptsIngredient(factory.getProduct())) continue;
            boolean full = !game.getDispatcher().canBindToProcessor(factory, p);
            String label = "  " + p.getOrigin() + " (" + p.getOutputProduct().title + ")";
            if (full) {
                int used = game.getDispatcher().countFactoriesOfIngredient(p, factory.getProduct());
                label += "  (" + used + "/" + p.getMaxFactoriesPerIngredient() + " " + factory.getProduct().title + ")";
            }
            addRow(label, new Color(0.8f, 0.8f, 0.8f, 1f),
                    "+", new Color(120 / 255f, 60 / 255f, 120 / 255f, 1f),
                    !full, () -> doBindFactoryProcessor(factory, p));
        }
    }

    private void buildProcessorBindings(Processor processor) {
        List<Factory> bound = game.getDispatcher().getFactoriesForProcessor(processor);
        addHeader("Фабрики-ингредиенты (по рецепту):");
        int limitPerType = processor.getMaxFactoriesPerIngredient();
        for (Product ingredient : processor.getRecipe().inputs.keySet()) {
            List<Factory> boundOfType = new ArrayList<>();
            for (Factory f : bound) if (f.getProduct() == ingredient) boundOfType.add(f);
            boolean full = boundOfType.size() >= limitPerType;
            Label typeHdr = new Label(ingredient.title + "  " + boundOfType.size() + "/" + limitPerType, skin);
            typeHdr.setColor(full ? new Color(1f, 180 / 255f, 100 / 255f, 1f) : Color.SKY);
            bindingsTable.add(typeHdr).left().padTop(6).row();
            for (Factory f : boundOfType) {
                addRow("  ✔ " + f.getOrigin(), new Color(180 / 255f, 1f, 180 / 255f, 1f),
                        "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                        () -> doUnbindFactoryProcessor(f, processor));
            }
            boolean canAdd = !full;
            for (Factory f : game.getFactories()) {
                if (f.getProduct() != ingredient || boundOfType.contains(f)) continue;
                addRow("  " + f.getOrigin(), new Color(0.8f, 0.8f, 0.8f, 1f),
                        "+", new Color(60 / 255f, 120 / 255f, 60 / 255f, 1f),
                        canAdd, () -> doBindFactoryProcessor(f, processor));
            }
        }

        List<Shop> boundShops = game.getDispatcher().getShopsForProcessor(processor);
        addHeader("Магазины-получатели (" + boundShops.size() + "/" + processor.getMaxShops() + "):");
        for (Shop s : boundShops) {
            addRow("  ✔ " + s.getOrigin(), new Color(180 / 255f, 220 / 255f, 1f, 1f),
                    "✖", new Color(140 / 255f, 60 / 255f, 60 / 255f, 1f), true,
                    () -> doUnbindProcessorShop(processor, s));
        }
        boolean canAddShop = boundShops.size() < processor.getMaxShops();
        for (Shop s : game.getShops()) {
            if (boundShops.contains(s)) continue;
            addRow("  " + s.getOrigin(), new Color(0.8f, 0.8f, 0.8f, 1f),
                    "+", new Color(60 / 255f, 120 / 255f, 60 / 255f, 1f),
                    canAddShop, () -> doBindProcessorShop(processor, s));
        }
    }

    private void addHeader(String text) {
        Label hdr = new Label(filter(text), skin);
        hdr.setColor(Color.WHITE);
        bindingsTable.add(hdr).left().padTop(8).row();
    }

    private void addRow(String label, Color labelColor, String buttonText, Color buttonBg,
                        boolean enabled, Runnable onClick) {
        Table row = new Table(skin);
        Label lbl = new Label(filter(label), skin);
        lbl.setColor(labelColor);
        TextButton btn = new TextButton(filter(buttonText), skin);
        btn.setColor(buttonBg);
        btn.setDisabled(!enabled);
        btn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                if (enabled) onClick.run();
            }
        });
        row.add(lbl).growX().left();
        row.add(btn).width(36).height(22);
        bindingsTable.add(row).growX().height(24).padBottom(2).row();
    }

    private void doBindFactoryShop(Factory f, Shop s) {
        String err = game.bindManual(f, s);
        if (err != null) showError(err); else updateInfo();
    }

    private void doUnbindFactoryShop(Factory f, Shop s) {
        String err = game.unbindManual(f, s);
        if (err != null) showError(err); else updateInfo();
    }

    private void doBindFactoryProcessor(Factory f, Processor p) {
        String err = game.bindFactoryToProcessorManual(f, p);
        if (err != null) showError(err); else updateInfo();
    }

    private void doUnbindFactoryProcessor(Factory f, Processor p) {
        String err = game.unbindFactoryFromProcessorManual(f, p);
        if (err != null) showError(err); else updateInfo();
    }

    private void doBindProcessorShop(Processor p, Shop s) {
        String err = game.bindProcessorToShopManual(p, s);
        if (err != null) showError(err); else updateInfo();
    }

    private void doUnbindProcessorShop(Processor p, Shop s) {
        String err = game.unbindProcessorFromShopManual(p, s);
        if (err != null) showError(err); else updateInfo();
    }

    private void onAutoBind() {
        if (!(selected instanceof Shop shop)) return;
        String err = game.autoBindShopManual(shop);
        if (err != null) showError(err); else updateInfo();
    }

    private void onSell() {
        if (selected == null) return;
        String err = game.trySell(selected.getOrigin());
        if (err != null) showError(err);
        else {
            selected = null;
            refresh();
            updateInfo();
        }
    }

    private void showError(String err) {
        if (onError != null) onError.accept("❌ " + err);
    }

    private String iconFor(Building b) {
        if (b instanceof Factory) return "🏭";
        if (b instanceof Shop) return "🏪";
        if (b instanceof House) return "🏠";
        if (b instanceof Processor) return "🍫";
        return "🏢";
    }

    private String filter(String text) {
        return uiStage.filterEmojis(text);
    }
}