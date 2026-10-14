package ru.student.fabricTown.core;

/**
 * Событие игрового мира.
 *
 * Каждое событие имеет:
 *   - время в игровых секундах, когда оно должно сработать,
 *   - уникальный порядковый номер (для стабильной сортировки при равных временах),
 *   - действие (Runnable), которое выполнится при обработке,
 *   - необязательный тег для отладки/фильтрации.
 *
 * События могут быть:
 *   - разовыми (по умолчанию),
 *   - периодическими (см. {@link #isRepeating()} и {@link #reschedule(double)}).
 */
public class Event implements Comparable<Event> {

    /** Время срабатывания в игровых секундах от старта. */
    private double time;

    /** Уникальный номер (автоинкремент). Нужен для FIFO при равных time. */
    private final long sequence;

    /** Что делать при срабатывании. Может быть null — тогда просто метка. */
    private final Runnable action;

    /** Отладочный тег: "factory.tick", "car.arrive", "person.exit" и т.п. */
    private final String tag;

    /** Если > 0 — событие повторяется с этим интервалом. */
    private double period = 0;

    /** Ссылка на «владельца» — фабрика, машина, человек. Для отмены. */
    private final Object owner;

    /** Флаг: событие отменено (не будет обработано). */
    private boolean cancelled = false;

    // ==================== КОНСТРУКТОРЫ ====================

    public Event(double time, long sequence, String tag, Object owner, Runnable action) {
        this.time = time;
        this.sequence = sequence;
        this.tag = tag == null ? "" : tag;
        this.owner = owner;
        this.action = action;
    }

    /** Периодическое событие: повторяется каждые period секунд. */
    public Event asRepeating(double period) {
        this.period = period;
        return this;
    }

    // ==================== ГЕТТЕРЫ ====================

    public double getTime()       { return time; }
    public long getSequence()     { return sequence; }
    public String getTag()        { return tag; }
    public Object getOwner()      { return owner; }
    public boolean isRepeating()  { return period > 0; }
    public double getPeriod()     { return period; }
    public boolean isCancelled()  { return cancelled; }

    // ==================== УПРАВЛЕНИЕ ====================

    /**
     * Выполнить действие события. Если событие отменено — ничего не делает.
     * Возвращает true, если действие выполнено.
     */
    public boolean execute() {
        if (cancelled) return false;
        if (action != null) action.run();
        return true;
    }

    /**
     * Сдвинуть время периодического события на один период вперёд.
     * Если событие разовое — не делает ничего.
     */
    public void reschedule(double now) {
        if (period <= 0) return;
        // Сдвигаем на следующую «правильную» точку, чтобы не накапливать дрейф
        do {
            time += period;
        } while (time <= now);
    }

    public void cancel() {
        this.cancelled = true;
    }

    // ==================== СРАВНЕНИЕ ====================

    /**
     * Сортировка по времени, при равенстве — по sequence (FIFO).
     * Это даёт детерминированный порядок обработки.
     */
    @Override
    public int compareTo(Event other) {
        int c = Double.compare(this.time, other.time);
        if (c != 0) return c;
        return Long.compare(this.sequence, other.sequence);
    }

    @Override
    public String toString() {
        return String.format("Event[t=%.3f, seq=%d, tag=%s, rep=%s]",
                time, sequence, tag, period > 0 ? String.format("%.2f", period) : "no");
    }
}