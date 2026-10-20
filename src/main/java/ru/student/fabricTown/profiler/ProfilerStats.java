package ru.student.fabricTown.profiler;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Агрегаты по одному тегу.
 *
 * Потокобезопасно: LongAdder для счётчиков, AtomicLong для min/max.
 * p95 не хранится (гистограмма — отдельная история, для неё нужен
 * HdrHistogram или ручной массив). Здесь — среднее, min, max.
 */
public final class ProfilerStats {

    public final String tag;

    private final LongAdder calls = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong(0);

    ProfilerStats(String tag) { this.tag = tag; }

    void add(long nanos) {
        calls.increment();
        totalNanos.add(nanos);
        if (nanos < minNanos.get()) minNanos.set(nanos);
        if (nanos > maxNanos.get()) maxNanos.set(nanos);
    }

    public long calls()      { return calls.sum(); }
    public long totalNanos() { return totalNanos.sum(); }
    public long minNanos()   { long m = minNanos.get(); return m == Long.MAX_VALUE ? 0 : m; }
    public long maxNanos()   { return maxNanos.get(); }

    public double avgNanos() {
        long c = calls();
        return c == 0 ? 0 : totalNanos() / (double) c;
    }

    /** Снимок значений (для UI). */
    public ProfilerSnapshot.Row toRow(long windowNs) {
        long c = calls();
        double callsPerSec = windowNs <= 0 ? 0 : c * 1_000_000_000.0 / windowNs;
        double totalMs = totalNanos() / 1_000_000.0;
        double avgMs = avgNanos() / 1_000_000.0;
        double minMs = minNanos() / 1_000_000.0;
        double maxMs = maxNanos() / 1_000_000.0;
        return new ProfilerSnapshot.Row(tag, c, callsPerSec,
                totalMs, avgMs, minMs, maxMs);
    }
}