package ru.student.fabricTown.visualization;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.logic.Blueprint;
import ru.student.fabricTown.logic.Catalog;
import ru.student.fabricTown.logic.CatalogGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Левая панель: категории объектов с подкатегориями */
public class BuildPanel extends JPanel {

    private final Consumer<Blueprint> onSelect;
    private final Runnable onCancel;
    private final Runnable onDemolish;
    private final Runnable onToggleDelivery;

    private final JPanel listContainer = new JPanel();

    /** Ссылка на кнопку доставки — для внешнего обновления. */
    private JButton deliveryButton;

    public BuildPanel(Consumer<Blueprint> onSelect,
                      Runnable onCancel,
                      Runnable onDemolish,
                      Runnable onToggleDelivery) {
        this.onSelect = onSelect;
        this.onCancel = onCancel;
        this.onDemolish = onDemolish;
        this.onToggleDelivery = onToggleDelivery;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, 0));
        setBackground(new Color(35, 35, 45));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 80)));

        // Заголовок
        JLabel title = new JLabel("🔨 Строительство");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setBorder(new EmptyBorder(12, 12, 8, 12));
        add(title, BorderLayout.NORTH);

        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(new Color(35, 35, 45));

        // ==================== ПРОСТЫЕ КАТЕГОРИИ (без подгрупп) ====================
        addSimpleCategory("🛣 Дороги", Catalog.roads());
        addSimpleCategory("🔀 Перекрёстки", Catalog.intersections());

        // ==================== ФАБРИКИ С ПОДГРУППАМИ ПО ТОВАРУ ====================
        addGroupedCategory("🏭 Фабрики", Catalog.factoriesGrouped());

        // ==================== ПРОСТЫЕ КАТЕГОРИИ ====================
        addSimpleCategory("🍫 Переработчики", Catalog.processors());
        addSimpleCategory("🏪 Магазины", Catalog.shops());
        addSimpleCategory("🏠 Дома", Catalog.houses());
        addSimpleCategory("🗺 Расширение", Catalog.services());

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(new Color(35, 35, 45));
        add(scroll, BorderLayout.CENTER);

        // ==================== НИЖНИЕ КНОПКИ ====================
        JPanel bottom = new JPanel(new GridLayout(3, 1, 4, 4));
        bottom.setBackground(new Color(35, 35, 45));
        bottom.setBorder(new EmptyBorder(8, 8, 8, 8));

        deliveryButton = makeButton("⏸ Отозвать машины", new Color(60, 80, 110));
        deliveryButton.addActionListener(e -> {
            if (onToggleDelivery != null) onToggleDelivery.run();
        });
        bottom.add(deliveryButton);

        JButton cancelBtn = makeButton("❌ Отмена (Esc)", new Color(90, 60, 60));
        cancelBtn.addActionListener(e -> onCancel.run());
        bottom.add(cancelBtn);

        JButton demolishBtn = makeButton("🧨 Снести (D)", new Color(120, 80, 40));
        demolishBtn.addActionListener(e -> onDemolish.run());
        bottom.add(demolishBtn);

        add(bottom, BorderLayout.SOUTH);
    }

    // ==================== ПРОСТАЯ КАТЕГОРИЯ ====================

    private void addSimpleCategory(String title, List<Blueprint> items) {
        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(new Color(45, 45, 60));
        itemsPanel.setBorder(new EmptyBorder(4, 6, 4, 6));
        itemsPanel.setVisible(false);

        for (Blueprint bp : items) {
            itemsPanel.add(makeBlueprintButton(bp, 0));
            itemsPanel.add(Box.createVerticalStrut(2));
        }

        JPanel header = makeCategoryHeader(title, 0);
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                itemsPanel.setVisible(!itemsPanel.isVisible());
                listContainer.revalidate();
            }
        });

        listContainer.add(header);
        listContainer.add(itemsPanel);
        listContainer.add(Box.createVerticalStrut(4));
    }

    // ==================== КАТЕГОРИЯ С ПОДГРУППАМИ ====================

    private void addGroupedCategory(String title, List<CatalogGroup> groups) {
        // Контейнер для всего содержимого категории (подгрупп)
        JPanel groupsPanel = new JPanel();
        groupsPanel.setLayout(new BoxLayout(groupsPanel, BoxLayout.Y_AXIS));
        groupsPanel.setBackground(new Color(40, 40, 55));
        groupsPanel.setVisible(false);

        for (CatalogGroup group : groups) {
            if (group.isEmpty()) continue;

            // Контейнер элементов подгруппы
            JPanel groupItems = new JPanel();
            groupItems.setLayout(new BoxLayout(groupItems, BoxLayout.Y_AXIS));
            groupItems.setBackground(new Color(50, 50, 65));
            groupItems.setBorder(new EmptyBorder(2, 6, 4, 6));
            groupItems.setVisible(false);

            for (Blueprint bp : group.items) {
                groupItems.add(makeBlueprintButton(bp, 1));
                groupItems.add(Box.createVerticalStrut(2));
            }

            // Заголовок подгруппы (с отступом)
            JPanel groupHeader = makeCategoryHeader(group.title, 1);
            groupHeader.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    groupItems.setVisible(!groupItems.isVisible());
                    listContainer.revalidate();
                }
            });

            groupsPanel.add(groupHeader);
            groupsPanel.add(groupItems);
        }

        // Заголовок самой категории
        JPanel header = makeCategoryHeader(title, 0);
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                groupsPanel.setVisible(!groupsPanel.isVisible());
                listContainer.revalidate();
            }
        });

        listContainer.add(header);
        listContainer.add(groupsPanel);
        listContainer.add(Box.createVerticalStrut(4));
    }

    // ==================== ОБЩИЕ ЭЛЕМЕНТЫ ====================

    /**
     * Создать заголовок категории/подгруппы.
     *
     * @param level 0 — верхний уровень (категория),
     *              1 — подгруппа (с отступом и другим фоном).
     */
    private JPanel makeCategoryHeader(String title, int level) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(level == 0
                ? new Color(50, 50, 65)
                : new Color(60, 60, 80));
        header.setBorder(level == 0
                ? new EmptyBorder(6, 10, 6, 10)
                : new EmptyBorder(4, 24, 4, 10));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, level == 0 ? 28 : 24));

        // Стрелка + название
        JLabel lbl = new JLabel((level == 0 ? "▸ " : "· ") + title);
        lbl.setForeground(level == 0 ? Color.WHITE : new Color(220, 220, 240));
        lbl.setFont(new Font("SansSerif",
                level == 0 ? Font.BOLD : Font.PLAIN,
                level == 0 ? 13 : 12));
        header.add(lbl, BorderLayout.WEST);

        return header;
    }

    /**
     * Создать кнопку blueprint'а.
     *
     * @param level 0 — обычная (в простой категории),
     *              1 — вложенная (в подгруппе, с отступом).
     */
    private JButton makeBlueprintButton(Blueprint bp, int level) {
        String text = bp.displayName + "  (" + (int) bp.cost + ")";
        JButton b = makeButton(text,
                level == 0 ? new Color(60, 60, 80) : new Color(70, 70, 90));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        if (level > 0) {
            b.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 8));
        }
        b.addActionListener(e -> onSelect.accept(bp));
        return b;
    }

    private JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ==================== ВНЕШНЕЕ УПРАВЛЕНИЕ ====================

    public void setDeliveryButtonState(boolean paused) {
        if (deliveryButton == null) return;
        deliveryButton.setText(paused
                ? "▶ Возобновить доставку"
                : "⏸ Отозвать машины");
        deliveryButton.setBackground(paused
                ? new Color(60, 100, 60)
                : new Color(60, 80, 110));
    }
}