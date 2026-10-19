package ru.student.fabricTown.agents;

import java.util.List;

/**
 * Получатель груза для машины-курьера.
 *
 * Реализуется всем, куда машина может выгрузить товар:
 *   - Shop      (склад магазина),
 *   - Processor (склад ингредиентов переработчика).
 *
 * Контракт: acceptProducts УВЕЛИЧИВАЕТ склад и возвращает,
 * сколько реально принято (может быть меньше списка, если места мало
 * или тип товара не принимается).
 */
public interface CargoSink {

    /** Сколько ещё единиц влезет (с учётом типа товаров, если важно). */
    int freeSpace();

    /** Есть ли хоть какое-то место. */
    default boolean hasFreeSpace() {
        return freeSpace() > 0;
    }

    /** Выгрузить. Возвращает, сколько реально принято. */
    int acceptProducts(List<Product> products);
}