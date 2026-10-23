package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import ru.student.fabricTown.profiler.ProfilerSnapshot;

public final class ProfilerHud {
    private ProfilerHud() {}

    public static void draw(SpriteBatch batch, Texture pixel, BitmapFont font,
                            ProfilerSnapshot snap, double fps) {
        if (snap == null) return;
        int pad = 8, rowH = 14, width = 380, maxRows = 8;
        int rows = Math.min(maxRows, snap.rows.size());
        int height = pad * 2 + 16 + 14 + rows * rowH;
        float x = 12;
        float y = Gdx.graphics.getHeight() - 12 - height;

        batch.setColor(15 / 255f, 15 / 255f, 20 / 255f, 0.86f);
        batch.draw(pixel, x, y, width, height);
        batch.setColor(120 / 255f, 180 / 255f, 1f, 0.78f);
        batch.draw(pixel, x, y, width, 1.2f);
        batch.draw(pixel, x, y + height - 1.2f, width, 1.2f);
        batch.draw(pixel, x, y, 1.2f, height);
        batch.draw(pixel, x + width - 1.2f, y, 1.2f, height);

        float top = y + height - pad - 4;
        font.setColor(180 / 255f, 220 / 255f, 1f, 1f);
        font.draw(batch, "PROFILER", x + pad, top);
        if (fps >= 55) font.setColor(120 / 255f, 240 / 255f, 120 / 255f, 1f);
        else if (fps >= 30) font.setColor(240 / 255f, 220 / 255f, 90 / 255f, 1f);
        else font.setColor(1f, 100 / 255f, 100 / 255f, 1f);
        font.draw(batch, String.format("FPS %.1f", fps), x + pad + 100, top);
        font.setColor(150 / 255f, 150 / 255f, 170 / 255f, 1f);
        font.draw(batch, "calls/s", x + pad + 200, top);
        font.draw(batch, "total ms", x + pad + 300, top);

        float rowY = top - 16;
        for (int i = 0; i < rows; i++) {
            var r = snap.rows.get(i);
            double t = Math.min(1.0, r.totalMs / 5.0);
            font.setColor(
                    (120 + (int) (135 * t)) / 255f,
                    (220 - (int) (180 * t)) / 255f,
                    (120 - (int) (100 * t)) / 255f, 1f);
            String tag = r.tag.length() > 28 ? r.tag.substring(0, 28) : r.tag;
            font.draw(batch, tag, x + pad, rowY);
            font.setColor(220 / 255f, 220 / 255f, 230 / 255f, 1f);
            font.draw(batch, String.format("%.1f", r.callsPerSec), x + pad + 220, rowY);
            font.draw(batch, String.format("%.2f", r.totalMs), x + pad + 320, rowY);
            rowY -= rowH;
        }
        batch.setColor(Color.WHITE);
        font.setColor(Color.WHITE);
    }
}
