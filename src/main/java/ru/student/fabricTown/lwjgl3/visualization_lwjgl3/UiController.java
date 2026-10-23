package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import ru.student.fabricTown.buildings.Building;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Processor;
import ru.student.fabricTown.buildings.Road;
import ru.student.fabricTown.buildings.RoadShape;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.PlacementRules;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;
import ru.student.fabricTown.profiler.Profiler;

import java.util.EnumSet;
import java.util.Set;

public class UiController extends InputAdapter {
    private final Game game;
    private final CameraController cam;
    private InfoUi infoUi;
    private BuildUi buildUi;
    private HudRenderer hud;
    private RouteOverlay overlay;

    public BuildMode mode = BuildMode.NONE;
    public Blueprint selectedBlueprint = null;
    public Point hoverCell = null;
    public Building hoverBuilding = null;
    public boolean hoverValid = false;

    private boolean dKeyHeld = false;
    private boolean painting = false;
    private Point lastPaintedCell = null;

    private long lastClickTime = 0;
    private int lastClickX, lastClickY;

    public boolean profilerOverlayVisible = false;

    public UiController(Game game, CameraController cam, RouteOverlay overlay) {
        this.game = game;
        this.cam = cam;
        this.overlay = overlay;
    }

    public void setInfoUi(InfoUi i) { infoUi = i; }
    public void setBuildUi(BuildUi b) { buildUi = b; }
    public void setHud(HudRenderer h) { hud = h; }

