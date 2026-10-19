package ru.student.fabricTown.buildings;

import ru.student.fabricTown.agents.CargoSink;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.map.Point;

import java.util.*;

/**
 * Магазин.
 *
 * Ключевая механика:
 *   - Магазин принимает товары от фабрик.
 *   - На КАЖДЫЙ ТИП ТОВАРА (курица, говядина, фрукты, овощи, хлеб)
 *     магазин может принимать до N фабрик, где N зависит от размера:
 *       - S1x1       → 1 фабрика на тип (итого до 5)
 *       - S1x2/S2x1  → 2 фабрики на тип (итого до 10)
 *       - S2x2       → 3 фабрики на тип (итого до 15)
 *
 *   - Склад магазина — общий на все типы товаров. Вместимость зависит
 *     от размера магазина.
 */
public class Shop extends Building implements CargoSink {

    /** Вместимость склада (общая, все товары вместе). */
    private final int capacity;

    /** Лимит фабрик НА КАЖДЫЙ ТИП товара. */
    private final int maxFactoriesPerType;

    /** Склад: тип товара → количество. */
    private final Map<Product, Integer> storage = new EnumMap<>(Product.class);

    /** Сколько всего продано на деньги. */
    private double moneyEarned = 0;

    // ==================== КОНСТРУКТОР ====================

    public Shop(Point origin, BuildingSize size) {
        super(origin, size, size.area() * 150.0);

        this.capacity = switch (size) {
            case S1x1 -> 200;
            case S1x2, S2x1 -> 400;
            case S2x2 -> 800;
            case S3x3 -> 1500;
            case S4x4 -> 2500;
            case S5x5 -> 4000;
            case S10x10 -> 15000;
        };

        this.maxFactoriesPerType = switch (size) {
            case S1x1 -> 1;
            case S1x2, S2x1 -> 2;
            case S2x2 -> 3;
            case S3x3 -> 4;
            case S4x4 -> 5;
            case S5x5 -> 6;
            case S10x10 -> 12;
        };
    }

    // ==================== СКЛАД ====================

    public boolean hasProduct(Product p) {
        return storage.getOrDefault(p, 0) > 0;
    }

    /** Сколько товара конкретного типа на складе. */
    public int countOf(Product p) {
        return storage.getOrDefault(p, 0);
    }

    public boolean trySell(Product p) {
        int have = storage.getOrDefault(p, 0);
        if (have <= 0) return false;
        storage.put(p, have - 1);
        moneyEarned += p.price;
        return true;
    }
    public int addProducts(List<Product> products) {
        int added = 0;
        for (Product p : products) {
            if (isFull()) break;
            storage.merge(p, 1, Integer::sum);
            added++;
        }
        return added;
    }
    public void addProduct(Product p) {
        storage.merge(p, 1, Integer::sum);
    }

    public int totalStored() {
        int sum = 0;
        for (int v : storage.values()) sum += v;
        return sum;
    }

    public boolean isFull()  { return totalStored() >= capacity; }
    public boolean isEmpty() { return totalStored() == 0; }

    /** Множество типов, которые сейчас есть на складе (>0). */
    public Set<Product> getTypesStored() {
        Set<Product> result = EnumSet.noneOf(Product.class);
        for (Map.Entry<Product, Integer> e : storage.entrySet()) {
            if (e.getValue() > 0) result.add(e.getKey());
        }
        return result;
    }

    // ==================== ГЕТТЕРЫ ====================

    public int getCapacity() { return capacity; }

    /**
     * Лимит фабрик на ОДИН тип товара.
     * Именно это значение используется при привязке фабрики к магазину.
     */
    public int getMaxFactoriesPerType() { return maxFactoriesPerType; }

    /**
     * Суммарный лимит фабрик по всем типам.
     * Оставлен для совместимости и для UI (общий счётчик).
     */
    public int getMaxFactories() {
        return maxFactoriesPerType * Product.values().length;
    }

    public double getMoneyEarned() { return moneyEarned; }

    /** Склад. Возвращается живой Map — не мутируй его извне. */
    public Map<Product, Integer> getStorage() { return storage; }

    @Override
    public String getTypeName() { return "Магазин"; }



    @Override
    public int freeSpace() {
        return Math.max(0, capacity - totalStored());
    }

    @Override
    public int acceptProducts(java.util.List<Product> products) {
        return addProducts(products);   // addProducts уже есть и возвращает int
    }
}