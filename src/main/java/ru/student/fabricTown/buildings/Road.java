package ru.student.fabricTown.buildings;

import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Road extends Building {

    private final EnumSet<Direction> exits;
    private final EnumSet<Direction> primaryExits;

    public Road(Point origin, Direction direction) {
        this(origin, EnumSet.of(direction), EnumSet.of(direction));
    }

    public Road(Point origin, Set<Direction> exits, Set<Direction> primaryExits) {
        super(origin, BuildingSize.S1x1, 10);
        if (exits == null || exits.isEmpty())
            throw new IllegalArgumentException("Need at least one exit");
        this.exits = EnumSet.copyOf(exits);


        EnumSet<Direction> primary = EnumSet.noneOf(Direction.class);
        if (primaryExits != null) {
            for (Direction d : primaryExits) {
                if (this.exits.contains(d)) primary.add(d);
            }
        }
        if (primary.isEmpty()) primary.add(this.exits.iterator().next());
        this.primaryExits = primary;
    }

    public Road(Point origin, RoadShape shape) {
        this(origin, shape.getExits(), shape.getPrimaryExits());
    }

    public Set<Direction> getExits() {
        return Collections.unmodifiableSet(exits);
    }

    public Set<Direction> getPrimaryExits() {
        return Collections.unmodifiableSet(primaryExits);
    }

    /** Совместимость: основное направление = первый primary. */
    public Direction getDirection() { return primaryExits.iterator().next(); }

    public void rotateCW() {
        rotateSet(exits, Road::rotateCW);
        rotateSet(primaryExits, Road::rotateCW);
    }

    public void rotateCCW() {
        rotateSet(exits, Road::rotateCCW);
        rotateSet(primaryExits, Road::rotateCCW);
    }

    /** Сдвинуть главное направление на следующий выход (для T). */
    public void cyclePrimary() {
        Direction current = primaryExits.iterator().next();
        Direction[] all = Direction.values();
        int start = current.ordinal();
        for (int i = 1; i <= 4; i++) {
            Direction candidate = all[(start + i) % 4];
            if (exits.contains(candidate)) {
                primaryExits.clear();
                primaryExits.add(candidate);
                return;
            }
        }
    }

    private static void rotateSet(EnumSet<Direction> set,
                                  java.util.function.Function<Direction, Direction> f) {
        EnumSet<Direction> rotated = EnumSet.noneOf(Direction.class);
        for (Direction d : set) rotated.add(f.apply(d));
        set.clear();
        set.addAll(rotated);
    }

    private static Direction rotateCW(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST  -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST  -> Direction.NORTH;
        };
    }

    private static Direction rotateCCW(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case WEST  -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST  -> Direction.NORTH;
        };
    }

    public boolean isStraight() { return exits.size() == 1; }
    public boolean isCorner()   { return exits.size() == 2; }
    public boolean isTee()      { return exits.size() == 3; }
    public boolean isCross()    { return exits.size() == 4; }

    @Override
    public String getTypeName() {
        return switch (exits.size()) {
            case 1 -> "Дорога";
            case 2 -> "Угол";
            case 3 -> "Перекрёсток T";
            case 4 -> "Перекрёсток +";
            default -> "Дорога";
        };
    }
}