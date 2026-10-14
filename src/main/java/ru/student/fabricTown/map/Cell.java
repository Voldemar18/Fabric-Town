package ru.student.fabricTown.map;

import ru.student.fabricTown.buildings.Building;
import ru.student.fabricTown.buildings.Road;

import java.util.Objects;

/**
 * Клетка карты 1×1 м.
 *
 * Используется как вспомогательный value-объект:
 *   - в рендеринге (для итерации по видимой области),
 *   - в алгоритмах (когда нужно «пробежаться по клеткам»),
 *   - в UI (для подсветки, тумана войны, курсора).
 *
 * Основное хранилище — GameMap (Map<Point, Building>), поэтому
 * Cell не дублирует состояние, а ссылается на содержимое.
 */
public class Cell {

    /** Координаты клетки в мировых единицах (метрах). */
    private final int x;
    private final int y;

    /** Ссылка на здание, занимающее эту клетку (может быть null). */
    private Building building;

    /** Ссылка на дорогу в этой клетке (если building == Road, то это он же). */
    private Road road;

    /** Видима ли клетка игроком (задел под туман войны, всегда true сейчас). */
    private boolean visible = true;

    /** Куплена ли клетка игроком (для границ расширения). */
    private boolean owned = true;

    public Cell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Cell(Point p) {
        this(p.x, p.y);
    }

    // ==================== СВОЙСТВА КЛЕТКИ ====================

    public int getX() { return x; }
    public int getY() { return y; }

    public Point toPoint() { return new Point(x, y); }

    /** Занята ли клетка хоть чем-то. */
    public boolean isOccupied() {
        return building != null;
    }

    /** Есть ли в клетке дорога. */
    public boolean hasRoad() {
        return road != null;
    }

    public Building getBuilding() { return building; }
    public Road getRoad() { return road; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }

    public boolean isOwned() { return owned; }
    public void setOwned(boolean o) { this.owned = o; }

    // ==================== МУТАТОРЫ ====================

    /** Назначить содержимое клетки. Передавай null, чтобы очистить. */
    public void setBuilding(Building b) {
        this.building = b;
        this.road = (b instanceof Road r) ? r : null;
    }

    /** Очистить клетку. */
    public void clear() {
        this.building = null;
        this.road = null;
    }

    // ==================== ГЕОМЕТРИЯ ====================

    /** Соседняя клетка в заданном направлении. */
    public Cell neighbor(Direction d) {
        return new Cell(x + d.dx, y + d.dy);
    }

    /** Клетка с прибавленным смещением. */
    public Cell add(int dx, int dy) {
        return new Cell(x + dx, y + dy);
    }

    /** Манхэттенское расстояние до другой клетки. */
    public int manhattan(Cell other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    /** Евклидово расстояние до другой клетки. */
    public double distance(Cell other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    // ==================== СЛУЖЕБНОЕ ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cell c)) return false;
        return x == c.x && y == c.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Cell(" + x + "," + y
                + (road != null ? ",road" : "")
                + (building != null && road == null ? "," + building.getTypeName() : "")
                + ")";
    }
}