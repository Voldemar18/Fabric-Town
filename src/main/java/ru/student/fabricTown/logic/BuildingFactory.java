package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import java.util.EnumSet;
// Когда появятся другие рецепты — замени resolveRecipe на поиск по Map<Product, Recipe>.
public class BuildingFactory {

    public static Building create(Blueprint bp, Point origin) {
        return switch (bp.kind) {
            case ROAD -> createRoad(origin, bp);
            case FACTORY -> new Factory(origin, bp.size,
                    bp.product == null ? Product.CHICKEN : bp.product);
            case SHOP -> new Shop(origin, bp.size);
            case HOUSE -> new House(origin, bp.size);
            case PROCESSOR -> new Processor(origin, bp.size, resolveRecipe(bp));
            default -> null;
        };
    }

    /** Какой рецепт дать переработчику по блюпринту. */
    private static Recipe resolveRecipe(Blueprint bp) {
        // Пока рецепт один — шоколад. Когда появятся другие —
        // сделаем Map<Product, Recipe> и будем искать по bp.product.
        if (bp.product == Product.CHOCOLATE) return Recipes.CHOCOLATE;
        return Recipes.CHOCOLATE;   // фолбэк
    }

    private static Road createRoad(Point origin, Blueprint bp) {
        if (bp.shape != null) {
            return new Road(origin, bp.shape);
        }
        if (bp.direction != null) {
            return new Road(origin, bp.direction);
        }
        return new Road(origin, Direction.EAST);
    }
}