package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Диспетчер маршрутов фабрика ↔ магазин.
 *
 * Для каждой пары хранит ДВА пути:
 *   - routesToShop:    от выхода фабрики к входу магазина;
 *   - routesToFactory: от входа магазина обратно к выходу фабрики.
 *
 * Оба пути независимы и строятся отдельными вызовами Дейкстры.
 * Это единственный корректный способ работать с односторонними дорогами:
 * обратный путь — не «реверс» прямого, а самостоятельный маршрут по циклу.
 */
public class Dispatcher {

    private final PathFinder pathFinder;

    /** Магазин → список фабрик, которые его обслуживают. */
    private final Map<Shop, List<Factory>> shopToFactories = new HashMap<>();

    /** Магазин → фабрика → путь «туда». */
    private final Map<Shop, Map<Factory, List<Point>>> routesToShop = new HashMap<>();

    /** Магазин → фабрика → путь «обратно». */
    private final Map<Shop, Map<Factory, List<Point>>> routesToFactory = new HashMap<>();

    public Dispatcher(PathFinder pathFinder) {
        this.pathFinder = pathFinder;
    }

    // ==================== ПРИВЯЗКА ====================

    /**
     * Привязать магазин к фабрике.
     * Пути уже найдены вызывающим (Game) — здесь только регистрация.
     * Возвращает false, если лимит магазина превышен или пара уже существует.
     */
    public boolean bind(Factory factory, Shop shop,
                        List<Point> toShop, List<Point> toFactory) {
        if (factory == null || shop == null) return false;
        if (toShop == null || toFactory == null) return false;

        List<Factory> list = shopToFactories.computeIfAbsent(shop, s -> new ArrayList<>());
        if (list.size() >= shop.getMaxFactories()) return false;
        if (list.contains(factory)) return false;

        list.add(factory);
        routesToShop.computeIfAbsent(shop, s -> new HashMap<>())
                .put(factory, new ArrayList<>(toShop));
        routesToFactory.computeIfAbsent(shop, s -> new HashMap<>())
                .put(factory, new ArrayList<>(toFactory));
        return true;
    }

    /**
     * Удобная перегрузка: Dispatcher сам ищет оба пути.
     * Возвращает true, если оба пути существуют и привязка выполнена.
     */
    public boolean bind(Factory factory, Shop shop,
                        Point factoryExit, Point shopEntry) {
        List<Point> toShop = pathFinder.findPath(factoryExit, shopEntry);
        if (toShop == null) return false;
        List<Point> toFactory = pathFinder.findPath(shopEntry, factoryExit);
        if (toFactory == null) return false;
        return bind(factory, shop, toShop, toFactory);
    }

    // ==================== ОТВЯЗКА ====================

    /** Отвязать одну пару фабрика↔магазин. */
    public void unbind(Factory factory, Shop shop) {
        List<Factory> list = shopToFactories.get(shop);
        if (list != null) {
            list.remove(factory);
            if (list.isEmpty()) shopToFactories.remove(shop);
        }

        Map<Factory, List<Point>> m1 = routesToShop.get(shop);
        if (m1 != null) {
            m1.remove(factory);
            if (m1.isEmpty()) routesToShop.remove(shop);
        }

        Map<Factory, List<Point>> m2 = routesToFactory.get(shop);
        if (m2 != null) {
            m2.remove(factory);
            if (m2.isEmpty()) routesToFactory.remove(shop);
        }
    }

    /** Отвязать магазин от ВСЕХ фабрик (например, при сносе магазина). */
    public void unbindAll(Shop shop) {
        if (shop == null) return;
        shopToFactories.remove(shop);
        routesToShop.remove(shop);
        routesToFactory.remove(shop);
    }

