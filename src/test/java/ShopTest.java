
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.map.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShopTest {

    private Shop shop;

    @BeforeEach
    void setUp() {
        shop = new Shop(new Point(0, 0), BuildingSize.S1x1);
    }

    // ==================== ОСНОВНОЙ СЦЕНАРИЙ ====================

    @Test
    @DisplayName("Добавление и продажа товара")
    void добавление_и_продажа() {
        shop.addProducts(List.of(Product.CHICKEN, Product.CHICKEN));

        assertEquals(2, shop.countOf(Product.CHICKEN));

        boolean sold = shop.trySell(Product.CHICKEN);

        assertTrue(sold);
        assertEquals(1, shop.countOf(Product.CHICKEN));
        assertEquals(Product.CHICKEN.price, shop.getMoneyEarned(), 0.001);
    }

    // ==================== ПОГРАНИЧНЫЕ СЛУЧАИ ====================

    @Test
    @DisplayName("Пустой склад — продать нельзя")
    void пустой_склад_продать_нельзя() {
        assertFalse(shop.trySell(Product.BEEF),
                "Если товара нет — продажа не проходит");
        assertEquals(0, shop.getMoneyEarned(), 0.001);
    }

    @Test
    @DisplayName("Пустой склад — isEmpty() == true, isFull() == false")
    void пустой_склад_флаги() {
        assertTrue(shop.isEmpty());
        assertFalse(shop.isFull());
    }

    @Test
    @DisplayName("Переполнение склада: лишнее не добавляется")
    void переполнение_склада() {
        int cap = shop.getCapacity();

        // Пытаемся добавить cap + 100 товаров
        Product[] all = { Product.CHICKEN };
        List<Product> big = new java.util.ArrayList<>();
        for (int i = 0; i < cap + 100; i++) big.add(Product.CHICKEN);

        int added = shop.addProducts(big);

        assertEquals(cap, added, "Принято ровно capacity товаров");
        assertEquals(cap, shop.totalStored());
        assertTrue(shop.isFull(), "Склад должен быть полон");
    }

    @Test
    @DisplayName("После переполнения продажа освобождает место")
    void продажа_освобождает_место() {
        int cap = shop.getCapacity();
        List<Product> big = new java.util.ArrayList<>();
        for (int i = 0; i < cap; i++) big.add(Product.BREAD);
        shop.addProducts(big);

        assertTrue(shop.isFull());

        shop.trySell(Product.BREAD);

        assertFalse(shop.isFull(), "После продажи одного товара склад уже не полон");
        assertEquals(cap - 1, shop.totalStored());
    }

    @Test
    @DisplayName("Лимит фабрик на тип зависит от размера магазина")
    void лимит_фабрик_по_размеру() {
        assertEquals(1, new Shop(new Point(0, 0), BuildingSize.S1x1)
                .getMaxFactoriesPerType());
        assertEquals(2, new Shop(new Point(0, 0), BuildingSize.S1x2)
                .getMaxFactoriesPerType());
        assertEquals(2, new Shop(new Point(0, 0), BuildingSize.S2x1)
                .getMaxFactoriesPerType());
        assertEquals(3, new Shop(new Point(0, 0), BuildingSize.S2x2)
                .getMaxFactoriesPerType());
    }
}