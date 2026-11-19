package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.agents.UniversalCar;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Processor;
import ru.student.fabricTown.buildings.Road;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.profiler.Profiler;

import java.util.EnumMap;
import java.util.Map;

/**
 * Информационная доска: сколько каких объектов на карте.
 * Показывается сверху по центру по F1, обновляется раз в 0.5 с.
 */
public class StatsUi {
    public final Table root;
    private final Game game;
    private final UiStage uiStage;
    private final Label body;
    private final ScrollPane scroll;
    private float refreshAcc;

    public StatsUi(Skin skin, Game game, UiStage uiStage) {
        this.game = game;
        this.uiStage = uiStage;

        root = new Table(skin);
        root.setBackground("panel");
        root.top().pad(10);

        Label title = new Label(filter("📊 Статистика города"), skin);
        root.add(title).left().padBottom(8).row();

        body = new Label("", skin);
        body.setWrap(true);
        body.setAlignment(Align.topLeft);

        scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setForceScroll(false, true);
        root.add(scroll).growX().width(320).height(360).row();

        root.setVisible(false);
        refresh();
    }

    public void toggle() {
        boolean vis = !root.isVisible();
        root.setVisible(vis);
        if (vis) refresh();
    }

    public boolean isVisible() { return root.isVisible(); }

    public ScrollPane getScrollPane() { return scroll; }

    public void act(float dt) {
        if (!root.isVisible()) return;
        refreshAcc += dt;
        if (refreshAcc >= 0.5f) {
            refreshAcc = 0;
            refresh();
        }
    }

    public void refresh() {
        try (var s = Profiler.start("statsUi.refresh")) {
            StringBuilder sb = new StringBuilder();

            // ---- Дороги ----
            int roads = game.getMap().getRoads().size();
            int straight = 0, corners = 0, tees = 0, crosses = 0;
            for (Road r : game.getMap().getRoads().values()) {
                if (r.isStraight()) straight++;
                else if (r.isCorner()) corners++;
                else if (r.isTee()) tees++;
                else if (r.isCross()) crosses++;
            }
            sb.append("🛣 Дорог: ").append(roads).append('\n');
            if (roads > 0) {
                sb.append("   ├ прямых: ").append(straight).append('\n');
                sb.append("   ├ углов: ").append(corners).append('\n');
                sb.append("   ├ T-перекр.: ").append(tees).append('\n');
                sb.append("   └ крестов: ").append(crosses).append("\n\n");
            }

            // ---- Фабрики ----
            sb.append("🏭 Фабрик: ").append(game.getFactories().size()).append('\n');
            Map<Product, Integer> factoryByType = new EnumMap<>(Product.class);
            for (Factory f : game.getFactories()) {
                factoryByType.merge(f.getProduct(), 1, Integer::sum);
            }
            for (var e : factoryByType.entrySet()) {
                sb.append("   · ").append(e.getKey().title)
                        .append(": ").append(e.getValue()).append('\n');
            }
            sb.append('\n');

            // ---- Переработчики ----
            sb.append("🍫 Переработчиков: ").append(game.getProcessors().size()).append('\n');
            Map<Product, Integer> procByType = new EnumMap<>(Product.class);
            for (Processor p : game.getProcessors()) {
                procByType.merge(p.getOutputProduct(), 1, Integer::sum);
            }
            for (var e : procByType.entrySet()) {
                sb.append("   · ").append(e.getKey().title)
                        .append(": ").append(e.getValue()).append('\n');
            }
            sb.append('\n');

            // ---- Магазины ----
            int shops = game.getShops().size();
            int emptyShops = 0;
            double shopMoney = 0;
            for (Shop sh : game.getShops()) {
                if (sh.isEmpty()) emptyShops++;
                shopMoney += sh.getMoneyEarned();
            }
            sb.append("🏪 Магазинов: ").append(shops);
            if (emptyShops > 0) {
                sb.append("  (пустых: ").append(emptyShops).append(')');
            }
            sb.append('\n');
            sb.append("   · продано на: ").append((int) shopMoney).append("\n\n");

            // ---- Дома и население ----
            int houses = game.getHouses().size();
            int residents = 0;
            for (House h : game.getHouses()) residents += h.getResidents();
            sb.append("🏠 Домов: ").append(houses)
                    .append("  (жителей: ").append(residents).append(")\n\n");

            // ---- Пешеходы ----
            sb.append("🧍 Пешеходов сейчас: ").append(game.getPeople().size()).append("\n\n");

            // ---- Машины ----
            int cars = game.getCars().size();
            int carsMoving = 0, carsIdle = 0, carsBlocked = 0, carsNoRoute = 0;
            for (UniversalCar c : game.getCars()) {
                switch (c.getState()) {
                    case IDLE -> carsIdle++;
                    case TO_SINK, RETURNING, UNLOADING -> carsMoving++;
                    case BLOCKED -> carsBlocked++;
                    case NO_ROUTE -> carsNoRoute++;
                }
            }
            sb.append("🚗 Машин: ").append(cars).append('\n');
            if (cars > 0) {
                sb.append("   ├ в пути: ").append(carsMoving).append('\n');
                sb.append("   ├ ждут груз: ").append(carsIdle).append('\n');
                sb.append("   ├ заблокированы: ").append(carsBlocked).append('\n');
                sb.append("   └ без маршрута: ").append(carsNoRoute).append("\n\n");
            }

            // ---- Экономика ----
            sb.append("💰 Деньги: ").append((int) game.getMoney()).append('\n');
            sb.append("🗺 Расширений карты: ").append(game.getLandPurchases()).append('\n');

            body.setText(filter(sb.toString()));
        }
    }

    private String filter(String s) { return uiStage.filterEmojis(s); }
}