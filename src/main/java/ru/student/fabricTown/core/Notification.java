package ru.student.fabricTown.core;

/**
 * Одно уведомление для показа игроку.
 *
 * Типы:
 *   INFO     — обычная подсказка (синий)
 *   SUCCESS  — успешное действие (зелёный)
 *   WARNING  — предупреждение (жёлтый)
 *   ERROR    — ошибка (красный)
 *
 * У каждого уведомления есть:
 *   - текст,
 *   - уровень важности,
 *   - время жизни (в игровых секундах),
 *   - время создания,
 *   - уникальный id (для плавного появления/исчезновения).
 */
public class Notification {

    public enum Level { INFO, SUCCESS, WARNING, ERROR }

    private static long nextId = 0;

    public final long id;
    public final String text;
    public final Level level;
    public final double createdAt;
    public final double lifeTime;   // секунд
    public final String tag;        // опциональный тег для группировки/дедупликации

    // Анимация появления/исчезновения
    private double appearProgress = 0;  // 0..1
    private boolean fadingOut = false;

    public Notification(String text, Level level, double createdAt,
                        double lifeTime, String tag) {
        this.id = nextId++;
        this.text = text == null ? "" : text;
        this.level = level == null ? Level.INFO : level;
        this.createdAt = createdAt;
        this.lifeTime = lifeTime;
        this.tag = tag == null ? "" : tag;
    }

    public double getAge(double now) { return now - createdAt; }

    public boolean isExpired(double now) {
        return getAge(now) >= lifeTime;
    }

    /** 0..1 — прогресс появления (первые 0.25 сек). */
    public double getAppearProgress() { return appearProgress; }
    public void setAppearProgress(double p) { this.appearProgress = p; }

    /** 0..1 — прогресс исчезновения (последние 0.4 сек). */
    public double getFadeOutAlpha(double now) {
        double age = getAge(now);
        double timeLeft = lifeTime - age;
        if (timeLeft <= 0.4) {
            return Math.max(0, timeLeft / 0.4);
        }
        return 1.0;
    }

    public boolean isFadingOut() { return fadingOut; }
    public void setFadingOut(boolean b) { this.fadingOut = b; }

    @Override
    public String toString() {
        return "[" + level + "] " + text;
    }
}