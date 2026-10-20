package ru.student.fabricTown.visualization;

import ru.student.fabricTown.core.Game;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameFrame extends JFrame {
    public GameFrame() {
        super("Fabric Town");
        Game game = new Game();
        GamePanel panel = new GamePanel(game);
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);

        // Корректно закрыть дочернее окно профайлера
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.hideProfilerWindow();
            }
        });
    }
}