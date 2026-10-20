package ru.student.fabricTown.profiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Иммутабельный снимок для UI. UI читает его в EDT — никаких блокировок.
 *
 * Строки отсортированы по totalMs (самые затратные сверху).
 */
public final class ProfilerSnapshot {

    public final long timestampNanos;
    public final long windowNanos;
    public final List<Row> rows;

    private ProfilerSnapshot(long timestampNanos, long windowNanos, List<Row> rows) {
        this.timestampNanos = timestampNanos;
        this.windowNanos = windowNanos;
        this.rows = rows;
    }

    static ProfilerSnapshot build(Map<String, ProfilerStats> stats, long windowNs) {
        List<Row> rows = new ArrayList<>(stats.size());
        for (ProfilerStats s : stats.values()) {
            rows.add(s.toRow(windowNs));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.totalMs).reversed());
        return new ProfilerSnapshot(System.nanoTime(), windowNs, rows);
    }

    public Row find(String tag) {
        for (Row r : rows) if (r.tag.equals(tag)) return r;
        return null;
    }

    /** Одна строка: всё, что нужно UI. */
    public static final class Row {
        public final String tag;
        public final long calls;
        public final double callsPerSec;
        public final double totalMs;
        public final double avgMs;
        public final double minMs;
        public final double maxMs;

        Row(String tag, long calls, double callsPerSec,
            double totalMs, double avgMs, double minMs, double maxMs) {
            this.tag = tag;
            this.calls = calls;
            this.callsPerSec = callsPerSec;
            this.totalMs = totalMs;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
        }

        @Override
        public String toString() {
            return String.format("%-30s calls=%9d  /s=%8.1f  total=%9.2fms  avg=%8.4fms  min=%8.4f  max=%8.2f",
                    tag, calls, callsPerSec, totalMs, avgMs, minMs, maxMs);
        }
    }
}