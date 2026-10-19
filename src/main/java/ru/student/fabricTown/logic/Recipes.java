package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;

import java.util.Map;

/** Все известные рецепты переработчиков. */
public final class Recipes {

    private Recipes() {}

    public static final Recipe CHOCOLATE = new Recipe(
            Product.CHOCOLATE,
            Map.of(Product.MILK, 1, Product.COCOA, 1),
            3.0   // секунд на 1 плитку
    );
}