package ru.student.fabricTown.agents;

public enum Product {
    CHICKEN("Курица", 5),
    BEEF("Говядина", 8),
    FRUIT("Фрукты", 3),
    VEGETABLES("Овощи", 2),
    BREAD("Хлеб", 4),
    MILK("Молоко", 6),
    COCOA("Какао", 7),

    // === Только переработчики ===
    CHOCOLATE("Шоколад", 18);

    public final String title;
    public final int price;

    Product(String title, int price) { this.title = title; this.price = price; }

    /** Может ли этот продукт производиться на обычной фабрике. */
    public boolean isRawMaterial() {
        return this != CHOCOLATE;
    }
}