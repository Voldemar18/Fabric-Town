package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ru.student.fabricTown.agents.Person;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.agents.UniversalCar;
import ru.student.fabricTown.buildings.*;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.RoadRegistry;
import ru.student.fabricTown.map.Direction;
import ru.student.fabricTown.map.Point;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Мировая отрисовка карты: сетка, дороги, здания, агенты, призрак размещения.
 * Подписи рисуются отдельно в экранных координатах, чтобы шрифт не масштабировался клетками.
 */
public class GameRenderer {
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final GlyphLayout glyph = new GlyphLayout();

    private static final Color BG           = Draw.rgb(25, 25, 30);
    private static final Color GRID         = Draw.rgb(40, 40, 50);
    private static final Color GRID_MAJOR   = Draw.rgb(55, 55, 70);
    private static final Color BORDER       = Draw.rgb(90, 90, 130);

    private static final Color ROAD_FILL    = Draw.rgb(70, 70, 80);
    private static final Color ROAD_EDGE    = Draw.rgb(120, 120, 130);
    private static final Color ROAD_ARROW   = Draw.rgb(230, 230, 90);
    private static final Color ROAD_BUSY    = Draw.rgb(120, 90, 60);

    private static final Color FACTORY_FILL = Draw.rgb(180, 90, 70);
    private static final Color FACTORY_EDGE = Draw.rgb(240, 200, 120);
    private static final Color SHOP_FILL    = Draw.rgb(70, 130, 180);
    private static final Color SHOP_EDGE    = Draw.rgb(180, 220, 255);
    private static final Color SHOP_WARN    = Draw.rgb(255, 80, 80);
    private static final Color HOUSE_FILL   = Draw.rgb(160, 160, 90);
    private static final Color HOUSE_EDGE   = Draw.rgb(230, 230, 170);
    private static final Color PROC_FILL    = Draw.rgb(150, 80, 150);
    private static final Color PROC_EDGE    = Draw.rgb(230, 180, 230);

    private static final Color CAR_FILL     = Draw.rgb(255, 200, 50);
    private static final Color CAR_EDGE     = Draw.rgb(30, 30, 30);
    private static final Color PERSON_FILL  = Draw.rgb(255, 100, 180);

    private static final Color GHOST_OK     = Draw.rgba(80, 220, 100, 120);
    private static final Color GHOST_BAD    = Draw.rgba(240, 80, 80, 120);
    private static final Color GHOST_OK_ED  = Draw.rgb(80, 220, 100);
    private static final Color GHOST_BAD_ED = Draw.rgb(240, 80, 80);
    private static final Color DEMOLISH     = Draw.rgba(255, 60, 60, 100);
    private static final Color DEMOLISH_ED  = Draw.rgb(255, 60, 60);
    private static final Color ROT_HINT     = Draw.rgba(120, 200, 255, 200);

    public void renderWorld(Game game, CameraController cam,
                            BuildMode mode, Blueprint selected,
                            Point hoverCell, boolean hoverValid,
                            Building hoverBuilding) {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        shapes.setProjectionMatrix(cam.camera.combined);

        Draw.enableBlend();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BG);
        float wx0 = (float) cam.screenToWorldX(0);
        float wy0 = (float) cam.screenToWorldY(0);
        float wx1 = (float) cam.screenToWorldX(w);
        float wy1 = (float) cam.screenToWorldY(h);
        shapes.rect(wx0, wy0, wx1 - wx0, wy1 - wy0);

        drawRoadsFilled(game);
        drawBuildingsFilled(game);
        drawCarsFilled(game);
        drawPeopleFilled(game);
        drawGhostFilled(mode, selected, hoverCell, hoverValid);
        drawDemolishFilled(mode, hoverBuilding);
        shapes.end();

        float cell = cam.pxPerCell();
        Gdx.gl.glLineWidth(Math.max(1f, Math.min(4f, cell / 16f)));

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawGrid(game, cam, w, h);
        drawMapBounds(game);
        drawRoadArrows(game, cam);
        drawBuildingEdges(game);
        drawCarEdges(game, cam);
        drawGhostEdges(mode, selected, hoverCell, hoverValid, cam);
        drawDemolishEdges(mode, hoverBuilding);
        drawRotateHint(mode, hoverBuilding, cam);
        shapes.end();

