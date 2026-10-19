package ru.student.fabricTown.visualization;

import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.core.GameLoop;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.PlacementRules;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.Set;

public class GamePanel extends JPanel {

    // ==================== ССЫЛКИ НА МОДЕЛЬ ====================
    private final Game game;
    private final Camera camera = new Camera();
    private final GameLoop loop;

    // ==================== UI-ПАНЕЛИ ====================
    private final HudPanel hud;
    private final BuildPanel buildPanel;
    private final ShopPanel shopPanel;

    // ==================== ОВЕРЛЕЙ СВЯЗЕЙ ФАБРИКИ ====================
    private final RouteOverlay routeOverlay = new RouteOverlay();

    // ==================== РЕЖИМ ВЗАИМОДЕЙСТВИЯ ====================
    private BuildMode mode = BuildMode.NONE;
    private Blueprint selectedBlueprint = null;

    // Курсор в мировых координатах
    private Point hoverCell = null;
    private boolean hoverValid = false;
    private Building hoverBuilding = null;

    // ==================== ПЕРЕТАСКИВАНИЕ КАМЕРЫ ====================
    private int lastMouseX, lastMouseY;
    private boolean dragging = false;

    // ==================== РИСОВАНИЕ ЗАЖАТОЙ ЛКМ ====================
    private boolean painting = false;
    private Point lastPaintedCell = null;

    /** Зажата ли клавиша D (снос линией в любом режиме). */
    private boolean dKeyHeld = false;

