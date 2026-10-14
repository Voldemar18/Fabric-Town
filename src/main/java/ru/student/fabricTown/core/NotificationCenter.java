package ru.student.fabricTown.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Центр уведомлений.
 *
 * Хранит стек активных уведомлений, следит за временем жизни,
 * поддерживает дедупликацию по тегу (чтобы одно и то же сообщение
 * не спамилось каждый кадр).
 *
 * Использование:
 *   notif.info("Построена фабрика");
 *   notif.warn("В магазине нет курицы");
 *   notif.error("Нет пути от фабрики к магазину");
 *   notif.success("Магазин расширен");
 *
 * В GameLoop каждый тик вызывается {@link #update(double)}.
 */
public class NotificationCenter {

    /** Максимум уведомлений на экране одновременно. */
    public static final int MAX_VISIBLE = 5;

    /** Время по умолчанию (сек). */
    public static final double DEFAULT_LIFE = 4.0;

    private final List<Notification> active = new ArrayList<>();

    /**
     * Дедупликация: если по этому тегу уже висит уведомление,
     * не создаём новое, а обновляем существующее (продлеваем жизнь).
     * Ключ — tag, значение — само уведомление.
     */
    private final Map<String, Notification> byTag = new HashMap<>();

    // ==================== API ПУБЛИКАЦИИ ====================

    public Notification info(String text)    { return push(text, Notification.Level.INFO, null, DEFAULT_LIFE); }
    public Notification success(String text) { return push(text, Notification.Level.SUCCESS, null, DEFAULT_LIFE); }
    public Notification warn(String text)    { return push(text, Notification.Level.WARNING, null, DEFAULT_LIFE); }
    public Notification error(String text)   { return push(text, Notification.Level.ERROR, null, DEFAULT_LIFE + 1.0); }

    public Notification info(String text, String tag)    { return push(text, Notification.Level.INFO, tag, DEFAULT_LIFE); }
    public Notification warn(String text, String tag)    { return push(text, Notification.Level.WARNING, tag, DEFAULT_LIFE); }
    public Notification error(String text, String tag)   { return push(text, Notification.Level.ERROR, tag, DEFAULT_LIFE + 1.0); }
    public Notification success(String text, String tag) { return push(text, Notification.Level.SUCCESS, tag, DEFAULT_LIFE); }

    /**
     * Основной метод публикации.
     * Если tag не пуст и такое уведомление уже висит — продлеваем жизнь,
     * чтобы не спамить одинаковыми сообщениями.
     */
    public Notification push(String text, Notification.Level level,
                             String tag, double lifeTime) {
        if (text == null || text.isBlank()) return null;

        // Дедупликация: если по тегу уже висит — обновляем
        if (tag != null && !tag.isEmpty()) {
            Notification existing = byTag.get(tag);
            if (existing != null && active.contains(existing)) {
                // Продлеваем жизнь: перезаписываем время создания
                Notification refreshed = new Notification(
                        text, level, now, Math.max(lifeTime, existing.lifeTime), tag);
                active.remove(existing);
                active.add(refreshed);
                byTag.put(tag, refreshed);
                sortByAge();
                return refreshed;
            }
        }

        Notification n = new Notification(text, level, now, lifeTime, tag);
        active.add(n);
        if (tag != null && !tag.isEmpty()) byTag.put(tag, n);

        // Ограничиваем количество видимых — самые старые вытесняются
        while (active.size() > MAX_VISIBLE) {
            Notification old = active.remove(0);
            if (!old.tag.isEmpty()) byTag.remove(old.tag);
        }

        sortByAge();
        return n;
    }

    private void sortByAge() {
        active.sort(Comparator.comparingDouble(Notification::hashCode));
    }

    // ==================== ВРЕМЯ ====================

    /** Текущее игровое время. Обновляется из GameLoop. */
    private double now = 0;

    /** Продвинуть время и убрать истёкшие уведомления. */
    public void update(double dt) {
        now += dt;

        Iterator<Notification> it = active.iterator();
        while (it.hasNext()) {
            Notification n = it.next();
            if (n.isExpired(now)) {
                it.remove();
                if (!n.tag.isEmpty()) byTag.remove(n.tag);
            }
        }
    }

    public double getNow() { return now; }

    // ==================== ДОСТУП ДЛЯ РЕНДЕРА ====================

    /**
     * Возвращает список видимых уведомлений в порядке отображения
     * (новые сверху).
     */
    public List<Notification> getVisible() {
        List<Notification> copy = new ArrayList<>(active);
        copy.sort((a, b) -> Long.compare(b.id, a.id));   // новые — первыми
        return copy;
    }

    public int size() { return active.size(); }
    public boolean isEmpty() { return active.isEmpty(); }

    /** Очистить всё (например, при старте новой игры). */
    public void clear() {
        active.clear();
        byTag.clear();
    }

    /** Убрать уведомление досрочно. */
    public void dismiss(Notification n) {
        if (n == null) return;
        active.remove(n);
        if (!n.tag.isEmpty()) byTag.remove(n.tag);
    }

    /** Убрать все уведомления с данным тегом. */
    public void dismissByTag(String tag) {
        if (tag == null || tag.isEmpty()) return;
        active.removeIf(n -> n.tag.equals(tag));
        byTag.remove(tag);
    }

    /**
     * Убирает все уведомления определённого уровня.
     * Полезно, когда UI хочет «очистить ошибки».
     */
    public void dismissByLevel(Notification.Level level) {
        active.removeIf(n -> n.level == level);
        // Перестроим индекс по тегам
        byTag.clear();
        for (Notification n : active) {
            if (!n.tag.isEmpty()) byTag.put(n.tag, n);
        }
    }
}