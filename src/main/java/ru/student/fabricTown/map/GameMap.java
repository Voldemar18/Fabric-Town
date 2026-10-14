package ru.student.fabricTown.map;

import ru.student.fabricTown.buildings.Building;
import ru.student.fabricTown.buildings.Road;

import java.util.HashMap;
import java.util.Map;

public class GameMap {
    private final Map<Point, Building> buildings = new HashMap<>();
    private final Map<Point, Road> roads = new HashMap<>();

    public boolean canPlace(Building b) {
        for (int x = 0; x < b.getSize().w; x++) {
            for (int y = 0; y < b.getSize().h; y++) {
                Point p = new Point(b.getOrigin().x + x, b.getOrigin().y + y);
                if (buildings.containsKey(p)) return false;
            }
        }
        return true;
    }

    public void place(Building b) {
        for (int x = 0; x < b.getSize().w; x++) {
            for (int y = 0; y < b.getSize().h; y++) {
                Point p = new Point(b.getOrigin().x + x, b.getOrigin().y + y);
                buildings.put(p, b);
                if (b instanceof Road r) roads.put(p, r);
            }
        }
    }

    public void remove(Building b) {
        for (int x = 0; x < b.getSize().w; x++) {
            for (int y = 0; y < b.getSize().h; y++) {
                Point p = new Point(b.getOrigin().x + x, b.getOrigin().y + y);
                buildings.remove(p);
                roads.remove(p);
            }
        }
    }

    public Building get(Point p) { return buildings.get(p); }
    public Road getRoad(Point p) { return roads.get(p); }
    public boolean hasRoad(Point p) { return roads.containsKey(p); }
    public Map<Point, Building> getAll() { return buildings; }
    public Map<Point, Road> getRoads() { return roads; }
}