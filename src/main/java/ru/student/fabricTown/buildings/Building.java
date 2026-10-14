package ru.student.fabricTown.buildings;

import ru.student.fabricTown.map.Point;

public abstract class Building {
    protected Point origin;      // левый верхний угол
    protected BuildingSize size;
    protected double buildCost;

    public Building(Point origin, BuildingSize size, double buildCost) {
        this.origin = origin;
        this.size = size;
        this.buildCost = buildCost;
    }

    public Point getOrigin() { return origin; }
    public BuildingSize getSize() { return size; }
    public double getBuildCost() { return buildCost; }
    public double getSellPrice() { return buildCost * 0.7; }

    /** Занимает ли здание данную клетку */
    public boolean occupies(Point p) {
        return p.x >= origin.x && p.x < origin.x + size.w
                && p.y >= origin.y && p.y < origin.y + size.h;
    }

    public abstract String getTypeName();
}