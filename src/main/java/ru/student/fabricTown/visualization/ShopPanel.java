package ru.student.fabricTown.visualization;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.Building;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.map.Point;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Правая панель: список объектов игрока, продажа и управление связями
 * «фабрика ↔ магазин».
 *
 * Логика связей для магазина:
 *   - лимит фабрик задаётся ПО ТИПУ ТОВАРА (Shop.getMaxFactoriesPerType):
 *       S1x1 → 1 на тип, S1x2/S2x1 → 2 на тип, S2x2 → 3 на тип;
 *   - список фабрик в панели разбит по типам, у каждого типа свой счётчик.
 *
 * Логика связей для фабрики:
 *   - лимит магазинов задаётся суммарно (Factory.getMaxShops);
 *   - при добавлении магазина проверяется, что у него ещё есть место
 *     для фабрики ЭТОГО типа (Dispatcher.canBind).
 *
 * Дополнительно:
 *   - для фабрики показывается грузоподъёмность её машины
 *     (Factory.getCarryCapacity — 10/13/16/19 по размеру).
 */
public class ShopPanel extends JPanel {

    private final Game game;

    private final DefaultListModel<Building> model = new DefaultListModel<>();
    private final JList<Building> list = new JList<>(model);
    private final JLabel infoLabel = new JLabel();
    private final JButton sellButton = new JButton("💰 Продать (70%)");

    /** Динамический список связей выбранного объекта. */
    private final JPanel bindingsPanel = new JPanel();

    /** Кнопка «связать автоматически» — только для магазина без связей. */
    private final JButton autoBindButton = new JButton("🔗 Связать автоматически");

    private Building selected;

    // ==================== КОНСТРУКТОР ====================

