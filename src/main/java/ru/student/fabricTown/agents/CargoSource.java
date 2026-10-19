package ru.student.fabricTown.agents;

import java.util.List;

/**
 * Источник груза для машины-курьера.
 *
 * Реализуется всем, откуда машина может взять товар:
 *   - Factory   (обычная продукция),
 *   - Processor (готовая продукция переработчика).
 *
 * Контракт: takeProducts возвращает до max товаров и УМЕНЬШАЕТ склад.
 */
public interface CargoSource {

    /** Взять до max товаров со склада. Возвращает пустой список, если нечего. */
    List<Product> takeProducts(int max);

    /** Есть ли на складе хоть что-то. */
    boolean hasProduct();

    /** Грузоподъёмность машин этого поставщика. */
    int getCarryCapacity();
}