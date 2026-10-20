package ru.student.fabricTown.profiler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Лёгкий профайлер без внешних зависимостей.
 *
 * Использование:
 *   try (var s = Profiler.start("game.tick")) { ... }
 *   Profiler.count("car.tryStart");          // счётчик без таймера
 *   Profiler.snapshot();                     // снимок для UI
 *
 * ВАЖНО: start/stop — быстрые (~30-50 нс). Не бойтесь вставлять
 * в горячие циклы, но не в самые внутренние (там счётчик).
 */
public final class Profiler {

    private Profiler() {}

    private static final ConcurrentHashMap<String, ProfilerStats> STATS =
            new ConcurrentHashMap<>();

    /** Глобальный флаг: если false — start() возвращает no-op. */
    private static volatile boolean enabled = true;

    /** Накопитель общего времени между снимками (для вызовов/сек). */
    private static volatile long windowStartNanos = System.nanoTime();

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean e) { enabled = e; }

    /** Начать замер. Возвращает AutoCloseable — используйте try-with-resources. */
    public static ProfilerSection start(String tag) {
        if (!enabled) return ProfilerSection.NOOP;
        return new ProfilerSection(tag);
    }

    /** Зарегистрировать вызов без замера времени (счётчик). */
    public static void count(String tag) {
        if (!enabled) return;
        stats(tag).add(0L);
    }

    /** Зарегистрировать вызов с готовым временем (нс). */
    public static void record(String tag, long nanos) {
        if (!enabled) return;
        stats(tag).add(nanos);
    }

    static ProfilerStats stats(String tag) {
        return STATS.computeIfAbsent(tag, t -> new ProfilerStats(t));
    }

    /**
     * Снимок для UI. Считает вызовы/сек от прошлого снимка,
     * сбрасывает windowStartNanos.
     */
    public static ProfilerSnapshot snapshot() {
        long now = System.nanoTime();
        long windowNs = now - windowStartNanos;
        windowStartNanos = now;
        return ProfilerSnapshot.build(STATS, windowNs);
    }

    /** Полный сброс (между сессиями). */
    public static void reset() {
        STATS.clear();
        windowStartNanos = System.nanoTime();
    }

    /** Список всех тегов (для отладки). */
    public static java.util.Set<String> tags() {
        return java.util.Set.copyOf(STATS.keySet());
    }
}