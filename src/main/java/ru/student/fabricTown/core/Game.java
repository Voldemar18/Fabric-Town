package ru.student.fabricTown.core;

import ru.student.fabricTown.agents.Car;
import ru.student.fabricTown.agents.Person;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.BuildingFactory;
import ru.student.fabricTown.logic.Catalog;
import ru.student.fabricTown.logic.Dispatcher;
import ru.student.fabricTown.logic.GameEvents;
import ru.student.fabricTown.logic.GraphBuilder;
import ru.student.fabricTown.logic.PathFinder;
import ru.student.fabricTown.logic.PlacementRules;
import ru.student.fabricTown.logic.RoadRegistry;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.GameMap;
import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ядро игры Fabric Town.
 *
 * Стартовый город — карта 20×20 с дорожной сеткой 3×3 квартала.
 * Улицы — «двойные»: две параллельные односторонние дороги,
 * одна едет в одну сторону, другая — в обратную.
 * На каждом пересечении улиц — перекрёсток CROSS.
 *
 * Такая сетка ГАРАНТИРУЕТ, что:
 *   - для любой пары дорожных клеток есть путь по графу;
 *   - для любой пары фабрика↔магазин есть и прямой, и обратный путь;
 *   - машина всегда может развернуться через ближайший перекрёсток.
 *
 * Динамическое обновление маршрутов:
 *   При ЛЮБОМ изменении дорожной сети (постройка, снос, поворот)
 *   вызывается {@link #rebuildAllRoutes()}, который:
 *     - для каждой пары фабрика↔магазин заново ищет пути «туда» и «обратно»;
 *     - если оба пути есть — обновляет маршрут машины (даже в пути);
 *     - если хотя бы одного нет — переводит машину в NO_ROUTE,
 *       чтобы она автоматически ожила, когда игрок достроит дорогу.
 *
 * Отзыв машин:
 *   {@link #recallAllCars()} — все машины мгновенно возвращаются к фабрикам
 *   и перестают выезжать. {@link #resumeAllCars()} — возобновляет доставку.
 *   Маршруты при этом продолжают пересчитываться: после снятия паузы машины
 *   поедут по актуальным путям.
 */
public class Game {

    // ================== ПОЛЯ ==================

    private final GameMap map = new GameMap();
    private final GraphBuilder graphBuilder;
    private final PathFinder pathFinder;
    private final Dispatcher dispatcher;
    private final NotificationCenter notifications = new NotificationCenter();

    /** Стартовые деньги. */
    private double money = 10000000;

    private final List<Factory> factories = new ArrayList<>();
    private final List<Shop> shops = new ArrayList<>();
    private final List<House> houses = new ArrayList<>();
    private final List<Car> cars = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();

    private final Random rng = new Random();

    private int landPurchases = 0;

    /** Границы карты — стартовые 0..19 (карта 20×20). */
    private int mapMinX = 0, mapMaxX = 19;
    private int mapMinY = 0, mapMaxY = 19;

    /**
     * Приостановлена ли доставка машин.
     *   false — машины работают как обычно;
     *   true  — машины отозваны на фабрики и не выезжают.
     *
     * Управляется методами {@link #recallAllCars()} / {@link #resumeAllCars()}.
     */
    private boolean deliveryPaused = false;

    // ================== КОНСТРУКТОР ==================

    public Game() {
        RoadRegistry.clear();

        this.graphBuilder = new GraphBuilder(map);
        this.pathFinder = new PathFinder(graphBuilder);
        this.dispatcher = new Dispatcher(pathFinder);

        initStartingKit();
    }

    // ================== СТАРТОВЫЙ НАБОР ==================

    private void initStartingKit() {
        initRoadGrid();
        graphBuilder.rebuild();
        initBuildings();
        autoBindAllFactories();

        notifications.success("Город основан: "
                + factories.size() + " фабрик, "
                + shops.size() + " магазинов, "
                + houses.size() + " домов");
    }

    // ================== 1. ДОРОЖНАЯ СЕТКА 20×20 ==================

    /**
     * Сетка из «двойных улиц»:
     *
     *   Горизонтальные пары (верхняя — EAST, нижняя — WEST):
     *     (y = 0, y = 1), (y = 6, y = 7), (y = 12, y = 13), (y = 18, y = 19)
     *
     *   Вертикальные пары (левая — SOUTH, правая — NORTH):
     *     (x = 0, x = 1), (x = 6, x = 7), (x = 12, x = 13), (x = 18, x = 19)
     *
     * На каждом пересечении пары-с-парой стоит CROSS.
     */
    private void initRoadGrid() {
        // ================== ГОРИЗОНТАЛЬНЫЕ ПАРЫ ==================
        int[] hEastRows = { 0,  6, 12, 18 };
        int[] hWestRows = { 1,  7, 13, 19 };

        for (int i = 0; i < hEastRows.length; i++) {
            int yEast = hEastRows[i];
            int yWest = hWestRows[i];

            for (int x = 0; x <= 19; x++) {
                Point pe = new Point(x, yEast);
                if (!map.hasRoad(pe)) {
                    map.place(new Road(pe, Direction.EAST));
                }
                Point pw = new Point(x, yWest);
                if (!map.hasRoad(pw)) {
                    map.place(new Road(pw, Direction.WEST));
                }
            }
        }

        // ================== ВЕРТИКАЛЬНЫЕ ПАРЫ ==================
        int[] vSouthCols = { 0,  6, 12, 18 };
        int[] vNorthCols = { 1,  7, 13, 19 };

        for (int i = 0; i < vSouthCols.length; i++) {
            int xSouth = vSouthCols[i];
            int xNorth = vNorthCols[i];

            for (int y = 0; y <= 19; y++) {
                Point ps = new Point(xSouth, y);
                if (!map.hasRoad(ps)) {
                    map.place(new Road(ps, Direction.SOUTH));
                }
                Point pn = new Point(xNorth, y);
                if (!map.hasRoad(pn)) {
                    map.place(new Road(pn, Direction.NORTH));
                }
            }
        }

        // ================== ПЕРЕКРЁСТКИ CROSS ==================
        for (int x : vSouthCols) {
            for (int y : hEastRows) {
                replaceWithRoad(new Point(x, y), RoadShape.CROSS);
            }
        }
        for (int x : vNorthCols) {
            for (int y : hEastRows) {
                replaceWithRoad(new Point(x, y), RoadShape.CROSS);
            }
        }
        for (int x : vSouthCols) {
            for (int y : hWestRows) {
                replaceWithRoad(new Point(x, y), RoadShape.CROSS);
            }
        }
        for (int x : vNorthCols) {
            for (int y : hWestRows) {
                replaceWithRoad(new Point(x, y), RoadShape.CROSS);
            }
        }
    }

    /**
     * Заменить дорогу в клетке на дорогу заданной формы.
     */
    private void replaceWithRoad(Point p, RoadShape shape) {
        Building old = map.get(p);
        if (old != null) {
            map.remove(old);
        }
        map.place(new Road(p, shape));
    }

    // ================== 2. ЗДАНИЯ ==================

    private void initBuildings() {
        // ========== КВАРТАЛ 1 ==========
        placeFactory(new Factory(new Point(2, 2),
                BuildingSize.S1x1, Product.CHICKEN));

        // ========== КВАРТАЛ 2 ==========
        placeFactory(new Factory(new Point(8, 2),
                BuildingSize.S2x2, Product.BEEF));

        // ========== КВАРТАЛ 3 ==========
        placeFactory(new Factory(new Point(14, 2),
                BuildingSize.S3x3, Product.FRUIT));

        // ========== КВАРТАЛ 4 ==========
        placeFactory(new Factory(new Point(2, 8),
                BuildingSize.S1x2, Product.VEGETABLES));

        // ========== КВАРТАЛ 5 ==========
        placeFactory(new Factory(new Point(8, 8),
                BuildingSize.S2x2, Product.BREAD));

        // ========== КВАРТАЛ 6 ==========
        placeFactory(new Factory(new Point(14, 8),
                BuildingSize.S2x2, Product.CHICKEN));

        // ========== КВАРТАЛ 7 ==========
        placeShop(new Shop(new Point(2, 14), BuildingSize.S1x1));

        // ========== КВАРТАЛ 8 ==========
        placeShop(new Shop(new Point(8, 14), BuildingSize.S2x2));

        // ========== КВАРТАЛ 9 ==========
        placeShop(new Shop(new Point(14, 14), BuildingSize.S1x2));

        // Дома
        placeHouse(new House(new Point(4, 4), BuildingSize.S1x1));
        placeHouse(new House(new Point(10, 4), BuildingSize.S1x2));
        placeHouse(new House(new Point(4, 10), BuildingSize.S2x2));
        placeHouse(new House(new Point(4, 16), BuildingSize.S1x1));
        placeHouse(new House(new Point(10, 16), BuildingSize.S2x2));
        placeHouse(new House(new Point(16, 16), BuildingSize.S1x1));
    }

    private void placeFactory(Factory f) {
        if (!map.canPlace(f)) {
            System.err.println("[Game] Не могу поставить фабрику "
                    + f.getOrigin() + " — место занято");
            return;
        }
        map.place(f);
        factories.add(f);
    }

    private void placeShop(Shop s) {
        if (!map.canPlace(s)) {
            System.err.println("[Game] Не могу поставить магазин "
                    + s.getOrigin() + " — место занято");
            return;
        }
        map.place(s);
        shops.add(s);
    }

    private void placeHouse(House h) {
        if (!map.canPlace(h)) {
            System.err.println("[Game] Не могу поставить дом "
                    + h.getOrigin() + " — место занято");
            return;
        }
        map.place(h);
        houses.add(h);
    }

    // ================== 3. АВТОПРИВЯЗКА ==================

    private void autoBindAllFactories() {
        for (Factory f : new ArrayList<>(factories)) {
            tryAutoBindFactory(f);
        }
    }

    private void tryAutoBindFactory(Factory f) {
        List<Shop> sorted = new ArrayList<>(shops);
        sorted.sort(Comparator.comparingDouble(s ->
                Math.hypot(s.getOrigin().x - f.getOrigin().x,
                        s.getOrigin().y - f.getOrigin().y)));

        for (Shop s : sorted) {
            if (dispatcher.getFactoriesForShopCount(f) >= f.getMaxShops()) break;
            if (!dispatcher.canBind(f, s)) continue;
            bindFactoryToShop(f, s);
        }
    }

    // ================== ТИК СИМУЛЯЦИИ ==================

    public void tick(double dt) {
        notifications.update(dt);

        for (Factory f : factories) {
            f.tick(dt);
            if (f.getStorageCount() >= f.getMaxStorage()) {
                GameEvents.factoryFull(notifications, f);
            }
        }

        for (Car c : cars) {
            if (c.getState() == Car.State.IDLE) {
                // Если доставка на паузе — машины стоят и не выезжают.
                if (deliveryPaused) continue;

                Shop s = c.getShop();
                if (s.isFull()) {
                    GameEvents.shopFull(notifications, s);
                } else {
                    c.tryStartDelivery();
                }
            }
            c.tick(dt);
        }

        for (House h : houses) {
            if (h.tickAndCheckExit(dt)) {
                Product need = randomProduct();
                Shop target = findNearestShopWithProduct(h, need);
                if (target != null) {
                    Person p = new Person(h, target, need);
                    p.setShopFinder((n, visited) ->
                            findAlternativeShop(h, n, visited));
                    people.add(p);
                } else {
                    notifications.warn("Дом " + h.getOrigin()
                            + " не нашёл ни одного магазина");
                }
            }
        }

        Iterator<Person> it = people.iterator();
        while (it.hasNext()) {
            Person p = it.next();
            double earned = p.tick(dt);
            if (earned > 0) money += earned;

            if (p.isDone()) {
                if (!p.hasBought()) {
                    GameEvents.missingProduct(notifications,
                            p.getTargetShop(), p.getNeed());
                }
                it.remove();
            }
        }
    }

    private Product randomProduct() {
        Product[] all = Product.values();
        return all[rng.nextInt(all.length)];
    }

    private Shop findNearestShopWithProduct(House h, Product need) {
        Shop best = null;
        double bestD = Double.MAX_VALUE;
        for (Shop s : shops) {
            if (!s.hasProduct(need)) continue;
            double d = Math.hypot(s.getOrigin().x - h.getOrigin().x,
                    s.getOrigin().y - h.getOrigin().y);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best != null ? best : findNearestShop(h);
    }

    private Shop findNearestShop(House h) {
        Shop best = null;
        double bestD = Double.MAX_VALUE;
        for (Shop s : shops) {
            double d = Math.hypot(s.getOrigin().x - h.getOrigin().x,
                    s.getOrigin().y - h.getOrigin().y);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private Shop findAlternativeShop(House h, Product need, Set<Shop> visited) {
        Shop best = null;
        double bestD = Double.MAX_VALUE;
        for (Shop s : shops) {
            if (visited.contains(s)) continue;
            if (!s.hasProduct(need)) continue;
            double d = Math.hypot(s.getOrigin().x - h.getOrigin().x,
                    s.getOrigin().y - h.getOrigin().y);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    // ================== СТРОИТЕЛЬСТВО ==================

    public String tryBuild(Blueprint bp, Point origin) {
        if (bp == null) {
            notifications.error("Ничего не выбрано");
            return "Ничего не выбрано";
        }
        if (bp.kind == Blueprint.Kind.LAND) return tryBuyLand();
        if (money < bp.cost) {
            notifications.warn("Недостаточно денег: нужно "
                    + (int) bp.cost + ", есть " + (int) money);
            return "Недостаточно денег";
        }
        String err = PlacementRules.check(bp, origin, map);
        if (err != null) {
            notifications.error("Размещение: " + err);
            return err;
        }
        Building b = BuildingFactory.create(bp, origin);
        if (b == null) {
            notifications.error("Ошибка создания объекта");
            return "Ошибка создания объекта";
        }
        map.place(b);
        money -= bp.cost;

        if (b instanceof Factory f) {
            factories.add(f);
            GameEvents.built(notifications, f.getTypeName());
        } else if (b instanceof Shop s) {
            shops.add(s);
            GameEvents.built(notifications, "Магазин " + s.getOrigin());
        } else if (b instanceof House h) {
            houses.add(h);
            GameEvents.built(notifications, "Дом " + h.getOrigin());
        } else if (b instanceof Road road) {
            graphBuilder.rebuild();
            // Дорога изменилась — пересчитываем ВСЕ маршруты.
            // Может оказаться, что теперь путь стал короче.
            rebuildAllRoutes();
        }
        return null;
    }

    public String trySell(Point cell) {
        Building b = map.get(cell);
        if (b == null) {
            notifications.warn("Здесь ничего нет");
            return "Здесь ничего нет";
        }

        if (b instanceof Road road) {
            Point p = road.getOrigin();
            map.remove(b);
            money += b.getSellPrice();
            RoadRegistry.release(p);
            graphBuilder.rebuild();
            rebuildAllRoutes();
            return null;
        }

        String name = b.getTypeName() + " " + b.getOrigin();
        double price = b.getSellPrice();

        if (b instanceof Factory f) {
            factories.remove(f);
            removeCarsForFactory(f);
            dispatcher.unbindAll(f);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            return null;
        }
        if (b instanceof Shop s) {
            shops.remove(s);
            removeCarsForShop(s);
            dispatcher.unbindAll(s);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            return null;
        }
        if (b instanceof House h) {
            houses.remove(h);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            return null;
        }
        map.remove(b);
        money += price;
        GameEvents.sold(notifications, name, price);
        return null;
    }

    public String tryBuyLand() {
        if (money < Catalog.LAND_COST) {
            notifications.warn("Недостаточно денег для расширения карты (нужно "
                    + (int) Catalog.LAND_COST + ")");
            return "Недостаточно денег";
        }
        money -= Catalog.LAND_COST;
        landPurchases++;
        switch (landPurchases % 4) {
            case 1 -> mapMaxX += 5;
            case 2 -> mapMaxY += 5;
            case 3 -> mapMinX -= 5;
            case 0 -> mapMinY -= 5;
        }
        notifications.success("Карта расширена (+" + landPurchases + ")");
        return null;
    }

    // ================== УДАЛЕНИЕ МАШИН ==================

    private void removeCarsForFactory(Factory f) {
        Iterator<Car> it = cars.iterator();
        while (it.hasNext()) {
            Car c = it.next();
            if (c.getFactory() == f) { c.dispose(); it.remove(); }
        }
    }

    private void removeCarsForShop(Shop s) {
        Iterator<Car> it = cars.iterator();
        while (it.hasNext()) {
            Car c = it.next();
            if (c.getShop() == s) { c.dispose(); it.remove(); }
        }
    }

    // ================== ГРАФ ДОРОГ ==================

    private void updateGraphAround(Point p) {
        graphBuilder.updateVertex(p);
        for (Direction d : Direction.values()) {
            Point n = p.add(d.dx, d.dy);
            // ВСЕГДА обновляем соседа — если он не дорога, updateVertex удалит его
            graphBuilder.updateVertex(n);
        }
    }

    /**
     * Полный пересчёт всех маршрутов фабрика↔магазин.
     *
     * Вызывается при ЛЮБОМ изменении дорожной сети:
     *   - построена дорога,
     *   - снесена дорога,
     *   - повёрнута дорога.
     *
     * Для каждой привязки:
     *   - ищем новый кратчайший путь «туда» и «обратно»;
     *   - если оба есть — обновляем маршрут машины (даже в пути);
     *   - если хотя бы одного нет — переводим машину в NO_ROUTE
     *     и уведомляем игрока.
     *
     * Машины НЕ удаляются — они ждут, пока игрок построит дорогу.
     *
     * Если доставка на паузе (deliveryPaused) — маршруты всё равно
     * обновляются в диспетчере и в машинах, но машины принудительно
     * отзываются на фабрики и остаются в IDLE.
     */

    private void rebuildAllRoutes() {
        // Копируем entrySet, чтобы можно было безопасно модифицировать.
        List<Map.Entry<Shop, List<Factory>>> bindings =
                new ArrayList<>(dispatcher.getBindings().entrySet());

        System.out.println("========================================");
        System.out.println("[rebuildAllRoutes] START, bindings=" + bindings.size()
                + ", deliveryPaused=" + deliveryPaused
                + ", cars=" + cars.size());

        int i = 0;
        for (Map.Entry<Shop, List<Factory>> entry : bindings) {
            Shop shop = entry.getKey();
            for (Factory factory : new ArrayList<>(entry.getValue())) {
                i++;
                System.out.println("----------------------------------------");
                System.out.println("[rebuildAllRoutes] #" + i
                        + " pair factory=" + factory.getOrigin()
                        + " shop=" + shop.getOrigin());

                Point factoryExit = findAdjacentRoad(factory);
                Point shopExit = findAdjacentRoad(shop);

                Car car = findCar(factory, shop);

                System.out.println("  factoryExit=" + factoryExit
                        + " (hasRoad=" + (factoryExit != null && map.hasRoad(factoryExit))
                        + ", hasVertex=" + (factoryExit != null && graphBuilder.hasVertex(factoryExit))
                        + ")");
                System.out.println("  shopExit=" + shopExit
                        + " (hasRoad=" + (shopExit != null && map.hasRoad(shopExit))
                        + ", hasVertex=" + (shopExit != null && graphBuilder.hasVertex(shopExit))
                        + ")");
                System.out.println("  car=" + (car == null ? "null"
                        : ("state=" + car.getState()
                        + ", pos=(" + String.format("%.2f", car.getX())
                        + "," + String.format("%.2f", car.getY()) + ")"
                        + ", pathToShop=" + (car.getPathToShop() == null ? "null"
                        : car.getPathToShop().size())
                        + ", pathToFactory=" + (car.getPathToFactory() == null ? "null"
                        : car.getPathToFactory().size()))));

                // Нет выхода к дороге — маршрут невозможен.
                if (factoryExit == null || shopExit == null) {
                    System.out.println("  → ACTION: markNoRoute (нет выхода к дороге)");
                    if (car != null) car.markNoRoute();
                    GameEvents.noPath(notifications, factory, shop);
                    continue;
                }

                List<Point> toShop = pathFinder.findPath(factoryExit, shopExit);
                List<Point> toFactory = pathFinder.findPath(shopExit, factoryExit);

                System.out.println("  toShop=" + (toShop == null ? "null"
                        : (toShop.size() + " cells, first=" + toShop.get(0)
                        + ", last=" + toShop.get(toShop.size() - 1))));
                System.out.println("  toFactory=" + (toFactory == null ? "null"
                        : (toFactory.size() + " cells, first=" + toFactory.get(0)
                        + ", last=" + toFactory.get(toFactory.size() - 1))));

                if (toShop == null || toFactory == null) {
                    // Путь сломан — машина ждёт, уведомляем без спама.
                    System.out.println("  → ACTION: markNoRoute (путь не найден)");
                    if (car != null) car.markNoRoute();
                    GameEvents.noPath(notifications, factory, shop);
                } else {
                    // Путь есть — обновляем маршрут.
                    if (car != null) {
                        System.out.println("  → ACTION: updateRoutes (car exists)");
                        car.updateRoutes(toShop, toFactory);
                        // Если пауза — принудительно возвращаем на фабрику.
                        if (deliveryPaused) {
                            System.out.println("     + recall() (deliveryPaused)");
                            car.recall();
                        }
                    } else {
                        // Машины нет — создаём заново (на случай, если
                        // её удалили или привязка была создана вручную).
                        System.out.println("  → ACTION: new Car (car was null)");
                        Car newCar = new Car(factory, shop, toShop, toFactory);
                        newCar.setDeliveryEnabled(!deliveryPaused);
                        cars.add(newCar);
                    }
                    // Синхронизируем пути в диспетчере.
                    dispatcher.updateRoute(factory, shop, toShop, toFactory);
                }
            }
        }
        System.out.println("========================================");
    }

    /** Найти машину, обслуживающую данную пару фабрика↔магазин. */
    private Car findCar(Factory f, Shop s) {
        for (Car c : cars) {
            if (c.getFactory() == f && c.getShop() == s) return c;
        }
        return null;
    }

    // ================== ПРИВЯЗКА ==================

    private boolean bindFactoryToShop(Factory f, Shop shop) {
        Point factoryExit = findAdjacentRoad(f);
        Point shopExit = findAdjacentRoad(shop);
        if (factoryExit == null || shopExit == null) return false;

        List<Point> toShop = pathFinder.findPath(factoryExit, shopExit);
        if (toShop == null) {
            GameEvents.noPath(notifications, f, shop);
            return false;
        }
        List<Point> toFactory = pathFinder.findPath(shopExit, factoryExit);
        if (toFactory == null) {
            GameEvents.noPath(notifications, f, shop);
            return false;
        }
        if (!dispatcher.bind(f, shop, toShop, toFactory)) return false;

        Car car = new Car(f, shop, toShop, toFactory);
        // Учитываем текущее состояние паузы.
        car.setDeliveryEnabled(!deliveryPaused);
        cars.add(car);
        return true;
    }

    private int countBoundShops(Factory f) {
        int n = 0;
        for (List<Factory> list : dispatcher.getBindings().values()) {
            if (list.contains(f)) n++;
        }
        return n;
    }

    public Point findAdjacentRoad(Building b) {
        for (int x = 0; x < b.getSize().w; x++) {
            for (int y = 0; y < b.getSize().h; y++) {
                Point p = new Point(b.getOrigin().x + x, b.getOrigin().y + y);
                Point[] candidates = {
                        p.add(0, -1), p.add(0, 1),
                        p.add(-1, 0), p.add(1, 0)
                };
                for (Point c : candidates) {
                    if (map.hasRoad(c) && graphBuilder.hasVertex(c)) {  // ← проверка графа
                        return c;
                    }
                }
            }
        }
        return null;
    }

    // ================== ЭКОНОМИКА ==================

    public void spend(double amount) { if (money >= amount) money -= amount; }
    public void earn(double amount) { money += amount; }

    // ================== РУЧНАЯ ПРИВЯЗКА ==================

    public String bindManual(Factory f, Shop s) {
        if (f == null || s == null) return "Не выбраны фабрика или магазин";
        if (!factories.contains(f)) return "Фабрика уже снесена";
        if (!shops.contains(s))     return "Магазин уже снесён";
        if (dispatcher.isBound(f, s)) return "Эта фабрика уже привязана";
        if (countBoundShops(f) >= f.getMaxShops())
            return "Фабрика обслуживает максимум магазинов";
        if (!dispatcher.canBind(f, s)) {
            int perType = s.getMaxFactoriesPerType();
            int already = dispatcher.countFactoriesOfType(s, f.getProduct());
            return "Магазин уже принимает " + already + "/" + perType
                    + " фабрик типа «" + f.getProduct().title + "»";
        }
        if (bindFactoryToShop(f, s)) {
            notifications.success("Магазин " + s.getOrigin()
                    + " подключён к фабрике " + f.getOrigin());
            return null;
        }
        return "Не удалось построить маршрут";
    }

    public String unbindManual(Factory f, Shop s) {
        if (f == null || s == null) return "Не выбраны фабрика или магазин";
        if (!dispatcher.isBound(f, s)) return "Этой связи нет";
        dispatcher.unbind(f, s);
        Iterator<Car> it = cars.iterator();
        while (it.hasNext()) {
            Car c = it.next();
            if (c.getFactory() == f && c.getShop() == s) {
                c.dispose();
                it.remove();
            }
        }
        notifications.info("Связь разорвана");
        return null;
    }

    public String autoBindShopManual(Shop shop) {
        if (shop == null) return "Магазин не выбран";
        if (!shops.contains(shop)) return "Магазин уже снесён";
        List<Factory> sorted = new ArrayList<>(factories);
        sorted.sort(Comparator.comparingDouble(f ->
                Math.hypot(f.getOrigin().x - shop.getOrigin().x,
                        f.getOrigin().y - shop.getOrigin().y)));
        for (Factory f : sorted) {
            if (countBoundShops(f) >= f.getMaxShops()) continue;
            if (!dispatcher.canBind(f, shop)) continue;
            if (bindFactoryToShop(f, shop)) {
                notifications.success("Магазин подключён к фабрике");
                return null;
            }
        }
        return "Не нашлось подходящей фабрики";
    }

    // ================== ПОВОРОТ ДОРОГИ ==================

    public void rotateRoadAt(Point cell) {
        Building b = map.get(cell);
        if (!(b instanceof Road road)) return;
        if (road.isStraight()) return;
        if (road.isCross())    return;
        road.rotateCW();
        graphBuilder.rebuild();
        // Граф изменился — маршруты могли поменяться.
        rebuildAllRoutes();
    }

    // ================== ОТЗЫВ / ВОЗОБНОВЛЕНИЕ ДОСТАВКИ ==================

    /**
     * Отозвать все машины на их фабрики и запретить им выезжать.
     *
     * Действие:
     *   - выставляет deliveryPaused = true;
     *   - каждой машине ставит setDeliveryEnabled(false) и зовёт recall();
     *   - машины телепортируются на стартовые клетки своих маршрутов
     *     и остаются в IDLE, пока не будет вызван resumeAllCars().
     *
     * Маршруты при этом продолжают пересчитываться (если игрок построит
     * или снесёт дорогу) — но машины не поедут, пока пауза не снята.
     */
    public void recallAllCars() {
        deliveryPaused = true;
        for (Car c : cars) {
            c.setDeliveryEnabled(false);
            c.recall();
        }
        notifications.info("🚚 Все машины отозваны на фабрики");
    }

    /**
     * Возобновить доставку: машины снова начнут выезжать по маршрутам.
     */
    public void resumeAllCars() {
        deliveryPaused = false;
        rebuildAllRoutes();
        for (Car c : cars) {
            c.setDeliveryEnabled(true);
        }
        notifications.success("🚚 Доставка возобновлена");
    }

    /** На паузе ли доставка. */
    public boolean isDeliveryPaused() {
        return deliveryPaused;
    }

    // ================== ГЕТТЕРЫ ==================

    public GameMap getMap() { return map; }
    public double getMoney() { return money; }
    public List<Factory> getFactories() { return factories; }
    public List<Shop> getShops() { return shops; }
    public List<House> getHouses() { return houses; }
    public List<Car> getCars() { return cars; }
    public List<Person> getPeople() { return people; }

    public Dispatcher getDispatcher() { return dispatcher; }
    public PathFinder getPathFinder() { return pathFinder; }
    public GraphBuilder getGraphBuilder() { return graphBuilder; }
    public NotificationCenter getNotifications() { return notifications; }
    public int getLandPurchases() { return landPurchases; }

    public int getMapMinX() { return mapMinX; }
    public int getMapMaxX() { return mapMaxX; }
    public int getMapMinY() { return mapMinY; }
    public int getMapMaxY() { return mapMaxY; }
}