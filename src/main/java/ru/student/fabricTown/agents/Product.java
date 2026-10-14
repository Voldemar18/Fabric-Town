package ru.student.fabricTown.agents;

public enum Product {
    CHICKEN("Курица", 5),
    BEEF("Говядина", 8),
    FRUIT("Фрукты", 3),
    VEGETABLES("Овощи", 2),
    BREAD("Хлеб", 4);

    public final String title;
    public final int price;

    Product(String title, int price) { this.title = title; this.price = price; }
}