    /**
     * Единая точка для зума колёсиком.
     * Вызывается из UiStage (см. capture-listener), когда курсор над картой.
     */
    public boolean onScrolled(float amountY) {
        if (amountY == 0f) return false;
        cam.zoomWheel(amountY);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;
        updateHover(screenX, screenY);

        long now = System.currentTimeMillis();
        boolean dbl = (now - lastClickTime < 350)
                && Math.abs(screenX - lastClickX) < 6 && Math.abs(screenY - lastClickY) < 6;
        lastClickTime = now;
        lastClickX = screenX;
        lastClickY = screenY;
        if (dbl) {
            handleDoubleClick(screenX, screenY);
            return true;
        }
        if (handleOverlayClick(screenX, screenY)) return true;

        onClick();
        boolean paintMode = mode == BuildMode.PLACE || mode == BuildMode.DEMOLISH || dKeyHeld;
        if (paintMode) {
            painting = true;
            lastPaintedCell = hoverCell;
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        painting = false;
        lastPaintedCell = null;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (cam.isDragging()) return false;
        updateHover(screenX, screenY);
        if (painting && hoverCell != null && !hoverCell.equals(lastPaintedCell)) {
            paintLine(lastPaintedCell, hoverCell);
            lastPaintedCell = hoverCell;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        updateHover(screenX, screenY);
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.ESCAPE -> {
                if (overlay.isActive()) {
                    if (overlay.getSelectedShop() != null) overlay.deselectShop();
                    else overlay.clear();
                    return true;
                }
                cancelMode();
                return true;
            }
            case Input.Keys.D -> {
                dKeyHeld = true;
                enableDemolish();
                return true;
            }
            case Input.Keys.R -> {
                rotateRoad();
                return true;
            }
            case Input.Keys.F3 -> {
                profilerOverlayVisible = !profilerOverlayVisible;
                return true;
            }
            case Input.Keys.F4 -> {
                Profiler.setEnabled(!Profiler.isEnabled());
                Profiler.reset();
                if (hud != null) {
                    hud.setStatus(Profiler.isEnabled() ? "🔬 Профайлер ВКЛ" : "🔬 Профайлер ВЫКЛ",
                            new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.D) dKeyHeld = false;
        return false;
    }

    public void update() {
        updateHover(Gdx.input.getX(), Gdx.input.getY());
    }

    private void updateHover(int screenX, int screenY) {
        hoverCell = cam.cellUnder(screenX, screenY);
        hoverBuilding = game.getMap().get(hoverCell);
        if (mode == BuildMode.PLACE && selectedBlueprint != null) {
            hoverValid = PlacementRules.check(selectedBlueprint, hoverCell, game.getMap()) == null;
        } else {
            hoverValid = false;
        }
        if (hud != null) hud.setCoord(hoverCell);
    }

    private void handleDoubleClick(int screenX, int screenY) {
        if (overlay.hitTestList(screenX, screenY, Gdx.graphics.getWidth()) >= 0) return;
        Point cell = cam.cellUnder(screenX, screenY);
        Building b = game.getMap().get(cell);
        if (b == null) {
            overlay.clear();
            if (infoUi != null) infoUi.selectBuilding(null);
            return;
        }
        if (b instanceof Factory f) {
            overlay.toggle(f, game);
            infoUi.selectBuilding(f);
        } else if (b instanceof Processor p) {
            overlay.clear();
            infoUi.selectBuilding(p);
        } else if (b instanceof Shop s) {
            overlay.clear();
            infoUi.selectBuilding(s);
        } else if (b instanceof House h) {
            overlay.clear();
            infoUi.selectBuilding(h);
        } else {
            overlay.clear();
            infoUi.selectBuilding(null);
        }
    }

    public void selectBlueprint(Blueprint bp) {
        if (bp == null) return;
        if (bp.kind == Blueprint.Kind.LAND) {
            String err = game.tryBuild(bp, new Point(0, 0));
            if (err != null) hud.setStatus("❌ " + err, new Color(1f, 120 / 255f, 120 / 255f, 1f));
            else hud.setStatus("🗺 Карта расширена (+" + game.getLandPurchases() + ")",
                    new Color(120 / 255f, 240 / 255f, 120 / 255f, 1f));
            return;
        }
        selectedBlueprint = bp;
        mode = BuildMode.PLACE;
        String hint = "ЛКМ — поставить, зажать ЛКМ — вести линию, Esc — отмена";
        if (bp.kind == Blueprint.Kind.ROAD && bp.shape != null
                && bp.shape != RoadShape.CROSS && bp.shape.getExits().size() > 1) {
            hint += ", R — повернуть";
        }
        hud.setStatus("Размещение: " + bp.displayName, new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));
        hud.setHint(hint);
        if (infoUi != null) infoUi.selectBuilding(null);
    }

    public void cancelMode() {
        mode = BuildMode.NONE;
        selectedBlueprint = null;
        if (hud != null) {
            hud.setStatus("Готов", new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));
            hud.setHint("ЛКМ — строить, зажать ЛКМ — линия, D — снос, R — поворот");
        }
    }

    public void enableDemolish() {
        mode = BuildMode.DEMOLISH;
        selectedBlueprint = null;
        hud.setStatus("Режим сноса: кликните по зданию", new Color(240 / 255f, 180 / 255f, 120 / 255f, 1f));
        hud.setHint("ЛКМ — снести, зажать ЛКМ — сносить линией, Esc — отмена");
    }

    public void toggleDelivery() {
        if (game.isDeliveryPaused()) {
            game.resumeAllCars();
            if (buildUi != null) buildUi.setDeliveryButtonState(false);
            hud.setStatus("🚚 Доставка возобновлена", new Color(120 / 255f, 240 / 255f, 120 / 255f, 1f));
        } else {
            game.recallAllCars();
            if (buildUi != null) buildUi.setDeliveryButtonState(true);
            hud.setStatus("🚚 Все машины на фабриках", new Color(240 / 255f, 200 / 255f, 120 / 255f, 1f));
        }
    }

    private void onClick() {
        if (hoverCell == null) return;
        if (dKeyHeld) {
            String err = game.trySell(hoverCell);
            hud.setStatus(err != null ? "❌ " + err : "💥 Снесено",
                    err != null ? new Color(1f, 120 / 255f, 120 / 255f, 1f)
                            : new Color(240 / 255f, 200 / 255f, 120 / 255f, 1f));
            overlay.rebuildMarkers(game);
            return;
        }
        switch (mode) {
            case PLACE -> {
                if (selectedBlueprint == null) return;
                String err = game.tryBuild(selectedBlueprint, hoverCell);
                hud.setStatus(err != null ? "❌ " + err : "✅ Построено: " + selectedBlueprint.displayName,
                        err != null ? new Color(1f, 120 / 255f, 120 / 255f, 1f)
                                : new Color(120 / 255f, 240 / 255f, 120 / 255f, 1f));
                overlay.rebuildMarkers(game);
            }
            case DEMOLISH -> {
                String err = game.trySell(hoverCell);
                hud.setStatus(err != null ? "❌ " + err : "💥 Снесено",
                        err != null ? new Color(1f, 120 / 255f, 120 / 255f, 1f)
                                : new Color(240 / 255f, 200 / 255f, 120 / 255f, 1f));
                overlay.rebuildMarkers(game);
            }
            default -> { }
        }
    }

    private void paintLine(Point from, Point to) {
        if (to == null) return;
        if (from == null) { paintCell(to); return; }
        int x0 = from.x, y0 = from.y, x1 = to.x, y1 = to.y;
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            paintCell(new Point(x0, y0));
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private void paintCell(Point c) {
        if (c == null) return;
        if (c.x < game.getMapMinX() || c.x > game.getMapMaxX()
                || c.y < game.getMapMinY() || c.y > game.getMapMaxY()) return;
        if (dKeyHeld) { game.trySell(c); return; }
        if (mode == BuildMode.PLACE && selectedBlueprint != null) game.tryBuild(selectedBlueprint, c);
        else if (mode == BuildMode.DEMOLISH) game.trySell(c);
    }

    private void rotateRoad() {
        if (mode == BuildMode.PLACE && selectedBlueprint != null
                && selectedBlueprint.kind == Blueprint.Kind.ROAD
                && selectedBlueprint.shape != null) {
            if (selectedBlueprint.shape == RoadShape.CROSS) {
                hud.setStatus("Крест крутить не нужно — он на все стороны",
                        new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
                return;
            }
            if (selectedBlueprint.shape.getExits().size() == 1) {
                hud.setStatus("Прямую дорогу крутить не нужно",
                        new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
                return;
            }
            selectedBlueprint = rotateBlueprint(selectedBlueprint);
            hud.setStatus("Поворот: " + selectedBlueprint.displayName,
                    new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));
            return;
        }
        Point cell = cam.cellUnder(Gdx.input.getX(), Gdx.input.getY());
        Building b = game.getMap().get(cell);
        if (!(b instanceof Road r)) {
            hud.setStatus("Под курсором нет дороги", new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
            return;
        }
        if (r.isStraight()) {
            hud.setStatus("Прямую дорогу крутить не нужно", new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
            return;
        }
        if (r.isCross()) {
            hud.setStatus("Крест крутить не нужно — он на все стороны",
                    new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
            return;
        }
        String before = r.getTypeName();
        game.rotateRoadAt(cell);
        hud.setStatus("Поворот: " + before + " → " + r.getTypeName() + " " + cell,
                new Color(120 / 255f, 240 / 255f, 120 / 255f, 1f));
    }

    private Blueprint rotateBlueprint(Blueprint bp) {
        EnumSet<Direction> newExits = EnumSet.noneOf(Direction.class);
        for (Direction d : bp.shape.getExits()) newExits.add(rotateCW(d));
        EnumSet<Direction> newPrimary = EnumSet.noneOf(Direction.class);
        for (Direction d : bp.shape.getPrimaryExits()) newPrimary.add(rotateCW(d));
        RoadShape shape = findShape(newExits, newPrimary);
        if (shape == null) return bp;
        return new Blueprint(bp.kind, bp.size, bp.product, bp.direction,
                shape, buildShapeName(shape), bp.cost);
    }

    private RoadShape findShape(Set<Direction> exits, Set<Direction> primary) {
        for (RoadShape s : RoadShape.values()) {
            if (s.getExits().equals(exits) && s.getPrimaryExits().equals(primary)) return s;
        }
        return null;
    }

    private Direction rotateCW(Direction d) {
        return switch (d) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
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
            case TEE_ESW_S -> "T (E+S+W) ↓";
            case TEE_ESW_EW -> "T (E+S+W) ↔";
            case TEE_NES_N -> "T (N+E+S) ↑";
            case TEE_NES_EW -> "T (N+E+S) ↔";
            case TEE_SWN_S -> "T (S+W+N) ↓";
            case TEE_SWN_EW -> "T (S+W+N) ↔";
            case TEE_WNE_W -> "T (W+N+E) ←";
            case TEE_WNE_NS -> "T (W+N+E) ↕";
            case CROSS -> "Перекрёсток +";
        };
    }

    private boolean handleOverlayClick(int sx, int sy) {
        if (!overlay.isActive()) return false;
        int idx = overlay.hitTestList(sx, sy, Gdx.graphics.getWidth());
        if (idx >= 0) {
            Shop s = overlay.getMarkers().get(idx).shop;
            if (s == overlay.getSelectedShop()) overlay.deselectShop();
            else overlay.selectShop(s);
            return true;
        }
        RouteOverlay.ShopMarker m = overlay.hitTest(sx, sy);
        if (m != null) {
            if (m.shop == overlay.getSelectedShop()) overlay.deselectShop();
            else overlay.selectShop(m.shop);
            return true;
        }
        overlay.clear();
        return false;
    }
}