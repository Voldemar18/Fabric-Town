package ru.student.fabricTown.buildings;

import ru.student.fabricTown.agents.CargoSource;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.map.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Factory extends Building implements CargoSource {
    private final Product product;
    private final int maxStorage;
    private final Deque<Product> storage = new ArrayDeque<>();
    private final double productionRate; // товаров в секунду
    private double productionProgress = 0;
    private final int maxShops;

    public Factory(Point origin, BuildingSize size, Product product) {
        super(origin, size, size.area() * 200.0);
        this.product = product;
        this.maxStorage = switch (size) {
            case S1x1 -> 10;
            case S1x2, S2x1 -> 20;
            case S2x2 -> 40;
            case S3x3 -> 80;
            case S4x4 -> 160;
            case S5x5 -> 300;
            case S10x10 -> 1500;
        };
        this.productionRate = switch (size) {
            case S1x1 -> 0.5;
            case S1x2, S2x1 -> 1.0;
            case S2x2 -> 2.0;
            case S3x3 -> 4.0;
            case S4x4 -> 8.0;
            case S5x5 -> 16.0;
            case S10x10 -> 80.0;
        };
        this.maxShops = switch (size) {
            case S1x1 -> 2;
            case S1x2, S2x1 -> 4;
            case S2x2 -> 6;
            case S3x3 -> 8;
            case S4x4 -> 12;
            case S5x5 -> 18;
            case S10x10 -> 50;
        };
    }

    /** Тик производства */
    public void tick(double dt) {
        if (storage.size() >= maxStorage) return;
        productionProgress += productionRate * dt;
        while (productionProgress >= 1.0 && storage.size() < maxStorage) {
            storage.push(product);
            productionProgress -= 1.0;
        }
    }
    public int getCarryCapacity() {
        return switch (getSize()) {
            case S1x1 -> 10;
            case S1x2, S2x1 -> 13;
            case S2x2 -> 16;
            case S3x3 -> 19;
            case S4x4 -> 25;
            case S5x5 -> 32;
            case S10x10 -> 60;
        };
    }
    public Product getProduct() { return product; }
    public int getStorageCount() { return storage.size(); }
    public int getMaxStorage() { return maxStorage; }
    public int getMaxShops() { return maxShops; }

    public List<Product> takeProducts(int max) {
        List<Product> result = new ArrayList<>(max);
        while (result.size() < max && !storage.isEmpty()) {
            result.add(storage.pop());
        }
        return result;
    }


    public boolean hasProduct() { return !storage.isEmpty(); }

    @Override public String getTypeName() { return "Фабрика (" + product.title + ")"; }
}