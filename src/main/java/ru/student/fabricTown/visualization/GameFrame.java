package ru.student.fabricTown.visualization;

import ru.student.fabricTown.core.Game;

import javax.swing.*;

public class GameFrame extends JFrame {
    public GameFrame() {
        super("Fabric Town");
        Game game = new Game();
        GamePanel panel = new GamePanel(game);
        setContentPane(panel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
    }
}