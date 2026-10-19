package ru.student.fabricTown.buildings;

import ru.student.fabricTown.agents.CargoSink;
import ru.student.fabricTown.agents.CargoSource;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.logic.Recipe;
import ru.student.fabricTown.map.Point;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Фабрика-переработчик.
 *
 * Принимает от фабрик ингредиенты (как магазин), копит их на складе,
 * по рецепту производит новый продукт и отдаёт его машинам-курьерам,
 * которые везут товар в магазины (как машины обычной фабрики).
 *
 * Отличие от Factory:
 *   - не производит «из воздуха», а только из принятых ингредиентов;
 *   - продукт (output) не может быть сырьём обычной фабрики;
 *   - лимит фабрик считается по каждому типу ингредиента отдельно
 *     (как в Shop.getMaxFactoriesPerType).
 */
public class Processor extends Building implements CargoSource, CargoSink {

    private final Recipe recipe;

    /** Склад ингредиентов: тип → количество. */
    private final Map<Product, Integer> inputStorage = new EnumMap<>(Product.class);

    /** Склад готовой продукции: FIFO. */
    private final Deque<Product> outputStorage = new ArrayDeque<>();

    /** Вместимость склада — отдельно для ингредиентов и для готового товара. */
    private final int inputCapacity;
    private final int outputCapacity;

    /** Сколько фабрик на каждый тип ингредиента можно привязать. */
    private final int maxFactoriesPerIngredient;

    /** Сколько магазинов может обслуживать одна машина переработчика. */
    private final int maxShops;

    /** Грузоподъёмность машины переработчика. */
    private final int carCapacity;

    /** Текущий прогресс партии (0..craftTime). */
    private double craftProgress = 0;

    public Processor(Point origin, BuildingSize size, Recipe recipe) {
        super(origin, size, size.area() * 250.0);
        this.recipe = recipe;

        this.inputCapacity = switch (size) {
            case S1x1 -> 30;
            case S1x2, S2x1 -> 60;
            case S2x2 -> 120;
            case S3x3 -> 240;
            case S4x4 -> 400;
            case S5x5 -> 600;
            case S10x10 -> 2000;
        };
        this.outputCapacity = inputCapacity / 2;

        this.maxFactoriesPerIngredient = switch (size) {
            case S1x1 -> 1;
            case S1x2, S2x1 -> 2;
            case S2x2 -> 3;
            case S3x3 -> 4;
            case S4x4 -> 5;
            case S5x5 -> 6;
            case S10x10 -> 12;
        };

        this.maxShops = switch (size) {
            case S1x1 -> 1;
            case S1x2, S2x1 -> 2;
            case S2x2 -> 3;
            case S3x3 -> 5;
            case S4x4 -> 8;
            case S5x5 -> 12;
            case S10x10 -> 30;
        };

        this.carCapacity = switch (size) {
            case S1x1 -> 5;
            case S1x2, S2x1 -> 7;
            case S2x2 -> 10;
            case S3x3 -> 14;
            case S4x4 -> 18;
            case S5x5 -> 24;
            case S10x10 -> 50;
        };
    }

    // ==================== ТИК ====================

    /** Производство: если ингредиентов хватает — потихоньку копим прогресс. */
    public void tick(double dt) {
        // Склад готового товара полон — не производим.
        if (outputStorage.size() >= outputCapacity) return;

        // Если ингредиентов не хватает — прогресс «замирает»
        // (не сбрасывается, чтобы не терять работу).
        if (!recipe.canCraft(inputStorage)) return;

        craftProgress += dt;
        while (craftProgress >= recipe.craftTime
                && outputStorage.size() < outputCapacity) {
            if (!recipe.canCraft(inputStorage)) {
                craftProgress = recipe.craftTime; // ждём ингредиент
                break;
            }
            recipe.consume(inputStorage);
            outputStorage.addLast(recipe.output);
            craftProgress -= recipe.craftTime;
        }
    }

    // ==================== ПРИЁМ ИНГРЕДИЕНТОВ ====================

    /** Может ли переработчик принять этот тип ингредиента (есть в рецепте). */
    public boolean acceptsIngredient(Product p) {
        return recipe.inputs.containsKey(p);
    }

    /** Сколько ещё единиц ингредиента влезет на склад. */
    public int freeSpaceForInput() {
        return Math.max(0, inputCapacity - totalInputStored());
    }

    /**
     * Принять пачку ингредиентов от машины фабрики.
     * Возвращает, сколько реально принято.
     */
    public int addIngredients(List<Product> products) {
        int added = 0;
        for (Product p : products) {
            if (!acceptsIngredient(p)) break;          // не тот тип — стоп
            if (totalInputStored() >= inputCapacity) break;
            inputStorage.merge(p, 1, Integer::sum);
            added++;
        }
        return added;
    }

    public int totalInputStored() {
        int sum = 0;
        for (int v : inputStorage.values()) sum += v;
        return sum;
    }

    public int countOf(Product p) {
        return inputStorage.getOrDefault(p, 0);
    }

    // ==================== ОТГРУЗКА ГОТОВОГО ====================

    /** Взять готовый продукт для доставки в магазин. */
    public List<Product> takeProducts(int max) {
        List<Product> result = new java.util.ArrayList<>(max);
        while (result.size() < max && !outputStorage.isEmpty()) {
            result.add(outputStorage.pollFirst());
        }
        return result;
    }

    public boolean hasProduct() { return !outputStorage.isEmpty(); }

    public int getOutputCount() { return outputStorage.size(); }

    // ==================== ГЕТТЕРЫ ====================

    public Recipe getRecipe() { return recipe; }
    public Product getOutputProduct() { return recipe.output; }
    public int getInputCapacity() { return inputCapacity; }
    public int getOutputCapacity() { return outputCapacity; }
    public int getMaxFactoriesPerIngredient() { return maxFactoriesPerIngredient; }
    public int getMaxShops() { return maxShops; }
    public int getCarCapacity() { return carCapacity; }

    /** Живой склад ингредиентов (read-only по смыслу). */
    public Map<Product, Integer> getInputStorage() { return inputStorage; }

    /** Можно ли привязать к переработчику ещё одну фабрику этого типа. */
    public boolean canAcceptMoreOfType(int currentlyBound) {
        return currentlyBound < maxFactoriesPerIngredient;
    }

    @Override
    public String getTypeName() {
        return "Переработчик (" + recipe.output.title + ")";
    }

    // Processor.java — как CargoSource:
    @Override
    public int getCarryCapacity() {
        return carCapacity;
    }


    // Processor.java — как CargoSink:
    @Override
    public int freeSpace() {
        return freeSpaceForInput();
    }

    @Override
    public int acceptProducts(java.util.List<Product> products) {
        return addIngredients(products);
    }
}