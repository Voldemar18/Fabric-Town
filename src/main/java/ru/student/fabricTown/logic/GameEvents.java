package ru.student.fabricTown.logic;

import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.core.NotificationCenter;

/**
 * Хелперы для типовых игровых сообщений.
 * Использует теги для дедупликации — одинаковые предупреждения не спамятся.
 */
public final class GameEvents {

    private GameEvents() {}

    // ==================== СТРОИТЕЛЬСТВО ====================

    public static void built(NotificationCenter n, String what) {
        n.success("Построено: " + what, null);
    }

    public static void buildFailed(NotificationCenter n, String reason) {
        n.error("Не удалось построить: " + reason, null);
    }

    public static void sold(NotificationCenter n, String what, double price) {
        n.info("Продано: " + what + " за " + (int)price + " 💰", null);
    }

    // ==================== ЛОГИСТИКА ====================

    /**
     * Нет пути от фабрики к магазину.
     * Тег уникальный на пару — чтобы не спамить одной и той же ошибкой.
     */
    public static void noPath(NotificationCenter n, Factory f, Shop s) {
        String tag = "no-path:" + System.identityHashCode(f) + ":" + System.identityHashCode(s);
        n.error("Нет пути от фабрики " + f.getOrigin()
                + " к магазину " + s.getOrigin(), tag);
    }

    /**
     * Магазин не привязан ни к одной фабрике после постройки.
     */
    public static void shopUnbound(NotificationCenter n, Shop s) {
        String tag = "shop-unbound:" + System.identityHashCode(s);
        n.warn("Магазин " + s.getOrigin()
                + " не подключён ни к одной фабрике", tag);
    }

    /**
     * Склад магазина полон — машина ждёт.
     */
    public static void shopFull(NotificationCenter n, Shop s) {
        String tag = "shop-full:" + System.identityHashCode(s);
        n.warn("Магазин " + s.getOrigin() + " переполнен — товар не доставляется", tag);
    }

    /**
     * Склад фабрики полон — производство остановлено.
     */
    public static void factoryFull(NotificationCenter n, Factory f) {
        String tag = "factory-full:" + System.identityHashCode(f);
        n.warn("Склад фабрики " + f.getOrigin() + " полон — производство пауза", tag);
    }

    /**
     * Фабрика обслуживает максимум магазинов — новый не привязать.
     */
    public static void factoryAtLimit(NotificationCenter n, Factory f) {
        String tag = "factory-limit:" + System.identityHashCode(f);
        n.warn("Фабрика " + f.getOrigin()
                + " обслуживает максимум магазинов (" + f.getMaxShops() + ")", tag);
    }

    /**
     * В магазине нет нужного товара — покупатель уходит.
     */
    public static void missingProduct(NotificationCenter n, Shop s, Product p) {
        String tag = "missing:" + System.identityHashCode(s) + ":" + p.name();
        n.info("В магазине " + s.getOrigin()
                + " нет товара: " + p.title, tag);
    }

    /**
     * Дорога снесена — маршрут разрушен.
     */
    public static void routeBroken(NotificationCenter n, Factory f, Shop s) {
        String tag = "route-broken:" + System.identityHashCode(f) + ":" + System.identityHashCode(s);
        n.error("Маршрут фабрика→магазин нарушен (дорога снесена). "
                + "Ф: " + f.getOrigin() + " → М: " + s.getOrigin(), tag);
    }
}