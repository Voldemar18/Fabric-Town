package ru.student.fabricTown.profiler;

import java.awt.*;
import java.util.List;

/**
 * Мини-оверлей поверх игры: топ-N по totalMs.
 * Рисуется в GamePanel.paintComponent() ПОСЛЕ всего остального.
 *
 * Показывает:
 *   - FPS (считается снаружи, передаётся в draw),
 *   - топ-8 тегов по totalMs,
 *   - вызовы/сек.
 */
public final class ProfilerOverlay {

    private ProfilerOverlay() {}

    private static final Font FONT = new Font("Monospaced", Font.PLAIN, 11);
    private static final Font FONT_BOLD = new Font("Monospaced", Font.BOLD, 12);

    private static final int PAD = 8;
    private static final int ROW_H = 14;
    private static final int WIDTH = 380;
    private static final int MAX_ROWS = 8;

    /** Рисует оверлей в левом верхнем углу. */
    public static void draw(Graphics2D g, ProfilerSnapshot snap,
                            double fps, int screenW, int screenH) {
        if (snap == null) return;

        List<ProfilerSnapshot.Row> top = snap.rows;
        int rows = Math.min(MAX_ROWS, top.size());
        int height = PAD * 2 + 16 + 14 + rows * ROW_H;

        int x = 12;
        int y = 12;

        // Фон
        g.setColor(new Color(15, 15, 20, 220));
        g.fillRoundRect(x, y, WIDTH, height, 8, 8);
        g.setColor(new Color(120, 180, 255, 200));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, WIDTH, height, 8, 8);
        g.setStroke(new BasicStroke(1f));

        // Заголовок
        g.setFont(FONT_BOLD);
        g.setColor(new Color(180, 220, 255));
        g.drawString("PROFILER", x + PAD, y + PAD + 12);

        g.setColor(fps >= 55 ? new Color(120, 240, 120)
                : fps >= 30 ? new Color(240, 220, 90)
                : new Color(255, 100, 100));
        g.drawString(String.format("FPS %.1f", fps), x + PAD + 100, y + PAD + 12);

        g.setColor(new Color(150, 150, 170));
        g.setFont(FONT);
        g.drawString("calls/s", x + PAD + 200, y + PAD + 12);
        g.drawString("total ms", x + PAD + 300, y + PAD + 12);

        // Строки
        int rowY = y + PAD + 16 + 14;
        for (int i = 0; i < rows; i++) {
            ProfilerSnapshot.Row r = top.get(i);

            // Тепловая карта: чем больше totalMs — тем краснее
            double t = Math.min(1.0, r.totalMs / 5.0);  // 5 мс = 100% красный
            g.setColor(new Color(120 + (int)(135 * t),
                    220 - (int)(180 * t), 120 - (int)(100 * t)));

            g.setFont(FONT);
            String tag = r.tag.length() > 28 ? r.tag.substring(0, 28) : r.tag;
            g.drawString(tag, x + PAD, rowY);

            g.setColor(new Color(220, 220, 230));
            g.drawString(String.format("%.1f", r.callsPerSec),
                    x + PAD + 220, rowY);
            g.drawString(String.format("%.2f", r.totalMs),
                    x + PAD + 320, rowY);

            rowY += ROW_H;
        }
    }
}