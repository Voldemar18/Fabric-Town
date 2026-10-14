import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.student.fabricTown.agents.Product;
import ru.student.fabricTown.buildings.BuildingSize;
import ru.student.fabricTown.buildings.Factory;
import ru.student.fabricTown.buildings.Shop;
import ru.student.fabricTown.logic.Dispatcher;
import ru.student.fabricTown.map.Point;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DispatcherTest {

    private Dispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new Dispatcher(null);   // пути не нужны для проверки лимитов
    }

    private List<Point> stubPath() {
        return List.of(new Point(0, 0), new Point(1, 0));
    }

    // ==================== ОСНОВНОЙ СЦЕНАРИЙ ====================

    @Test
    @DisplayName("Первая привязка фабрики к магазину проходит успешно")
    void первая_привязка_успешна() {
        Factory f = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Shop s = new Shop(new Point(10, 10), BuildingSize.S1x1);

        boolean ok = dispatcher.bind(f, s, stubPath(), stubPath());

        assertTrue(ok, "Первая привязка должна пройти");
        assertTrue(dispatcher.isBound(f, s));
        assertEquals(1, dispatcher.getFactoriesFor(s).size());
    }

    @Test
    @DisplayName("Повторная привязка той же пары отклоняется")
    void повторная_привязка_отклоняется() {
        Factory f = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Shop s = new Shop(new Point(10, 10), BuildingSize.S1x1);

        assertTrue(dispatcher.bind(f, s, stubPath(), stubPath()));
        assertFalse(dispatcher.bind(f, s, stubPath(), stubPath()),
                "Повторная привязка той же пары не должна проходить");
    }

    // ==================== ЛИМИТЫ ПО ТИПУ ТОВАРА ====================

    @Test
    @DisplayName("Магазин S1x1 принимает только одну фабрику каждого типа")
    void магазин_S1x1_лимит_по_типу() {
        Shop s = new Shop(new Point(10, 10), BuildingSize.S1x1);

        Factory f1 = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Factory f2 = new Factory(new Point(1, 0), BuildingSize.S1x1, Product.CHICKEN);

        assertTrue(dispatcher.canBind(f1, s), "Первая курица — можно");
        dispatcher.bind(f1, s, stubPath(), stubPath());

        assertFalse(dispatcher.canBind(f2, s),
                "Вторая курица в S1x1 — уже нельзя (лимит 1 на тип)");
        assertFalse(dispatcher.bind(f2, s, stubPath(), stubPath()));
    }

    @Test
    @DisplayName("Магазин S2x2 принимает до трёх фабрик одного типа")
    void магазин_S2x2_лимит_3_на_тип() {
        Shop s = new Shop(new Point(10, 10), BuildingSize.S2x2);

        for (int i = 0; i < 3; i++) {
            Factory f = new Factory(new Point(i, 0), BuildingSize.S1x1, Product.BEEF);
            assertTrue(dispatcher.canBind(f, s),
                    "Фабрика #" + (i + 1) + " должна влезать");
            dispatcher.bind(f, s, stubPath(), stubPath());
        }

        Factory extra = new Factory(new Point(99, 0), BuildingSize.S1x1, Product.BEEF);
        assertFalse(dispatcher.canBind(extra, s),
                "Четвёртая фабрика говядины не влезает в S2x2");
    }

    @Test
    @DisplayName("Разные типы товаров не мешают друг другу")
    void разные_типы_не_конфликтуют() {
        Shop s = new Shop(new Point(10, 10), BuildingSize.S1x1);  // 1 на тип

        Factory chicken = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Factory beef    = new Factory(new Point(1, 0), BuildingSize.S1x1, Product.BEEF);

        assertTrue(dispatcher.canBind(chicken, s));
        dispatcher.bind(chicken, s, stubPath(), stubPath());

        assertTrue(dispatcher.canBind(beef, s),
                "Другой тип товара должен влезать независимо");
    }

    // ==================== ОТВЯЗКА ====================

    @Test
    @DisplayName("После unbind фабрика снова может быть привязана")
    void unbind_освобождает_место() {
        Factory f = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Shop s = new Shop(new Point(10, 10), BuildingSize.S1x1);

        dispatcher.bind(f, s, stubPath(), stubPath());
        assertFalse(dispatcher.canBind(f, s));

        dispatcher.unbind(f, s);

        assertFalse(dispatcher.isBound(f, s));
        assertTrue(dispatcher.canBind(f, s), "После unbind место освободилось");
    }

    @Test
    @DisplayName("unbindAll(Shop) снимает все связи магазина")
    void unbindAll_снимает_все_связи_магазина() {
        Shop s = new Shop(new Point(10, 10), BuildingSize.S2x2);
        Factory f1 = new Factory(new Point(0, 0), BuildingSize.S1x1, Product.CHICKEN);
        Factory f2 = new Factory(new Point(1, 0), BuildingSize.S1x1, Product.BEEF);

        dispatcher.bind(f1, s, stubPath(), stubPath());
        dispatcher.bind(f2, s, stubPath(), stubPath());

        dispatcher.unbindAll(s);

        assertTrue(dispatcher.getFactoriesFor(s).isEmpty(),
                "После unbindAll магазин ни с кем не связан");
    }
}