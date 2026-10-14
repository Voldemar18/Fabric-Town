package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import java.util.EnumSet;

public class BuildingFactory {

    public static Building create(Blueprint bp, Point origin) {
        return switch (bp.kind) {
            case ROAD -> createRoad(origin, bp);
            case FACTORY -> new Factory(origin, bp.size,
                    bp.product == null ? Product.CHICKEN : bp.product);
            case SHOP -> new Shop(origin, bp.size);
            case HOUSE -> new House(origin, bp.size);
            default -> null;
        };
    }

    private static Road createRoad(Point origin, Blueprint bp) {
        if (bp.shape != null) {
            return new Road(origin, bp.shape);   // ← это должно быть
        }
        if (bp.direction != null) {
            return new Road(origin, bp.direction);
        }
        return new Road(origin, Direction.EAST);
    }
}