import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.student.fabricTown.agents.Person;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.House;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.map.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersonTest {

    private House home;
    private Shop shop;

    @BeforeEach
    void setUp() {
        home = new House(new Point(0, 0), BuildingSize.S1x1);
        shop = new Shop(new Point(3, 0), BuildingSize.S1x1);
    }

    @Test
    @DisplayName("Человек доходит до магазина и покупает товар")
    void человек_покупает_товар() {
        shop.addProduct(Product.CHICKEN);

        Person p = new Person(home, shop, Product.CHICKEN);

        double earned = 0;
        for (int i = 0; i < 500 && !p.isDone(); i++) {
            earned += p.tick(0.05);
        }

        assertTrue(p.isDone(), "Человек должен завершить поход");
        assertTrue(p.hasBought(), "Человек должен купить товар");
        assertEquals(Product.CHICKEN.price, earned, 0.001);
    }

    @Test
    @DisplayName("Пустой магазин: человек уходит без покупки")
    void пустой_магазин_без_покупки() {
        Person p = new Person(home, shop, Product.CHICKEN);

        for (int i = 0; i < 500 && !p.isDone(); i++) {
            p.tick(0.05);
        }

        assertTrue(p.isDone(), "Человек должен завершить поход");
        assertFalse(p.hasBought(), "Товара не было — покупки не случилось");
    }

    @Test
    @DisplayName("targetShop == null: человек сразу возвращается домой")
    void null_магазин_сразу_домой() {
        Person p = new Person(home, null, Product.BEEF);

        for (int i = 0; i < 500 && !p.isDone(); i++) {
            p.tick(0.05);
        }

        assertTrue(p.isDone());
        assertFalse(p.hasBought());
    }

    @Test
    @DisplayName("ShopFinder находит альтернативный магазин")
    void альтернативный_магазин() {
        Shop empty = new Shop(new Point(2, 0), BuildingSize.S1x1);   // пустой
        Shop withFood = new Shop(new Point(5, 0), BuildingSize.S1x1);
        withFood.addProduct(Product.BEEF);

        Person p = new Person(home, empty, Product.BEEF);
        p.setShopFinder((need, visited) ->
                visited.contains(withFood) ? null : withFood);

        for (int i = 0; i < 1000 && !p.isDone(); i++) {
            p.tick(0.05);
        }

        assertTrue(p.hasBought(),
                "Человек должен был дойти до второго магазина и купить");
        assertTrue(p.getVisitedShops().contains(withFood));
    }

    @Test
    @DisplayName("Все магазины посещены — покупки нет")
    void все_магазины_посещены() {
        Person p = new Person(home, shop, Product.FRUIT);

        // ShopFinder всегда возвращает null — других магазинов нет
        p.setShopFinder((need, visited) -> null);

        for (int i = 0; i < 1000 && !p.isDone(); i++) {
            p.tick(0.05);
        }

        assertTrue(p.isDone());
        assertFalse(p.hasBought(), "Обойдя все магазины, человек уходит без покупки");
    }
}