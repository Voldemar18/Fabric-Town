package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.RoadShape;
import ru.student.fabricTown.map.Direction;

import java.util.ArrayList;
import java.util.List;

/** Магазин объектов: список доступных покупок */
public class Catalog {
    public static final double LAND_COST = 500;

    // ==================== СТАРЫЕ МЕТОДЫ (оставлены для совместимости) ====================

    public static List<Blueprint> roads() {
        List<Blueprint> list = new ArrayList<>();

        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.STRAIGHT_E, "Дорога →", 10));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.STRAIGHT_W, "Дорога ←", 10));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.STRAIGHT_N, "Дорога ↑", 10));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.STRAIGHT_S, "Дорога ↓", 10));

        return list;
    }

    public static List<Blueprint> intersections() {
        List<Blueprint> list = new ArrayList<>();

        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_NE_E, "Угол N+E → E", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_NE_N, "Угол N+E → N", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_ES_S, "Угол E+S → S", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_ES_E, "Угол E+S → E", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_SW_W, "Угол S+W → W", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_SW_S, "Угол S+W → S", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_WN_N, "Угол W+N → N", 15));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CORNER_WN_W, "Угол W+N → W", 15));

        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.TEE_ESW_S, "T (E+S+W) стрелка ↓", 25));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.TEE_ESW_EW, "T (E+S+W) стрелки ↔", 25));

        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.TEE_NES_N, "T (N+E+S) стрелка ↑", 25));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.TEE_SWN_S, "T (S+W+N) стрелка ↓", 25));
        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.TEE_WNE_W, "T (W+N+E) стрелка ←", 25));

        list.add(new Blueprint(Blueprint.Kind.ROAD, BuildingSize.S1x1, null, null,
                RoadShape.CROSS, "Перекрёсток +", 40));

        return list;
    }

    public static List<Blueprint> factories() {
        List<Blueprint> list = new ArrayList<>();
        for (Product p : Product.values()) {
            list.addAll(factoriesOfType(p));
        }
        return list;
    }

    /**
     * Все фабрики одного типа товара.
     */
    public static List<Blueprint> factoriesOfType(Product p) {
        if (!p.isRawMaterial()) return List.of();
        List<Blueprint> list = new ArrayList<>();
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S1x1, p, null,
                "Завод 1×1 (" + p.title + ")", 200));
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S1x2, p, null,
                "Завод 1×2 (" + p.title + ")", 400));
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S2x2, p, null,
                "Завод 2×2 (" + p.title + ")", 800));
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S3x3, p, null,
                "Завод 3×3 (" + p.title + ")", 1800));
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S5x5, p, null,
                "Завод 5×5 (" + p.title + ")", 5000));
        list.add(new Blueprint(Blueprint.Kind.FACTORY, BuildingSize.S10x10, p, null,
                "Завод 10×10 (" + p.title + ")", 20000));
        return list;
    }

    public static List<Blueprint> shops() {
        List<Blueprint> list = new ArrayList<>();
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S1x1, null, null,
                "Магазин 1×1", 150));
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S1x2, null, null,
                "Магазин 1×2", 300));
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S2x2, null, null,
                "Магазин 2×2", 600));
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S3x3, null, null,
                "Магазин 3×3", 1350));
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S4x4, null, null,
                "Магазин 4×4", 2400));
        list.add(new Blueprint(Blueprint.Kind.SHOP, BuildingSize.S5x5, null, null,
                "Магазин 5×5", 3750));
        return list;
    }

    public static List<Blueprint> houses() {
        List<Blueprint> list = new ArrayList<>();
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S1x1, null, null,
                "Дом 1×1", 100));
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S1x2, null, null,
                "Дом 1×2", 200));
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S2x2, null, null,
                "Дом 2×2", 400));
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S3x3, null, null,
                "Дом 3×3", 900));
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S4x4, null, null,
                "Дом 4×4", 1600));
        list.add(new Blueprint(Blueprint.Kind.HOUSE, BuildingSize.S5x5, null, null,
                "Дом 5×5", 2500));
        return list;
    }

    public static List<Blueprint> services() {
        List<Blueprint> list = new ArrayList<>();
        list.add(new Blueprint(Blueprint.Kind.LAND, BuildingSize.S1x1, null, null,
                "Расширить карту (+1 участок)", LAND_COST));
        return list;
    }
    public static List<Blueprint> processors() {
        List<Blueprint> list = new ArrayList<>();
        list.add(new Blueprint(Blueprint.Kind.PROCESSOR, BuildingSize.S1x1,
                Product.CHOCOLATE, null, "Шоколадный цех 1×1", 400));
        list.add(new Blueprint(Blueprint.Kind.PROCESSOR, BuildingSize.S2x2,
                Product.CHOCOLATE, null, "Шоколадный цех 2×2", 1600));
        list.add(new Blueprint(Blueprint.Kind.PROCESSOR, BuildingSize.S3x3,
                Product.CHOCOLATE, null, "Шоколадный цех 3×3", 3600));
        return list;
    }
    // ==================== НОВЫЕ МЕТОДЫ С ГРУППАМИ ====================

    /**
     * Категории с подгруппами для аккордеона в BuildPanel.
     */
    public static List<CatalogGroup> factoriesGrouped() {

        List<CatalogGroup> groups = new ArrayList<>();
        for (Product p : Product.values()) {
            if (!p.isRawMaterial()) continue;
            groups.add(new CatalogGroup(p.title, factoriesOfType(p)));
        }
        return groups;
    }
}