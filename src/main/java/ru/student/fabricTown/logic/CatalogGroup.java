package ru.student.fabricTown.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Группа blueprint'ов внутри категории.
 * Используется для отображения подкатегорий в BuildPanel.
 *
 * Пример: категория "Фабрики" содержит группы:
 *   - "Курица"  → [Завод 1×1, Завод 2×1, ...]
 *   - "Говядина" → [Завод 1×1, Завод 2×1, ...]
 *   - ...
 */
public class CatalogGroup {
    public final String title;
    public final List<Blueprint> items;

    public CatalogGroup(String title, List<Blueprint> items) {
        this.title = title;
        this.items = items == null ? new ArrayList<>() : items;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}