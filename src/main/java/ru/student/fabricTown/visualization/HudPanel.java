package ru.student.fabricTown.visualization;

import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.map.Point;

import javax.swing.*;
import java.awt.*;

/**
 * Верхняя полоса HUD.
 * Показывает:
 *   - деньги (слева),
 *   - статус текущего действия (в центре),
 *   - координаты клетки под курсором (перед подсказками),
 *   - подсказку по управлению (справа).
 *
 * Все обновления — через методы {@link #refresh()}, {@link #setStatus},
 * {@link #setHint}, {@link #setCoord}.
 */
public class HudPanel extends JPanel {

    private final Game game;

    private final JLabel moneyLabel  = new JLabel();
    private final JLabel statusLabel = new JLabel("Готов");
    private final JLabel coordLabel  = new JLabel("—");
    private final JLabel hintLabel   = new JLabel("Выберите объект слева");

    public HudPanel(Game game) {
        this.game = game;

        setLayout(new BorderLayout());
        setBackground(new Color(25, 25, 35));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 80)));
        setPreferredSize(new Dimension(0, 40));

        // ==================== ДЕНЬГИ (слева) ====================
        moneyLabel.setForeground(new Color(255, 220, 100));
        moneyLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        moneyLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        add(moneyLabel, BorderLayout.WEST);

        // ==================== СТАТУС (по центру) ====================
        statusLabel.setForeground(new Color(200, 240, 200));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(statusLabel, BorderLayout.CENTER);

        // ==================== ПРАВАЯ ЗОНА: координаты + подсказка ====================
        // Правая часть HUD: сначала координаты, потом подсказки.
        // Пробуем использовать BoxLayout слева направо.
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.setOpaque(false);

        // Координаты под курсором
        coordLabel.setForeground(new Color(180, 220, 255));
        coordLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        coordLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        rightPanel.add(coordLabel);

        // Вертикальный разделитель
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setForeground(new Color(60, 60, 80));
        sep.setMaximumSize(new Dimension(1, 24));
        sep.setPreferredSize(new Dimension(1, 24));
        rightPanel.add(sep);

        // Подсказка
        hintLabel.setForeground(new Color(180, 180, 180));
        hintLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 16));
        rightPanel.add(hintLabel);

        add(rightPanel, BorderLayout.EAST);
    }

    // ==================== ОБНОВЛЕНИЕ ====================

    /** Обновляет деньги. Вызывается из GameLoop каждый кадр. */
    public void refresh() {
        moneyLabel.setText("💰 " + (int) game.getMoney());
    }

    /** Меняет статус по центру. */
    public void setStatus(String s, Color c) {
        statusLabel.setText(s);
        statusLabel.setForeground(c);
    }

    /** Меняет подсказку справа. */
    public void setHint(String s) {
        hintLabel.setText(s);
    }

    /**
     * Обновляет координаты клетки под курсором.
     * Передавай null, если курсор вне карты — покажет «—».
     */
    public void setCoord(Point cell) {
        if (cell == null) {
            coordLabel.setText("—");
        } else {
            coordLabel.setText("(" + cell.x + ", " + cell.y + ")");
        }
    }
}