package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.RoadShape;
import ru.student.fabricTown.map.Direction;

public class Blueprint {
    public enum Kind { ROAD, FACTORY, SHOP, HOUSE, LAND }

    public final Kind kind;
    public final BuildingSize size;
    public final Product product;
    public final Direction direction;   // для простых прямых дорог (совместимость)
    public final RoadShape shape;       // для углов, T, крестов (может быть null)
    public final String displayName;
    public final double cost;

    public Blueprint(Kind kind, BuildingSize size, Product product,
                     Direction direction, RoadShape shape,
                     String displayName, double cost) {
        this.kind = kind;
        this.size = size;
        this.product = product;
        this.direction = direction;
        this.shape = shape;
        this.displayName = displayName;
        this.cost = cost;
    }

    /** Удобный конструктор без формы (обратная совместимость). */
    public Blueprint(Kind kind, BuildingSize size, Product product,
                     Direction direction, String displayName, double cost) {
        this(kind, size, product, direction, null, displayName, cost);
    }
}