    /**
     * Отвязать фабрику от ВСЕХ магазинов (например, при сносе фабрики).
     * Чистит и список магазинов, и оба маршрута. Пустые записи удаляются,
     * чтобы не оставалось висячих ссылок на снесённую фабрику.
     */
    public void unbindAll(Factory factory) {
        if (factory == null) return;

        // Копируем ключи — во время итерации будем удалять записи из map.
        for (Shop shop : new ArrayList<>(shopToFactories.keySet())) {
            List<Factory> list = shopToFactories.get(shop);
            if (list != null) {
                list.remove(factory);
                if (list.isEmpty()) shopToFactories.remove(shop);
            }

            Map<Factory, List<Point>> m1 = routesToShop.get(shop);
            if (m1 != null) {
                m1.remove(factory);
                if (m1.isEmpty()) routesToShop.remove(shop);
            }

            Map<Factory, List<Point>> m2 = routesToFactory.get(shop);
            if (m2 != null) {
                m2.remove(factory);
                if (m2.isEmpty()) routesToFactory.remove(shop);
            }
        }
    }

    // ==================== ДОСТУП ====================

    public Map<Shop, List<Factory>> getBindings() {
        return shopToFactories;
    }

    /** Путь «туда»: от фабрики к магазину. */
    public List<Point> getRoute(Shop shop, Factory factory) {
        Map<Factory, List<Point>> m = routesToShop.get(shop);
        return m == null ? null : m.get(factory);
    }

    /** Путь «обратно»: от магазина к фабрике. */
    public List<Point> getRouteBack(Shop shop, Factory factory) {
        Map<Factory, List<Point>> m = routesToFactory.get(shop);
        return m == null ? null : m.get(factory);
    }

    public List<Factory> getFactoriesFor(Shop shop) {
        return shopToFactories.getOrDefault(shop, Collections.emptyList());
    }

    public boolean isBound(Factory factory, Shop shop) {
        List<Factory> list = shopToFactories.get(shop);
        return list != null && list.contains(factory);
    }

    // ==================== ПЕРЕСЧЁТ МАРШРУТОВ ====================

    /**
     * Пересчитать оба маршрута для пары.
     * Вызывается после изменения дорог — если путь сломан, вернёт false.
     */
    public boolean rebuildRoute(Factory factory, Shop shop,
                                Point factoryExit, Point shopEntry) {
        if (!isBound(factory, shop)) return false;

        List<Point> toShop = pathFinder.findPath(factoryExit, shopEntry);
        if (toShop == null) return false;
        List<Point> toFactory = pathFinder.findPath(shopEntry, factoryExit);
        if (toFactory == null) return false;

        routesToShop.get(shop).put(factory, toShop);
        routesToFactory.get(shop).put(factory, toFactory);
        return true;
    }

    /** Сколько магазинов привязано к фабрике (по факту). */
    public int getFactoriesForShopCount(Factory f) {
        int n = 0;
        for (List<Factory> list : shopToFactories.values()) {
            if (list.contains(f)) n++;
        }
        return n;
    }

    /** Список магазинов, привязанных к фабрике. */
    public List<Shop> getShopsFor(Factory f) {
        List<Shop> result = new ArrayList<>();
        for (Map.Entry<Shop, List<Factory>> e : shopToFactories.entrySet()) {
            if (e.getValue().contains(f)) result.add(e.getKey());
        }
        return result;
    }

    /**
     * Сколько фабрик данного типа товара привязано к магазину.
     * Используется для проверки лимита «1 фабрика каждого типа на S1x1».
     */
    public int countFactoriesOfType(Shop shop, Product type) {
        List<Factory> list = shopToFactories.get(shop);
        if (list == null) return 0;
        int n = 0;
        for (Factory f : list) {
            if (f.getProduct() == type) n++;
        }
        return n;
    }

    /**
     * Проверка: можно ли привязать фабрику к магазину с учётом лимита
     * на тип товара.
     */
    public boolean canBind(Factory factory, Shop shop) {
        if (factory == null || shop == null) return false;
        if (isBound(factory, shop)) return false;

        int alreadyOfType = countFactoriesOfType(shop, factory.getProduct());
        int limitPerType = shop.getMaxFactoriesPerType();

        return alreadyOfType < limitPerType;
    }
    public void updateRoute(Factory factory, Shop shop,
                            List<Point> toShop, List<Point> toFactory) {
        if (!isBound(factory, shop)) return;
        routesToShop.computeIfAbsent(shop, s -> new HashMap<>())
                .put(factory, new ArrayList<>(toShop));
        routesToFactory.computeIfAbsent(shop, s -> new HashMap<>())
                .put(factory, new ArrayList<>(toFactory));
    }
}