        Gdx.gl.glLineWidth(1f);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void renderScreenLabels(SpriteBatch batch, BitmapFont tiny, BitmapFont label,
                                   BitmapFont big, Game game, CameraController cam,
                                   BuildMode mode, Blueprint selected, Point hoverCell,
                                   Building hoverBuilding) {
        float zoom = cam.pxPerCell();
        float screenH = Gdx.graphics.getHeight();

        Set<Building> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Building b : game.getMap().getAll().values()) {
            if (b instanceof Road || !drawn.add(b)) continue;
            if (zoom < 14) {
                if (b instanceof Shop s && s.isEmpty() && zoom >= 12) {
                    drawShopBang(batch, big, s, cam, screenH);
                }
                continue;
            }
            if (b instanceof Factory f) drawFactoryLabels(batch, tiny, label, f, cam, screenH, zoom);
            else if (b instanceof Shop s) drawShopLabels(batch, tiny, label, big, s, cam, screenH, zoom);
            else if (b instanceof House h) drawHouseLabels(batch, tiny, label, h, cam, screenH);
            else if (b instanceof Processor p) drawProcessorLabels(batch, tiny, label, p, cam, screenH, zoom);
        }

        if (zoom >= 16) {
            tiny.setColor(Color.WHITE);
            for (UniversalCar c : game.getCars()) {
                int count = c.getCargoCount();
                if (count <= 0) continue;
                float sx = cam.worldToScreenX(c.getX());
                float sy = screenH - cam.worldToScreenY(c.getY());
                float size = Math.max(4, zoom * 0.5f);
                tiny.draw(batch, String.valueOf(count), sx + size / 2f + 2, sy);
                if (zoom >= 24) {
                    Product first = c.getCarrying();
                    if (first != null) {
                        tiny.setColor(1f, 1f, 1f, 0.7f);
                        tiny.draw(batch, first.title, sx - 20, sy + size);
                        tiny.setColor(Color.WHITE);
                    }
                }
            }
        }

        if (mode == BuildMode.PLACE && selected != null && hoverCell != null && zoom >= 16) {
            float x = cam.worldToScreenX(hoverCell.x) + 4;
            float y = screenH - cam.worldToScreenY(hoverCell.y) - 4;
            String text = switch (selected.kind) {
                case ROAD -> "Дорога";
                case FACTORY -> "Фабрика";
                case PROCESSOR -> "Переработчик";
                case SHOP -> "Магазин";
                case HOUSE -> "Дом";
                case LAND -> "Земля";
            };
            label.setColor(Color.WHITE);
            label.draw(batch, text, x, y);
        }