    public ShopPanel(Game game) {
        this.game = game;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(280, 0));
        setBackground(new Color(35, 35, 45));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(60, 60, 80)));

        // Заголовок
        JLabel title = new JLabel("🏬 Мои объекты");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setBorder(new EmptyBorder(12, 12, 8, 12));
        add(title, BorderLayout.NORTH);

        // Список объектов
        list.setBackground(new Color(45, 45, 60));
        list.setForeground(Color.WHITE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        list.setCellRenderer(new BuildingCellRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selected = list.getSelectedValue();
                updateInfo();
            }
        });

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(null);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
        listScroll.setPreferredSize(new Dimension(0, 180));
        add(listScroll, BorderLayout.CENTER);

        // Нижний блок: инфо + связи + кнопки
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(new Color(35, 35, 45));
        bottom.setBorder(new EmptyBorder(8, 8, 8, 8));

        // infoLabel
        infoLabel.setForeground(new Color(200, 200, 200));
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(infoLabel);
        bottom.add(Box.createVerticalStrut(6));

        // bindingsPanel в скролле
        bindingsPanel.setLayout(new BoxLayout(bindingsPanel, BoxLayout.Y_AXIS));
        bindingsPanel.setBackground(new Color(35, 35, 45));
        bindingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane bindScroll = new JScrollPane(bindingsPanel);
        bindScroll.setBorder(null);
        bindScroll.getVerticalScrollBar().setUnitIncrement(16);
        bindScroll.setPreferredSize(new Dimension(0, 240));
        bindScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        bindScroll.getViewport().setBackground(new Color(35, 35, 45));
        bottom.add(bindScroll);
        bottom.add(Box.createVerticalStrut(6));

        // Кнопка «связать автоматически»
        autoBindButton.setBackground(new Color(60, 100, 140));
        autoBindButton.setForeground(Color.WHITE);
        autoBindButton.setFocusPainted(false);
        autoBindButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        autoBindButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoBindButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        autoBindButton.addActionListener(e -> onAutoBind());
        autoBindButton.setVisible(false);
        bottom.add(autoBindButton);
        bottom.add(Box.createVerticalStrut(6));

        // Кнопка «продать»
        sellButton.setBackground(new Color(120, 80, 40));
        sellButton.setForeground(Color.WHITE);
        sellButton.setFocusPainted(false);
        sellButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sellButton.setEnabled(false);
        sellButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sellButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        sellButton.addActionListener(e -> onSell());
        bottom.add(sellButton);

        add(bottom, BorderLayout.SOUTH);

        // Периодическое обновление списка объектов и связей
        Timer t = new Timer(true);
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(ShopPanel.this::refresh);
            }
        }, 0, 500);
    }

    // ==================== ОБНОВЛЕНИЕ СПИСКА ====================

    private void refresh() {
        Building prev = selected;
        model.clear();

        for (Factory f : game.getFactories()) model.addElement(f);
        for (Shop s : game.getShops()) model.addElement(s);
        for (House h : game.getHouses()) model.addElement(h);

        // Восстанавливаем выделение, если объект ещё существует
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i) == prev) {
                list.setSelectedIndex(i);
                return;
            }
        }
        // Объект удалён — сбрасываем
        if (prev != null && !model.contains(prev)) {
            selected = null;
            updateInfo();
        }
    }

    // ==================== ИНФО О ВЫБРАННОМ ====================

    private void updateInfo() {
        // Обновляем панель связей ВСЕГДА — даже если selected == null
        rebuildBindingsPanel();

        if (selected == null) {
            infoLabel.setText("<html><i>Ничего не выбрано</i></html>");
            sellButton.setEnabled(false);
            sellButton.setText("💰 Продать");
            autoBindButton.setVisible(false);
            return;
        }

        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(selected.getTypeName()).append("</b><br>");
        sb.append("Позиция: ").append(selected.getOrigin()).append("<br>");

        if (selected instanceof Factory f) {
            sb.append("Склад: ").append(f.getStorageCount())
                    .append("/").append(f.getMaxStorage()).append("<br>");
            int bound = game.getDispatcher().getFactoriesForShopCount(f);
            sb.append("Обслуживает: ").append(bound)
                    .append("/").append(f.getMaxShops()).append(" магазинов<br>");
            sb.append("Вместимость машины: ").append(f.getCarryCapacity())
                    .append(" шт.");
        } else if (selected instanceof Shop s) {
            sb.append("Товаров: ").append(s.totalStored())
                    .append("/").append(s.getCapacity()).append("<br>");
            int bound = game.getDispatcher().getFactoriesFor(s).size();
            int maxTotal = s.getMaxFactoriesPerType() * Product.values().length;
            sb.append("Фабрик привязано: ").append(bound)
                    .append("/").append(maxTotal)
                    .append(" (лимит по типу: ").append(s.getMaxFactoriesPerType())
                    .append(")<br>");
            sb.append("Продано на: ").append((int) s.getMoneyEarned()).append(" 💰");
        } else if (selected instanceof House h) {
            sb.append("Жителей: ").append(h.getResidents());
        }
        sb.append("</html>");
        infoLabel.setText(sb.toString());

        sellButton.setText("💰 Продать за " + (int) selected.getSellPrice());
        sellButton.setEnabled(true);
    }

    // ==================== ПАНЕЛЬ СВЯЗЕЙ ====================

    private void rebuildBindingsPanel() {
        bindingsPanel.removeAll();
        autoBindButton.setVisible(false);

        if (selected instanceof Shop shop) {
            buildShopBindings(shop);
        } else if (selected instanceof Factory factory) {
            buildFactoryBindings(factory);
        }

        bindingsPanel.revalidate();
        bindingsPanel.repaint();
    }

    // ---------------- Связи МАГАЗИНА: разбито по типам ----------------

    private void buildShopBindings(Shop shop) {
        List<Factory> bound = game.getDispatcher().getFactoriesFor(shop);

        JLabel hdr = new JLabel("Фабрики для магазина (по типам):");
        hdr.setForeground(Color.WHITE);
        hdr.setFont(new Font("SansSerif", Font.BOLD, 11));
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        bindingsPanel.add(hdr);
        bindingsPanel.add(Box.createVerticalStrut(4));

        int limitPerType = shop.getMaxFactoriesPerType();

        // Проходим по каждому типу товара
        for (Product type : Product.values()) {
            List<Factory> boundOfType = new ArrayList<>();
            for (Factory f : bound) {
                if (f.getProduct() == type) boundOfType.add(f);
            }

            int used = boundOfType.size();
            boolean full = used >= limitPerType;

            JLabel typeHdr = new JLabel(type.title + "  " + used + "/" + limitPerType);
            typeHdr.setForeground(full
                    ? new Color(255, 180, 100)
                    : new Color(180, 220, 255));
            typeHdr.setFont(new Font("SansSerif", Font.BOLD, 11));
            typeHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
            typeHdr.setBorder(new EmptyBorder(6, 0, 2, 0));
            bindingsPanel.add(typeHdr);

            // Уже привязанные фабрики этого типа
            for (Factory f : boundOfType) {
                JPanel row = createBindingRow(
                        "  ✔ " + f.getOrigin(),
                        new Color(180, 255, 180),
                        "✖",
                        new Color(140, 60, 60),
                        true,
                        () -> doUnbind(f, shop));
                bindingsPanel.add(row);
                bindingsPanel.add(Box.createVerticalStrut(2));
            }

            // Доступные фабрики этого типа
            boolean canAdd = !full;
            for (Factory f : game.getFactories()) {
                if (f.getProduct() != type) continue;
                if (boundOfType.contains(f)) continue;

                boolean factoryBusy =
                        game.getDispatcher().getFactoriesForShopCount(f)
                                >= f.getMaxShops();
                boolean enabled = canAdd && !factoryBusy;

                String label = "  " + f.getOrigin();
                if (factoryBusy) label += "  (занята)";

                JPanel row = createBindingRow(
                        label,
                        new Color(200, 200, 200),
                        "+",
                        new Color(60, 120, 60),
                        enabled,
                        () -> doBind(f, shop));
                bindingsPanel.add(row);
                bindingsPanel.add(Box.createVerticalStrut(2));
            }
        }

        if (bound.isEmpty()) {
            autoBindButton.setVisible(true);
        }
    }

    // ---------------- Связи ФАБРИКИ: список магазинов ----------------

    private void buildFactoryBindings(Factory factory) {
        List<Shop> bound = game.getDispatcher().getShopsFor(factory);

        JLabel hdr = new JLabel("Магазины для этой фабрики (" +
                bound.size() + "/" + factory.getMaxShops() + "):");
        hdr.setForeground(Color.WHITE);
        hdr.setFont(new Font("SansSerif", Font.BOLD, 11));
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        bindingsPanel.add(hdr);
        bindingsPanel.add(Box.createVerticalStrut(4));

        // Уже привязанные
        for (Shop s : bound) {
            JPanel row = createBindingRow(
                    s.getOrigin().toString(),
                    new Color(180, 255, 180),
                    "✖",
                    new Color(140, 60, 60),
                    true,
                    () -> doUnbind(factory, s));
            bindingsPanel.add(row);
            bindingsPanel.add(Box.createVerticalStrut(2));
        }

        // Доступные
        boolean canAdd = bound.size() < factory.getMaxShops();
        for (Shop s : game.getShops()) {
            if (bound.contains(s)) continue;

            boolean shopFull = !game.getDispatcher().canBind(factory, s);
            boolean enabled = canAdd && !shopFull;

            String label = s.getOrigin().toString();
            if (shopFull) {
                int used = game.getDispatcher()
                        .countFactoriesOfType(s, factory.getProduct());
                int lim = s.getMaxFactoriesPerType();
                label += "  (" + used + "/" + lim + " "
                        + factory.getProduct().title + ")";
            }

            JPanel row = createBindingRow(
                    label,
                    new Color(200, 200, 200),
                    "+",
                    new Color(60, 120, 60),
                    enabled,
                    () -> doBind(factory, s));
            bindingsPanel.add(row);
            bindingsPanel.add(Box.createVerticalStrut(2));
        }
    }

    // ---------------- Вспомогательная строка ----------------

    private JPanel createBindingRow(String label, Color labelColor,
                                    String buttonText, Color buttonBg,
                                    boolean enabled, Runnable onClick) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(35, 35, 45));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setPreferredSize(new Dimension(240, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(labelColor);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        row.add(lbl, BorderLayout.CENTER);

        JButton btn = new JButton(buttonText);
        btn.setBackground(buttonBg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setMargin(new Insets(1, 8, 1, 8));
        btn.setEnabled(enabled);
        btn.addActionListener(e -> onClick.run());
        row.add(btn, BorderLayout.EAST);

        return row;
    }

    // ==================== ДЕЙСТВИЯ ====================

    private void doBind(Factory f, Shop s) {
        String err = game.bindManual(f, s);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err,
                    "Не удалось связать", JOptionPane.WARNING_MESSAGE);
        } else {
            updateInfo();
        }
    }

    private void doUnbind(Factory f, Shop s) {
        String err = game.unbindManual(f, s);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err,
                    "Не удалось разорвать", JOptionPane.WARNING_MESSAGE);
        } else {
            updateInfo();
        }
    }

    private void onAutoBind() {
        if (!(selected instanceof Shop shop)) return;
        String err = game.autoBindShopManual(shop);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err,
                    "Автопривязка не удалась", JOptionPane.WARNING_MESSAGE);
        } else {
            updateInfo();
        }
    }

    private void onSell() {
        if (selected == null) return;
        Point p = selected.getOrigin();
        String err = game.trySell(p);
        if (err != null) {
            JOptionPane.showMessageDialog(this, err,
                    "Не удалось продать", JOptionPane.WARNING_MESSAGE);
        } else {
            selected = null;
            refresh();
            updateInfo();
        }
    }

    // ==================== РЕНДЕРЕР СПИСКА ====================

    private static class BuildingCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Building b) {
                String icon = "🏢";
                if (b instanceof Factory) icon = "🏭";
                else if (b instanceof Shop) icon = "🏪";
                else if (b instanceof House) icon = "🏠";
                setText(icon + " " + b.getTypeName() + " " + b.getOrigin());
            }
            setBackground(isSelected ? new Color(70, 100, 150) : new Color(45, 45, 60));
            setForeground(Color.WHITE);
            return this;
        }
    }
    // ==================== ВНЕШНИЙ ВЫБОР ОБЪЕКТА ====================

    /**
     * Выбрать здание в списке программно (например, при двойном клике по карте).
     * Если здание не найдено в списке — сбрасывает выбор.
     */
    public void selectBuilding(Building building) {
        if (building == null) {
            list.clearSelection();
            selected = null;
            updateInfo();
            return;
        }

        for (int i = 0; i < model.size(); i++) {
            if (model.get(i) == building) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }

        // Здание не найдено — сбрасываем
        list.clearSelection();
        selected = null;
        updateInfo();
    }
}