package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
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
import ru.student.fabricTown.profiler.Profiler;

import java.util.List;

public class BuildUi {
    public final Table root;
    private final UiController ui;
    private final UiStage uiStage;
    private final TextButton deliveryBtn;

    /**
     * Кнопка чертежа, которая сейчас визуально подсвечена (checked).
     * Подсветка управляется вручную через ClickListener — это надёжнее,
     * чем ButtonGroup, потому что мы полностью контролируем момент
     * снятия checked со старой кнопки и не зависим от тонкостей
     * uncheckLast / maxCheckCount.
     */
    private TextButton currentSelected;

    private ScrollPane scrollPane;

    public BuildUi(Skin skin, UiController ui, UiStage uiStage) {
        try (var s = Profiler.start("buildUi.ctor")) {
            this.ui = ui;
            this.uiStage = uiStage;

            root = new Table(skin);
            root.setBackground("panel");
            root.top().pad(8);

            Label title = new Label(filter("🔨 Строительство"), skin);
            root.add(title).left().padBottom(8).row();

            Table content = new Table(skin);
            content.top().left();
            addSimpleCategory(content, skin, "🛣️ Дороги", Catalog.roads());
            addSimpleCategory(content, skin, "🔀 Перекрёстки", Catalog.intersections());
            addGroupedCategory(content, skin, "🏭 Фабрики", Catalog.factoriesGrouped());
            addSimpleCategory(content, skin, "🍫 Переработчики", Catalog.processors());
            addSimpleCategory(content, skin, "🏪 Магазины", Catalog.shops());
            addSimpleCategory(content, skin, "🏠 Дома", Catalog.houses());
            addSimpleCategory(content, skin, "↔️↕️ Расширение", Catalog.services());

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
    }

    public ScrollPane getScrollPane() { return scrollPane; }

    /** Снимает подсветку с текущей выбранной кнопки чертежа. */
    public void clearSelection() {
        if (currentSelected != null) {
            currentSelected.setChecked(false);
            currentSelected = null;
        }
    }

    public void setDeliveryButtonState(boolean paused) {
        deliveryBtn.setText(filter(paused ? "▶ Возобновить доставку" : "⏸ Отозвать машины"));
        deliveryBtn.setColor(paused
                ? new Color(60 / 255f, 100 / 255f, 60 / 255f, 1f)
                : Color.WHITE);
    }

    private String filter(String text) {
        return uiStage.filterEmojis(text);
    }

    /**
     * Создаёт кнопку чертежа и управляет подсветкой вручную.
     *
     * TextButton сам по себе toggle: по клику внутренний ClickListener
     * переключает checked. Мы перехватываем это поведение СВОИМ
     * ClickListener'ом (он добавляется после внутреннего и вызывается
     * после него) и приводим визуал к нужному состоянию:
     *
     *  - клик по новой кнопке: снимаем checked со старой, ставим на новую;
     *  - клик по уже выбранной: внутренний toggle её снял, мы возвращаем checked;
     *  - mode/selectedBlueprint в UiController при этом всегда отражают
     *    именно ту кнопку, которая подсвечена.
     */
    private TextButton makeBlueprintButton(Skin skin, Blueprint bp) {
        TextButton btn = new TextButton(filter(bp.displayName + "  (" + (int) bp.cost + ")"), skin);
        btn.getLabel().setAlignment(Align.left);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Внутренний ClickListener TextButton уже выполнил setChecked(!isChecked).
                // Приводим визуал к состоянию «выбрана именно эта кнопка».
                if (currentSelected != null && currentSelected != btn) {
                    currentSelected.setChecked(false);
                }
                btn.setChecked(true);
                currentSelected = btn;
                ui.selectBlueprint(bp);
            }
        });
        return btn;
    }

    /**
     * Заголовок-«гармошка». Возвращает Table-body, который будет
     * сворачиваться/разворачиваться по клику.
     *
     * Важно: при сворачивании мы не просто вызываем setVisible(false) —
     * это скрывает отрисовку, но не убирает место в layout. Вместо этого
     * отвязываем body от Cell (setActor(null)) — тогда Table полностью
     * схлопывается, как JPanel в BoxLayout.
     */
    private Table makeCollapsibleHeader(Table parent, Skin skin, String title, int indent) {
        String filteredTitle = filter(title);
        TextButton header = new TextButton("▸ " + filteredTitle, skin);
        header.getLabel().setAlignment(Align.left);

        Table body = new Table(skin);
        body.top().left().padLeft(indent);
        body.setVisible(false);

        // 1) Заголовок сверху, завершаем строку.
        parent.add(header).growX().height(26).padTop(4).left().row();

        // 2) Захватываем Cell ДО row() — иначе row() вернёт void.
        Cell<Table> bodyCell = parent.add(body).growX().left();
        bodyCell.row();               // завершаем строку отдельным вызовом
        bodyCell.setActor(null);      // изначально свернуто — не занимает места

        header.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boolean vis = !body.isVisible();
                body.setVisible(vis);
                bodyCell.setActor(vis ? body : null);
                header.setText((vis ? "▾ " : "▸ ") + filteredTitle);
                parent.invalidateHierarchy();
                if (scrollPane != null) scrollPane.invalidate();
            }
        });

        parent.invalidateHierarchy();
        return body;
    }

    private void addSimpleCategory(Table parent, Skin skin, String title, List<Blueprint> items) {
        if (items.isEmpty()) return;
        Table body = makeCollapsibleHeader(parent, skin, title, 0);
        for (Blueprint bp : items) {
            TextButton btn = makeBlueprintButton(skin, bp);
            body.add(btn).growX().height(26).padBottom(2).row();
        }
    }

    private void addGroupedCategory(Table parent, Skin skin, String title, List<CatalogGroup> groups) {
        Table body = makeCollapsibleHeader(parent, skin, title, 0);
        for (CatalogGroup g : groups) {
            if (g.isEmpty()) continue;
            Table sub = makeCollapsibleHeader(body, skin, "· " + g.title, 8);
            for (Blueprint bp : g.items) {
                TextButton btn = makeBlueprintButton(skin, bp);
                sub.add(btn).growX().height(26).padBottom(2).row();
            }
        }
    }
}