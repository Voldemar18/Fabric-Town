package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;

import java.util.Map;

/**
 * Рецепт переработки: сколько каких ингредиентов нужно
 * на одну единицу выходного продукта.
 */
public final class Recipe {

    public final Product output;
    /** ингредиент → количество на 1 выход */
    public final Map<Product, Integer> inputs;
    /** Сколько секунд занимает одна партия. */
    public final double craftTime;

    public Recipe(Product output, Map<Product, Integer> inputs, double craftTime) {
        this.output = output;
        this.inputs = Map.copyOf(inputs);
        this.craftTime = craftTime;
    }

    /** Хватает ли ингредиентов на складе для одной партии. */
    public boolean canCraft(Map<Product, Integer> storage) {
        for (var e : inputs.entrySet()) {
            if (storage.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        return true;
    }

    /** Списать ингредиенты со склада (вызывать после canCraft). */
    public void consume(Map<Product, Integer> storage) {
        for (var e : inputs.entrySet()) {
            storage.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
    }
}