package ru.student.fabricTown.logic;

import ru.student.fabricTown.map.Point;
import ru.student.fabricTown.profiler.Profiler;

import java.util.HashSet;
import java.util.Set;

/**
 * Лёгкий реестр занятых дорожных клеток.
 *
 * Машина при входе на клетку регистрируется здесь,
 * при уходе — снимается. Это позволяет PathFinder'у
 * и другим алгоритмам проверять занятость, не таща за собой GameMap.
 */
public final class RoadRegistry {

    private static final Set<Point> occupied = new HashSet<>();

    private RoadRegistry() {}

    public static synchronized void occupy(Point p) {
        Profiler.count("road.occupy");
        if (p != null) occupied.add(p);
    }

    public static synchronized void release(Point p) {
        Profiler.count("road.release");
        if (p != null) occupied.remove(p);
    }

    public static synchronized boolean isBlocked(Point p) {
        Profiler.count("road.isBlocked");
        return occupied.contains(p);
    }

    public static synchronized void clear() {
        occupied.clear();
    }
}