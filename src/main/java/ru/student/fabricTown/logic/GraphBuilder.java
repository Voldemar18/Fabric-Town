package ru.student.fabricTown.logic;

import ru.student.fabricTown.buildings.Road;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.GameMap;
import ru.student.fabricTown.map.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Построитель и хранитель графа дорог.
 *
 * Вершина графа — клетка с дорогой.
 * Ребро — переход между двумя соседними дорогами с учётом:
 *   1) направления дороги (одностороннее движение),
 *   2) запрета разворота на 180°.
 *
 * Граф представлен в виде списков смежности (Map<Point, List<Edge>>),
 * что эффективно для разреженных карт (см. ТЗ, часть 5).
 *
 * Используется:
 *   - PathFinder (Дейкстра/A*) для построения маршрутов машин,
 *   - для BFS-проверки связности,
 *   - для поиска ближайшего магазина по дорогам.
 */
public class GraphBuilder {

    /** Ребро графа: куда идём и с каким весом (по умолчанию 1 клетка). */
    public static final class Edge {
        public final Point to;
        public final Direction direction;
        public final double weight;

        public Edge(Point to, Direction direction, double weight) {
            this.to = to;
            this.direction = direction;
            this.weight = weight;
        }
    }

    private final GameMap map;

    /** Списки смежности: Point -> исходящие рёбра. */
    private final Map<Point, List<Edge>> adjacency = new HashMap<>();

    public GraphBuilder(GameMap map) {
        this.map = map;
        rebuild();
    }

    // ==================== ПОСТРОЕНИЕ ====================

    /**
     * Полная пересборка графа по текущему состоянию карты.
     * O(V + E), где V — число дорог, E — число переходов.
     */
    public void rebuild() {
        adjacency.clear();

        for (Point p : map.getRoads().keySet()) {
            Road road = map.getRoad(p);
            if (road == null) continue;

            List<Edge> edges = buildEdgesFrom(p, road);
            adjacency.put(p, edges);
        }
    }

    /** Точечное обновление одной вершины (после постройки/сноса дороги). */
    public void updateVertex(Point p) {
        if (!map.hasRoad(p)) {
            adjacency.remove(p);
            return;
        }
        Road road = map.getRoad(p);
        adjacency.put(p, buildEdgesFrom(p, road));
    }

    /**
     * Строит исходящие рёбра из клетки p.
     *
     * Правило перехода:
     *   - дорога на p имеет направление d (например, EAST),
     *   - соседняя клетка по d должна содержать дорогу,
     *   - направление соседней дороги не может быть d.opposite() (запрет разворота).
     *
     * То есть машина может ехать только «вперёд» — прямо или повернуть на 90°.
     */
    private List<Edge> buildEdgesFrom(Point p, Road road) {
        List<Edge> edges = new ArrayList<>(4);
        for (Direction d : road.getPrimaryExits()) {
            Point next = p.add(d.dx, d.dy);
            if (!map.hasRoad(next)) continue;
            edges.add(new Edge(next, d, 1.0));
        }

        return edges;
    }

    // ==================== ДОСТУП К ГРАФУ ====================

    public Map<Point, List<Edge>> getAdjacency() {
        return Collections.unmodifiableMap(adjacency);
    }

    public List<Edge> neighbors(Point p) {
        return adjacency.getOrDefault(p, Collections.emptyList());
    }

    public boolean hasVertex(Point p) {
        return adjacency.containsKey(p);
    }

    public int vertexCount() {
        return adjacency.size();
    }

    public int edgeCount() {
        int n = 0;
        for (List<Edge> list : adjacency.values()) n += list.size();
        return n;
    }

    // ==================== BFS ====================

