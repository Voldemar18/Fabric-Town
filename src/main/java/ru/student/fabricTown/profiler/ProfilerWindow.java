package ru.student.fabricTown.profiler;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Отдельное окно: JTable + JPanel с line chart.
 *
 * Открывается по клавише F3 (см. GamePanel.installKeyHandlers).
 * Обновляется каждые 500 мс из EDT (Swing Timer).
 */
public class ProfilerWindow extends JFrame {

    private final ProfilerTableModel tableModel = new ProfilerTableModel();
    private final JTable table = new JTable(tableModel);
    private final LineChartPanel chart = new LineChartPanel();

    private final javax.swing.Timer timer;

    /** История totalMs по тегам для графика (последние 120 точек = 60 сек). */
    private static final int HISTORY = 120;

    public ProfilerWindow() {
        super("Profiler — Fabric Town");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // === Верх: таблица ===
        table.setFillsViewportHeight(true);
        table.setRowHeight(20);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setDefaultRenderer(Object.class, new RowColorRenderer());

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(0, 320));
        add(tableScroll, BorderLayout.NORTH);

        // === Низ: график ===
        chart.setBackground(new Color(25, 25, 30));
        chart.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 110)),
                "История (total ms, 60 сек)",
                0, 0, new Font("SansSerif", Font.BOLD, 12),
                new Color(200, 220, 255)));
        add(chart, BorderLayout.CENTER);

        // === Таймер обновления ===
        timer = new javax.swing.Timer(500, e -> refresh());
        timer.start();
    }

    private void refresh() {
        ProfilerSnapshot snap = Profiler.snapshot();
        tableModel.update(snap.rows);
        chart.update(snap);
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    // ==================== ТАБЛИЦА ====================

    private static class ProfilerTableModel extends AbstractTableModel {

        private static final String[] COLS = {
                "Tag", "Calls", "Calls/s", "Total ms", "Avg ms", "Min ms", "Max ms"
        };

        private List<ProfilerSnapshot.Row> rows = new ArrayList<>();

        void update(List<ProfilerSnapshot.Row> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ProfilerSnapshot.Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.tag;
                case 1 -> row.calls;
                case 2 -> String.format("%.1f", row.callsPerSec);
                case 3 -> String.format("%.2f", row.totalMs);
                case 4 -> String.format("%.4f", row.avgMs);
                case 5 -> String.format("%.4f", row.minMs);
                case 6 -> String.format("%.2f", row.maxMs);
                default -> "";
            };
        }
    }

    /**
     * Красный фон для строк с avgMs > 8, оранжевый > 3,
     * жёлтый > 0.5, зелёный иначе.
     */
    private static class RowColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);

            ProfilerSnapshot.Row r = ((ProfilerTableModel) table.getModel()).rows.get(row);

            Color bg = isSelected ? new Color(70, 100, 150)
                    : r.avgMs > 8.0   ? new Color(140, 40, 40)   // 🔴 критично (больше половины кадра)
                    : r.avgMs > 3.0   ? new Color(130, 70, 30)   // 🟠 тяжело
                    : r.avgMs > 0.5   ? new Color(90, 90, 30)    // 🟡 заметно
                    : new Color(40, 60, 40);                     // 🟢 копейки

            c.setBackground(bg);
            c.setForeground(Color.WHITE);
            return c;
        }
    }

    // ==================== ГРАФИК ====================

    private static class LineChartPanel extends JPanel {

        /** тег → история значений (totalMs). */
        private final Map<String, double[]> histories = new LinkedHashMap<>();
        private int[] colors = {
                0x5AD0FF, 0xFF9F43, 0x55E07F, 0xFF5C5C,
                0xC77DFF, 0xFFE45C, 0x5CFFB1, 0xFF7FB2
        };

        LineChartPanel() {
            setOpaque(true);
        }

        void update(ProfilerSnapshot snap) {
            // Берём топ-8 по totalMs, остальные игнорируем
            List<ProfilerSnapshot.Row> top = snap.rows.size() > 8
                    ? snap.rows.subList(0, 8) : snap.rows;

            // Обновляем истории только для топ-8
            for (int i = 0; i < top.size(); i++) {
                ProfilerSnapshot.Row r = top.get(i);
                double[] hist = histories.computeIfAbsent(r.tag,
                        k -> new double[HISTORY]);
                // Сдвиг влево
                System.arraycopy(hist, 1, hist, 0, HISTORY - 1);
                hist[HISTORY - 1] = r.totalMs;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padL = 50, padR = 20, padT = 20, padB = 30;
            int cw = w - padL - padR;
            int ch = h - padT - padB;

            if (histories.isEmpty()) {
                g.setColor(new Color(150, 150, 150));
                g.drawString("Нет данных", w / 2 - 30, h / 2);
                return;
            }

            // Ось X
            g.setColor(new Color(80, 80, 100));
            g.drawLine(padL, h - padB, w - padR, h - padB);

            // Найдём max для масштаба
            double max = 1.0;
            for (double[] hist : histories.values()) {
                for (double v : hist) max = Math.max(max, v);
            }
            max = Math.ceil(max * 1.1);   // запас сверху

            // Горизонтальные линии сетки + подписи
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= 4; i++) {
                double v = max * i / 4.0;
                int y = h - padB - (int) (ch * (v / max));
                g.setColor(new Color(50, 50, 65));
                g.drawLine(padL, y, w - padR, y);
                g.setColor(new Color(150, 150, 170));
                g.drawString(String.format("%.1f", v), 8, y + 4);
            }

            // Легенда (топ-8)
            int ly = padT;
            int li = 0;
            for (Map.Entry<String, double[]> e : histories.entrySet()) {
                Color color = new Color(colors[li % colors.length]);
                g.setColor(color);
                g.fillRect(w - padR - 180, ly, 8, 8);
                g.setColor(new Color(220, 220, 230));
                String tag = e.getKey().length() > 24
                        ? e.getKey().substring(0, 24) : e.getKey();
                g.drawString(tag, w - padR - 168, ly + 8);
                ly += 14;
                li++;
                if (li >= 8) break;
            }

            // Линии
            li = 0;
            for (Map.Entry<String, double[]> e : histories.entrySet()) {
                Color color = new Color(colors[li % colors.length]);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.6f));
                double[] hist = e.getValue();
                int prevX = -1, prevY = -1;
                for (int i = 0; i < HISTORY; i++) {
                    int x = padL + (int) ((double) i / (HISTORY - 1) * cw);
                    int y = h - padB - (int) (ch * (hist[i] / max));
                    if (prevX >= 0) g.drawLine(prevX, prevY, x, y);
                    prevX = x; prevY = y;
                }
                li++;
                if (li >= 8) break;
            }
        }
    }
}