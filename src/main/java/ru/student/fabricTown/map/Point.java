package ru.student.fabricTown.map;

import java.util.Objects;

public final class Point {
    public final int x, y;

    public Point(int x, int y) { this.x = x; this.y = y; }

    public Point add(int dx, int dy) { return new Point(x + dx, y + dy); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }

    @Override public int hashCode() { return Objects.hash(x, y); }

    @Override public String toString() { return "(" + x + "," + y + ")"; }
}