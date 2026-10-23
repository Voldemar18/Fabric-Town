package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.map.Point;

import java.util.ArrayList;
import java.util.List;

public class RouteOverlay {
    private Factory activeFactory;
    private Shop selectedShop;
    private final List<ShopMarker> markers = new ArrayList<>();
    private final GlyphLayout glyph = new GlyphLayout();

    public static final int LIST_W = 260;
    public static final int LIST_PAD = 10;
    public static final int LIST_ROW_H = 24;
    public static final int LIST_TITLE_H = 28;
    public static final int LIST_TOP_FROM_HUD = 60;

    public static class ShopMarker {
        public final Shop shop;
        public int screenX, screenY, width, height;
        public ShopMarker(Shop shop) { this.shop = shop; }
        public boolean contains(int mx, int my) {
            return mx >= screenX && mx <= screenX + width
                    && my >= screenY && my <= screenY + height;
        }
    }

    public boolean isActive() { return activeFactory != null; }
    public Factory getActiveFactory() { return activeFactory; }
    public Shop getSelectedShop() { return selectedShop; }
    public List<ShopMarker> getMarkers() { return markers; }

    public void toggle(Factory factory, Game game) {
        if (factory == null || factory == activeFactory) {
            clear();
            return;
        }
        activeFactory = factory;
        selectedShop = null;
        rebuildMarkers(game);
    }

    public void clear() {
        activeFactory = null;
        selectedShop = null;
        markers.clear();
    }

    public void selectShop(Shop shop) { selectedShop = shop; }
    public void deselectShop() { selectedShop = null; }

    public void rebuildMarkers(Game game) {
        markers.clear();
        if (activeFactory == null) return;
        List<Shop> bound = game.getDispatcher().getShopsFor(activeFactory);
        for (Shop s : bound) markers.add(new ShopMarker(s));
        if (selectedShop != null && !bound.contains(selectedShop)) selectedShop = null;
    }

    public ShopMarker hitTest(int mx, int my) {
        for (ShopMarker m : markers) if (m.contains(mx, my)) return m;
        return null;
    }

    public void drawFilled(ShapeRenderer shapes, Game game) {
        if (activeFactory == null || selectedShop == null) return;
        Point factoryExit = game.findAdjacentRoad(activeFactory);
        Point shopExit = game.findAdjacentRoad(selectedShop);
        if (factoryExit == null || shopExit == null) return;
        List<Point> toShop = game.getPathFinder().findPath(factoryExit, shopExit);
        List<Point> toFactory = game.getPathFinder().findPath(shopExit, factoryExit);
        if (toShop == null || toFactory == null) return;
        drawPathCells(shapes, toShop, Draw.rgba(90, 220, 120, 140));
        drawPathCells(shapes, toFactory, Draw.rgba(255, 140, 90, 140));
    }

    public void drawLines(ShapeRenderer shapes, Game game, CameraController cam) {
        if (activeFactory == null) return;

        Point o = activeFactory.getOrigin();
        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 250.0);
        shapes.setColor(1f, 220 / 255f, 100 / 255f, 0.7f * pulse);
        Draw.dashRect(shapes, o.x - 0.08f, o.y - 0.08f,
                activeFactory.getSize().w + 0.16f,
                activeFactory.getSize().h + 0.16f, 0.25f, 0.18f);

        if (selectedShop == null) return;
        Point factoryExit = game.findAdjacentRoad(activeFactory);
        Point shopExit = game.findAdjacentRoad(selectedShop);
        if (factoryExit == null || shopExit == null) return;

        List<Point> toShop = game.getPathFinder().findPath(factoryExit, shopExit);
        List<Point> toFactory = game.getPathFinder().findPath(shopExit, factoryExit);
        if (toShop == null || toFactory == null) return;

