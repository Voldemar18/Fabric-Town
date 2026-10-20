package ru.student.fabricTown.profiler;

/**
 * Замер одного вызова. Закрывайте через try-with-resources.
 *
 *   try (var s = Profiler.start("render.cars")) {
 *       for (var c : cars) Renderer.drawCar(...);
 *   }
 *
 * ВАЖНО: NOOP-экземпляр для disabled-режима — не создаёт объект.
 */
public final class ProfilerSection implements AutoCloseable {

    /** Синглтон для disabled-режима: close() ничего не делает. */
    public static final ProfilerSection NOOP = new ProfilerSection(null);

    private final String tag;
    private final long startNanos;

    ProfilerSection(String tag) {
        this.tag = tag;
        this.startNanos = System.nanoTime();
    }

    @Override
    public void close() {
        if (tag == null) return;
        long elapsed = System.nanoTime() - startNanos;
        Profiler.stats(tag).add(elapsed);
    }
}