    /**
     * Проверка достижимости goal из start по графу.
     * O(V + E). Возвращает true, если goal достижим.
     */
    public boolean isReachable(Point start, Point goal) {
        if (start.equals(goal)) return true;
        if (!adjacency.containsKey(start) || !adjacency.containsKey(goal)) return false;

        Set<Point> visited = new HashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            if (cur.equals(goal)) return true;

            for (Edge e : neighbors(cur)) {
                if (visited.add(e.to)) {
                    queue.add(e.to);
                }
            }
        }
        return false;
    }

    /**
     * Список всех вершин, достижимых из start (BFS-обход).
     * Используется для проверки «оторванных» дорог и оценки компоненты связности.
     */
    public Set<Point> reachableFrom(Point start) {
        Set<Point> visited = new HashSet<>();
        if (!adjacency.containsKey(start)) return visited;

        Deque<Point> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            for (Edge e : neighbors(cur)) {
                if (visited.add(e.to)) {
                    queue.add(e.to);
                }
            }
        }
        return visited;
    }

    /**
     * Поиск ближайшего магазина (по клеткам дорог) из start.
     * Проверяет, стоит ли магазин рядом с клеткой дороги, до которой мы дошли.
     * Возвращает пару (магазин, входная клетка дороги) или null.
     */
    public NearestShop findNearestShop(Point start, List<Shop> shops) {
        if (shops == null || shops.isEmpty()) return null;
        if (!adjacency.containsKey(start)) return null;

        // Собираем все «входные» клетки дорог, соседние с магазинами
        Map<Point, Shop> shopEntries = new HashMap<>();
        for (Shop s : shops) {
            for (int dx = 0; dx < s.getSize().w; dx++) {
                for (int dy = 0; dy < s.getSize().h; dy++) {
                    Point cell = new Point(s.getOrigin().x + dx, s.getOrigin().y + dy);
                    // 4 соседние клетки
                    for (Direction d : Direction.values()) {
                        Point n = cell.add(d.dx, d.dy);
                        if (map.hasRoad(n)) {
                            shopEntries.putIfAbsent(n, s);
                        }
                    }
                }
            }
        }
        if (shopEntries.isEmpty()) return null;

        // BFS до первой входной клетки
        Set<Point> visited = new HashSet<>();
        Deque<Point> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            Shop s = shopEntries.get(cur);
            if (s != null) {
                return new NearestShop(s, cur);
            }
            for (Edge e : neighbors(cur)) {
                if (visited.add(e.to)) {
                    queue.add(e.to);
                }
            }
        }
        return null;
    }

    /** Результат поиска ближайшего магазина. */
    public static final class NearestShop {
        public final Shop shop;
        public final Point entryCell;

        public NearestShop(Shop shop, Point entryCell) {
            this.shop = shop;
            this.entryCell = entryCell;
        }
    }

    // ==================== ПРОВЕРКА ЦЕЛОСТНОСТИ ====================

    /**
     * Проверка, что все дороги принадлежат одной компоненте слабой связности
     * (т.е. карта «связная», без оторванных кусков).
     * Возвращает true, если связно, иначе false.
     */
    public boolean isWeaklyConnected() {
        if (adjacency.isEmpty()) return true;

        // Стартовая вершина — любая
        Point start = adjacency.keySet().iterator().next();

        // Собираем все вершины, достижимые в прямом направлении
        Set<Point> forward = reachableFrom(start);

        // Проверяем, покрывает ли forward все вершины
        return forward.size() == adjacency.size();
    }

    /**
     * Возвращает список «оторванных» дорог (компонент, не связанных с основной).
     * Полезно для отладки и подсветки проблемных мест в UI.
     */
    public List<Point> findIsolatedRoads() {
        if (adjacency.isEmpty()) return Collections.emptyList();

        Point start = adjacency.keySet().iterator().next();
        Set<Point> mainComponent = reachableFrom(start);

        List<Point> isolated = new ArrayList<>();
        for (Point p : adjacency.keySet()) {
            if (!mainComponent.contains(p)) {
                isolated.add(p);
            }
        }
        return isolated;
    }

    // ==================== СЛУЖЕБНОЕ ====================

    @Override
    public String toString() {
        return "GraphBuilder{v=" + vertexCount() + ", e=" + edgeCount() + "}";
    }
}