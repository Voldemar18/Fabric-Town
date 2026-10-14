import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.student.fabricTown.buildings.Road;
import ru.student.fabricTown.logic.GraphBuilder;
import ru.student.fabricTown.logic.PathFinder;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.GameMap;
import ru.student.fabricTown.map.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathFinderTest {

    private GameMap map;
    private GraphBuilder graph;
    private PathFinder pathFinder;

    @BeforeEach
    void setUp() {
        map = new GameMap();
        graph = new GraphBuilder(map);
        pathFinder = new PathFinder(graph);
    }

    /** Вспомогательный метод: поставить прямую дорогу. */
    private void road(Point p, Direction d) {
        map.place(new Road(p, d));
        graph.updateVertex(p);
    }

    // ==================== ОСНОВНОЙ СЦЕНАРИЙ ====================

    @Test
    @DisplayName("Путь по прямой из трёх клеток находится корректно")
    void простой_путь_по_прямой() {
        // (0,0) -> (1,0) -> (2,0), все дороги смотрят на восток
        road(new Point(0, 0), Direction.EAST);
        road(new Point(1, 0), Direction.EAST);
        road(new Point(2, 0), Direction.EAST);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(2, 0));

        assertNotNull(path, "Путь должен существовать");
        assertEquals(3, path.size(), "Путь должен содержать 3 клетки");
        assertEquals(new Point(0, 0), path.get(0), "Старт — первая клетка");
        assertEquals(new Point(2, 0), path.get(2), "Финиш — последняя клетка");
    }

    // ==================== ПОГРАНИЧНЫЕ СЛУЧАИ ====================

    @Test
    @DisplayName("Путь из клетки в саму себя — список из одной точки")
    void путь_из_клетки_в_себя() {
        road(new Point(0, 0), Direction.EAST);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(0, 0));

        assertNotNull(path);
        assertEquals(1, path.size());
        assertEquals(new Point(0, 0), path.get(0));
    }

    @Test
    @DisplayName("Пустая карта — пути нет")
    void пустая_карта_путь_не_найден() {
        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(5, 5));

        assertNull(path, "На пустой карте пути быть не может");
    }

    @Test
    @DisplayName("Старт не в графе — пути нет")
    void старт_не_в_графе() {
        road(new Point(5, 5), Direction.EAST);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(5, 5));

        assertNull(path, "Если старт не является дорогой — пути нет");
    }

    @Test
    @DisplayName("Финиш не в графе — пути нет")
    void финиш_не_в_графе() {
        road(new Point(0, 0), Direction.EAST);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(5, 5));

        assertNull(path, "Если финиш не является дорогой — пути нет");
    }

    @Test
    @DisplayName("Односторонняя дорога: обратный путь не находится")
    void односторонняя_дорога_обратно_не_едет() {
        // Только (0,0) -> (1,0), обратной дороги нет
        road(new Point(0, 0), Direction.EAST);
        road(new Point(1, 0), Direction.EAST);

        List<Point> forward = pathFinder.findPath(new Point(0, 0), new Point(1, 0));
        List<Point> backward = pathFinder.findPath(new Point(1, 0), new Point(0, 0));

        assertNotNull(forward, "Прямой путь есть");
        assertNull(backward, "Обратного пути быть не должно — дороги односторонние");
    }

    @Test
    @DisplayName("Разворот на 180° запрещён — путь не строится")
    void разворот_на_180_запрещён() {
        // Две клетки смотрят друг на друга: (0,0) на восток, (1,0) на запад.
        // Машина с (0,0) поедет на (1,0), но с (1,0) обратно на (0,0) не сможет.
        road(new Point(0, 0), Direction.EAST);
        road(new Point(1, 0), Direction.WEST);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(1, 0));
        assertNotNull(path, "В одну сторону проехать можно");

        // Пытаемся проехать (1,0) -> (0,0): дорога (1,0) смотрит на запад,
        // но переход на (0,0) — это разворот, запрещён.
        List<Point> back = pathFinder.findPath(new Point(1, 0), new Point(0, 0));
        assertNull(back, "Разворот на 180° запрещён графом");
    }

    @Test
    @DisplayName("Обход через перекрёсток: путь длиннее прямого, но существует")
    void обход_через_перекрёсток() {
        // Кольцо: (0,0) → (1,0) → (1,1) → (0,1) → (0,0)
        road(new Point(0, 0), Direction.EAST);
        road(new Point(1, 0), Direction.SOUTH);
        road(new Point(1, 1), Direction.WEST);
        road(new Point(0, 1), Direction.NORTH);

        List<Point> path = pathFinder.findPath(new Point(0, 0), new Point(0, 1));
        assertNotNull(path, "Путь по кольцу должен существовать");
        assertEquals(4, path.size(), "Длина пути по кольцу — 4 клетки");
    }
}