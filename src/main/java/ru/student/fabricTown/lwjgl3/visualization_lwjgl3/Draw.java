package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

final class Draw {
    private Draw() {}

    static void enableBlend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void roundRectFilled(ShapeRenderer s, float x, float y, float w, float h, float r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        if (r <= 0.01f) {
            s.rect(x, y, w, h);
            return;
        }
        s.rect(x + r, y, w - 2 * r, h);
        s.rect(x, y + r, r, h - 2 * r);
        s.rect(x + w - r, y + r, r, h - 2 * r);
        s.circle(x + r, y + r, r, 10);
        s.circle(x + w - r, y + r, r, 10);
        s.circle(x + r, y + h - r, r, 10);
        s.circle(x + w - r, y + h - r, r, 10);
    }

    static void roundRectLine(ShapeRenderer s, float x, float y, float w, float h) {
        s.rect(x, y, w, h);
    }

    static void arrowHead(ShapeRenderer s, float ex, float ey, double ang, float headLen) {
        s.line(ex, ey,
                (float) (ex - headLen * Math.cos(ang - 0.5)),
                (float) (ey - headLen * Math.sin(ang - 0.5)));
        s.line(ex, ey,
                (float) (ex - headLen * Math.cos(ang + 0.5)),
                (float) (ey - headLen * Math.sin(ang + 0.5)));
    }

    static void dashRect(ShapeRenderer s, float x, float y, float w, float h,
                         float dash, float gap) {
        dashLine(s, x, y, x + w, y, dash, gap);
        dashLine(s, x + w, y, x + w, y + h, dash, gap);
        dashLine(s, x + w, y + h, x, y + h, dash, gap);
        dashLine(s, x, y + h, x, y, dash, gap);
    }

    static void dashLine(ShapeRenderer s, float x1, float y1, float x2, float y2,
                         float dash, float gap) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return;
        float ux = dx / len, uy = dy / len;
        float d = 0;
        while (d < len) {
            float a = d;
            float b = Math.min(len, d + dash);
            s.line(x1 + ux * a, y1 + uy * a, x1 + ux * b, y1 + uy * b);
            d += dash + gap;
        }
    }

    static Color rgba(int r, int g, int b, int a) {
        return new Color(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    static Color rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }
}