        if (mode == BuildMode.NONE && hoverBuilding instanceof Road r
                && !r.isStraight() && !r.isCross() && zoom >= 20) {
            float x = cam.worldToScreenX(r.getOrigin().x) + zoom / 2f - 4;
            float y = screenH - cam.worldToScreenY(r.getOrigin().y) + 12;
            tiny.setColor(0.86f, 0.94f, 1f, 0.86f);
            tiny.draw(batch, "R", x, y);
        }
    }

    public void drawHelp(SpriteBatch batch, BitmapFont small, BitmapFont labelFont,
                         Point hoverCell, com.badlogic.gdx.graphics.Texture pixel) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float right = w - 20;
        float bottom = 20;

        if (hoverCell != null) {
            String coord = "(" + hoverCell.x + ", " + hoverCell.y + ")";
            glyph.setText(labelFont, coord);
            float padX = 8, padY = 4;
            float boxW = glyph.width + padX * 2;
            float boxH = glyph.height + padY * 2 + 6;
            float boxX = right - boxW;
            float boxY = bottom;
            batch.setColor(20 / 255f, 20 / 255f, 30 / 255f, 0.78f);
            batch.draw(pixel, boxX, boxY, boxW, boxH);
            batch.setColor(255 / 255f, 220 / 255f, 100 / 255f, 1f);
            batch.draw(pixel, boxX, boxY, boxW, 1.5f);
            batch.draw(pixel, boxX, boxY + boxH - 1.5f, boxW, 1.5f);
            batch.draw(pixel, boxX, boxY, 1.5f, boxH);
            batch.draw(pixel, boxX + boxW - 1.5f, boxY, 1.5f, boxH);
            labelFont.setColor(255 / 255f, 235 / 255f, 130 / 255f, 1f);
            labelFont.draw(batch, coord, boxX + padX, boxY + padY + glyph.height);
            bottom = boxY + boxH + 8;
            batch.setColor(Color.WHITE);
        }

        small.setColor(180 / 255f, 180 / 255f, 180 / 255f, 0.86f);
        String[] lines = {
                "ПКМ/СКМ — тащить карту",
                "Колесо — зум",
                "ЛКМ — разместить / снести",
                "Esc — отмена, D — снос"
        };
        float y = bottom + 14 * (lines.length - 1);
        for (String line : lines) {
            glyph.setText(small, line);
            small.draw(batch, line, right - glyph.width, y);
            y -= 14;
        }
        small.setColor(Color.WHITE);
        labelFont.setColor(Color.WHITE);
    }

    public ShapeRenderer getShapes() { return shapes; }

    public void dispose() {
        shapes.dispose();
    }

    private void drawGrid(Game game, CameraController cam, int w, int h) {
        if (cam.zoom < 4) return;
        int startX = (int) Math.floor(cam.offsetX);
        int startY = (int) Math.floor(cam.offsetY);
        int endX = (int) Math.ceil(cam.offsetX + w / cam.zoom);
        int endY = (int) Math.ceil(cam.offsetY + h / cam.zoom);

        shapes.setColor(GRID);
        for (int x = startX; x <= endX; x++) shapes.line(x, startY, x, endY);
        for (int y = startY; y <= endY; y++) shapes.line(startX, y, endX, y);
        shapes.setColor(GRID_MAJOR);
        for (int x = startX; x <= endX; x++) if (x % 5 == 0) shapes.line(x, startY, x, endY);
        for (int y = startY; y <= endY; y++) if (y % 5 == 0) shapes.line(startX, y, endX, y);
    }

    private void drawMapBounds(Game game) {
        shapes.setColor(BORDER);
        Draw.dashRect(shapes,
                game.getMapMinX(), game.getMapMinY(),
                game.getMapMaxX() - game.getMapMinX() + 1,
                game.getMapMaxY() - game.getMapMinY() + 1,
                0.35f, 0.25f);
    }

    private void drawRoadsFilled(Game game) {
        for (Road r : game.getMap().getRoads().values()) {
            Point p = r.getOrigin();
            shapes.setColor(RoadRegistry.isBlocked(p) ? ROAD_BUSY : ROAD_FILL);
            shapes.rect(p.x, p.y, 1f, 1f);
        }
    }

    private void drawRoadArrows(Game game, CameraController cam) {
        float head = Math.max(0.18f, 6f / cam.pxPerCell());
        for (Road r : game.getMap().getRoads().values()) {
            Point p = r.getOrigin();
            shapes.setColor(ROAD_EDGE);
            shapes.rect(p.x, p.y, 1f, 1f);
            float cx = p.x + 0.5f, cy = p.y + 0.5f;
            shapes.setColor(ROAD_ARROW);
            for (Direction d : r.getExits()) {
                shapes.line(cx, cy, cx + d.dx * 0.5f, cy + d.dy * 0.5f);
            }
            for (Direction d : r.getPrimaryExits()) {
                float ex = cx + d.dx * 0.5f;
                float ey = cy + d.dy * 0.5f;
                Draw.arrowHead(shapes, ex, ey, Math.atan2(d.dy, d.dx), head);
            }
        }
    }

    private void drawBuildingsFilled(Game game) {
        Set<Building> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Building b : game.getMap().getAll().values()) {
            if (b instanceof Road || !drawn.add(b)) continue;
            Color fill = buildingFill(b);
            if (fill == null) continue;
            shapes.setColor(fill);
            float x = b.getOrigin().x + 0.06f;
            float y = b.getOrigin().y + 0.06f;
            float w = b.getSize().w - 0.12f;
            float h = b.getSize().h - 0.12f;
            Draw.roundRectFilled(shapes, x, y, w, h, 0.18f);
        }
    }

    private void drawBuildingEdges(Game game) {
        Set<Building> drawn = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Building b : game.getMap().getAll().values()) {
            if (b instanceof Road || !drawn.add(b)) continue;
            Color edge = buildingEdge(b);
            if (edge == null) continue;
            shapes.setColor(edge);
            shapes.rect(b.getOrigin().x + 0.06f, b.getOrigin().y + 0.06f,
                    b.getSize().w - 0.12f, b.getSize().h - 0.12f);
        }
    }

    private Color buildingFill(Building b) {
        if (b instanceof Factory) return FACTORY_FILL;
        if (b instanceof Shop) return SHOP_FILL;
        if (b instanceof House) return HOUSE_FILL;
        if (b instanceof Processor) return PROC_FILL;
        return null;
    }

    private Color buildingEdge(Building b) {
        if (b instanceof Factory) return FACTORY_EDGE;
        if (b instanceof Shop) return SHOP_EDGE;
        if (b instanceof House) return HOUSE_EDGE;
        if (b instanceof Processor) return PROC_EDGE;
        return null;
    }

    private void drawCarsFilled(Game game) {
        for (UniversalCar c : game.getCars()) {
            float size = 0.5f;
            shapes.setColor(0, 0, 0, 0.3f);
            shapes.circle((float) c.getX() + 0.03f, (float) c.getY() + 0.06f, size / 2f, 12);
            shapes.setColor(CAR_FILL);
            shapes.circle((float) c.getX(), (float) c.getY(), size / 2f, 12);
        }
    }

    private void drawCarEdges(Game game, CameraController cam) {
        shapes.setColor(CAR_EDGE);
        for (UniversalCar c : game.getCars()) {
            shapes.circle((float) c.getX(), (float) c.getY(), 0.25f, 12);
        }
    }

    private void drawPeopleFilled(Game game) {
        shapes.setColor(PERSON_FILL);
        for (Person p : game.getPeople()) {
            shapes.setColor(0, 0, 0, 0.24f);
            shapes.circle((float) p.getX() + 0.02f, (float) p.getY() + 0.04f, 0.175f, 10);
            shapes.setColor(PERSON_FILL);
            shapes.circle((float) p.getX(), (float) p.getY(), 0.175f, 10);
        }
    }

    private void drawGhostFilled(BuildMode mode, Blueprint bp, Point cell, boolean valid) {
        if (mode != BuildMode.PLACE || bp == null || cell == null) return;
        shapes.setColor(valid ? GHOST_OK : GHOST_BAD);
        shapes.rect(cell.x, cell.y, bp.size.w, bp.size.h);
        if (mode == BuildMode.DEMOLISH) return;
    }

    private void drawGhostEdges(BuildMode mode, Blueprint bp, Point cell, boolean valid,
                                CameraController cam) {
        if (mode != BuildMode.PLACE || bp == null || cell == null) return;
        shapes.setColor(valid ? GHOST_OK_ED : GHOST_BAD_ED);
        shapes.rect(cell.x, cell.y, bp.size.w, bp.size.h);
        if (bp.kind == Blueprint.Kind.ROAD && bp.shape != null) {
            float cx = cell.x + 0.5f, cy = cell.y + 0.5f;
            float head = Math.max(0.18f, 6f / cam.pxPerCell());
            Color arrow = valid
                    ? Draw.rgba(230, 255, 230, 220)
                    : Draw.rgba(255, 200, 200, 220);
            shapes.setColor(arrow);
            for (Direction d : bp.shape.getExits()) {
                shapes.line(cx, cy, cx + d.dx * 0.5f, cy + d.dy * 0.5f);
            }
            for (Direction d : bp.shape.getPrimaryExits()) {
                float ex = cx + d.dx * 0.5f, ey = cy + d.dy * 0.5f;
                Draw.arrowHead(shapes, ex, ey, Math.atan2(d.dy, d.dx), head);
            }
        }
    }

    private void drawDemolishFilled(BuildMode mode, Building b) {
        if (mode != BuildMode.DEMOLISH || b == null) return;
        shapes.setColor(DEMOLISH);
        shapes.rect(b.getOrigin().x, b.getOrigin().y, b.getSize().w, b.getSize().h);
    }

    private void drawDemolishEdges(BuildMode mode, Building b) {
        if (mode != BuildMode.DEMOLISH || b == null) return;
        float x = b.getOrigin().x, y = b.getOrigin().y;
        float w = b.getSize().w, h = b.getSize().h;
        shapes.setColor(DEMOLISH_ED);
        shapes.rect(x, y, w, h);
        shapes.line(x + 0.12f, y + 0.12f, x + w - 0.12f, y + h - 0.12f);
        shapes.line(x + w - 0.12f, y + 0.12f, x + 0.12f, y + h - 0.12f);
    }

    private void drawRotateHint(BuildMode mode, Building b, CameraController cam) {
        if (mode != BuildMode.NONE || !(b instanceof Road r)) return;
        if (r.isStraight() || r.isCross()) return;
        shapes.setColor(ROT_HINT);
        Draw.dashRect(shapes, r.getOrigin().x + 0.05f, r.getOrigin().y + 0.05f, 0.9f, 0.9f, 0.12f, 0.12f);
    }

    private float screenY(CameraController cam, double worldY, float screenH) {
        return screenH - cam.worldToScreenY(worldY);
    }

    private void drawBar(SpriteBatch batch, com.badlogic.gdx.graphics.Texture pixel,
                         float x, float y, float w, float h, double ratio, Color fill) {
        batch.setColor(0, 0, 0, 0.47f);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(fill);
        batch.draw(pixel, x, y, (float) (w * Math.max(0, Math.min(1, ratio))), h);
        batch.setColor(0.86f, 0.86f, 0.86f, 1f);
        batch.draw(pixel, x, y, w, 1);
        batch.draw(pixel, x, y + h - 1, w, 1);
        batch.draw(pixel, x, y, 1, h);
        batch.draw(pixel, x + w - 1, y, 1, h);
        batch.setColor(Color.WHITE);
    }

    private void drawFactoryLabels(SpriteBatch batch, BitmapFont tiny, BitmapFont label,
                                   Factory f, CameraController cam, float screenH, float zoom) {
        float x = cam.worldToScreenX(f.getOrigin().x);
        float top = screenY(cam, f.getOrigin().y, screenH);
        float w = f.getSize().w * zoom;
        float h = f.getSize().h * zoom;
        label.setColor(Color.WHITE);
        label.draw(batch, f.getProduct().title, x + 6, top - 6);
        tiny.setColor(Color.WHITE);
        tiny.draw(batch, f.getStorageCount() + "/" + f.getMaxStorage(), x + 6, top - h + 18);
    }

    private void drawShopBang(SpriteBatch batch, BitmapFont big, Shop s,
                              CameraController cam, float screenH) {
        float x = cam.worldToScreenX(s.getOrigin().x) + s.getSize().w * cam.pxPerCell() / 2f - 4;
        float y = screenY(cam, s.getOrigin().y, screenH) + 16;
        big.setColor(SHOP_WARN);
        big.draw(batch, "!", x, y);
        big.setColor(Color.WHITE);
    }

    private void drawShopLabels(SpriteBatch batch, BitmapFont tiny, BitmapFont label, BitmapFont big,
                                Shop s, CameraController cam, float screenH, float zoom) {
        float x = cam.worldToScreenX(s.getOrigin().x);
        float top = screenY(cam, s.getOrigin().y, screenH);
        float h = s.getSize().h * zoom;
        if (s.isEmpty()) {
            big.setColor(SHOP_WARN);
            big.draw(batch, "!", x + s.getSize().w * zoom / 2f - 4, top + 16);
            big.setColor(Color.WHITE);
        }
        label.setColor(Color.WHITE);
        label.draw(batch, "Магазин", x + 6, top - 6);
        if (zoom >= 24) {
            float yy = top - 30;
            int count = 0;
            tiny.setColor(Color.WHITE);
            for (Map.Entry<Product, Integer> e : s.getStorage().entrySet()) {
                if (e.getValue() <= 0) continue;
                if (count >= 3) break;
                tiny.draw(batch, e.getKey().title + ": " + e.getValue(), x + 6, yy);
                yy -= 11;
                count++;
            }
        }
    }

    private void drawHouseLabels(SpriteBatch batch, BitmapFont tiny, BitmapFont label,
                                 House h, CameraController cam, float screenH) {
        float x = cam.worldToScreenX(h.getOrigin().x);
        float top = screenY(cam, h.getOrigin().y, screenH);
        label.setColor(Color.WHITE);
        label.draw(batch, "Дом", x + 6, top - 6);
        tiny.setColor(Color.WHITE);
        tiny.draw(batch, "Жителей: " + h.getResidents(), x + 6, top - 18);
    }

    private void drawProcessorLabels(SpriteBatch batch, BitmapFont tiny, BitmapFont label,
                                     Processor p, CameraController cam, float screenH, float zoom) {
        float x = cam.worldToScreenX(p.getOrigin().x);
        float top = screenY(cam, p.getOrigin().y, screenH);
        float h = p.getSize().h * zoom;
        label.setColor(Color.WHITE);
        label.draw(batch, p.getOutputProduct().title, x + 6, top - 6);
        tiny.setColor(Color.WHITE);
        tiny.draw(batch, p.getOutputCount() + "/" + p.getOutputCapacity(), x + 6, top - h + 18);
        if (zoom >= 24) {
            float yy = top - 30;
            for (var e : p.getInputStorage().entrySet()) {
                if (e.getValue() <= 0) continue;
                tiny.draw(batch, e.getKey().title + ": " + e.getValue(), x + 6, yy);
                yy -= 11;
            }
        }
    }
}