    // ==================== КОНСТРУКТОР ====================
    public GamePanel(Game game) {
        this.game = game;

        setBackground(new Color(25, 25, 30));
        setFocusable(true);
        setLayout(new BorderLayout());

        // --- HUD сверху ---
        hud = new HudPanel(game);
        add(hud, BorderLayout.NORTH);

        // --- Панель строительства слева ---
        buildPanel = new BuildPanel(
                this::onBlueprintSelected,
                this::cancelMode,
                this::enableDemolish,
                this::toggleDelivery
        );
        add(buildPanel, BorderLayout.WEST);

        // --- Магазин объектов справа ---
        shopPanel = new ShopPanel(game);
        add(shopPanel, BorderLayout.EAST);

        // --- Игровой цикл ---
        this.loop = new GameLoop(game, dt -> {
            camera.update(dt);
            NotificationRenderer.tickAnimations(game.getNotifications(), dt);
            hud.refresh();
            repaint();
        }, 60);
        this.loop.start();

        installMouseHandlers();
        installKeyHandlers();

        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    // ==================== ОБРАБОТЧИКИ МЫШИ ====================
    private void installMouseHandlers() {
        MouseAdapter ma = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                // === Двойной клик по зданию → подсветка связей / выбор ===
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    onDoubleClick(e);
                    return;
                }

                if (e.getButton() == MouseEvent.BUTTON2
                        || e.getButton() == MouseEvent.BUTTON3) {
                    dragging = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                } else if (e.getButton() == MouseEvent.BUTTON1) {
                    // Клик по оверлею имеет приоритет
                    if (handleOverlayClick(e)) return;

                    // Обычный клик — постройка / снос
                    onClick(e);

                    // Включаем "рисование" для PLACE / DEMOLISH
                    // ИЛИ если зажат D — снос линией в любом режиме.
                    boolean paintMode = (mode == BuildMode.PLACE
                            || mode == BuildMode.DEMOLISH
                            || dKeyHeld);
                    if (paintMode) {
                        painting = true;
                        lastPaintedCell = cellUnder(e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                painting = false;
                lastPaintedCell = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    // панорамирование камеры
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    camera.targetOffsetX -= dx / camera.zoom;
                    camera.targetOffsetY -= dy / camera.zoom;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                } else {
                    updateHover(e);

                    // === Рисование подряд ===
                    if (painting) {
                        Point current = cellUnder(e.getX(), e.getY());
                        if (current != null && !current.equals(lastPaintedCell)) {
                            paintLine(lastPaintedCell, current);
                            lastPaintedCell = current;
                        }
                    }
                }
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e);
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() > 0 ? 0.9 : 1.1;
                camera.targetZoom = Math.max(8, Math.min(128, camera.targetZoom * factor));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverCell = null;
                hoverBuilding = null;
                hud.setCoord(null);
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private void updateHover(MouseEvent e) {
        double wx = camera.screenToWorldX(e.getX());
        double wy = camera.screenToWorldY(e.getY());
        hoverCell = new Point((int) Math.floor(wx), (int) Math.floor(wy));

        hoverBuilding = game.getMap().get(hoverCell);

        if (mode == BuildMode.PLACE && selectedBlueprint != null) {
            hoverValid = PlacementRules.check(selectedBlueprint, hoverCell,
                    game.getMap()) == null;
        } else {
            hoverValid = false;
        }

        hud.setCoord(hoverCell);
    }

    // ==================== КЛИКИ ====================

    /**
     * Двойной клик по зданию:
     *   - для фабрики → подсветка связей + выбор в панели;
     *   - для переработчика → выбор в панели;
     *   - для магазина/дома → выбор в панели.
     */
    private void onDoubleClick(MouseEvent e) {
        int listIdx = routeOverlay.hitTestList(e.getX(), e.getY(), getWidth());
        if (listIdx >= 0) return;

        Point cell = cellUnder(e.getX(), e.getY());
        Building b = game.getMap().get(cell);

        if (b == null) {
            // Клик по пустому месту — снимаем всё
            routeOverlay.clear();
            shopPanel.selectBuilding(null);
            repaint();
            return;
        }

        // Фабрика — показываем связи И выбираем в панели
        if (b instanceof Factory f) {
            routeOverlay.toggle(f, game);
            shopPanel.selectBuilding(f);
        }
        // Переработчик — снимаем оверлей фабрики, выбираем в панели
        else if (b instanceof Processor p) {
            routeOverlay.clear();
            shopPanel.selectBuilding(p);
        }
        // Магазин — выбираем в панели
        else if (b instanceof Shop s) {
            routeOverlay.clear();
            shopPanel.selectBuilding(s);
        }
        // Дом — выбираем в панели
        else if (b instanceof House h) {
            routeOverlay.clear();
            shopPanel.selectBuilding(h);
        }
        // Дорога или другое — просто снимаем оверлей
        else {
            routeOverlay.clear();
            shopPanel.selectBuilding(null);
        }

        repaint();
    }

    /**
     * Обработать клик по оверлею связей фабрики (маркеры и список).
     * Возвращает true, если клик «поглощён» оверлеем.
     */
    private boolean handleOverlayClick(MouseEvent e) {
        if (!routeOverlay.isActive()) return false;

        // 1) Клик по списку
        int listIdx = routeOverlay.hitTestList(e.getX(), e.getY(), getWidth());
        if (listIdx >= 0) {
            Shop s = routeOverlay.getMarkers().get(listIdx).shop;
            if (s == routeOverlay.getSelectedShop()) {
                routeOverlay.deselectShop();
            } else {
                routeOverlay.selectShop(s);
            }
            repaint();
            return true;
        }

        // 2) Клик по маркеру магазина
        RouteOverlay.ShopMarker m = routeOverlay.hitTest(e.getX(), e.getY());
        if (m != null) {
            if (m.shop == routeOverlay.getSelectedShop()) {
                routeOverlay.deselectShop();
            } else {
                routeOverlay.selectShop(m.shop);
            }
            repaint();
            return true;
        }

        // Клик мимо оверлея — снимаем подсветку, но не поглощаем клик.
        routeOverlay.clear();
        return false;
    }

    /**
     * Одиночный клик: точечная постройка или снос с показом в HUD.
     */
    private void onClick(MouseEvent e) {
        Point cell = cellUnder(e.getX(), e.getY());

        // Если зажат D — сносим в любом режиме.
        if (dKeyHeld) {
            String err = game.trySell(cell);
            if (err != null) {
                hud.setStatus("❌ " + err, new Color(255, 120, 120));
            } else {
                hud.setStatus("💥 Снесено", new Color(240, 200, 120));
            }
            routeOverlay.rebuildMarkers(game);
            return;
        }

        switch (mode) {
            case PLACE -> {
                if (selectedBlueprint == null) return;
                String err = game.tryBuild(selectedBlueprint, cell);
                if (err != null) {
                    hud.setStatus("❌ " + err, new Color(255, 120, 120));
                } else {
                    hud.setStatus("✅ Построено: " + selectedBlueprint.displayName,
                            new Color(120, 240, 120));
                }
                routeOverlay.rebuildMarkers(game);
            }
            case DEMOLISH -> {
                String err = game.trySell(cell);
                if (err != null) {
                    hud.setStatus("❌ " + err, new Color(255, 120, 120));
                } else {
                    hud.setStatus("💥 Снесено", new Color(240, 200, 120));
                }
                routeOverlay.rebuildMarkers(game);
            }
            default -> { /* ничего — только камера */ }
        }
    }

    // ==================== РИСОВАНИЕ ЛИНИИ ====================

    /** Мировая клетка под экранной точкой. */
    private Point cellUnder(int sx, int sy) {
        double wx = camera.screenToWorldX(sx);
        double wy = camera.screenToWorldY(sy);
        return new Point((int) Math.floor(wx), (int) Math.floor(wy));
    }

    /**
     * Пройти по всем клеткам между from и to (Брезенхэм)
     * и в каждой выполнить постройку/снос.
     */
    private void paintLine(Point from, Point to) {
        if (to == null) return;
        if (from == null) {
            paintCell(to);
            return;
        }

        int x0 = from.x, y0 = from.y;
        int x1 = to.x,   y1 = to.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            paintCell(new Point(x0, y0));
            if (x0 == x1 && y0 == y1) break;

            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 <  dx) { err += dx; y0 += sy; }
        }
    }