        if (cam.pxPerCell() >= 12) {
            drawArrows(shapes, toShop, Draw.rgb(120, 255, 140), cam);
            drawArrows(shapes, toFactory, Draw.rgb(255, 180, 110), cam);
        }
    }

    public void drawNoRouteIfNeeded(SpriteBatch batch, Texture pixel, BitmapFont font,
                                    Game game, CameraController cam) {
        if (activeFactory == null || selectedShop == null) return;
        Point factoryExit = game.findAdjacentRoad(activeFactory);
        Point shopExit = game.findAdjacentRoad(selectedShop);
        if (factoryExit != null && shopExit != null) {
            List<Point> toShop = game.getPathFinder().findPath(factoryExit, shopExit);
            List<Point> toFactory = game.getPathFinder().findPath(shopExit, factoryExit);
            if (toShop != null && toFactory != null) return;
        }
        Factory f = activeFactory;
        float fx = cam.worldToScreenX(f.getOrigin().x + f.getSize().w / 2.0);
        float fyTop = cam.worldToScreenY(f.getOrigin().y);
        float h = Gdx.graphics.getHeight();
        float fy = h - fyTop;
        float r = 12;
        batch.setColor(1f, 80 / 255f, 80 / 255f, 1f);
        drawX(batch, pixel, fx, fy, r);
        String text = "Нет пути!";
        glyph.setText(font, text);
        float tw = glyph.width;
        batch.setColor(20 / 255f, 20 / 255f, 20 / 255f, 0.86f);
        batch.draw(pixel, fx - tw / 2 - 6, fy - r - 26, tw + 12, 22);
        font.setColor(Color.WHITE);
        font.draw(batch, text, fx - tw / 2, fy - r - 10);
        batch.setColor(Color.WHITE);
    }

    public void drawScreen(SpriteBatch batch, Texture pixel, BitmapFont font, BitmapFont small,
                           Game game, CameraController cam) {
        if (activeFactory == null) return;
        float screenH = Gdx.graphics.getHeight();
        float cell = cam.pxPerCell();
        int size = (int) Math.max(18, Math.min(28, cell / 2));

        for (int i = 0; i < markers.size(); i++) {
            ShopMarker m = markers.get(i);
            Shop s = m.shop;
            float cx = cam.worldToScreenX(s.getOrigin().x) + s.getSize().w * cell / 2f;
            float cyTop = cam.worldToScreenY(s.getOrigin().y) - size - 4;
            m.screenX = (int) (cx - size / 2f);
            m.screenY = (int) cyTop;
            m.width = size;
            m.height = size;

            boolean selected = s == selectedShop;
            float drawY = screenH - (m.screenY + size);
            batch.setColor(selected
                    ? Draw.rgba(255, 210, 90, 240)
                    : Draw.rgba(80, 180, 255, 220));
            batch.draw(pixel, m.screenX, drawY, size, size);
            batch.setColor(selected ? Draw.rgb(255, 240, 180) : Draw.rgb(220, 240, 255));
            batch.draw(pixel, m.screenX, drawY, size, 2);
            batch.draw(pixel, m.screenX, drawY + size - 2, size, 2);
            batch.draw(pixel, m.screenX, drawY, 2, size);
            batch.draw(pixel, m.screenX + size - 2, drawY, 2, size);

            String num = String.valueOf(i + 1);
            glyph.setText(font, num);
            font.setColor(Color.BLACK);
            font.draw(batch, num,
                    m.screenX + (size - glyph.width) / 2f,
                    drawY + (size + glyph.height) / 2f);

            batch.setColor(1f, 1f, 1f, 0.3f);
            float shopTop = screenH - cam.worldToScreenY(s.getOrigin().y);
            batch.draw(pixel, cx, Math.min(drawY, shopTop), 1,
                    Math.abs(shopTop - drawY));
            batch.setColor(Color.WHITE);
        }

        drawShopList(batch, pixel, font, small);
        font.setColor(Color.WHITE);
        small.setColor(Color.WHITE);
    }

    public int hitTestList(int mx, int my, int screenW) {
        if (activeFactory == null) return -1;
        int rows = Math.max(1, markers.size());
        int totalH = LIST_TITLE_H + rows * LIST_ROW_H + LIST_PAD * 2;
        int x = screenW - LIST_W - 16;
        int y = LIST_TOP_FROM_HUD;
        if (mx < x || mx > x + LIST_W) return -1;
        if (my < y || my > y + totalH) return -1;
        int rowY = y + LIST_TITLE_H + 4;
        for (int i = 0; i < markers.size(); i++) {
            if (my >= rowY && my < rowY + LIST_ROW_H) return i;
            rowY += LIST_ROW_H;
        }
        return -1;
    }

    private void drawShopList(SpriteBatch batch, Texture pixel, BitmapFont font, BitmapFont small) {
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        int rows = Math.max(1, markers.size());
        int totalH = LIST_TITLE_H + rows * LIST_ROW_H + LIST_PAD * 2;
        float x = screenW - LIST_W - 16;
        float yTop = LIST_TOP_FROM_HUD;
        float y = screenH - yTop - totalH;

        batch.setColor(30 / 255f, 30 / 255f, 45 / 255f, 0.94f);
        batch.draw(pixel, x, y, LIST_W, totalH);
        batch.setColor(120 / 255f, 180 / 255f, 1f, 1f);
        batch.draw(pixel, x, y, LIST_W, 2);
        batch.draw(pixel, x, y + totalH - 2, LIST_W, 2);
        batch.draw(pixel, x, y, 2, totalH);
        batch.draw(pixel, x + LIST_W - 2, y, 2, totalH);
        batch.setColor(Color.WHITE);

        float cursor = screenH - yTop - 20;
        font.setColor(200 / 255f, 230 / 255f, 1f, 1f);
        if (markers.isEmpty()) {
            font.draw(batch, "Связи фабрики", x + LIST_PAD, cursor);
            small.setColor(180 / 255f, 180 / 255f, 180 / 255f, 1f);
            small.draw(batch, "Нет привязанных магазинов", x + LIST_PAD, cursor - 24);
            return;
        }

        font.draw(batch, "Связи фабрики " + activeFactory.getOrigin(), x + LIST_PAD, cursor);
        cursor -= LIST_TITLE_H - 8;
        batch.setColor(80 / 255f, 80 / 255f, 110 / 255f, 1f);
        batch.draw(pixel, x + LIST_PAD, cursor + 4, LIST_W - LIST_PAD * 2, 1);
        batch.setColor(Color.WHITE);

        for (int i = 0; i < markers.size(); i++) {
            Shop s = markers.get(i).shop;
            boolean selected = s == selectedShop;
            if (selected) {
                batch.setColor(80 / 255f, 80 / 255f, 40 / 255f, 0.78f);
                batch.draw(pixel, x + 4, cursor - 16, LIST_W - 8, LIST_ROW_H - 2);
                batch.setColor(Color.WHITE);
            }
            font.setColor(selected ? Draw.rgb(255, 210, 90) : Draw.rgb(140, 200, 255));
            font.draw(batch, (i + 1) + ".", x + LIST_PAD, cursor);
            small.setColor(selected ? Draw.rgb(255, 210, 90) : Draw.rgb(220, 220, 220));
            small.draw(batch, "Магазин " + s.getOrigin()
                            + "   (" + s.totalStored() + "/" + s.getCapacity() + ")",
                    x + LIST_PAD + 22, cursor);
            cursor -= LIST_ROW_H;
        }
        small.setColor(150 / 255f, 150 / 255f, 150 / 255f, 1f);
        small.draw(batch, "ЛКМ — показать маршрут, Esc — закрыть", x + LIST_PAD, y + 12);
    }

    private void drawPathCells(ShapeRenderer shapes, List<Point> path, Color color) {
        shapes.setColor(color);
        for (Point p : path) {
            shapes.rect(p.x + 0.12f, p.y + 0.12f, 0.76f, 0.76f);
        }
    }

    private void drawArrows(ShapeRenderer shapes, List<Point> path, Color color, CameraController cam) {
        if (path.size() < 2) return;
        shapes.setColor(color);
        float head = Math.max(0.15f, 5f / cam.pxPerCell());
        for (int i = 0; i < path.size() - 1; i++) {
            Point a = path.get(i), b = path.get(i + 1);
            float mx = (a.x + b.x) / 2f + 0.5f;
            float my = (a.y + b.y) / 2f + 0.5f;
            double ang = Math.atan2(b.y - a.y, b.x - a.x);
            Draw.arrowHead(shapes, mx, my, ang, head);
        }
    }

    private void drawX(SpriteBatch batch, Texture pixel, float cx, float cy, float r) {
        for (int i = -1; i <= 1; i++) {
            float t = i;
            batch.draw(pixel, cx - r + t, cy - r, 2, (r * 2) * 1.4f);
        }
    }
}
