import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.student.fabricTown.agents.Car;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.map.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CarTest {

    private Factory factory;
    private Shop shop;
    private List<Point> pathToShop;
    private List<Point> pathToFactory;

    @BeforeEach
    void setUp() {
        factory = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        shop = new Shop(new Point(10, 10), BuildingSize.S1x1);

        // Фиктивные маршруты: 2 клетки в каждую сторону
        pathToShop    = List.of(new Point(0, 0), new Point(1, 0));
        pathToFactory = List.of(new Point(1, 0), new Point(0, 0));
    }

    // ==================== КОНСТРУКТОР ====================

    @Test
    @DisplayName("Пустой pathToShop — исключение")
    void пустой_маршрут_туда_исключение() {
        assertThrows(IllegalArgumentException.class,
                () -> new Car(factory, shop, List.of(), pathToFactory));
    }

    @Test
    @DisplayName("Пустой pathToFactory — исключение")
    void пустой_маршрут_обратно_исключение() {
        assertThrows(IllegalArgumentException.class,
                () -> new Car(factory, shop, pathToShop, List.of()));
    }

    // ==================== СТАРТ ДОСТАВКИ ====================

    @Test
    @DisplayName("Фабрика пуста — доставка не стартует")
    void фабрика_пуста_доставка_не_стартует() {
        Car car = new Car(factory, shop, pathToShop, pathToFactory);

        boolean started = car.tryStartDelivery();

        assertFalse(started, "Без товара на фабрике машина не выезжает");
        assertEquals(Car.State.IDLE, car.getState());
    }

    @Test
    @DisplayName("Магазин полон — машина уходит в BLOCKED")
    void магазин_полон_машина_блокируется() {
        // Забиваем магазин
        int cap = shop.getCapacity();
        for (int i = 0; i < cap; i++) shop.addProduct(Product.CHICKEN);

        // Кладём товар на фабрику
        factory.tick(20.0);

        Car car = new Car(factory, shop, pathToShop, pathToFactory);
        boolean started = car.tryStartDelivery();

        assertFalse(started, "В полный магазин машина не едет");
        assertEquals(Car.State.BLOCKED, car.getState());
    }

    @Test
    @DisplayName("Успешный старт: машина забирает пачку и едет")
    void успешный_старт() {
        // Накопим на фабрике 5 товаров
        factory.tick(20.0);
        int onFactory = factory.getStorageCount();
        assertTrue(onFactory > 0, "Фабрика должна была что-то произвести");

        Car car = new Car(factory, shop, pathToShop, pathToFactory);
        boolean started = car.tryStartDelivery();

        assertTrue(started);
        assertEquals(Car.State.TO_SHOP, car.getState());
        assertTrue(car.getCargoCount() > 0, "В машине должен быть груз");
        assertTrue(car.getCargoCount() <= car.getCapacity(),
                "Груз не должен превышать грузоподъёмность");
    }

    @Test
    @DisplayName("Пачка не превышает свободное место в магазине")
    void пачка_ограничена_свободным_местом() {
        // Оставим в магазине 1 свободное место
        int cap = shop.getCapacity();
        for (int i = 0; i < cap - 1; i++) shop.addProduct(Product.CHICKEN);

        // Накопим полный склад на фабрике
        for (int i = 0; i < 100; i++) factory.tick(1.0);

        Car car = new Car(factory, shop, pathToShop, pathToFactory);
        car.tryStartDelivery();

        assertEquals(1, car.getCargoCount(),
                "Машина должна взять ровно столько, сколько влезает в магазин");
    }

    // ==================== ОТЗЫВ ====================

    @Test
    @DisplayName("recall сбрасывает состояние и возвращает на старт")
    void recall_сбрасывает_состояние() {
        factory.tick(20.0);
        Car car = new Car(factory, shop, pathToShop, pathToFactory);
        car.tryStartDelivery();

        // Сдвигаем машину
        car.tick(0.3);
        assertNotEquals(Car.State.IDLE, car.getState());

        car.recall();

        assertEquals(Car.State.IDLE, car.getState());
        assertEquals(0, car.getCargoCount(), "После recall груз сброшен");
    }

    @Test
    @DisplayName("При выключенной доставке tryStartDelivery возвращает false")
    void доставка_выключена_старт_запрещён() {
        factory.tick(20.0);

        Car car = new Car(factory, shop, pathToShop, pathToFactory);
        car.setDeliveryEnabled(false);

        boolean started = car.tryStartDelivery();

        assertFalse(started, "При deliveryEnabled == false старт запрещён");
        assertEquals(Car.State.IDLE, car.getState());
    }
}