package ru.student.fabricTown.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Очередь событий игрового мира (см. ТЗ, часть 4.2, п.9).
 *
 * Внутри — PriorityQueue, упорядоченная по времени и sequence.
 *
 * Основной сценарий:
 *   EventQueue eq = new EventQueue();
 *   eq.schedule(new Event(now + 1.0, ..., "factory.tick", factory, () -> factory.tick(1.0)));
 *   ...
 *   eq.update(now);   // обработать все события с time <= now
 *
 * В связке с GameLoop:
 *   каждый кадр dt → now += dt → eq.update(now).
 */
public class EventQueue {

    /** Счётчик sequence — глобальный для всех событий этой очереди. */
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    /** Основная куча событий. */
    private final PriorityQueue<Event> queue =
            new PriorityQueue<>(Comparator.naturalOrder());

    /** Сколько событий отменено/обработано — для статистики. */
    private long processedCount = 0;

    /** Сколько секунд суммарно обработано (последнее now). */
    private double currentTime = 0;

    // ==================== ПЛАНИРОВАНИЕ ====================

    /**
     * Запланировать разовое событие.
     * Возвращает сам Event — можно позже вызвать cancel() или asRepeating().
     */
    public Event schedule(double time, String tag, Object owner, Runnable action) {
        Event e = new Event(time, sequenceCounter.incrementAndGet(), tag, owner, action);
        queue.add(e);
        return e;
    }

    /** Запланировать событие через delay секунд от текущего времени очереди. */
    public Event scheduleAfter(double delay, String tag, Object owner, Runnable action) {
        return schedule(currentTime + delay, tag, owner, action);
    }

    /** Периодическое событие: сработает в time, потом каждые period секунд. */
    public Event scheduleRepeating(double time, double period,
                                   String tag, Object owner, Runnable action) {
        Event e = new Event(time, sequenceCounter.incrementAndGet(), tag, owner, action);
        e.asRepeating(period);
        queue.add(e);
        return e;
    }

    /** Отменить событие. */
    public void cancel(Event e) {
        if (e != null) e.cancel();
    }

    /** Отменить все события, принадлежащие заданному владельцу. */
    public void cancelOwner(Object owner) {
        if (owner == null) return;
        for (Event e : queue) {
            if (e.getOwner() == owner) e.cancel();
        }
    }

    // ==================== ОБРАБОТКА ====================

    /**
     * Продвинуть время до now и обработать все события с time <= now.
     * Возвращает количество реально выполненных событий.
     *
     * Защита от бесконечного цикла:
     *   - события, «провалившиеся» в прошлое из-за большого dt,
     *     не пересчитываются больше одного раза на кадр,
     *   - периодические — перепланируются со сдвигом в будущее.
     */
    public int update(double now) {
        this.currentTime = Math.max(currentTime, now);
        int count = 0;

        while (!queue.isEmpty() && queue.peek().getTime() <= currentTime) {
            Event e = queue.poll();

            if (e.isCancelled()) continue;

            // Выполнить действие
            try {
                e.execute();
                count++;
                processedCount++;
            } catch (RuntimeException ex) {
                // Событие не должно убивать игровой цикл
                System.err.println("[EventQueue] Ошибка в событии " + e.getTag()
                        + ": " + ex.getMessage());
                ex.printStackTrace();
            }

            // Перепланировать периодическое
            if (e.isRepeating() && !e.isCancelled()) {
                e.reschedule(currentTime);
                queue.add(e);
            }
        }

        return count;
    }

    // ==================== УТИЛИТЫ ====================

    /** Пустая ли очередь (без учёта отменённых). */
    public boolean isEmpty() {
        for (Event e : queue) {
            if (!e.isCancelled()) return false;
        }
        return true;
    }

    /** Сколько событий в очереди (включая отменённые). */
    public int size() {
        return queue.size();
    }

    /** Сколько событий всего выполнено с момента создания очереди. */
    public long getProcessedCount() {
        return processedCount;
    }

    /** Текущее время очереди. */
    public double getCurrentTime() {
        return currentTime;
    }

    /** Время ближайшего события или -1, если очередь пуста. */
    public double peekNextTime() {
        while (!queue.isEmpty() && queue.peek().isCancelled()) {
            queue.poll();
        }
        return queue.isEmpty() ? -1 : queue.peek().getTime();
    }

    /** Полный сброс очереди. */
    public void clear() {
        queue.clear();
    }

    /**
     * Список ближайших событий (для отладки/HUD).
     * Не более limit штук, отсортированных по времени.
     */
    public List<Event> peekUpcoming(int limit) {
        List<Event> live = new ArrayList<>();
        for (Event e : queue) {
            if (!e.isCancelled()) live.add(e);
        }
        live.sort(Comparator.naturalOrder());
        if (live.size() > limit) {
            return live.subList(0, limit);
        }
        return live;
    }

    @Override
    public String toString() {
        return "EventQueue{size=" + queue.size()
                + ", processed=" + processedCount
                + ", now=" + String.format("%.2f", currentTime) + "}";
    }
}