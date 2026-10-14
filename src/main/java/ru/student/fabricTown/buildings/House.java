package ru.student.fabricTown.buildings;

import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.List;

public class House extends Building {
    private final int residents;
    private final List<Double> exitTimers = new ArrayList<>();

    public House(Point origin, BuildingSize size) {
        super(origin, size, size.area() * 100.0);
        this.residents = switch (size) {
            case S1x1 -> 1 + (int)(Math.random() * 3);       // 1..3
            case S1x2, S2x1 -> 3 + (int)(Math.random() * 4); // 3..6
            case S2x2 -> 6 + (int)(Math.random() * 5);       // 6..10
            case S3x3 -> 12 + (int)(Math.random() * 6);      // 12..17
            case S4x4 -> 20 + (int)(Math.random() * 8);      // 20..27
            case S5x5 -> 30 + (int)(Math.random() * 12);     // 30..41
            case S10x10 -> 100 + (int)(Math.random() * 40);  // 100..139
        };
        for (int i = 0; i < residents; i++) {
            exitTimers.add(3 + Math.random() * 10);
        }
    }

    public int getResidents() { return residents; }

    /** Уменьшает таймеры, возвращает true, если кто-то выходит */
    public boolean tickAndCheckExit(double dt) {
        for (int i = 0; i < exitTimers.size(); i++) {
            double t = exitTimers.get(i) - dt;
            if (t <= 0) {
                exitTimers.set(i, 5 + Math.random() * 15);
                return true;
            }
            exitTimers.set(i, t);
        }
        return false;
    }

    @Override public String getTypeName() { return "Дом"; }
}