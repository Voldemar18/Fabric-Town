package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import ru.student.fabricTown.map.Point;

/**
 * Камера как в Swing-версии: {@code zoom} — пикселей на клетку,
 * {@code offset} — мировая клетка в левом верхнем углу экрана.
 * Мир направлен как Swing: Y вниз (север = меньше Y).
 */
public class CameraController {
    public final OrthographicCamera camera = new OrthographicCamera();

    public double offsetX = 0;
    public double offsetY = 0;
    public double zoom = 32.0;
    public double targetZoom = 32.0;
    public double targetOffsetX = 0;
    public double targetOffsetY = 0;

    public static final double MIN_ZOOM = 8;
    public static final double MAX_ZOOM = 128;

    private boolean dragging;
    private int lastMouseX;
    private int lastMouseY;

    public CameraController(int width, int height) {
        apply(width, height);
    }

    public void resize(int w, int h) {
        apply(w, h);
    }

    public void update(float dt) {
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)
                || Gdx.input.isButtonPressed(Input.Buttons.MIDDLE)) {
            int mx = Gdx.input.getX();
            int my = Gdx.input.getY();
            if (!dragging) {
                dragging = true;
                lastMouseX = mx;
                lastMouseY = my;
            } else {
                int dx = mx - lastMouseX;
                int dy = my - lastMouseY;
                targetOffsetX -= dx / zoom;
                targetOffsetY -= dy / zoom;
                lastMouseX = mx;
                lastMouseY = my;
            }
        } else {
            dragging = false;
        }

        zoom += (targetZoom - zoom) * 0.2;
        offsetX += (targetOffsetX - offsetX) * 0.15;
        offsetY += (targetOffsetY - offsetY) * 0.15;
        apply(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void zoomWheel(float amountY) {
        double factor = amountY > 0 ? 0.9 : 1.1;
        targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom * factor));
    }

    public void apply(int screenW, int screenH) {
        float vw = screenW / (float) zoom;
        float vh = screenH / (float) zoom;
        camera.setToOrtho(true, vw, vh);
        camera.position.set((float) (offsetX + vw / 2f), (float) (offsetY + vh / 2f), 0);
        camera.update();
    }

    /** Экран X, Y сверху вниз — как в Swing и {@code Gdx.input}. */
    public float worldToScreenX(double wx) {
        return (float) ((wx - offsetX) * zoom);
    }

    public float worldToScreenY(double wy) {
        return (float) ((wy - offsetY) * zoom);
    }

    public double screenToWorldX(float sx) {
        return sx / zoom + offsetX;
    }

    public double screenToWorldY(float syFromTop) {
        return syFromTop / zoom + offsetY;
    }

    public Point cellUnder(int screenX, int screenYFromTop) {
        return new Point(
                (int) Math.floor(screenToWorldX(screenX)),
                (int) Math.floor(screenToWorldY(screenYFromTop)));
    }

    public Vector3 unprojectMouse() {
        return new Vector3(
                (float) screenToWorldX(Gdx.input.getX()),
                (float) screenToWorldY(Gdx.input.getY()),
                0);
    }

    public float pxPerCell() {
        return (float) zoom;
    }

    public boolean isDragging() {
        return dragging;
    }
}
