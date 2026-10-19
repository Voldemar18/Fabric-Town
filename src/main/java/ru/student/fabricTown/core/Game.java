package ru.student.fabricTown.core;

import ru.student.fabricTown.agents.CargoSink;
import ru.student.fabricTown.agents.CargoSource;
import ru.student.fabricTown.agents.Person;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.agents.UniversalCar;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.BuildingFactory;
import ru.student.fabricTown.logic.Catalog;
import ru.student.fabricTown.logic.Dispatcher;
import ru.student.fabricTown.logic.GameEvents;
import ru.student.fabricTown.logic.GraphBuilder;
import ru.student.fabricTown.logic.PathFinder;
import ru.student.fabricTown.logic.PlacementRules;
import ru.student.fabricTown.logic.Recipes;
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
 *   - для любой пары поставщик↔получатель есть и прямой, и обратный путь;
 *   - машина всегда может развернуться через ближайший перекрёсток.
 *
 * Все машины — это {@link UniversalCar}. Он работает с двумя интерфейсами:
 *   - {@link CargoSource} — откуда брать товар (Factory, Processor);
 *   - {@link CargoSink}   — куда выгружать (Shop, Processor).
 *
 * Три логистические линии используют один и тот же класс:
 *   - Фабрика → Магазин         (src=Factory,   dst=Shop);
 *   - Фабрика → Переработчик    (src=Factory,   dst=Processor);
 *   - Переработчик → Магазин    (src=Processor, dst=Shop).
 *
 * Динамическое обновление маршрутов:
 *   При ЛЮБОМ изменении дорожной сети (постройка, снос, поворот)
 *   вызывается {@link #rebuildAllRoutes()}, который заново ищет пути
 *   «туда» и «обратно» для каждой привязки. Если оба пути есть —
 *   машина обновляет маршрут (даже в пути). Если хотя бы одного нет —
 *   машина уходит в NO_ROUTE и ждёт, пока игрок достроит дорогу.
 *
 * Отзыв машин:
 *   {@link #recallAllCars()} — все машины мгновенно возвращаются
 *   к поставщикам и перестают выезжать.
 *   {@link #resumeAllCars()} — возобновляет доставку.
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
    private final List<Processor> processors = new ArrayList<>();
    private final List<UniversalCar> cars = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();

    private final Random rng = new Random();

    private int landPurchases = 0;

    /** Границы карты — стартовые 0..19 (карта 20×20). */
    private int mapMinX = 0, mapMaxX = 19;
    private int mapMinY = 0, mapMaxY = 19;

    /**
     * Приостановлена ли доставка машин.
     *   false — машины работают как обычно;
     *   true  — машины отозваны и не выезжают.
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
//        autoBindAllFactories();
//        autoBindAllProcessors();

        notifications.success("Город основан: "
                + factories.size() + " фабрик, "
                + shops.size() + " магазинов, "
                + processors.size() + " переработчиков, "
                + houses.size() + " домов");
    }

    // ================== 1. ДОРОЖНАЯ СЕТКА 20×20 ==================

    private void initRoadGrid() {
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

        for (int x : vSouthCols) {
            for (int y : hEastRows) replaceWithRoad(new Point(x, y), RoadShape.CROSS);
        }
        for (int x : vNorthCols) {
            for (int y : hEastRows) replaceWithRoad(new Point(x, y), RoadShape.CROSS);
        }
        for (int x : vSouthCols) {
            for (int y : hWestRows) replaceWithRoad(new Point(x, y), RoadShape.CROSS);
        }
        for (int x : vNorthCols) {
            for (int y : hWestRows) replaceWithRoad(new Point(x, y), RoadShape.CROSS);
        }
    }

    private void replaceWithRoad(Point p, RoadShape shape) {
        Building old = map.get(p);
        if (old != null) map.remove(old);
        map.place(new Road(p, shape));
    }

    // ================== 2. ЗДАНИЯ ==================

    private void initBuildings() {
        // Обычные фабрики
        placeFactory(new Factory(new Point(2, 2), BuildingSize.S1x1, Product.CHICKEN));
        placeFactory(new Factory(new Point(8, 2), BuildingSize.S2x2, Product.BEEF));
        placeFactory(new Factory(new Point(14, 2), BuildingSize.S3x3, Product.FRUIT));
        placeFactory(new Factory(new Point(2, 8), BuildingSize.S1x2, Product.VEGETABLES));
        placeFactory(new Factory(new Point(8, 8), BuildingSize.S2x2, Product.BREAD));
        placeFactory(new Factory(new Point(14, 8), BuildingSize.S2x2, Product.CHICKEN));

        // Фабрики-ингредиенты для шоколада
        placeFactory(new Factory(new Point(16, 4), BuildingSize.S1x1, Product.MILK));
        placeFactory(new Factory(new Point(16, 6), BuildingSize.S1x1, Product.COCOA));

        // Переработчик шоколада
        placeProcessor(new Processor(new Point(10, 8),
                BuildingSize.S2x2, Recipes.CHOCOLATE));

        // Магазины
        placeShop(new Shop(new Point(2, 14), BuildingSize.S1x1));
        placeShop(new Shop(new Point(8, 14), BuildingSize.S2x2));
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

    private void placeProcessor(Processor p) {
        if (!map.canPlace(p)) {
            System.err.println("[Game] Не могу поставить переработчик "
                    + p.getOrigin() + " — место занято");
            return;
        }
        map.place(p);
        processors.add(p);
    }

    // ================== 3. АВТОПРИВЯЗКА ==================

    private void autoBindAllFactories() {
        for (Factory f : new ArrayList<>(factories)) {
            tryAutoBindFactory(f);
        }
    }

    private void tryAutoBindFactory(Factory f) {
        // Фабрика может возить свою продукцию в магазины как обычный товар.
        List<Shop> sorted = new ArrayList<>(shops);
        sorted.sort(Comparator.comparingDouble(s ->
                Math.hypot(s.getOrigin().x - f.getOrigin().x,
                        s.getOrigin().y - f.getOrigin().y)));

        for (Shop s : sorted) {
            if (countBoundShops(f) >= f.getMaxShops()) break;
            if (!dispatcher.canBind(f, s)) continue;
            bindFactoryToShop(f, s);
        }
    }

    private void autoBindAllProcessors() {
        for (Processor p : new ArrayList<>(processors)) {
            tryAutoBindProcessor(p);
        }
    }

    /**
     * Автопривязка переработчика:
     *   1) все фабрики-ингредиенты, которые принимает рецепт,
     *      ближайшие к переработчику — привязываем (Ф → П);
     *   2) ближайшие магазины — привязываем как получателей
     *      готового продукта (П → М).
     */
    private void tryAutoBindProcessor(Processor p) {
        // 1) ингредиенты
        List<Factory> sortedFactories = new ArrayList<>(factories);
        sortedFactories.sort(Comparator.comparingDouble(f ->
                Math.hypot(f.getOrigin().x - p.getOrigin().x,
                        f.getOrigin().y - p.getOrigin().y)));

        for (Factory f : sortedFactories) {
            if (!p.acceptsIngredient(f.getProduct())) continue;
            if (!dispatcher.canBindToProcessor(f, p)) continue;
            bindFactoryToProcessor(f, p);
        }

        // 2) магазины
        List<Shop> sortedShops = new ArrayList<>(shops);
        sortedShops.sort(Comparator.comparingDouble(s ->
                Math.hypot(s.getOrigin().x - p.getOrigin().x,
                        s.getOrigin().y - p.getOrigin().y)));

        int shopsBound = 0;
        for (Shop s : sortedShops) {
            if (shopsBound >= p.getMaxShops()) break;
            if (dispatcher.getProcessorsForShop(s).contains(p)) continue;
            if (bindProcessorToShop(p, s)) shopsBound++;
        }
    }

    // ================== ТИК СИМУЛЯЦИИ ==================

    public void tick(double dt) {
        notifications.update(dt);

        // --- Фабрики ---
        for (Factory f : factories) {
            f.tick(dt);
            if (f.getStorageCount() >= f.getMaxStorage()) {
                GameEvents.factoryFull(notifications, f);
            }
        }

        // --- Переработчики: производство ---
        for (Processor p : processors) {
            p.tick(dt);
        }

        // --- Все машины (один цикл) ---
        for (UniversalCar c : cars) {
            if (c.getState() == UniversalCar.State.IDLE) {
                if (deliveryPaused) {
                    // машина уже стоит, но tick всё равно нужен
                } else if (!c.getSink().hasFreeSpace()) {
                    // Получатель полон: магазину — уведомление,
                    // переработчику — просто ждём без спама.
                    if (c.getSink() instanceof Shop s) {
                        GameEvents.shopFull(notifications, s);
                    }
                } else {
                    c.tryStartDelivery();
                }
            }
            c.tick(dt);
        }

        // --- Люди ---
        for (House h : houses) {
            if (h.tickAndCheckExit(dt)) {
                Product need = randomProduct();
                Shop target = findNearestShopWithProduct(h, need);
                if (target != null) {
                    Person p = new Person(h, target, need);
                    p.setShopFinder((n, visited) -> findAlternativeShop(h, n, visited));
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

        if (b instanceof Processor p) {
            processors.add(p);
            //autoBindAllProcessors();
            rebuildAllRoutes();
            GameEvents.built(notifications, p.getTypeName());
        } else if (b instanceof Factory f) {
            factories.add(f);
            //autoBindAllFactories();
            //autoBindAllProcessors();
            rebuildAllRoutes();
            GameEvents.built(notifications, f.getTypeName());
        } else if (b instanceof Shop s) {
            shops.add(s);
            //autoBindAllProcessors();
            rebuildAllRoutes();
            GameEvents.built(notifications, "Магазин " + s.getOrigin());
        } else if (b instanceof House h) {
            houses.add(h);
            GameEvents.built(notifications, "Дом " + h.getOrigin());
        } else if (b instanceof Road road) {
            graphBuilder.rebuild();
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

        if (b instanceof Processor p) {
            processors.remove(p);
            removeCarsInvolving(p);
            dispatcher.unbindAllForProcessor(p);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            rebuildAllRoutes();
            return null;
        }
        if (b instanceof Factory f) {
            factories.remove(f);
            removeCarsInvolving(f);
            dispatcher.unbindAll(f);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            rebuildAllRoutes();
            return null;
        }
        if (b instanceof Shop s) {
            shops.remove(s);
            removeCarsInvolving(s);
            dispatcher.unbindAll(s);
            map.remove(b);
            money += price;
            GameEvents.sold(notifications, name, price);
            rebuildAllRoutes();
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

    /**
     * Удалить все машины, у которых поставщик или получатель — объект o.
     * Работает для Factory, Shop и Processor одинаково.
     */
    private void removeCarsInvolving(Object o) {
        Iterator<UniversalCar> it = cars.iterator();
        while (it.hasNext()) {
            UniversalCar c = it.next();
            if (c.getSource() == o || c.getSink() == o) {
                c.dispose();
                it.remove();
            }
        }
    }

    // ================== ГРАФ ДОРОГ ==================

    private void updateGraphAround(Point p) {
        graphBuilder.updateVertex(p);
        for (Direction d : Direction.values()) {
            Point n = p.add(d.dx, d.dy);
            graphBuilder.updateVertex(n);
        }
    }

    // ================== ПЕРЕСЧЁТ МАРШРУТОВ ==================

    /**
     * Полный пересчёт всех маршрутов:
     *   - Фабрика ↔ Магазин;
     *   - Фабрика ↔ Переработчик;
     *   - Переработчик ↔ Магазин.
     * Все линии одинаковые с точки зрения логики — используем один помощник.
     */
    private void rebuildAllRoutes() {
        // 1) Фабрика → Магазин
        for (var e : new ArrayList<>(dispatcher.getBindings().entrySet())) {
            Shop shop = e.getKey();
            for (Factory f : new ArrayList<>(e.getValue())) {
                rebuildRouteFor(f, shop,
                        () -> GameEvents.noPath(notifications, f, shop));
            }
        }

        // 2) Фабрика → Переработчик
        for (var e : new ArrayList<>(dispatcher.getProcessorBindings().entrySet())) {
            Processor p = e.getKey();
            for (Factory f : new ArrayList<>(e.getValue())) {
                rebuildRouteFor(f, p,
                        () -> GameEvents.noPath(notifications, f, p));
            }
        }

        // 3) Переработчик → Магазин
        for (Processor p : new ArrayList<>(processors)) {
            for (Shop s : new ArrayList<>(dispatcher.getShopsForProcessor(p))) {
                rebuildRouteFor(p, s,
                        () -> GameEvents.noPath(notifications, p, s));
            }
        }
    }

    /**
     * Пересчитать одну пару «источник → получатель».
     * Если машины ещё нет — создать. Если есть — обновить маршрут
     * (или увести в NO_ROUTE при отсутствии пути).
     */
    private void rebuildRouteFor(CargoSource src, CargoSink dst,
                                 Runnable notFoundAction) {
        // Для поиска выхода нужен Building — но у нас есть только интерфейсы.
        // Все реальные реализации (Factory, Shop, Processor) — это Building,
        // так что каст безопасен. Если вдруг появится другой поставщик
        // без Building — вернёт null и уйдёт в notFound.
        Building srcB = (src instanceof Building b) ? b : null;
        Building dstB = (dst instanceof Building b) ? b : null;

        Point srcExit = (srcB != null) ? findAdjacentRoad(srcB) : null;
        Point dstExit = (dstB != null) ? findAdjacentRoad(dstB) : null;

        UniversalCar car = findCar(src, dst);

        if (srcExit == null || dstExit == null) {
            if (car != null) car.markNoRoute();
            notFoundAction.run();
            return;
        }

        List<Point> toSink   = pathFinder.findPath(srcExit, dstExit);
        List<Point> toSource = pathFinder.findPath(dstExit, srcExit);

        if (toSink == null || toSource == null) {
            if (car != null) car.markNoRoute();
            notFoundAction.run();
            return;
        }

        if (car != null) {
            car.updateRoutes(toSink, toSource);
            if (deliveryPaused) car.recall();
        } else {
            UniversalCar newCar = new UniversalCar(src, dst, toSink, toSource);
            newCar.setDeliveryEnabled(!deliveryPaused);
            cars.add(newCar);
        }

        // Синхронизируем пути в диспетчере.
        updateDispatcherRoute(src, dst, toSink, toSource);
    }

    private void updateDispatcherRoute(CargoSource src, CargoSink dst,
                                       List<Point> toSink, List<Point> toSource) {
        if (src instanceof Factory f && dst instanceof Shop s) {
            dispatcher.updateRoute(f, s, toSink, toSource);
        } else if (src instanceof Factory f && dst instanceof Processor p) {
            dispatcher.updateFactoryToProcessorRoute(f, p, toSink, toSource);
        } else if (src instanceof Processor p && dst instanceof Shop s) {
            dispatcher.updateProcessorToShopRoute(p, s, toSink, toSource);
        }
    }

    private UniversalCar findCar(CargoSource src, CargoSink dst) {
        for (UniversalCar c : cars) {
            if (c.getSource() == src && c.getSink() == dst) return c;
        }
        return null;
    }

    // ================== ПРИВЯЗКА ==================

    private boolean bindFactoryToShop(Factory f, Shop shop) {
        Point fe = findAdjacentRoad(f);
        Point se = findAdjacentRoad(shop);
        if (fe == null || se == null) return false;

        List<Point> toShop = pathFinder.findPath(fe, se);
        if (toShop == null) {
            GameEvents.noPath(notifications, f, shop);
            return false;
        }
        List<Point> toFactory = pathFinder.findPath(se, fe);
        if (toFactory == null) {
            GameEvents.noPath(notifications, f, shop);
            return false;
        }
        if (!dispatcher.bind(f, shop, toShop, toFactory)) return false;

        addCar(f, shop, toShop, toFactory);
        return true;
    }

    private boolean bindFactoryToProcessor(Factory f, Processor p) {
        Point fe = findAdjacentRoad(f);
        Point pe = findAdjacentRoad(p);
        if (fe == null || pe == null) return false;

        List<Point> toProcessor = pathFinder.findPath(fe, pe);
        if (toProcessor == null) {
            GameEvents.noPath(notifications, f, p);
            return false;
        }
        List<Point> toFactory = pathFinder.findPath(pe, fe);
        if (toFactory == null) {
            GameEvents.noPath(notifications, f, p);
            return false;
        }
        if (!dispatcher.bindFactoryToProcessor(f, p, toProcessor, toFactory)) return false;

        addCar(f, p, toProcessor, toFactory);
        return true;
    }

    private boolean bindProcessorToShop(Processor p, Shop shop) {
        Point pe = findAdjacentRoad(p);
        Point se = findAdjacentRoad(shop);
        if (pe == null || se == null) return false;

        List<Point> toShop = pathFinder.findPath(pe, se);
        if (toShop == null) {
            GameEvents.noPath(notifications, p, shop);
            return false;
        }
        List<Point> toProcessor = pathFinder.findPath(se, pe);
        if (toProcessor == null) {
            GameEvents.noPath(notifications, p, shop);
            return false;
        }
        if (!dispatcher.bindProcessorToShop(p, shop, toShop, toProcessor)) return false;

        addCar(p, shop, toShop, toProcessor);
        return true;
    }

    /** Создать машину и сразу учесть паузу доставки. */
    private void addCar(CargoSource src, CargoSink dst,
                        List<Point> toSink, List<Point> toSource) {
        UniversalCar car = new UniversalCar(src, dst, toSink, toSource);
        car.setDeliveryEnabled(!deliveryPaused);
        cars.add(car);
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
                    if (map.hasRoad(c) && graphBuilder.hasVertex(c)) {
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

    // ================== РУЧНАЯ ПРИВЯЗКА (Фабрика ↔ Магазин) ==================

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
        removeCar(f, s);
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

    // ================== РУЧНАЯ ПРИВЯЗКА: Фабрика → Переработчик ==================

    public String bindFactoryToProcessorManual(Factory f, Processor p) {
        if (f == null || p == null) return "Не выбраны фабрика или переработчик";
        if (!factories.contains(f)) return "Фабрика уже снесена";
        if (!processors.contains(p)) return "Переработчик уже снесён";
        if (!p.acceptsIngredient(f.getProduct()))
            return "Переработчик не принимает «" + f.getProduct().title + "»";
        if (!dispatcher.canBindToProcessor(f, p)) {
            int lim = p.getMaxFactoriesPerIngredient();
            int already = dispatcher.countFactoriesOfIngredient(p, f.getProduct());
            return "Переработчик уже принимает " + already + "/" + lim
                    + " фабрик типа «" + f.getProduct().title + "»";
        }
        if (bindFactoryToProcessor(f, p)) {
            notifications.success("Фабрика " + f.getOrigin()
                    + " подключена к переработчику " + p.getOrigin());
            return null;
        }
        return "Не удалось построить маршрут";
    }

    public String unbindFactoryFromProcessorManual(Factory f, Processor p) {
        if (f == null || p == null) return "Не выбраны фабрика или переработчик";
        dispatcher.unbindFactoryFromProcessor(f, p);
        removeCar(f, p);
        notifications.info("Связь фабрика↔переработчик разорвана");
        return null;
    }

    // ================== РУЧНАЯ ПРИВЯЗКА: Переработчик → Магазин ==================

    public String bindProcessorToShopManual(Processor p, Shop s) {
        if (p == null || s == null) return "Не выбраны переработчик или магазин";
        if (!processors.contains(p)) return "Переработчик уже снесён";
        if (!shops.contains(s))      return "Магазин уже снесён";
        if (dispatcher.getProcessorsForShop(s).contains(p))
            return "Этот переработчик уже привязан к магазину";
        List<Shop> current = dispatcher.getShopsForProcessor(p);
        if (current.size() >= p.getMaxShops())
            return "Переработчик обслуживает максимум магазинов ("
                    + p.getMaxShops() + ")";
        if (bindProcessorToShop(p, s)) {
            notifications.success("Переработчик " + p.getOrigin()
                    + " подключён к магазину " + s.getOrigin());
            return null;
        }
        return "Не удалось построить маршрут";
    }

    public String unbindProcessorFromShopManual(Processor p, Shop s) {
        if (p == null || s == null) return "Не выбраны переработчик или магазин";
        dispatcher.unbindProcessorFromShop(p, s);
        removeCar(p, s);
        notifications.info("Связь переработчик↔магазин разорвана");
        return null;
    }

    /** Удалить одну конкретную машину по паре src/dst. */
    private void removeCar(CargoSource src, CargoSink dst) {
        Iterator<UniversalCar> it = cars.iterator();
        while (it.hasNext()) {
            UniversalCar c = it.next();
            if (c.getSource() == src && c.getSink() == dst) {
                c.dispose();
                it.remove();
            }
        }
    }

    // ================== ПОВОРОТ ДОРОГИ ==================

    public void rotateRoadAt(Point cell) {
        Building b = map.get(cell);
        if (!(b instanceof Road road)) return;
        if (road.isStraight()) return;
        if (road.isCross())    return;
        road.rotateCW();
        graphBuilder.rebuild();
        rebuildAllRoutes();
    }

    // ================== ОТЗЫВ / ВОЗОБНОВЛЕНИЕ ДОСТАВКИ ==================

    public void recallAllCars() {
        deliveryPaused = true;
        for (UniversalCar c : cars) {
            c.setDeliveryEnabled(false);
            c.recall();
        }
        notifications.info("🚚 Все машины отозваны к поставщикам");
    }

    public void resumeAllCars() {
        deliveryPaused = false;
        rebuildAllRoutes();
        for (UniversalCar c : cars) c.setDeliveryEnabled(true);
        notifications.success("🚚 Доставка возобновлена");
    }

    public boolean isDeliveryPaused() {
        return deliveryPaused;
    }

    // ================== ГЕТТЕРЫ ==================

    public GameMap getMap() { return map; }
    public double getMoney() { return money; }
    public List<Factory> getFactories() { return factories; }
    public List<Shop> getShops() { return shops; }
    public List<House> getHouses() { return houses; }
    public List<Processor> getProcessors() { return processors; }
    public List<UniversalCar> getCars() { return cars; }
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