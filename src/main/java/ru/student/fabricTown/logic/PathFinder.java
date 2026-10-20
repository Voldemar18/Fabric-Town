package ru.student.fabricTown.logic;

import ru.student.fabricTown.map.Point;
import ru.student.fabricTown.profiler.Profiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class PathFinder {

    private final GraphBuilder graph;

    public PathFinder(GraphBuilder graph) {
        this.graph = graph;
    }

    /**
     * Узел приоритетной очереди: фиксированное расстояние + точка.
     * Ключ (distance) больше не меняется после добавления — это
     * гарантирует корректный порядок в куче.
     */
    private static final class Node implements Comparable<Node> {
        final Point point;
        final double dist;

        Node(Point point, double dist) {
            this.point = point;
            this.dist = dist;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.dist, other.dist);
        }
    }

    public List<Point> findPath(Point start, Point goal) {
        try (var s = Profiler.start("game.rebuildAllRoutes")){
            if (start == null || goal == null) return null;
            if (!graph.hasVertex(start) || !graph.hasVertex(goal)) return null;
            if (start.equals(goal)) {
                List<Point> single = new LinkedList<>();
                single.add(start);
                return single;
            }

            Map<Point, Double> dist = new HashMap<>();
            Map<Point, Point> prev = new HashMap<>();
            Set<Point> settled = new HashSet<>();

            PriorityQueue<Node> pq = new PriorityQueue<>();

            dist.put(start, 0.0);
            pq.add(new Node(start, 0.0));

            while (!pq.isEmpty()) {
                Node cur = pq.poll();
                Point u = cur.point;

                // Lazy deletion: если узел уже обработан — пропускаем.
                if (!settled.add(u)) continue;

                // Если ключ устарел (в dist уже меньшее значение) — пропускаем.
                if (cur.dist > dist.getOrDefault(u, Double.MAX_VALUE)) continue;

                if (u.equals(goal)) break;

                double dU = dist.get(u);

                for (GraphBuilder.Edge e : graph.neighbors(u)) {
                    Point v = e.to;
                    if (settled.contains(v)) continue;

                    double nd = dU + e.weight;
                    if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                        dist.put(v, nd);
                        prev.put(v, u);
                        pq.add(new Node(v, nd));   // фиксированный ключ
                    }
                }
            }

            if (!prev.containsKey(goal)) return null;

            LinkedList<Point> path = new LinkedList<>();
            Point cur = goal;
            while (cur != null && !cur.equals(start)) {
                path.addFirst(cur);
                cur = prev.get(cur);
            }
            path.addFirst(start);
            return path;
        }
    }
}