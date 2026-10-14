package ru.student.fabricTown.agents;

import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.logic.RoadRegistry;
import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Машина-курьер.
 *
 * Ездит по маршруту из списка клеток:
 *   - toShop    — от выхода фабрики до входа магазина;
 *   - toFactory — от входа магазина обратно до выхода фабрики.
 *
 * Оба маршрута независимы, потому что дороги односторонние:
 * обратный путь — это НЕ реверс прямого, а самостоятельный цикл.
 *
 * Движение — плавное: между двумя соседними клетками машина
 * интерполирует позицию по времени.
 *
 * Перевозит ПАЧКУ товаров, а не один. Грузоподъёмность зависит
 * от размера фабрики:
 *   S1x1         → 10
 *   S1x2 / S2x1  → 13
 *   S2x2         → 16
 *   S3x3         → 19
 *
 * Обновление маршрута на лету:
 *   - Если игрок снёс/построил дорогу, Game зовёт {@link #updateRoutes}
 *     с новыми путями — машина продолжит движение с ближайшей точки
 *     нового маршрута.
 *   - Если пути вообще нет, Game зовёт {@link #markNoRoute()} — машина
 *     останавливается и ждёт. Когда путь восстановится, {@link #updateRoutes}
 *     снова запустит её в ту же сторону (в магазин или на фабрику).
 *
 * Отзыв машин:
 *   - {@link #recall()} телепортирует машину на стартовую клетку,
 *     сбрасывает груз и переводит в IDLE.
 *   - Пока {@link #deliveryEnabled} == false, машина не начинает рейс
 *     (tryStartDelivery сразу возвращает false).
 *   - {@link #setDeliveryEnabled(boolean)} включает доставку обратно.
 *
 * ВАЖНО про RoadRegistry:
 *   - Машина НЕ занимает стартовую клетку маршрута. Иначе она
 *     блокирует саму возможность построить второй маршрут от той же
 *     фабрики (стартовая клетка общая для всех её машин).
 *   - Машина регистрируется только на промежуточных клетках, куда
 *     она реально переезжает.
 *   - При удалении машины из игры нужно вызвать {@link #dispose()},
 *     чтобы освободить занятую клетку.
 */
public class Car {

    public enum State {
        TO_SHOP,     // едет на фабрику → в магазин
        UNLOADING,   // разгружается в магазине
        RETURNING,   // едет из магазина → на фабрику
        IDLE,        // стоит на фабрике, ждёт груз
        BLOCKED,     // магазин полон, ждёт
        NO_ROUTE     // маршрут сломан, ждёт восстановления
    }

    /** Скорость движения: клеток в секунду. */
    private static final double SPEED = 2.0;

    /** Сколько секунд разгрузки на один товар в пачке. */
    private static final double UNLOAD_PER_ITEM = 0.05;

    /** Базовая длительность разгрузки. */
    private static final double UNLOAD_BASE = 0.6;

    private final Factory factory;
    private final Shop shop;

    /** Путь от фабрики к магазину. */
    private List<Point> pathToShop;

    /** Путь от магазина к фабрике (другой цикл, не реверс). */
    private List<Point> pathToFactory;

    private State state = State.IDLE;

    /**
     * Куда машина ехала до того, как маршрут сломался.
     * Нужно, чтобы после восстановления пути продолжить в ту же сторону.
     * Актуально только при state == NO_ROUTE.
     */
    private State pendingState = State.TO_SHOP;

    /** Индекс клетки, к которой едем (в текущем пути). */
    private int pathIndex = 0;

    /** Прогресс между path[pathIndex-1] и path[pathIndex], 0..1. */
    private double segmentProgress = 0;

    /** Мировые координаты (с дробной частью). */
    private double x, y;

    /** Таймер разгрузки. */
    private double unloadTimer = 0;

    /** Груз — список товаров (может быть пустым). */
    private final List<Product> cargo = new ArrayList<>();

    /** Грузоподъёмность этой машины (зависит от размера фабрики). */
    private final int capacity;

    /**
     * Клетка, на которой машина зарегистрирована в RoadRegistry.
     * null — машина ещё не выехала или стоит на стартовой клетке.
     */
    private Point registeredCell;

    /**
     * Разрешена ли доставка. Управляется из Game через
     * recallAllCars() / resumeAllCars().
     *
     *   - true  — машина работает как обычно;
     *   - false — машина не начинает новые рейсы, а при вызове
     *             recall() телепортируется на фабрику и замирает.
     */
    private boolean deliveryEnabled = true;

    // ==================== КОНСТРУКТОР ====================

    public Car(Factory factory, Shop shop,
               List<Point> pathToShop, List<Point> pathToFactory) {
        if (pathToShop == null || pathToShop.isEmpty()) {
            throw new IllegalArgumentException("pathToShop is empty");
        }
        if (pathToFactory == null || pathToFactory.isEmpty()) {
            throw new IllegalArgumentException("pathToFactory is empty");
        }

        this.factory = factory;
        this.shop = shop;
        this.pathToShop = new ArrayList<>(pathToShop);
        this.pathToFactory = new ArrayList<>(pathToFactory);

        // Грузоподъёмность — свойство фабрики.
        this.capacity = factory.getCarryCapacity();

        // Ставим машину на стартовую клетку toShop, но НЕ регистрируем её.
        // Это позволяет другим машинам этой же фабрики стартовать с той же
        // клетки и не мешать построению их маршрутов.
        Point start = this.pathToShop.get(0);
        this.x = start.x + 0.5;
        this.y = start.y + 0.5;
        this.registeredCell = null;
    }

    // ==================== ТИК ====================

    public void tick(double dt) {
        switch (state) {
            case BLOCKED -> {
                if (!shop.isFull()) state = State.IDLE;
            }
            case NO_ROUTE -> {
                // Ждём, пока Game пересчитает маршрут.
                // Ничего не делаем — позиция заморожена.
            }
            case TO_SHOP -> advance(dt, pathToShop, () -> {
                state = State.UNLOADING;
                // Чем больше пачка — тем дольше разгрузка
                unloadTimer = UNLOAD_BASE + cargo.size() * UNLOAD_PER_ITEM;
            });
            case UNLOADING -> {
                unloadTimer -= dt;
                if (unloadTimer <= 0) {
                    if (!cargo.isEmpty()) {
                        // Отдаём магазину всё, что он может принять.
                        // addProducts сам остановится, если склад полон.
                        shop.addProducts(cargo);
                        cargo.clear();
                    }
                    beginPath(pathToFactory, State.RETURNING);
                }
            }
            case RETURNING -> advance(dt, pathToFactory, () -> {
                state = State.IDLE;
            });
            default -> { /* IDLE — ничего */ }
        }
    }

    // ==================== ДВИЖЕНИЕ ====================

    /**
     * Продвинуть машину вдоль текущего пути на dt.
     * Если дошли до конца — вызвать onFinish.
     */
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
                    Point newCell = path.get(pathIndex);
                    registerOn(newCell);
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

    /**
     * Начать движение по заданному пути.
     * Машина встаёт на первую клетку, но НЕ регистрирует её
     * в RoadRegistry (см. комментарий к классу).
     */
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
        // На start НЕ регистрируемся — иначе заблокируем стартовую клетку
        // для других машин с этой же фабрики.

        x = start.x + 0.5;
        y = start.y + 0.5;
        state = newState;
    }

    /** Зарегистрироваться на новой клетке, снять старую (если была). */
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
     *  0) если доставка отключена (deliveryEnabled == false) — нет;
     *  1) если машина не IDLE — нет;
     *  2) если магазин полон — BLOCKED;
     *  3) вычисляем, сколько можем взять:
     *       min(грузоподъёмность, свободное место в магазине);
     *  4) забираем пачку с фабрики;
     *  5) если пусто — ждём следующего тика;
     *  6) едем.
     */
    public boolean tryStartDelivery() {
        if (!deliveryEnabled) return false;
        if (state != State.IDLE) return false;
        if (shop.isFull()) {
            state = State.BLOCKED;
            return false;
        }
//        if (pathToShop == null || pathToShop.isEmpty()) return false;
//
//        if (shop.isFull()) { state = State.BLOCKED; return false; }
        int freeSpace = shop.getCapacity() - shop.totalStored();
        if (freeSpace <= 0) {
            state = State.BLOCKED;
            return false;
        }

        int wantToTake = Math.min(capacity, freeSpace);
        List<Product> taken = factory.takeProducts(wantToTake);
        if (taken.isEmpty()) return false;

        cargo.clear();
        cargo.addAll(taken);

        beginPath(pathToShop, State.TO_SHOP);
        return true;
    }

    // ==================== ОБНОВЛЕНИЕ МАРШРУТОВ ====================

    /**
     * Обновить оба маршрута на лету (после изменения дорог).
     *
     * Три сценария:
     *   1) state == NO_ROUTE — маршрут восстановлен, запускаем машину
     *      в ту же сторону, куда она ехала до поломки.
     *   2) state == TO_SHOP или RETURNING — машина в пути, ставим её
     *      на ближайшую клетку нового маршрута и едем дальше.
     *   3) IDLE / UNLOADING / BLOCKED — просто запоминаем новые пути,
     *      машина подхватит их на следующем рейсе.
     *
     * ВАЖНО: если deliveryEnabled == false, машина НЕ оживает из NO_ROUTE —
     * она ждёт, пока её не разбудят через setDeliveryEnabled(true).
     */
    public void updateRoutes(List<Point> toShop, List<Point> toFactory) {
        if (toShop == null || toFactory == null) {
            //markNoRoute();
            return;
        }
        this.pathToShop = new ArrayList<>(toShop);
        this.pathToFactory = new ArrayList<>(toFactory);

        // Если доставка отключена — новые пути просто сохраняем,
        // машина остаётся там, где стоит.
        if (!deliveryEnabled) return;

        // Сценарий 1: маршрут был сломан — оживляем машину.
        if (state == State.NO_ROUTE) {
            if (pendingState == State.RETURNING) {
                beginPath(pathToFactory, State.RETURNING);
            } else {
                beginPath(pathToShop, State.TO_SHOP);
            }
            return;
        }

        // Сценарий 2: машина в пути — пересаживаем на новый маршрут
        // с ближайшей клетки, чтобы не телепортировать её.
        if (state == State.TO_SHOP || state == State.RETURNING) {
            List<Point> active = (state == State.TO_SHOP) ? pathToShop : pathToFactory;
            if (active.size() < 2) return;

            int nearest = findNearestIndex(active);
            this.pathIndex = Math.min(nearest + 1, active.size() - 1);
            this.segmentProgress = 0;

            // >>> ФИКС: синхронизировать позицию с точкой nearest
            Point np = active.get(nearest);
            this.x = np.x + 0.5;
            this.y = np.y + 0.5;

            if (registeredCell != null) {
                RoadRegistry.release(registeredCell);
                registeredCell = null;
            }
        }

        // Сценарий 3: IDLE / UNLOADING / BLOCKED — просто сохранили пути,
        // ничего не трогаем. Машина использует их при следующем рейсе.
    }

    /**
     * Маршрут сломан — остановить машину и ждать.
     * Запоминаем, куда она ехала, чтобы при восстановлении пути
     * продолжить в правильную сторону.
     *
     * Вызывается из Game.rebuildAllRoutes(), когда findPath вернул null.
     */
    public void markNoRoute() {
        // Освобождаем занятую клетку — её может уже не быть на карте.
        dispose();

        if (state == State.NO_ROUTE) return;

        // Запоминаем сторону, в которую ехали.
        // UNLOADING и IDLE считаем как «на фабрике» — после восстановления
        // пути машина поедет в магазин.
        if (state == State.RETURNING) {
            pendingState = State.RETURNING;
        } else {
            pendingState = State.TO_SHOP;
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

    /**
     * Отозвать машину на фабрику.
     *
     *   - Освобождает занятую клетку.
     *   - Телепортирует на стартовую клетку маршрута toShop.
     *   - Сбрасывает груз (товар возвращается на склад фабрики).
     *   - Переходит в IDLE.
     *
     * После recall() машина не выезжает, пока deliveryEnabled == false
     * (см. tryStartDelivery).
     */
    public void recall() {
        // Освобождаем занятую клетку — она может быть уже удалена.
        dispose();

        // Груз возвращаем на склад фабрики, чтобы товар не пропадал.
        if (!cargo.isEmpty()) {
            // Factory.takeProducts возвращает список — кладём обратно
            // через productionProgress? Нет, у Factory нет addProduct.
            // Поэтому просто очищаем груз: по правилам игры отозванный
            // товар «исчезает» (или можно вернуть через storage, если
            // добавишь метод в Factory).
            cargo.clear();
        }

        // Ставим на стартовую клетку первого маршрута.
        if (pathToShop != null && !pathToShop.isEmpty()) {
            Point start = pathToShop.get(0);
            this.x = start.x + 0.5;
            this.y = start.y + 0.5;
        }

        this.pathIndex = 0;
        this.segmentProgress = 0;
        this.unloadTimer = 0;
        this.pendingState = State.TO_SHOP;
        this.state = State.IDLE;
    }

    public boolean isDeliveryEnabled() {
        return deliveryEnabled;
    }

    /**
     * Управляет тем, может ли машина начинать рейсы.
     * Если выключить во время рейса — машина не остановится сама,
     * для этого нужно вызвать {@link #recall()}.
     */
    public void setDeliveryEnabled(boolean enabled) {
        this.deliveryEnabled = enabled;
    }

    // ==================== ОЧИСТКА ====================

    /**
     * Освободить занятую клетку. Нужно вызывать перед удалением машины
     * из игры — иначе клетка останется «занятой» навсегда.
     */
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
    public Shop getShop() { return shop; }
    public Factory getFactory() { return factory; }

    /** Сколько товаров сейчас в машине. */
    public int getCargoCount() { return cargo.size(); }

    /** Грузоподъёмность этой машины. */
    public int getCapacity() { return capacity; }

    /**
     * Список товаров в грузе (read-only).
     */
    public List<Product> getCargo() {
        return Collections.unmodifiableList(cargo);
    }

    /**
     * Для совместимости с рендером/UI, который мог вызывать getCarrying().
     * Возвращает ПЕРВЫЙ товар из груза или null.
     */
    public Product getCarrying() {
        return cargo.isEmpty() ? null : cargo.get(0);
    }

    public Point getCurrentCell() { return registeredCell; }

    public List<Point> getPathToShop()    { return pathToShop; }
    public List<Point> getPathToFactory() { return pathToFactory; }
}