    /**
     * Построить или снести один объект в клетке.
     * Работает «тихо»: не спамит HUD, ошибки игнорируются.
     */
    private void paintCell(Point cell) {
        if (cell == null) return;

        // Проверка границ карты
        if (cell.x < game.getMapMinX() || cell.x > game.getMapMaxX()
                || cell.y < game.getMapMinY() || cell.y > game.getMapMaxY()) {
            return;
        }

        // Зажат D — снос линией, в любом режиме.
        if (dKeyHeld) {
            game.trySell(cell);
            return;
        }

        if (mode == BuildMode.PLACE) {
            if (selectedBlueprint == null) return;
            game.tryBuild(selectedBlueprint, cell);
        } else if (mode == BuildMode.DEMOLISH) {
            game.trySell(cell);
        }
    }

    // ==================== ОБРАБОТКА КЛАВИШ ====================
    private void installKeyHandlers() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancelMode");
        am.put("cancelMode", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (routeOverlay.isActive()) {
                    if (routeOverlay.getSelectedShop() != null) {
                        routeOverlay.deselectShop();
                    } else {
                        routeOverlay.clear();
                    }
                    repaint();
                    return;
                }
                cancelMode();
            }
        });

        // ===== D: нажали / отпустили =====
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "dPressed");
        am.put("dPressed", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                dKeyHeld = true;
                enableDemolish();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "dReleased");
        am.put("dReleased", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                dKeyHeld = false;
            }
        });

        im.put(KeyStroke.getKeyStroke("R"), "rotateRoad");
        am.put("rotateRoad", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { rotateRoad(); }
        });
    }

    /**
     * Поворот дороги клавишей R.
     */
    private void rotateRoad() {
        // === Вариант 1: крутим призрак ===
        if (mode == BuildMode.PLACE
                && selectedBlueprint != null
                && selectedBlueprint.kind == Blueprint.Kind.ROAD
                && selectedBlueprint.shape != null) {

            if (selectedBlueprint.shape == RoadShape.CROSS) {
                hud.setStatus("Крест крутить не нужно — он на все стороны",
                        new Color(200, 200, 200));
                return;
            }
            if (isStraightShape(selectedBlueprint.shape)) {
                hud.setStatus("Прямую дорогу крутить не нужно",
                        new Color(200, 200, 200));
                return;
            }

            selectedBlueprint = rotateBlueprint(selectedBlueprint);
            hud.setStatus("Поворот: " + selectedBlueprint.displayName,
                    new Color(200, 240, 200));
            repaint();
            return;
        }

        // === Вариант 2: крутим уже построенную дорогу ===
        java.awt.Point mouse = getMousePosition();
        if (mouse == null) {
            hud.setStatus("Наведите мышь на дорогу и нажмите R",
                    new Color(200, 200, 200));
            return;
        }

        Point cell = cellUnder(mouse.x, mouse.y);

        Building b = game.getMap().get(cell);
        if (!(b instanceof Road r)) {
            hud.setStatus("Под курсором нет дороги", new Color(200, 200, 200));
            return;
        }
        if (r.isStraight()) {
            hud.setStatus("Прямую дорогу крутить не нужно",
                    new Color(200, 200, 200));
            return;
        }
        if (r.isCross()) {
            hud.setStatus("Крест крутить не нужно — он на все стороны",
                    new Color(200, 200, 200));
            return;
        }

        String before = r.getTypeName();
        game.rotateRoadAt(cell);
        String after = r.getTypeName();
        hud.setStatus("Поворот: " + before + " → " + after + " " + cell,
                new Color(120, 240, 120));
        repaint();
    }

    /** Прямая — это shape с одним выходом. */
    private boolean isStraightShape(RoadShape shape) {
        return shape.getExits().size() == 1;
    }

    /**
     * Повернуть blueprint на 90° CW.
     */
    private Blueprint rotateBlueprint(Blueprint bp) {
        if (bp == null || bp.shape == null) return bp;

        EnumSet<Direction> newExits = EnumSet.noneOf(Direction.class);
        for (Direction d : bp.shape.getExits()) newExits.add(rotateCW(d));

        EnumSet<Direction> newPrimary = EnumSet.noneOf(Direction.class);
        for (Direction d : bp.shape.getPrimaryExits()) newPrimary.add(rotateCW(d));

        RoadShape newShape = findShape(newExits, newPrimary);
        if (newShape == null) return bp;

        return new Blueprint(
                bp.kind, bp.size, bp.product, bp.direction,
                newShape, buildShapeName(newShape), bp.cost);
    }

    private RoadShape findShape(Set<Direction> exits, Set<Direction> primary) {
        for (RoadShape s : RoadShape.values()) {
            if (s.getExits().equals(exits) && s.getPrimaryExits().equals(primary)) {
                return s;
            }
        }
        return null;
    }

    private Direction rotateCW(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST  -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST  -> Direction.NORTH;
        };
    }

    private String buildShapeName(RoadShape shape) {
        return switch (shape) {
            case STRAIGHT_E -> "Дорога →";
            case STRAIGHT_W -> "Дорога ←";
            case STRAIGHT_N -> "Дорога ↑";
            case STRAIGHT_S -> "Дорога ↓";

            case CORNER_NE_E -> "Угол N+E → E";
            case CORNER_NE_N -> "Угол N+E → N";
            case CORNER_ES_S -> "Угол E+S → S";
            case CORNER_ES_E -> "Угол E+S → E";
            case CORNER_SW_W -> "Угол S+W → W";
            case CORNER_SW_S -> "Угол S+W → S";
            case CORNER_WN_N -> "Угол W+N → N";
            case CORNER_WN_W -> "Угол W+N → W";

            case TEE_ESW_S  -> "T (E+S+W) ↓";
            case TEE_ESW_EW -> "T (E+S+W) ↔";
            case TEE_NES_N  -> "T (N+E+S) ↑";
            case TEE_NES_EW -> "T (N+E+S) ↔";
            case TEE_SWN_S  -> "T (S+W+N) ↓";
            case TEE_SWN_EW -> "T (S+W+N) ↔";
            case TEE_WNE_W  -> "T (W+N+E) ←";
            case TEE_WNE_NS -> "T (W+N+E) ↕";

            case CROSS -> "Перекрёсток +";
        };
    }

    // ==================== CALLBACKS ДЛЯ UI ====================

    private void onBlueprintSelected(Blueprint bp) {
        if (bp == null) return;

        if (bp.kind == Blueprint.Kind.LAND) {
            String err = game.tryBuild(bp, new Point(0, 0));
            if (err != null) {
                hud.setStatus("❌ " + err, new Color(255, 120, 120));
            } else {
                hud.setStatus("🗺 Карта расширена (+" + game.getLandPurchases() + ")",
                        new Color(120, 240, 120));
            }
            return;
        }

        this.selectedBlueprint = bp;
        this.mode = BuildMode.PLACE;

        String hint = "ЛКМ — поставить, зажать ЛКМ — вести линию, Esc — отмена";
        if (bp.kind == Blueprint.Kind.ROAD
                && bp.shape != null
                && bp.shape != RoadShape.CROSS
                && !isStraightShape(bp.shape)) {
            hint += ", R — повернуть";
        }
        hud.setStatus("Размещение: " + bp.displayName, new Color(200, 240, 200));
        hud.setHint(hint);
        repaint();
    }

    private void cancelMode() {
        this.mode = BuildMode.NONE;
        this.selectedBlueprint = null;
        this.hoverCell = null;
        this.hoverBuilding = null;
        hud.setStatus("Готов", new Color(200, 240, 200));
        hud.setHint("ЛКМ — строить, зажать ЛКМ — линия, D — снос, R — поворот");
        repaint();
    }

    private void enableDemolish() {
        this.mode = BuildMode.DEMOLISH;
        this.selectedBlueprint = null;
        hud.setStatus("Режим сноса: кликните по зданию", new Color(240, 180, 120));
        hud.setHint("ЛКМ — снести, зажать ЛКМ — сносить линией, Esc — отмена");
        repaint();
    }

    private void toggleDelivery() {
        if (game.isDeliveryPaused()) {
            game.resumeAllCars();
            buildPanel.setDeliveryButtonState(false);
            hud.setStatus("🚚 Доставка возобновлена", new Color(120, 240, 120));
        } else {
            game.recallAllCars();
            buildPanel.setDeliveryButtonState(true);
            hud.setStatus("🚚 Все машины на фабриках", new Color(240, 200, 120));
        }
        repaint();
    }

    // ==================== ОТРИСОВКА ====================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Renderer.drawBackground(g, getWidth(), getHeight());
        Renderer.drawGrid(g, game, camera, getWidth(), getHeight());
        Renderer.drawMapBounds(g, game, camera);

        Renderer.drawRoads(g, game, camera);

        Set<Building> drawn = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        for (var b : game.getMap().getAll().values()) {
            if (drawn.contains(b) || b instanceof Road) continue;
            drawn.add(b);
            if (b instanceof Factory f)
                Renderer.drawFactory(g, f, camera);
            else if (b instanceof Shop s)
                Renderer.drawShop(g, s, camera);
            else if (b instanceof House h)
                Renderer.drawHouse(g, h, camera);
            else if (b instanceof Processor p)          // ← NEW
                Renderer.drawProcessor(g, p, camera);  // ← NEW
        }

        for (var c : game.getCars()) Renderer.drawCar(g, c, camera);
        for (var p : game.getPeople()) Renderer.drawPerson(g, p, camera);

        if (mode == BuildMode.PLACE && selectedBlueprint != null && hoverCell != null) {
            Renderer.drawGhost(g, selectedBlueprint, hoverCell, camera, hoverValid);

            if (selectedBlueprint.kind == Blueprint.Kind.ROAD
                    && selectedBlueprint.shape != null) {
                Renderer.drawGhostRoad(g, selectedBlueprint.shape,
                        hoverCell, camera, hoverValid);
            }
        }

        if (mode == BuildMode.DEMOLISH && hoverBuilding != null) {
            Renderer.drawDemolishHighlight(g, hoverBuilding, camera);
        }

        if (mode == BuildMode.NONE && hoverCell != null) {
            Building b = game.getMap().get(hoverCell);
            if (b instanceof Road r && !r.isStraight() && !r.isCross()) {
                Renderer.drawRotateHint(g, r, camera);
            }
        }

        routeOverlay.draw(g, game, camera, getWidth(), getHeight());

        Renderer.drawHelp(g, getWidth(), getHeight(), hoverCell);
        NotificationRenderer.draw(g, game.getNotifications(), getWidth());
    }
}