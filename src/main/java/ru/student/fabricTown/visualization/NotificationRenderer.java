package ru.student.fabricTown.visualization;

import ru.student.fabricTown.core.Notification;
import ru.student.fabricTown.core.NotificationCenter;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Рисует стек уведомлений в верхней части экрана.
 *
 * Каждое уведомление — цветной прямоугольник с иконкой и текстом.
 * Появление — плавно сверху, исчезновение — плавное затухание.
 */
public final class NotificationRenderer {

    private NotificationRenderer() {}

    private static final int MARGIN_TOP      = 56;   // ниже HUD
    private static final int MARGIN_SIDE     = 16;
    private static final int PADDING_X       = 12;
    private static final int PADDING_Y       = 8;
    private static final int SPACING         = 6;
    private static final int MAX_WIDTH       = 520;

    private static final Font FONT_TITLE     = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_ICON      = new Font("SansSerif", Font.PLAIN, 14);

    public static void draw(Graphics2D g, NotificationCenter center, int screenWidth) {
        List<Notification> items = center.getVisible();
        if (items.isEmpty()) return;

        int y = MARGIN_TOP;

        for (Notification n : items) {
            double alpha = n.getFadeOutAlpha(center.getNow());
            if (alpha <= 0) continue;

            // Иконка и цвет по уровню
            String icon;
            Color bg, border, textColor;
            switch (n.level) {
                case SUCCESS -> {
                    icon = "✔"; bg = new Color(40, 90, 50, 230);
                    border = new Color(90, 200, 110); textColor = new Color(220, 255, 220);
                }
                case WARNING -> {
                    icon = "⚠"; bg = new Color(100, 80, 30, 230);
                    border = new Color(240, 200, 90); textColor = new Color(255, 245, 200);
                }
                case ERROR -> {
                    icon = "✖"; bg = new Color(100, 40, 40, 235);
                    border = new Color(255, 100, 100); textColor = new Color(255, 220, 220);
                }
                default -> {
                    icon = "ℹ"; bg = new Color(40, 60, 90, 230);
                    border = new Color(120, 170, 240); textColor = new Color(220, 235, 255);
                }
            }

            // Размеры
            g.setFont(FONT_TITLE);
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(n.text);
            int boxWidth = Math.min(MAX_WIDTH,
                    PADDING_X * 2 + 24 + 8 + textWidth);
            int boxHeight = PADDING_Y * 2 + Math.max(fm.getHeight(), 18);

            // Центрируем по горизонтали
            int x = (screenWidth - boxWidth) / 2;

            // Анимация появления: сдвиг сверху вниз
            double appear = Math.min(1.0, n.getAppearProgress());
            int appearOffset = (int) (-10 * (1 - appear));

            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) Math.max(0, Math.min(1, alpha))));

            // Фон
            g.setColor(bg);
            g.fillRoundRect(x, y + appearOffset, boxWidth, boxHeight, 10, 10);
            // Рамка
            g.setColor(border);
            g.drawRoundRect(x, y + appearOffset, boxWidth, boxHeight, 10, 10);
            // Полоска слева — акцент
            g.fillRoundRect(x, y + appearOffset, 5, boxHeight, 6, 6);

            // Иконка
            g.setFont(FONT_ICON);
            g.setColor(border);
            g.drawString(icon, x + PADDING_X, y + appearOffset + PADDING_Y + 15);

            // Текст
            g.setFont(FONT_TITLE);
            g.setColor(textColor);
            g.drawString(n.text,
                    x + PADDING_X + 24,
                    y + appearOffset + PADDING_Y + fm.getAscent());

            g.setComposite(oldComposite);

            y += boxHeight + SPACING;
        }
    }

    /**
     * Продвигает анимацию появления у всех уведомлений.
     * Вызывается каждый кадр из GamePanel.
     */
    public static void tickAnimations(NotificationCenter center, double dt) {
        for (Notification n : center.getVisible()) {
            double p = n.getAppearProgress();
            if (p < 1.0) {
                n.setAppearProgress(Math.min(1.0, p + dt * 4.0));   // ~0.25 сек
            }
        }
    }
}