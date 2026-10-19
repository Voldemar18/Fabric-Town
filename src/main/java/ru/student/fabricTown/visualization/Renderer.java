package ru.student.fabricTown.visualization;

import ru.student.fabricTown.agents.Person;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.agents.UniversalCar;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.RoadRegistry;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Полный рендерер игры.
 * Рисует:
 *   - сетку карты и границы купленной территории,
 *   - дороги (линии по exits + стрелки по primaryExits),
 *   - здания: фабрика, магазин, дом,
 *   - машины (с числом груза) и людей,
 *   - призрак размещения (рамка + лучи направлений),
 *   - подсветку сноса,
 *   - подсветку «крутится клавишей R»,
 *   - координаты клетки под курсором в правом нижнем углу.
 */
public final class Renderer {

    private Renderer() {}

    // ==================== ЦВЕТА ====================
    private static final Color BG_COLOR         = new Color(25, 25, 30);
    private static final Color GRID_COLOR       = new Color(40, 40, 50);
    private static final Color GRID_MAJOR_COLOR = new Color(55, 55, 70);
    private static final Color BORDER_COLOR     = new Color(90, 90, 130);

    private static final Color ROAD_FILL        = new Color(70, 70, 80);
    private static final Color ROAD_EDGE        = new Color(120, 120, 130);
    private static final Color ROAD_ARROW       = new Color(230, 230, 90);
    private static final Color ROAD_BUSY        = new Color(120, 90, 60);

    private static final Color FACTORY_FILL     = new Color(180, 90, 70);
    private static final Color FACTORY_EDGE     = new Color(240, 200, 120);

    private static final Color SHOP_FILL        = new Color(70, 130, 180);
    private static final Color SHOP_EDGE        = new Color(180, 220, 255);
    private static final Color SHOP_WARN        = new Color(255, 80, 80);

    private static final Color HOUSE_FILL       = new Color(160, 160, 90);
    private static final Color HOUSE_EDGE       = new Color(230, 230, 170);

    private static final Color CAR_FILL         = new Color(255, 200, 50);
    private static final Color CAR_EDGE         = new Color(30, 30, 30);
    private static final Color PERSON_FILL      = new Color(255, 100, 180);

    private static final Color GHOST_OK_FILL    = new Color(80, 220, 100, 120);
    private static final Color GHOST_OK_EDGE    = new Color(80, 220, 100);
    private static final Color GHOST_BAD_FILL   = new Color(240, 80, 80, 120);
    private static final Color GHOST_BAD_EDGE   = new Color(240, 80, 80);

    private static final Color DEMOLISH_FILL    = new Color(255, 60, 60, 100);
    private static final Color DEMOLISH_EDGE    = new Color(255, 60, 60);

    private static final Color ROTATE_HINT_EDGE = new Color(120, 200, 255, 200);

    // ==================== ШРИФТЫ ====================
    private static final Font FONT_TINY  = new Font("SansSerif", Font.PLAIN, 9);
    private static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font FONT_LABEL = new Font("SansSerif", Font.BOLD, 11);
    private static final Font FONT_BIG   = new Font("SansSerif", Font.BOLD, 16);

    // ==================== ФОН И СЕТКА ====================

    public static void drawBackground(Graphics2D g, int w, int h) {
        g.setColor(BG_COLOR);
        g.fillRect(0, 0, w, h);
    }

    public static void drawGrid(Graphics2D g, Game game, Camera cam, int w, int h) {
        int cell = (int) cam.zoom;
        if (cell < 4) return;

        int startX = (int) Math.floor(cam.offsetX);
        int startY = (int) Math.floor(cam.offsetY);
        int endX = (int) Math.ceil(cam.offsetX + w / cam.zoom);
        int endY = (int) Math.ceil(cam.offsetY + h / cam.zoom);

        g.setColor(GRID_COLOR);
        g.setStroke(new BasicStroke(1f));
        for (int x = startX; x <= endX; x++) {
            int sx = cam.worldToScreenX(x);
            g.drawLine(sx, 0, sx, h);
        }
        for (int y = startY; y <= endY; y++) {
            int sy = cam.worldToScreenY(y);
            g.drawLine(0, sy, w, sy);
        }

        g.setColor(GRID_MAJOR_COLOR);
        for (int x = startX; x <= endX; x++) {
            if (x % 5 == 0) {
                int sx = cam.worldToScreenX(x);
                g.drawLine(sx, 0, sx, h);
            }
        }
        for (int y = startY; y <= endY; y++) {
            if (y % 5 == 0) {
                int sy = cam.worldToScreenY(y);
                g.drawLine(0, sy, w, sy);
            }
        }
    }

