package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import ru.student.fabricTown.core.Notification;
import ru.student.fabricTown.core.NotificationCenter;

public final class NotificationRenderer {
    private NotificationRenderer() {}

    private static final GlyphLayout glyph = new GlyphLayout();
    private static final int MARGIN_TOP = 56;
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 8;
    private static final int SPACING = 6;
    private static final int MAX_WIDTH = 520;

    public static void draw(SpriteBatch batch, Texture pixel, BitmapFont font,
                            NotificationCenter center, UiStage uiStage) {
        var items = center.getVisible();
        if (items.isEmpty()) return;

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float yTop = MARGIN_TOP;

        for (Notification n : items) {
            double alpha = n.getFadeOutAlpha(center.getNow());
            if (alpha <= 0) continue;

            String icon;
            Color bg, border, textColor;
            switch (n.level) {
                case SUCCESS -> {
                    icon = "✔";
                    bg = Draw.rgba(40, 90, 50, (int) (230 * alpha));
                    border = Draw.rgb(90, 200, 110);
                    textColor = Draw.rgba(220, 255, 220, (int) (255 * alpha));
                }
                case WARNING -> {
                    icon = "⚠";
                    bg = Draw.rgba(100, 80, 30, (int) (230 * alpha));
                    border = Draw.rgb(240, 200, 90);
                    textColor = Draw.rgba(255, 245, 200, (int) (255 * alpha));
                }
                case ERROR -> {
                    icon = "✖";
                    bg = Draw.rgba(100, 40, 40, (int) (235 * alpha));
                    border = Draw.rgb(255, 100, 100);
                    textColor = Draw.rgba(255, 220, 220, (int) (255 * alpha));
                }
                default -> {
                    icon = "ℹ";
                    bg = Draw.rgba(40, 60, 90, (int) (230 * alpha));
                    border = Draw.rgb(120, 170, 240);
                    textColor = Draw.rgba(220, 235, 255, (int) (255 * alpha));
                }
            }

            String text = uiStage != null ? uiStage.filterEmojis(n.text) : n.text;
            float appear = (float) Math.min(1.0, n.getAppearProgress());
            float appearOffset = -10f * (1 - appear);

            glyph.setText(font, text);
            float boxW = Math.min(MAX_WIDTH, PADDING_X * 2 + 24 + 8 + glyph.width);
            float boxH = PADDING_Y * 2 + Math.max(glyph.height, 18);
            float x = (w - boxW) / 2f;
            float y = h - (yTop + appearOffset) - boxH;

            batch.setColor(bg);
            batch.draw(pixel, x, y, boxW, boxH);
            batch.setColor(border);
            batch.draw(pixel, x, y, 5, boxH);
            batch.draw(pixel, x, y, boxW, 1);
            batch.draw(pixel, x, y + boxH - 1, boxW, 1);
            batch.draw(pixel, x, y, 1, boxH);
            batch.draw(pixel, x + boxW - 1, y, 1, boxH);

            font.setColor(border);
            font.draw(batch, icon, x + PADDING_X, y + PADDING_Y + glyph.height);
            font.setColor(textColor);
            font.draw(batch, text, x + PADDING_X + 24, y + PADDING_Y + glyph.height);

            yTop += boxH + SPACING;
        }
        batch.setColor(Color.WHITE);
        font.setColor(Color.WHITE);
    }

    public static void tickAnimations(NotificationCenter center, float dt) {
        for (Notification n : center.getVisible()) {
            double p = n.getAppearProgress();
            if (p < 1.0) n.setAppearProgress(Math.min(1.0, p + dt * 4.0));
        }
    }
}
