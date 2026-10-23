package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.Catalog;
import ru.student.fabricTown.logic.CatalogGroup;

import java.util.List;

public class BuildUi {
    public final Table root;
    private final UiController ui;
    private final UiStage uiStage;
    private final TextButton deliveryBtn;

    /** Основной ScrollPane со списком чертежей — его крутит колесо мыши. */
    private ScrollPane scrollPane;

    public BuildUi(Skin skin, UiController ui, UiStage uiStage) {
        this.ui = ui;
        this.uiStage = uiStage;
        root = new Table(skin);
        root.setBackground("panel");
        root.top().pad(8);

        Label title = new Label(filter("🔨 Строительство"), skin);
        root.add(title).left().padBottom(8).row();

        Table content = new Table(skin);
        content.top().left();
        addSimpleCategory(content, skin, "🛣 Дороги", Catalog.roads());
        addSimpleCategory(content, skin, "🔀 Перекрёстки", Catalog.intersections());
        addGroupedCategory(content, skin, "🏭 Фабрики", Catalog.factoriesGrouped());
        addSimpleCategory(content, skin, "🍫 Переработчики", Catalog.processors());
        addSimpleCategory(content, skin, "🏪 Магазины", Catalog.shops());
        addSimpleCategory(content, skin, "🏠 Дома", Catalog.houses());
        addSimpleCategory(content, skin, "🗺 Расширение", Catalog.services());

        scrollPane = new ScrollPane(content, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        root.add(scrollPane).grow().width(204).row();

        Table bottom = new Table(skin);
        bottom.padTop(8);
        deliveryBtn = new TextButton(filter("⏸ Отозвать машины"), skin);
        deliveryBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                ui.toggleDelivery();
            }
        });
        TextButton cancel = new TextButton(filter("❌ Отмена (Esc)"), skin);
        cancel.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                ui.cancelMode();
            }
        });
        TextButton demolish = new TextButton(filter("🧨 Снести (D)"), skin);
        demolish.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                ui.enableDemolish();
            }
        });
        bottom.add(deliveryBtn).growX().height(28).padBottom(3).row();
        bottom.add(cancel).growX().height(28).padBottom(3).row();
        bottom.add(demolish).growX().height(28);
        root.add(bottom).growX().bottom();
    }

    /** Нужно UiStage для адресной обработки скролла. */
    public ScrollPane getScrollPane() { return scrollPane; }

    public void setDeliveryButtonState(boolean paused) {
        deliveryBtn.setText(filter(paused ? "▶ Возобновить доставку" : "⏸ Отозвать машины"));
        deliveryBtn.setColor(paused
                ? new Color(60 / 255f, 100 / 255f, 60 / 255f, 1f)
                : Color.WHITE);
    }

    private String filter(String text) {
        return uiStage.filterEmojis(text);
    }

    private Table makeCollapsibleHeader(Table parent, Skin skin, String title, int indent) {
        String filteredTitle = filter(title);
        TextButton header = new TextButton("▸ " + filteredTitle, skin);
        header.getLabel().setAlignment(Align.left);
        Table body = new Table(skin);
        body.top().left().padLeft(indent);
        body.setVisible(false);
        header.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                boolean vis = !body.isVisible();
                body.setVisible(vis);
                header.setText((vis ? "▾ " : "▸ ") + filteredTitle);
                parent.invalidateHierarchy();
            }
        });
        parent.add(header).growX().height(26).padTop(4).left().row();
        parent.add(body).growX().left().row();
        return body;
    }

    private void addSimpleCategory(Table parent, Skin skin, String title, List<Blueprint> items) {
        if (items.isEmpty()) return;
        Table body = makeCollapsibleHeader(parent, skin, title, 0);
        for (Blueprint bp : items) {
            TextButton btn = new TextButton(filter(bp.displayName + "  (" + (int) bp.cost + ")"), skin);
            btn.getLabel().setAlignment(Align.left);
            btn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                    ui.selectBlueprint(bp);
                }
            });
            body.add(btn).growX().height(26).padBottom(2).row();
        }
    }

    private void addGroupedCategory(Table parent, Skin skin, String title, List<CatalogGroup> groups) {
        Table body = makeCollapsibleHeader(parent, skin, title, 0);
        for (CatalogGroup g : groups) {
            if (g.isEmpty()) continue;
            Table sub = makeCollapsibleHeader(body, skin, "· " + g.title, 8);
            for (Blueprint bp : g.items) {
                TextButton btn = new TextButton(filter(bp.displayName + "  (" + (int) bp.cost + ")"), skin);
                btn.getLabel().setAlignment(Align.left);
                btn.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor a) {
                        ui.selectBlueprint(bp);
                    }
                });
                sub.add(btn).growX().height(26).padBottom(2).row();
            }
        }
    }
}