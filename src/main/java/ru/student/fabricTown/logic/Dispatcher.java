package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Processor;
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


// --- новые поля ---
    /** Переработчик → список фабрик (поставляют ингредиенты). */
    private final Map<Processor, List<Factory>> processorToFactories = new HashMap<>();
    private final Map<Processor, Map<Factory, List<Point>>> routesFactoryToProcessor = new HashMap<>();
    private final Map<Processor, Map<Factory, List<Point>>> routesProcessorToFactory = new HashMap<>();

    /** Магазин → список переработчиков (поставляют готовый продукт). */
    private final Map<Shop, List<Processor>> shopToProcessors = new HashMap<>();
    private final Map<Shop, Map<Processor, List<Point>>> routesProcessorToShop = new HashMap<>();
    private final Map<Shop, Map<Processor, List<Point>>> routesShopToProcessor = new HashMap<>();

    // --- привязка фабрики к переработчику ---
    public boolean bindFactoryToProcessor(Factory factory, Processor processor,
                                          List<Point> toProcessor, List<Point> toFactory) {
        if (factory == null || processor == null) return false;
        if (toProcessor == null || toFactory == null) return false;

        List<Factory> list = processorToFactories.computeIfAbsent(
                processor, p -> new ArrayList<>());
        if (list.contains(factory)) return false;

        list.add(factory);
        routesFactoryToProcessor.computeIfAbsent(processor, p -> new HashMap<>())
                .put(factory, new ArrayList<>(toProcessor));
        routesProcessorToFactory.computeIfAbsent(processor, p -> new HashMap<>())
                .put(factory, new ArrayList<>(toFactory));
        return true;
    }

    // --- привязка переработчика к магазину ---
    public boolean bindProcessorToShop(Processor processor, Shop shop,
                                       List<Point> toShop, List<Point> toProcessor) {
        if (processor == null || shop == null) return false;
        if (toShop == null || toProcessor == null) return false;

        List<Processor> list = shopToProcessors.computeIfAbsent(
                shop, s -> new ArrayList<>());
        if (list.size() >= processor.getMaxShops()) return false;
        if (list.contains(processor)) return false;

        list.add(processor);
        routesProcessorToShop.computeIfAbsent(shop, s -> new HashMap<>())
                .put(processor, new ArrayList<>(toShop));
        routesShopToProcessor.computeIfAbsent(shop, s -> new HashMap<>())
                .put(processor, new ArrayList<>(toProcessor));
        return true;
    }

    // --- геттеры ---
    public Map<Processor, List<Factory>> getProcessorBindings() { return processorToFactories; }
    public List<Factory> getFactoriesForProcessor(Processor p) {
        return processorToFactories.getOrDefault(p, Collections.emptyList());
    }
    public List<Processor> getProcessorsForShop(Shop s) {
        return shopToProcessors.getOrDefault(s, Collections.emptyList());
    }
    public List<Shop> getShopsForProcessor(Processor p) {
        List<Shop> res = new ArrayList<>();
        for (var e : shopToProcessors.entrySet()) {
            if (e.getValue().contains(p)) res.add(e.getKey());
        }
        return res;
    }
    public List<Point> getFactoryToProcessorRoute(Processor p, Factory f) {
        var m = routesFactoryToProcessor.get(p);
        return m == null ? null : m.get(f);
    }
    public List<Point> getProcessorToShopRoute(Shop s, Processor p) {
        var m = routesProcessorToShop.get(s);
        return m == null ? null : m.get(p);
    }
    public List<Point> getProcessorToFactoryRoute(Processor p, Factory f) {
        var m = routesProcessorToFactory.get(p);
        return m == null ? null : m.get(f);
    }
    public List<Point> getShopToProcessorRoute(Shop s, Processor p) {
        var m = routesShopToProcessor.get(s);
        return m == null ? null : m.get(p);
    }

    /** Сколько фабрик данного типа ингредиента уже привязано к переработчику. */
    public int countFactoriesOfIngredient(Processor processor, Product type) {
        List<Factory> list = processorToFactories.get(processor);
        if (list == null) return 0;
        int n = 0;
        for (Factory f : list) if (f.getProduct() == type) n++;
        return n;
    }

    /** Проверка: можно ли привязать фабрику к переработчику. */
    public boolean canBindToProcessor(Factory f, Processor p) {
        if (f == null || p == null) return false;
        if (!p.acceptsIngredient(f.getProduct())) return false;
        List<Factory> list = processorToFactories.get(p);
        if (list != null && list.contains(f)) return false;
        int already = countFactoriesOfIngredient(p, f.getProduct());
        return already < p.getMaxFactoriesPerIngredient();
    }

    /** Отвязка. */
    public void unbindFactoryFromProcessor(Factory f, Processor p) {
        var list = processorToFactories.get(p);
        if (list != null) {
            list.remove(f);
            if (list.isEmpty()) processorToFactories.remove(p);
        }
        var m1 = routesFactoryToProcessor.get(p);
        if (m1 != null) { m1.remove(f); if (m1.isEmpty()) routesFactoryToProcessor.remove(p); }
        var m2 = routesProcessorToFactory.get(p);
        if (m2 != null) { m2.remove(f); if (m2.isEmpty()) routesProcessorToFactory.remove(p); }
    }

    public void unbindProcessorFromShop(Processor p, Shop s) {
        var list = shopToProcessors.get(s);
        if (list != null) {
            list.remove(p);
            if (list.isEmpty()) shopToProcessors.remove(s);
        }
        var m1 = routesProcessorToShop.get(s);
        if (m1 != null) { m1.remove(p); if (m1.isEmpty()) routesProcessorToShop.remove(s); }
        var m2 = routesShopToProcessor.get(s);
        if (m2 != null) { m2.remove(p); if (m2.isEmpty()) routesShopToProcessor.remove(s); }
    }

    public void unbindAllForProcessor(Processor p) {
        processorToFactories.remove(p);
        routesFactoryToProcessor.remove(p);
        routesProcessorToFactory.remove(p);
        for (Shop shop : new ArrayList<>(shopToProcessors.keySet())) {
            unbindProcessorFromShop(p, shop);
        }
    }

    public void updateFactoryToProcessorRoute(Factory f, Processor p,
                                              List<Point> toProcessor,
                                              List<Point> toFactory) {
        var m1 = routesFactoryToProcessor.get(p);
        if (m1 != null) m1.put(f, new ArrayList<>(toProcessor));
        var m2 = routesProcessorToFactory.get(p);
        if (m2 != null) m2.put(f, new ArrayList<>(toFactory));
    }

    public void updateProcessorToShopRoute(Processor p, Shop s,
                                           List<Point> toShop,
                                           List<Point> toProcessor) {
        var m1 = routesProcessorToShop.get(s);
        if (m1 != null) m1.put(p, new ArrayList<>(toShop));
        var m2 = routesShopToProcessor.get(s);
        if (m2 != null) m2.put(p, new ArrayList<>(toProcessor));
    }

    /** Обратный поиск: к каким переработчикам привязана фабрика. */
    public List<Processor> getProcessorsForFactory(Factory f) {
        List<Processor> result = new ArrayList<>();
        for (var e : processorToFactories.entrySet()) {
            if (e.getValue().contains(f)) result.add(e.getKey());
        }
        return result;
    }
}