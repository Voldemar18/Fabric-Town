package ru.student.fabricTown.agents;

import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Shop;

import java.util.HashSet;
import java.util.Set;

public class Person {
    public enum State { GOING, SHOPPING, RETURNING, DONE }

    private final House home;
    private Shop targetShop;
    private final Product need;
    private State state = State.GOING;
    private double x, y;
    private double progress = 0;
    private double shopTimer = 0;

    private boolean bought = false;

    private final Set<Shop> visitedShops = new HashSet<>();

    /** Стратегия поиска альтернативного магазина. Устанавливается извне. */
    private ShopFinder shopFinder = (need, visited) -> null;

    /** Два параметра: нужный товар и уже посещённые магазины. */
    @FunctionalInterface
    public interface ShopFinder {
        Shop find(Product need, Set<Shop> visited);
    }

    public Person(House home, Shop shop, Product need) {
        this.home = home;
        this.targetShop = shop;
        this.need = need;
        this.x = home.getOrigin().x + 0.5;
        this.y = home.getOrigin().y + 0.5;
        if (shop != null) visitedShops.add(shop);
    }

    public void setShopFinder(ShopFinder finder) {
        if (finder != null) this.shopFinder = finder;
    }

    public double tick(double dt) {
        switch (state) {
            case GOING -> {
                if (targetShop == null) {
                    state = State.RETURNING;
                    return 0;
                }
                double dx = (targetShop.getOrigin().x + 0.5) - x;
                double dy = (targetShop.getOrigin().y + 0.5) - y;
                double len = Math.hypot(dx, dy);
                if (len < 0.1) {
                    state = State.SHOPPING;
                    shopTimer = 1.0;
                } else {
                    double speed = 1.5;
                    x += dx / len * speed * dt;
                    y += dy / len * speed * dt;
                }
            }
            case SHOPPING -> {
                shopTimer -= dt;
                if (shopTimer <= 0) {
                    if (targetShop != null && targetShop.trySell(need)) {
                        bought = true;
                        state = State.DONE;
                        return need.price;
                    }
                    if (!trySwitchShop()) {
                        state = State.RETURNING;
                    }
                }
            }
            case RETURNING -> {
                double dx = (home.getOrigin().x + 0.5) - x;
                double dy = (home.getOrigin().y + 0.5) - y;
                double len = Math.hypot(dx, dy);
                if (len < 0.1) {
                    state = State.DONE;
                } else {
                    double speed = 1.5;
                    x += dx / len * speed * dt;
                    y += dy / len * speed * dt;
                }
            }
            default -> {}
        }
        return 0;
    }

    private boolean trySwitchShop() {
        Shop next = shopFinder.find(need, visitedShops);
        if (next == null) return false;
        this.targetShop = next;
        this.visitedShops.add(next);
        this.state = State.GOING;
        return true;
    }

    public boolean isDone() { return state == State.DONE; }
    public boolean hasBought() { return bought; }

    public double getX() { return x; }
    public double getY() { return y; }
    public Product getNeed() { return need; }
    public State getState() { return state; }
    public Shop getTargetShop() { return targetShop; }
    public House getHome() { return home; }
    public Set<Shop> getVisitedShops() { return visitedShops; }

    public void setTargetShop(Shop s) {
        this.targetShop = s;
        this.state = State.GOING;
        if (s != null) this.visitedShops.add(s);
    }
}