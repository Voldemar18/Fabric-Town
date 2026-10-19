package ru.student.fabricTown.agents;

import ru.student.fabricTown.logic.RoadRegistry;
import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Универсальная машина-курьер.
 *
 * Ездит по циклу «источник → получатель → источник»:
 *   - берёт пачку товаров у CargoSource,
 *   - везёт её к CargoSink,
 *   - выгружает (сколько примут),
 *   - возвращается обратно.
 *
 * Используется для всех линий:
 *   - Фабрика → Магазин         (source=Factory,   sink=Shop);
 *   - Фабрика → Переработчик    (source=Factory,   sink=Processor);
 *   - Переработчик → Магазин    (source=Processor, sink=Shop).
 *
 * Оба маршрута независимы, потому что дороги односторонние:
 * обратный путь — НЕ реверс прямого, а самостоятельный цикл.
 *
 * Движение плавное: между соседними клетками позиция интерполируется
 * по времени.
 *
 * Обновление маршрута на лету:
 *   - если игрок снёс/построил дорогу, Game зовёт {@link #updateRoutes}
 *     с новыми путями — машина продолжит движение с ближайшей точки;
 *   - если пути вообще нет, Game зовёт {@link #markNoRoute()} — машина
 *     замирает и ждёт. Когда путь восстановится, {@link #updateRoutes}
 *     снова запустит её в ту же сторону.
 *
 * Отзыв машин:
 *   - {@link #recall()} телепортирует машину на стартовую клетку,
 *     сбрасывает груз и переводит в IDLE;
 *   - пока {@link #deliveryEnabled} == false, машина не начинает рейс;
 *   - {@link #setDeliveryEnabled(boolean)} включает доставку обратно.
 *
 * ВАЖНО про RoadRegistry:
 *   - машина НЕ занимает стартовую клетку маршрута (она общая для всех
 *     машин этого источника);
 *   - регистрируется только на промежуточных клетках;
 *   - при удалении из игры нужно вызвать {@link #dispose()}.
 */
public class UniversalCar {

    public enum State {
        TO_SINK,     // едет от источника к получателю
        UNLOADING,   // разгружается у получателя
        RETURNING,   // едет обратно к источнику
        IDLE,        // стоит у источника, ждёт груз
        BLOCKED,     // получатель полон, ждёт
        NO_ROUTE     // маршрут сломан, ждёт восстановления
    }

    /** Скорость движения: клеток в секунду. */
    private static final double SPEED = 2.0;

    /** Сколько секунд разгрузки на один товар в пачке. */
    private static final double UNLOAD_PER_ITEM = 0.05;

    /** Базовая длительность разгрузки. */
    private static final double UNLOAD_BASE = 0.6;

    private final CargoSource source;
    private final CargoSink sink;

    /** Путь от источника к получателю. */
    private List<Point> pathToSink;

    /** Путь от получателя к источнику (другой цикл, не реверс). */
    private List<Point> pathToSource;

    private State state = State.IDLE;

    /**
     * Куда машина ехала до поломки маршрута.
     * Актуально только при state == NO_ROUTE.
     */
    private State pendingState = State.TO_SINK;

    /** Индекс клетки, к которой едем (в текущем пути). */
    private int pathIndex = 0;

    /** Прогресс между path[pathIndex-1] и path[pathIndex], 0..1. */
    private double segmentProgress = 0;

    /** Мировые координаты (с дробной частью). */
    private double x, y;

    /** Таймер разгрузки. */
    private double unloadTimer = 0;

    /** Груз — список товаров. */
    private final List<Product> cargo = new ArrayList<>();

    /** Грузоподъёмность этой машины. */
    private final int capacity;

    /** Клетка, на которой машина зарегистрирована в RoadRegistry. */
    private Point registeredCell;

    /** Разрешена ли доставка. Управляется из Game. */
    private boolean deliveryEnabled = true;

    // ==================== КОНСТРУКТОР ====================

    public UniversalCar(CargoSource source, CargoSink sink,
                        List<Point> pathToSink, List<Point> pathToSource) {
        if (source == null || sink == null) {
            throw new IllegalArgumentException("source/sink is null");
        }
        if (pathToSink == null || pathToSink.isEmpty()) {
            throw new IllegalArgumentException("pathToSink is empty");
        }
        if (pathToSource == null || pathToSource.isEmpty()) {
            throw new IllegalArgumentException("pathToSource is empty");
        }

        this.source = source;
        this.sink = sink;
        this.pathToSink = new ArrayList<>(pathToSink);
        this.pathToSource = new ArrayList<>(pathToSource);
        this.capacity = source.getCarryCapacity();

        // Ставим машину на стартовую клетку, но НЕ регистрируем её.
        Point start = this.pathToSink.get(0);
        this.x = start.x + 0.5;
        this.y = start.y + 0.5;
        this.registeredCell = null;
    }

    // ==================== ТИК ====================

    public void tick(double dt) {
        switch (state) {
            case BLOCKED -> {
                if (sink.hasFreeSpace()) state = State.IDLE;
            }
            case NO_ROUTE -> { /* ждём пересчёта маршрута */ }
            case TO_SINK -> advance(dt, pathToSink, () -> {
                state = State.UNLOADING;
                unloadTimer = UNLOAD_BASE + cargo.size() * UNLOAD_PER_ITEM;
            });
            case UNLOADING -> {
                unloadTimer -= dt;
                if (unloadTimer <= 0) {
                    if (!cargo.isEmpty()) {
                        sink.acceptProducts(cargo);
                        cargo.clear();
                    }
                    beginPath(pathToSource, State.RETURNING);
                }
            }
            case RETURNING -> advance(dt, pathToSource, () -> {
                state = State.IDLE;
            });
            default -> { /* IDLE — ничего */ }
        }
    }

    // ==================== ДВИЖЕНИЕ ====================

    private void advance(double dt, List<Point> path, Runnable onFinish) {
        if (path == null || path.size() < 2) {
            onFinish.run();
            return;
        }

        if (pathIndex >= path.size()) {
            onFinish.run();
            return;
        }

        double step = SPEED * dt;

        while (step > 0 && pathIndex < path.size()) {
            double remaining = 1.0 - segmentProgress;

            if (step < remaining) {
                segmentProgress += step;
                step = 0;
            } else {
                step -= remaining;
                pathIndex++;
                segmentProgress = 0;

                if (pathIndex < path.size()) {
                    registerOn(path.get(pathIndex));
                }
            }
        }

        if (pathIndex >= path.size()) {
            Point last = path.get(path.size() - 1);
            x = last.x + 0.5;
            y = last.y + 0.5;
            onFinish.run();
            return;
        }

        Point from = path.get(pathIndex - 1);
        Point to = path.get(pathIndex);
        double t = Math.min(1.0, Math.max(0.0, segmentProgress));
        x = (from.x + 0.5) * (1 - t) + (to.x + 0.5) * t;
        y = (from.y + 0.5) * (1 - t) + (to.y + 0.5) * t;
    }

    private void beginPath(List<Point> path, State newState) {
        if (path == null || path.isEmpty()) {
            state = State.IDLE;
            return;
        }

        this.pathIndex = 1;
        this.segmentProgress = 0;

        Point start = path.get(0);
        if (registeredCell != null && !registeredCell.equals(start)) {
            RoadRegistry.release(registeredCell);
            registeredCell = null;
        }
        // На start НЕ регистрируемся — стартовая клетка общая.

        x = start.x + 0.5;
        y = start.y + 0.5;
        state = newState;
    }

    private void registerOn(Point cell) {
        if (cell == null) return;
        if (registeredCell != null && registeredCell.equals(cell)) return;

        if (registeredCell != null) {
            RoadRegistry.release(registeredCell);
        }
        registeredCell = cell;
        RoadRegistry.occupy(cell);
    }

    // ==================== ЗАПУСК ДОСТАВКИ ====================

    /**
     * Попытаться начать рейс.
     *
     *  0) если доставка отключена — нет;
     *  1) если машина не IDLE — нет;
     *  2) если у получателя нет места — BLOCKED;
     *  3) вычисляем, сколько можем взять: min(capacity, свободное место);
     *  4) забираем пачку у источника;
     *  5) если пусто — ждём следующего тика;
     *  6) едем.
     */
    public boolean tryStartDelivery() {
        if (!deliveryEnabled) return false;
        if (state != State.IDLE) return false;

        int freeSpace = sink.freeSpace();
        if (freeSpace <= 0) {
            state = State.BLOCKED;
            return false;
        }

        int wantToTake = Math.min(capacity, freeSpace);
        List<Product> taken = source.takeProducts(wantToTake);
        if (taken.isEmpty()) return false;

        cargo.clear();
        cargo.addAll(taken);

        beginPath(pathToSink, State.TO_SINK);
        return true;
    }

    // ==================== ОБНОВЛЕНИЕ МАРШРУТОВ ====================

    public void updateRoutes(List<Point> toSink, List<Point> toSource) {
        if (toSink == null || toSource == null) return;

        this.pathToSink = new ArrayList<>(toSink);
        this.pathToSource = new ArrayList<>(toSource);

        if (!deliveryEnabled) return;

        // Сценарий 1: маршрут был сломан — оживляем машину.
        if (state == State.NO_ROUTE) {
            if (pendingState == State.RETURNING) {
                beginPath(pathToSource, State.RETURNING);
            } else {
                beginPath(pathToSink, State.TO_SINK);
            }
            return;
        }

        // Сценарий 2: машина в пути — пересаживаем на новый маршрут
        // с ближайшей клетки.
        if (state == State.TO_SINK || state == State.RETURNING) {
            List<Point> active = (state == State.TO_SINK) ? pathToSink : pathToSource;
            if (active.size() < 2) return;

            int nearest = findNearestIndex(active);
            this.pathIndex = Math.min(nearest + 1, active.size() - 1);
            this.segmentProgress = 0;

            Point np = active.get(nearest);
            this.x = np.x + 0.5;
            this.y = np.y + 0.5;

            if (registeredCell != null) {
                RoadRegistry.release(registeredCell);
                registeredCell = null;
            }
        }

        // Сценарий 3: IDLE / UNLOADING / BLOCKED — просто сохранили пути.
    }

    public void markNoRoute() {
        dispose();
        if (state == State.NO_ROUTE) return;

        if (state == State.RETURNING) {
            pendingState = State.RETURNING;
        } else {
            pendingState = State.TO_SINK;
        }
        state = State.NO_ROUTE;
    }

    private int findNearestIndex(List<Point> path) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Point p = path.get(i);
            double dx = (p.x + 0.5) - x;
            double dy = (p.y + 0.5) - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ==================== ОТЗЫВ / ВОЗОБНОВЛЕНИЕ ====================

    public void recall() {
        dispose();

        if (!cargo.isEmpty()) {
            // Возврат товара источнику не поддерживается — очищаем.
            // (Для возврата нужен addProduct на Factory/Processor.)
            cargo.clear();
        }

        if (pathToSink != null && !pathToSink.isEmpty()) {
            Point start = pathToSink.get(0);
            this.x = start.x + 0.5;
            this.y = start.y + 0.5;
        }

        this.pathIndex = 0;
        this.segmentProgress = 0;
        this.unloadTimer = 0;
        this.pendingState = State.TO_SINK;
        this.state = State.IDLE;
    }

    public boolean isDeliveryEnabled() { return deliveryEnabled; }

    public void setDeliveryEnabled(boolean enabled) { this.deliveryEnabled = enabled; }

    // ==================== ОЧИСТКА ====================

    public void dispose() {
        if (registeredCell != null) {
            RoadRegistry.release(registeredCell);
            registeredCell = null;
        }
    }

    // ==================== ГЕТТЕРЫ ====================

    public double getX() { return x; }
    public double getY() { return y; }
    public State getState() { return state; }
    public CargoSource getSource() { return source; }
    public CargoSink getSink() { return sink; }

    public int getCargoCount() { return cargo.size(); }
    public int getCapacity() { return capacity; }

    public List<Product> getCargo() { return Collections.unmodifiableList(cargo); }

    public Product getCarrying() {
        return cargo.isEmpty() ? null : cargo.get(0);
    }

    public Point getCurrentCell() { return registeredCell; }

    public List<Point> getPathToSink()   { return pathToSink; }
    public List<Point> getPathToSource() { return pathToSource; }
}