    public static void drawMapBounds(Graphics2D g, Game game, Camera cam) {
        int x1 = cam.worldToScreenX(game.getMapMinX());
        int y1 = cam.worldToScreenY(game.getMapMinY());
        int x2 = cam.worldToScreenX(game.getMapMaxX() + 1);
        int y2 = cam.worldToScreenY(game.getMapMaxY() + 1);

        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{8f, 6f}, 0f));
        g.drawRect(x1, y1, x2 - x1, y2 - y1);
        g.setStroke(new BasicStroke(1f));
    }

    // ==================== ДОРОГА ====================

    /**
     * Дорога: линии по всем выходам, стрелки — только по главным.
     * Так угол — это уголок со стрелкой, а T — с одной стрелкой на главной стороне.
     */
    public static void drawRoad(Graphics2D g, Road r, Camera cam) {
        Point o = r.getOrigin();
        int x = cam.worldToScreenX(o.x);
        int y = cam.worldToScreenY(o.y);
        int s = (int) cam.zoom;

        boolean busy = RoadRegistry.isBlocked(o);
        g.setColor(busy ? ROAD_BUSY : ROAD_FILL);
        g.fillRect(x, y, s, s);

        g.setColor(ROAD_EDGE);
        g.drawRect(x, y, s, s);

        int cx = x + s / 2;
        int cy = y + s / 2;
        int half = s / 2;

        // Линии по всем выходам
        g.setColor(ROAD_ARROW);
        g.setStroke(new BasicStroke(Math.max(2f, s / 12f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Direction d : r.getExits()) {
            g.drawLine(cx, cy, cx + d.dx * half, cy + d.dy * half);
        }

        // Стрелки по главным направлениям
        int headLen = Math.max(6, s / 4);
        for (Direction d : r.getPrimaryExits()) {
            int ex = cx + d.dx * half;
            int ey = cy + d.dy * half;
            double ang = Math.atan2(d.dy, d.dx);
            int hx1 = (int) (ex - headLen * Math.cos(ang - 0.5));
            int hy1 = (int) (ey - headLen * Math.sin(ang - 0.5));
            int hx2 = (int) (ex - headLen * Math.cos(ang + 0.5));
            int hy2 = (int) (ey - headLen * Math.sin(ang + 0.5));
            g.drawLine(ex, ey, hx1, hy1);
            g.drawLine(ex, ey, hx2, hy2);
        }

        g.setStroke(new BasicStroke(1f));
    }

    public static void drawRoads(Graphics2D g, Game game, Camera cam) {
        for (Road r : game.getMap().getRoads().values()) {
            drawRoad(g, r, cam);
        }
    }

    // ==================== ФАБРИКА ====================

    public static void drawFactory(Graphics2D g, Factory f, Camera cam) {
        Point o = f.getOrigin();
        int x = cam.worldToScreenX(o.x);
        int y = cam.worldToScreenY(o.y);
        int w = (int) (f.getSize().w * cam.zoom);
        int h = (int) (f.getSize().h * cam.zoom);

        g.setColor(FACTORY_FILL);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setColor(FACTORY_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));

        if (cam.zoom < 14) return;

        g.setColor(Color.WHITE);
        g.setFont(FONT_LABEL);
        g.drawString(f.getProduct().title, x + 6, y + 16);

        int barW = w - 12;
        int barH = 6;
        int barX = x + 6;
        int barY = y + h - 10;
        double ratio = f.getStorageCount() / (double) f.getMaxStorage();

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(ratio > 0.9 ? new Color(255, 100, 100)
                : ratio > 0.5 ? new Color(255, 200, 80)
                : new Color(120, 220, 120));
        g.fillRect(barX, barY, (int) (barW * ratio), barH);
        g.setColor(new Color(220, 220, 220));
        g.drawRect(barX, barY, barW, barH);

        g.setFont(FONT_TINY);
        g.setColor(Color.WHITE);
        g.drawString(f.getStorageCount() + "/" + f.getMaxStorage(), barX, barY - 2);
    }

    // ==================== МАГАЗИН ====================

    public static void drawShop(Graphics2D g, Shop s, Camera cam) {
        Point o = s.getOrigin();
        int x = cam.worldToScreenX(o.x);
        int y = cam.worldToScreenY(o.y);
        int w = (int) (s.getSize().w * cam.zoom);
        int h = (int) (s.getSize().h * cam.zoom);

        g.setColor(SHOP_FILL);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setColor(SHOP_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));

        if (s.isEmpty() && cam.zoom >= 12) {
            g.setColor(SHOP_WARN);
            g.setFont(FONT_BIG);
            g.drawString("!", x + w / 2 - 4, y - 4);
        }

        if (cam.zoom < 14) return;

        g.setColor(Color.WHITE);
        g.setFont(FONT_LABEL);
        g.drawString("Магазин", x + 6, y + 16);

        int barW = w - 12;
        int barH = 6;
        int barX = x + 6;
        int barY = y + h - 10;
        double ratio = s.totalStored() / (double) s.getCapacity();

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(ratio > 0.9 ? new Color(120, 220, 120)
                : ratio > 0.5 ? new Color(255, 200, 80)
                : new Color(255, 100, 100));
        g.fillRect(barX, barY, (int) (barW * ratio), barH);
        g.setColor(new Color(220, 220, 220));
        g.drawRect(barX, barY, barW, barH);

        if (cam.zoom >= 24) {
            g.setFont(FONT_TINY);
            int yy = y + 30;
            int count = 0;
            for (Map.Entry<Product, Integer> e : s.getStorage().entrySet()) {
                if (e.getValue() <= 0) continue;
                if (count >= 3) break;
                g.setColor(Color.WHITE);
                g.drawString(e.getKey().title + ": " + e.getValue(), x + 6, yy);
                yy += 11;
                count++;
            }
        }
    }

    // ==================== ДОМ ====================

    public static void drawHouse(Graphics2D g, House h, Camera cam) {
        Point o = h.getOrigin();
        int x = cam.worldToScreenX(o.x);
        int y = cam.worldToScreenY(o.y);
        int w = (int) (h.getSize().w * cam.zoom);
        int hh = (int) (h.getSize().h * cam.zoom);

        g.setColor(HOUSE_FILL);
        g.fillRoundRect(x + 2, y + 2, w - 4, hh - 4, 8, 8);
        g.setColor(HOUSE_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x + 2, y + 2, w - 4, hh - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));

        if (cam.zoom < 14) return;

        g.setColor(Color.WHITE);
        g.setFont(FONT_LABEL);
        g.drawString("Дом", x + 6, y + 16);

        g.setFont(FONT_TINY);
        g.drawString("Жителей: " + h.getResidents(), x + 6, y + 28);
    }

    // ==================== МАШИНА ====================

    public static void drawCar(Graphics2D g, UniversalCar c, Camera cam) {
        int x = cam.worldToScreenX(c.getX());
        int y = cam.worldToScreenY(c.getY());
        int size = Math.max(4, (int) (cam.zoom * 0.5));

        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(x - size / 2 + 1, y - size / 2 + 2, size, size);

        g.setColor(CAR_FILL);
        g.fillOval(x - size / 2, y - size / 2, size, size);
        g.setColor(CAR_EDGE);
        g.drawOval(x - size / 2, y - size / 2, size, size);

        int count = c.getCargoCount();
        if (count > 0 && cam.zoom >= 16) {
            g.setColor(Color.WHITE);
            g.setFont(FONT_TINY);
            g.drawString(String.valueOf(count), x + size / 2 + 2, y);
        }

        if (count > 0 && cam.zoom >= 24) {
            Product first = c.getCarrying();
            if (first != null) {
                g.setColor(new Color(220, 220, 220, 180));
                g.drawString(first.title, x - 20, y - size);
            }
        }
    }

    // ==================== ЧЕЛОВЕК ====================

    public static void drawPerson(Graphics2D g, Person p, Camera cam) {
        int x = cam.worldToScreenX(p.getX());
        int y = cam.worldToScreenY(p.getY());
        int size = Math.max(3, (int) (cam.zoom * 0.35));

        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - size / 2 + 1, y - size / 2 + 2, size, size);

        g.setColor(PERSON_FILL);
        g.fillOval(x - size / 2, y - size / 2, size, size);
    }

    // ==================== ПРИЗРАК РАЗМЕЩЕНИЯ ====================

    public static void drawGhost(Graphics2D g, Blueprint bp, Point origin,
                                 Camera cam, boolean valid) {
        if (bp == null || origin == null) return;

        int cell = (int) cam.zoom;
        int w = bp.size.w * cell;
        int h = bp.size.h * cell;
        int x = cam.worldToScreenX(origin.x);
        int y = cam.worldToScreenY(origin.y);

        Color fill = valid ? GHOST_OK_FILL : GHOST_BAD_FILL;
        Color edge = valid ? GHOST_OK_EDGE : GHOST_BAD_EDGE;

        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(edge);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, y, w, h);
        g.setStroke(new BasicStroke(1f));

        if (cell >= 16) {
            g.setFont(FONT_SMALL);
            g.setColor(Color.WHITE);
            String label = switch (bp.kind) {
                case ROAD -> "Дорога";
                case FACTORY -> "Фабрика";
                case PROCESSOR -> "Переработчик";
                case SHOP -> "Магазин";
                case HOUSE -> "Дом";
                case LAND -> "Земля";
            };
            g.drawString(label, x + 4, y + 14);
        }
    }

    /**
     * Дорисовка лучей поверх призрака дороги: линии по всем exits,
     * стрелки по primaryExits.
     */
    public static void drawGhostRoad(Graphics2D g, RoadShape shape,
                                     Point origin, Camera cam, boolean valid) {
        if (shape == null || origin == null) return;

        int s = (int) cam.zoom;
        int x = cam.worldToScreenX(origin.x);
        int y = cam.worldToScreenY(origin.y);
        int cx = x + s / 2;
        int cy = y + s / 2;
        int half = s / 2;

        Color arrow = valid
                ? new Color(230, 255, 230, 220)
                : new Color(255, 200, 200, 220);

        g.setColor(arrow);
        g.setStroke(new BasicStroke(Math.max(2f, s / 12f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Direction d : shape.getExits()) {
            g.drawLine(cx, cy, cx + d.dx * half, cy + d.dy * half);
        }

        int headLen = Math.max(6, s / 4);
        for (Direction d : shape.getPrimaryExits()) {
            int ex = cx + d.dx * half;
            int ey = cy + d.dy * half;
            double ang = Math.atan2(d.dy, d.dx);
            int hx1 = (int) (ex - headLen * Math.cos(ang - 0.5));
            int hy1 = (int) (ey - headLen * Math.sin(ang - 0.5));
            int hx2 = (int) (ex - headLen * Math.cos(ang + 0.5));
            int hy2 = (int) (ey - headLen * Math.sin(ang + 0.5));
            g.drawLine(ex, ey, hx1, hy1);
            g.drawLine(ex, ey, hx2, hy2);
        }
        g.setStroke(new BasicStroke(1f));
    }

    // ==================== ПОДСВЕТКА СНОСА ====================

    public static void drawDemolishHighlight(Graphics2D g, Building b, Camera cam) {
        if (b == null) return;
        int x = cam.worldToScreenX(b.getOrigin().x);
        int y = cam.worldToScreenY(b.getOrigin().y);
        int w = (int) (b.getSize().w * cam.zoom);
        int h = (int) (b.getSize().h * cam.zoom);

        g.setColor(DEMOLISH_FILL);
        g.fillRect(x, y, w, h);
        g.setColor(DEMOLISH_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, y, w, h);
        g.setStroke(new BasicStroke(1f));

        g.setStroke(new BasicStroke(3f));
        g.drawLine(x + 6, y + 6, x + w - 6, y + h - 6);
        g.drawLine(x + w - 6, y + 6, x + 6, y + h - 6);
        g.setStroke(new BasicStroke(1f));
    }

    // ==================== ПОДСКАЗКА «КРУТИТСЯ» ====================

    public static void drawRotateHint(Graphics2D g, Road r, Camera cam) {
        if (r == null) return;
        int x = cam.worldToScreenX(r.getOrigin().x);
        int y = cam.worldToScreenY(r.getOrigin().y);
        int s = (int) cam.zoom;

        g.setColor(ROTATE_HINT_EDGE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 4f}, 0f));
        g.drawRect(x + 1, y + 1, s - 2, s - 2);
        g.setStroke(new BasicStroke(1f));

        if (cam.zoom >= 20) {
            g.setColor(new Color(220, 240, 255, 220));
            g.setFont(FONT_TINY);
            g.drawString("R", x + s / 2 - 3, y - 3);
        }
    }

    // ==================== МАРШРУТ (для ShopPanel) ====================

    public static void drawRoute(Graphics2D g, List<Point> path, Camera cam) {
        if (path == null || path.size() < 2) return;
        g.setColor(new Color(255, 220, 90, 200));
        g.setStroke(new BasicStroke(Math.max(2f, (float) cam.zoom / 12f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Point prev = path.get(0);
        for (int i = 1; i < path.size(); i++) {
            Point p = path.get(i);
            g.drawLine(cam.worldToScreenX(prev.x + 0.5),
                    cam.worldToScreenY(prev.y + 0.5),
                    cam.worldToScreenX(p.x + 0.5),
                    cam.worldToScreenY(p.y + 0.5));
            prev = p;
        }
        g.setStroke(new BasicStroke(1f));
    }

    // ==================== ЗДАНИЯ ПАКЕТОМ ====================

    public static void drawBuildings(Graphics2D g, Game game, Camera cam) {
        Set<Building> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Building b : game.getMap().getAll().values()) {
            if (b instanceof Road) continue;
            if (!drawn.add(b)) continue;

            if (b instanceof Factory f) {
                drawFactory(g, f, cam);
            } else if (b instanceof Shop s) {
                drawShop(g, s, cam);
            } else if (b instanceof House h) {
                drawHouse(g, h, cam);
            }
        }
    }

    // ==================== HUD ====================

    /**
     * Рисует в правом нижнем углу:
     *   - координаты клетки под курсором (крупно, в рамке);
     *   - подсказки по управлению (мелко, ниже).
     */
    public static void drawHelp(Graphics2D g, int w, int h, Point hoverCell) {
        int right = w - 20;
        int bottom = h - 20;

        // ==================== КООРДИНАТЫ ====================
        if (hoverCell != null) {
            g.setFont(FONT_LABEL);
            FontMetrics fm = g.getFontMetrics();
            String coord = "(" + hoverCell.x + ", " + hoverCell.y + ")";
            int textW = fm.stringWidth(coord);
            int textH = fm.getHeight();

            int padX = 8;
            int padY = 4;
            int boxW = textW + padX * 2;
            int boxH = textH + padY * 2;
            int boxX = right - boxW;
            int boxY = bottom - boxH;

            g.setColor(new Color(20, 20, 30, 200));
            g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);

            g.setColor(new Color(255, 220, 100));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);
            g.setStroke(new BasicStroke(1f));

            g.setColor(new Color(255, 235, 130));
            g.drawString(coord, boxX + padX, boxY + padY + fm.getAscent());

            bottom = boxY - 8;
        }

        // ==================== ПОДСКАЗКИ ====================
        g.setFont(FONT_SMALL);
        g.setColor(new Color(180, 180, 180, 220));

        String[] lines = {
                "ПКМ/СКМ — тащить карту",
                "Колесо — зум",
                "ЛКМ — разместить / снести",
                "Esc — отмена, D — снос"
        };

        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();

        int y = bottom - lineH * (lines.length - 1);
        for (String line : lines) {
            int lw = fm.stringWidth(line);
            g.drawString(line, right - lw, y);
            y += lineH;
        }
    }




    private static final Color PROCESSOR_FILL = new Color(150, 80, 150);
    private static final Color PROCESSOR_EDGE = new Color(230, 180, 230);

    public static void drawProcessor(Graphics2D g, Processor p, Camera cam) {
        Point o = p.getOrigin();
        int x = cam.worldToScreenX(o.x);
        int y = cam.worldToScreenY(o.y);
        int w = (int) (p.getSize().w * cam.zoom);
        int h = (int) (p.getSize().h * cam.zoom);

        g.setColor(PROCESSOR_FILL);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setColor(PROCESSOR_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 8, 8);
        g.setStroke(new BasicStroke(1f));

        if (cam.zoom < 14) return;

        g.setColor(Color.WHITE);
        g.setFont(FONT_LABEL);
        g.drawString(p.getOutputProduct().title, x + 6, y + 16);

        // Полоска прогресса крафта / склада готового
        int barW = w - 12, barH = 6;
        int barX = x + 6, barY = y + h - 10;
        double ratio = p.getOutputCount() / (double) p.getOutputCapacity();
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(220, 160, 220));
        g.fillRect(barX, barY, (int) (barW * ratio), barH);
        g.setColor(new Color(220, 220, 220));
        g.drawRect(barX, barY, barW, barH);

        g.setFont(FONT_TINY);
        g.setColor(Color.WHITE);
        g.drawString(p.getOutputCount() + "/" + p.getOutputCapacity(), barX, barY - 2);

        // Ингредиенты (если хватает места)
        if (cam.zoom >= 24) {
            int yy = y + 30;
            for (var e : p.getInputStorage().entrySet()) {
                if (e.getValue() <= 0) continue;
                g.drawString(e.getKey().title + ": " + e.getValue(), x + 6, yy);
                yy += 11;
            }
        }
    }
}