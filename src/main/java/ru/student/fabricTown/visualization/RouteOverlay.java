package ru.student.fabricTown.visualization;

import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.map.Point;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Оверлей для визуализации связей фабрики.
 *
 * Сценарий:
 *   1) Двойной клик по фабрике → активируется режим highlights.
 *      Рисуются цветные маркеры над всеми привязанными магазинами
 *      и список магазинов в правом верхнем углу.
 *   2) Клик по магазину в списке → выбирается маршрут:
 *      все блоки дорог вдоль пути "туда" подсвечиваются одним цветом,
 *      вдоль пути "обратно" — другим. Рисуются стрелки направления.
 *   3) Esc → снимает всю подсветку и список.
 *
 * Оверлей хранит ТОЛЬКО визуальное состояние (какая фабрика выбрана,
 * какой магазин выбран). Бизнес-логика — в Game/Dispatcher.
 */
public class RouteOverlay {

    /** Активная фабрика (чей список магазинов показываем). */
    private Factory activeFactory;

    /** Магазин, для которого показываем маршрут. */
    private Shop selectedShop;

    /** Список магазинов, привязанных к активной фабрике (с координатами маркеров). */
    private final List<ShopMarker> markers = new ArrayList<>();

    /** Внутренняя структура: магазин + экранная позиция его маркера. */
    public static class ShopMarker {
        public final Shop shop;
        public int screenX, screenY;   // обновляется каждый кадр при отрисовке
        public int width, height;      // размер хитбокса

        public ShopMarker(Shop shop) {
            this.shop = shop;
        }

        public boolean contains(int mx, int my) {
            return mx >= screenX && mx <= screenX + width
                    && my >= screenY && my <= screenY + height;
        }
    }

    // ==================== СОСТОЯНИЕ ====================

    public boolean isActive()      { return activeFactory != null; }
    public Factory getActiveFactory() { return activeFactory; }
    public Shop    getSelectedShop()  { return selectedShop; }

    public List<ShopMarker> getMarkers() { return markers; }

    // ==================== УПРАВЛЕНИЕ ====================

    /** Активировать режим для фабрики. Если та же — сбросить. */
    public void toggle(Factory factory, Game game) {
        if (factory == null) {
            clear();
            return;
        }
        if (factory == activeFactory) {
            clear();
            return;
        }
        this.activeFactory = factory;
        this.selectedShop = null;
        rebuildMarkers(game);
    }

    /** Снять всю подсветку. */
    public void clear() {
        activeFactory = null;
        selectedShop = null;
        markers.clear();
    }

    /** Выбрать магазин для показа маршрута. */
    public void selectShop(Shop shop) {
        this.selectedShop = shop;
    }

    /** Снять выбор магазина (но оставить список). */
    public void deselectShop() {
        this.selectedShop = null;
    }

    /** Пересобрать список магазинов, привязанных к активной фабрике. */
    public void rebuildMarkers(Game game) {
        markers.clear();
        if (activeFactory == null) return;

        List<Shop> bound = game.getDispatcher().getShopsFor(activeFactory);
        for (Shop s : bound) {
            markers.add(new ShopMarker(s));
        }
        // Если выбранный магазин отвалился — сбросим выбор
        if (selectedShop != null && !bound.contains(selectedShop)) {
            selectedShop = null;
        }
    }

    /** Найти маркер под мышью. */
    public ShopMarker hitTest(int mx, int my) {
        for (ShopMarker m : markers) {
            if (m.contains(mx, my)) return m;
        }
        return null;
    }

    // ==================== ОТРИСОВКА ====================

    /** Цвет маркера магазина. */
    private static final Color MARKER_FILL      = new Color(80, 180, 255, 220);
    private static final Color MARKER_EDGE      = new Color(220, 240, 255);
    private static final Color MARKER_SELECTED  = new Color(255, 210, 90, 240);
    private static final Color MARKER_SELECTED_EDGE = new Color(255, 240, 180);

    /** Цвета маршрута. */
    private static final Color ROUTE_TO_SHOP    = new Color(90, 220, 120, 140);
    private static final Color ROUTE_TO_FACTORY = new Color(255, 140, 90, 140);
    private static final Color ROUTE_ARROW_TO   = new Color(120, 255, 140);
    private static final Color ROUTE_ARROW_BACK = new Color(255, 180, 110);

