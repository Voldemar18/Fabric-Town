package ru.student.fabricTown.buildings;

import ru.student.fabricTown.map.Direction;

import java.util.EnumSet;
import java.util.Set;

public enum RoadShape {

    // ==================== ПРЯМЫЕ ====================
    STRAIGHT_N(EnumSet.of(Direction.NORTH), EnumSet.of(Direction.NORTH)),
    STRAIGHT_E(EnumSet.of(Direction.EAST),  EnumSet.of(Direction.EAST)),
    STRAIGHT_S(EnumSet.of(Direction.SOUTH), EnumSet.of(Direction.SOUTH)),
    STRAIGHT_W(EnumSet.of(Direction.WEST),  EnumSet.of(Direction.WEST)),

    // ==================== УГЛЫ ====================
    // 8 углов: две перпендикулярные линии + стрелка в одну сторону
    CORNER_NE_E(EnumSet.of(Direction.NORTH, Direction.EAST),  EnumSet.of(Direction.EAST)),
    CORNER_NE_N(EnumSet.of(Direction.NORTH, Direction.EAST),  EnumSet.of(Direction.NORTH)),

    CORNER_ES_S(EnumSet.of(Direction.EAST,  Direction.SOUTH), EnumSet.of(Direction.SOUTH)),
    CORNER_ES_E(EnumSet.of(Direction.EAST,  Direction.SOUTH), EnumSet.of(Direction.EAST)),

    CORNER_SW_W(EnumSet.of(Direction.SOUTH, Direction.WEST),  EnumSet.of(Direction.WEST)),
    CORNER_SW_S(EnumSet.of(Direction.SOUTH, Direction.WEST),  EnumSet.of(Direction.SOUTH)),

    CORNER_WN_N(EnumSet.of(Direction.WEST,  Direction.NORTH), EnumSet.of(Direction.NORTH)),
    CORNER_WN_W(EnumSet.of(Direction.WEST,  Direction.NORTH), EnumSet.of(Direction.WEST)),

    // ==================== T-ОБРАЗНЫЕ ====================
    TEE_ESW_S(EnumSet.of(Direction.EAST, Direction.SOUTH, Direction.WEST),
            EnumSet.of(Direction.SOUTH)),
    TEE_ESW_EW(EnumSet.of(Direction.EAST, Direction.SOUTH, Direction.WEST),
            EnumSet.of(Direction.EAST, Direction.WEST)),

    TEE_NES_N(EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH),
            EnumSet.of(Direction.NORTH)),
    TEE_NES_EW(EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH),
            EnumSet.of(Direction.EAST, Direction.WEST)),

    TEE_SWN_S(EnumSet.of(Direction.SOUTH, Direction.WEST, Direction.NORTH),
            EnumSet.of(Direction.SOUTH)),
    TEE_SWN_EW(EnumSet.of(Direction.SOUTH, Direction.WEST, Direction.NORTH),
            EnumSet.of(Direction.EAST, Direction.WEST)),

    TEE_WNE_W(EnumSet.of(Direction.WEST, Direction.NORTH, Direction.EAST),
            EnumSet.of(Direction.WEST)),
    TEE_WNE_NS(EnumSet.of(Direction.WEST, Direction.NORTH, Direction.EAST),
            EnumSet.of(Direction.NORTH, Direction.SOUTH)),

    // ==================== КРЕСТ ====================
    // Единственный крест — во все 4 стороны. Стрелки на всех 4 лучах,
    // потому что в этой модели крест симметричен и «главной» стороны нет.
    CROSS(EnumSet.allOf(Direction.class),
            EnumSet.allOf(Direction.class));

    private final Set<Direction> exits;
    private final Set<Direction> primaryExits;

    RoadShape(Set<Direction> exits, Set<Direction> primaryExits) {
        this.exits = EnumSet.copyOf(exits);
        this.primaryExits = EnumSet.copyOf(primaryExits);
    }

    public Set<Direction> getExits() { return exits; }
    public Set<Direction> getPrimaryExits() { return primaryExits; }
}