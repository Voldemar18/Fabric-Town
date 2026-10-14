package ru.student.fabricTown.core;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Игровой цикл на Swing Timer.
 *
 * Каждый тик:
 *   1) считает dt (секунды с прошлого кадра, с защитой от больших пауз),
 *   2) вызывает {@link Game#tick(double)} — там двигаются производство,
 *      машины, люди и уведомления,
 *   3) вызывает пользовательский колбэк (для рендера камеры и UI).
 */
public class GameLoop {
    private final Game game;
    private final Timer timer;
    private long lastTime = System.nanoTime();

    public GameLoop(Game game, Consumer<Double> onTick, int fps) {
        this.game = game;
        int delay = 1000 / fps;
        this.timer = new Timer(delay, e -> {
            long now = System.nanoTime();
            double dt = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;
            if (dt > 0.1) dt = 0.1;   // защита от больших пауз
            game.tick(dt);
            onTick.accept(dt);
        });
    }

    public void start() {
        lastTime = System.nanoTime();
        timer.start();
    }

    public void stop() {
        timer.stop();
    }
}