    /** Подсветка фабрики. */
    private static final Color FACTORY_HALO     = new Color(255, 220, 100, 180);

    /**
     * Главный метод отрисовки. Вызывается из GamePanel.paintComponent()
     * ПОСЛЕ отрисовки карты, но ДО уведомлений.
     *
     * @param g       Graphics2D
     * @param game    игра
     * @param camera  камера
     * @param w       ширина экрана
     * @param h       высота экрана
     */
    public void draw(Graphics2D g, Game game, Camera camera, int w, int h) {
        if (activeFactory == null) return;

        // 1. Подсветка самой фабрики
        drawFactoryHalo(g, camera);

        // 2. Подсветка блоков маршрута (если выбран магазин)
        if (selectedShop != null) {
            drawRoute(g, game, camera);
        }

        // 3. Маркеры магазинов (рисуем последними, чтобы были поверх)
        drawMarkers(g, camera);

        // 4. Список магазинов в правом верхнем углу
        drawShopList(g, w);
    }

    // ---------------- Подсветка фабрики ----------------

    private void drawFactoryHalo(Graphics2D g, Camera camera) {
        Point o = activeFactory.getOrigin();
        int x = camera.worldToScreenX(o.x);
        int y = camera.worldToScreenY(o.y);
        int w = (int) (activeFactory.getSize().w * camera.zoom);
        int h = (int) (activeFactory.getSize().h * camera.zoom);

        // Пульсирующая рамка
        long t = System.currentTimeMillis();
        float pulse = 0.6f + 0.4f * (float) Math.sin(t / 250.0);

        g.setColor(new Color(FACTORY_HALO.getRed(), FACTORY_HALO.getGreen(),
                FACTORY_HALO.getBlue(), (int)(180 * pulse)));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f));
        g.drawRect(x - 2, y - 2, w + 4, h + 4);
        g.setStroke(new BasicStroke(1f));
    }

    // ---------------- Подсветка маршрута ----------------

    private void drawRoute(Graphics2D g, Game game, Camera camera) {
        Factory f = activeFactory;
        Shop s = selectedShop;

        // Пересчитываем АКТУАЛЬНЫЕ пути прямо сейчас.
        Point factoryExit = game.findAdjacentRoad(f);
        Point shopExit = game.findAdjacentRoad(s);

        if (factoryExit == null || shopExit == null) {
            // Рисуем сообщение "нет пути"
            drawNoRouteMessage(g, camera, f, s);
            return;
        }

        List<Point> toShop = game.getPathFinder().findPath(factoryExit, shopExit);
        List<Point> toFactory = game.getPathFinder().findPath(shopExit, factoryExit);

        if (toShop == null || toFactory == null) {
            drawNoRouteMessage(g, camera, f, s);
            return;
        }

        drawPath(g, camera, toShop, ROUTE_TO_SHOP);
        drawPath(g, camera, toFactory, ROUTE_TO_FACTORY);

        drawArrows(g, camera, toShop, ROUTE_ARROW_TO);
        drawArrows(g, camera, toFactory, ROUTE_ARROW_BACK);
    }
    private void drawNoRouteMessage(Graphics2D g, Camera camera, Factory f, Shop s) {
        // Красный крест над фабрикой или над магазином
        Point fOrigin = f.getOrigin();
        int fx = camera.worldToScreenX(fOrigin.x + f.getSize().w / 2.0);
        int fy = camera.worldToScreenY(fOrigin.y);

        g.setColor(new Color(255, 80, 80));
        g.setStroke(new BasicStroke(4f));
        int r = 12;
        g.drawLine(fx - r, fy - r, fx + r, fy + r);
        g.drawLine(fx + r, fy - r, fx - r, fy + r);
        g.setStroke(new BasicStroke(1f));

        // Текст под крестом
        String text = "Нет пути!";
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.setColor(new Color(20, 20, 20, 220));
        g.fillRoundRect(fx - tw / 2 - 6, fy + r + 4, tw + 12, 22, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, fx - tw / 2, fy + r + 20);
    }
    /** Подсветить блоки дороги вдоль пути. */
    private void drawPath(Graphics2D g, Camera camera, List<Point> path, Color color) {
        int cell = (int) camera.zoom;
        int pad = Math.max(2, cell / 8);

        g.setColor(color);
        for (Point p : path) {
            int x = camera.worldToScreenX(p.x);
            int y = camera.worldToScreenY(p.y);
            g.fillRoundRect(x + pad, y + pad,
                    cell - pad * 2, cell - pad * 2, 6, 6);
        }
    }

    /** Нарисовать стрелки направления вдоль пути. */
    private void drawArrows(Graphics2D g, Camera camera, List<Point> path, Color color) {
        if (path.size() < 2) return;
        int cell = (int) camera.zoom;
        if (cell < 12) return;   // слишком мелко — стрелки не читаются

        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Рисуем стрелку в середине каждого сегмента
        for (int i = 0; i < path.size() - 1; i++) {
            Point a = path.get(i);
            Point b = path.get(i + 1);

            double sx = camera.worldToScreenX(a.x + 0.5);
            double sy = camera.worldToScreenY(a.y + 0.5);
            double ex = camera.worldToScreenX(b.x + 0.5);
            double ey = camera.worldToScreenY(b.y + 0.5);

            double mx = (sx + ex) / 2.0;
            double my = (sy + ey) / 2.0;
            double ang = Math.atan2(ey - sy, ex - sx);

            int headLen = Math.max(5, cell / 5);
            int hx1 = (int) (mx - headLen * Math.cos(ang - 0.5));
            int hy1 = (int) (my - headLen * Math.sin(ang - 0.5));
            int hx2 = (int) (mx - headLen * Math.cos(ang + 0.5));
            int hy2 = (int) (my - headLen * Math.sin(ang + 0.5));

            g.drawLine((int) mx, (int) my, hx1, hy1);
            g.drawLine((int) mx, (int) my, hx2, hy2);
        }
        g.setStroke(new BasicStroke(1f));
    }

    // ---------------- Маркеры магазинов ----------------

    private void drawMarkers(Graphics2D g, Camera camera) {
        int cell = (int) camera.zoom;
        int size = Math.max(18, Math.min(28, cell / 2));

        Font font = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(font);

        for (int i = 0; i < markers.size(); i++) {
            ShopMarker m = markers.get(i);
            Shop s = m.shop;

            // Позиция маркера — над магазином
            int cx = camera.worldToScreenX(s.getOrigin().x)
                    + (int)(s.getSize().w * camera.zoom / 2);
            int cy = camera.worldToScreenY(s.getOrigin().y) - size - 4;

            m.screenX = cx - size / 2;
            m.screenY = cy - size / 2;
            m.width   = size;
            m.height  = size;

            boolean selected = (s == selectedShop);

            // Кружок
            g.setColor(selected ? MARKER_SELECTED : MARKER_FILL);
            g.fillOval(m.screenX, m.screenY, size, size);

            // Рамка
            g.setColor(selected ? MARKER_SELECTED_EDGE : MARKER_EDGE);
            g.setStroke(new BasicStroke(selected ? 3f : 2f));
            g.drawOval(m.screenX, m.screenY, size, size);
            g.setStroke(new BasicStroke(1f));

            // Номер магазина
            String num = String.valueOf(i + 1);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(num);
            int th = fm.getAscent();
            g.setColor(Color.BLACK);
            g.drawString(num,
                    m.screenX + (size - tw) / 2,
                    m.screenY + (size + th) / 2 - 1);

            // Линия от маркера до магазина (визуальная привязка)
            g.setColor(new Color(255, 255, 255, 80));
            g.drawLine(cx, m.screenY + size,
                    cx, camera.worldToScreenY(s.getOrigin().y));
        }
    }

    // ---------------- Список магазинов ----------------

    private static final Color LIST_BG      = new Color(30, 30, 45, 240);
    private static final Color LIST_BORDER  = new Color(120, 180, 255);
    private static final Color LIST_TITLE   = new Color(200, 230, 255);
    private static final Color LIST_ITEM    = new Color(220, 220, 220);
    private static final Color LIST_ITEM_HL = new Color(255, 210, 90);

    private static final int LIST_W        = 260;
    private static final int LIST_PAD      = 10;
    private static final int LIST_ROW_H    = 24;
    private static final int LIST_TITLE_H  = 28;

    /**
     * Рисует список магазинов в правом верхнем углу.
     * Позиции строк запоминаются в хитбоксы для клика.
     * Тут же сохраняем хитбоксы строк в marker.screenX/Y — но нет,
     * у маркеров свои координаты. Список кликается отдельно,
     * через отдельный hit-test по строкам.
     */
    private void drawShopList(Graphics2D g, int screenW) {
        if (markers.isEmpty()) {
            // Пустой список — всё равно показываем плашку
            int x = screenW - LIST_W - 16;
            int y = 60;
            g.setColor(LIST_BG);
            g.fillRoundRect(x, y, LIST_W, LIST_TITLE_H + 30, 12, 12);
            g.setColor(LIST_BORDER);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(x, y, LIST_W, LIST_TITLE_H + 30, 12, 12);
            g.setStroke(new BasicStroke(1f));

            g.setColor(LIST_TITLE);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("Связи фабрики", x + LIST_PAD, y + 20);

            g.setColor(new Color(180, 180, 180));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Нет привязанных магазинов", x + LIST_PAD, y + 44);
            return;
        }

        int rows = markers.size();
        int totalH = LIST_TITLE_H + rows * LIST_ROW_H + LIST_PAD * 2;
        int x = screenW - LIST_W - 16;
        int y = 60;

        // Фон
        g.setColor(LIST_BG);
        g.fillRoundRect(x, y, LIST_W, totalH, 12, 12);

        // Рамка
        g.setColor(LIST_BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, LIST_W, totalH, 12, 12);
        g.setStroke(new BasicStroke(1f));

        // Заголовок
        g.setColor(LIST_TITLE);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Связи фабрики " + activeFactory.getOrigin(),
                x + LIST_PAD, y + 20);

        // Разделитель
        g.setColor(new Color(80, 80, 110));
        g.drawLine(x + LIST_PAD, y + LIST_TITLE_H - 2,
                x + LIST_W - LIST_PAD, y + LIST_TITLE_H - 2);

        // Строки
        Font rowFont = new Font("SansSerif", Font.PLAIN, 12);
        g.setFont(rowFont);

        int rowY = y + LIST_TITLE_H + 4;
        for (int i = 0; i < markers.size(); i++) {
            ShopMarker m = markers.get(i);
            Shop s = m.shop;

            boolean selected = (s == selectedShop);

            // Фон строки при выборе
            if (selected) {
                g.setColor(new Color(80, 80, 40, 200));
                g.fillRoundRect(x + 4, rowY - 2,
                        LIST_W - 8, LIST_ROW_H - 2, 6, 6);
            }

            // Номер
            g.setColor(selected ? LIST_ITEM_HL : new Color(140, 200, 255));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString((i + 1) + ".", x + LIST_PAD, rowY + 15);

            // Текст
            g.setColor(selected ? LIST_ITEM_HL : LIST_ITEM);
            g.setFont(rowFont);
            String label = "Магазин " + s.getOrigin()
                    + "   (" + s.totalStored() + "/" + s.getCapacity() + ")";
            g.drawString(label, x + LIST_PAD + 22, rowY + 15);

            rowY += LIST_ROW_H;
        }

        // Подсказка снизу
        g.setColor(new Color(150, 150, 150));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String hint = "ЛКМ — показать маршрут, Esc — закрыть";
        g.drawString(hint, x + LIST_PAD, y + totalH - 6);
    }

    /**
     * Хит-тест по строке списка.
     * Возвращает индекс магазина или -1.
     *
     * @param screenW ширина экрана — чтобы правильно посчитать X списка
     */
    public int hitTestList(int mx, int my, int screenW) {
        if (activeFactory == null) return -1;

        int rows = Math.max(1, markers.size());
        int totalH = LIST_TITLE_H + rows * LIST_ROW_H + LIST_PAD * 2;
        int x = screenW - LIST_W - 16;
        int y = 60;

        if (mx < x || mx > x + LIST_W) return -1;
        if (my < y || my > y + totalH) return -1;

        int rowY = y + LIST_TITLE_H + 4;
        for (int i = 0; i < markers.size(); i++) {
            if (my >= rowY && my < rowY + LIST_ROW_H) return i;
            rowY += LIST_ROW_H;
        }
        return -1;
    }
}