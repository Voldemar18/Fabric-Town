package ru.student.fabricTown.logic;

import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.map.GameMap;
import ru.student.fabricTown.map.Point;

/** Проверка правил близости из ТЗ */
public class PlacementRules {
    public static final int MIN_FACTORY_TO_SHOP = 3;
    public static final int MIN_FACTORY_TO_HOUSE = 3;

    /**
     * Возвращает null, если ставить можно.
     * Иначе — текст причины.
     */
    public static String check(Blueprint bp, Point origin, GameMap map) {
        Building candidate = BuildingFactory.create(bp, origin);
        if (candidate == null) return "Ошибка объекта";

        // 1. Клетки свободны
        if (!map.canPlace(candidate)) return "Место занято";

//        // 2. Для магазина — соседство с дорогой
//        if (bp.kind == Blueprint.Kind.SHOP && !hasAdjacentRoad(candidate, map)) {
//            return "Магазин должен стоять вплотную к дороге";
//        }

        // 3. Фабрика не рядом с магазином/домом
        if (bp.kind == Blueprint.Kind.FACTORY) {
            if (tooClose(candidate, map, Shop.class, MIN_FACTORY_TO_SHOP))
                return "Слишком близко к магазину";
            if (tooClose(candidate, map, House.class, MIN_FACTORY_TO_HOUSE))
                return "Слишком близко к дому";
        }

        // 4. Магазин не рядом с фабрикой
        if (bp.kind == Blueprint.Kind.SHOP) {
            if (tooClose(candidate, map, Factory.class, MIN_FACTORY_TO_SHOP))
                return "Слишком близко к фабрике";
        }

        // 5. Дом не рядом с фабрикой
        if (bp.kind == Blueprint.Kind.HOUSE) {
            if (tooClose(candidate, map, Factory.class, MIN_FACTORY_TO_HOUSE))
                return "Слишком близко к фабрике";
        }
        //6 Переработчик не рядом
        if (bp.kind == Blueprint.Kind.PROCESSOR) {
            if (tooClose(candidate, map, House.class, MIN_FACTORY_TO_HOUSE))
                return "Слишком близко к дому";
            if (tooClose(candidate, map, Shop.class, MIN_FACTORY_TO_SHOP))
                return "Слишком близко к магазину";
        }
        return null;
    }

    private static boolean hasAdjacentRoad(Building b, GameMap map) {
        for (int x = 0; x < b.getSize().w; x++) {
            for (int y = 0; y < b.getSize().h; y++) {
                Point p = new Point(b.getOrigin().x + x, b.getOrigin().y + y);
                // Проверяем 4 соседние клетки
                if (map.hasRoad(p.add(0, -1))) return true;
                if (map.hasRoad(p.add(0, 1))) return true;
                if (map.hasRoad(p.add(-1, 0))) return true;
                if (map.hasRoad(p.add(1, 0))) return true;
            }
        }
        return false;
    }

    private static boolean tooClose(Building b, GameMap map,
                                    Class<? extends Building> type, int minDist) {
        for (var e : map.getAll().entrySet()) {
            if (!type.isInstance(e.getValue())) continue;
            Point bp1 = b.getOrigin();
            Point bp2 = e.getValue().getOrigin();
            int dx = Math.abs(bp1.x - bp2.x);
            int dy = Math.abs(bp1.y - bp2.y);
            if (Math.max(dx, dy) < minDist) return true;
        }
        return false;
    }
}