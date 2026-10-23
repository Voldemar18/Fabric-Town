package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import ru.student.fabricTown.core.Game;

public class UiStage {
    public final Stage stage;
    public final BuildUi buildUi;
    public final InfoUi infoUi;
    public final HudRenderer hud;
    private final Skin skin;
    private EmojiSupport emojiSupport;

    public final BitmapFont fontUi;
    public final BitmapFont fontLabel;
    public final BitmapFont fontSmall;
    public final BitmapFont fontTiny;
    public final BitmapFont fontBig;

    private static final float LEFT_PANEL_W = 220f;
    private static final float RIGHT_PANEL_W = 420f;
    private static final float HUD_H = 40f;

    private static final String EXTRA_CHARS =
            "\u2190\u2191\u2192\u2193\u2194"
                    + "\u25B8\u25BE\u00B7"
                    + "\u2714\u2716\u26A0\u2139"
                    + "\u25B6\u23F8"
                    + "\u20BF"
                    + "\u00AB\u00BB"
                    + "\u2022"
                    + "\u25B6\u25BC";

    public UiStage(Game game, UiController ctrl) {
        stage = new Stage(new ScreenViewport());
        skin = createBasicSkin();
        fontUi = skin.getFont("default-font");
        fontLabel = skin.getFont("label-font");
        fontSmall = skin.getFont("small-font");
        fontTiny = skin.getFont("tiny-font");
        fontBig = skin.getFont("big-font");

        hud = new HudRenderer(this);
        buildUi = new BuildUi(skin, ctrl, this);
        infoUi = new InfoUi(skin, game, this, msg ->
                hud.setStatus(msg, new Color(1f, 120 / 255f, 120 / 255f, 1f)));

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.add(hud.bar).colspan(3).growX().height(HUD_H).row();

        root.add(buildUi.root).width(LEFT_PANEL_W).growY();
        Actor spacer = new Actor();
        spacer.setTouchable(Touchable.disabled);
        root.add(spacer).grow();
        root.add(infoUi.root).width(RIGHT_PANEL_W).growY();
        stage.addActor(root);

        // Единый обработчик скролла.
        // Логика:
        //   - курсор над ScrollPane левой панели   -> скроллим этот ScrollPane
        //   - курсор над ScrollPane правой панели  -> скроллим этот ScrollPane
        //   - курсор над остальной областью карты  -> зум камеры
        //   - курсор над HUD                       -> ничего
        // Всегда возвращаем true, чтобы событие не "протекло" в чужие ScrollPane.
        stage.getRoot().addCaptureListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y,
                                    float amountX, float amountY) {
                if (y < HUD_H) return true;

                if (overPane(buildUi.getScrollPane(), x, y)) {
                    scrollPane(buildUi.getScrollPane(), amountY);
                    return true;
                }
                if (overPane(infoUi.getScrollPane(), x, y)) {
                    scrollPane(infoUi.getScrollPane(), amountY);
                    return true;
                }
                // Всё остальное — карта.
                ctrl.onScrolled(amountY);
                return true;
            }
        });
    }

    /** Проверяет, попадает ли точка Stage (x, y сверху вниз) в границы ScrollPane. */
    private boolean overPane(ScrollPane pane, float stageX, float stageY) {
        if (pane == null || !pane.isVisible()) return false;
        Vector2 local = pane.stageToLocalCoordinates(new Vector2(stageX, stageY));
        return local.x >= 0 && local.y >= 0
                && local.x <= pane.getWidth() && local.y <= pane.getHeight();
    }

    /** Прокручивает ScrollPane на amountY, соблюдая лимиты. */
    private void scrollPane(ScrollPane pane, float amountY) {
        if (pane == null) return;
        float max = Math.max(0f, pane.getMaxY());
        float newY = pane.getScrollY() + amountY * 40f;
        if (newY < 0) newY = 0;
        if (newY > max) newY = max;
        pane.setScrollY(newY);
    }

    public void render(float delta) {
        infoUi.act(delta);
        stage.act(delta);
        stage.draw();
    }

    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
    }

    public Skin getSkin() { return skin; }

    public String filterEmojis(String text) {
        if (text == null) return null;
        if (emojiSupport == null) return text;
        try {
            return emojiSupport.FilterEmojis(text);
        } catch (Exception e) {
            return text;
        }
    }

    public void dispose() {
        stage.dispose();
        skin.dispose();
    }

    private Skin createBasicSkin() {
        Skin skin = new Skin();
        BitmapFont ui = loadFont(16);
        BitmapFont label = loadFont(13);
        BitmapFont small = loadFont(12);
        BitmapFont tiny = loadFont(10);
        BitmapFont big = loadFont(16);

        emojiSupport = null;
        try {
            EmojiSupport support = new EmojiSupport();
            FileHandle atlas = firstExisting(
                    "fonts/data/emojis25.atlas",
                    "assets/fonts/data/emojis25.atlas");
            if (atlas != null) {
                support.Load(atlas);
                support.AddEmojisToFont(ui);
                support.AddEmojisToFont(label);
                support.AddEmojisToFont(small);
                emojiSupport = support;
            } else {
                Gdx.app.error("UiStage", "Атлас эмодзи не найден (fonts/data/emojis25.atlas).");
            }
        } catch (Exception e) {
            Gdx.app.error("UiStage", "Не удалось загрузить эмодзи", e);
        }

        skin.add("default-font", ui);
        skin.add("label-font", label);
        skin.add("small-font", small);
        skin.add("tiny-font", tiny);
        skin.add("big-font", big);

        TextureRegionDrawable panel = drawable(new Color(35 / 255f, 35 / 255f, 45 / 255f, 0.96f));
        TextureRegionDrawable hudBg = drawable(new Color(25 / 255f, 25 / 255f, 35 / 255f, 0.96f));
        TextureRegionDrawable btn = drawable(new Color(60 / 255f, 60 / 255f, 80 / 255f, 1f));
        TextureRegionDrawable down = drawable(new Color(70 / 255f, 100 / 255f, 150 / 255f, 1f));

        skin.add("panel", panel, Drawable.class);
        skin.add("hud", hudBg, Drawable.class);
        skin.add("bg", panel, Drawable.class);
        skin.add("btn", btn, Drawable.class);
        skin.add("btn-down", down, Drawable.class);

        Label.LabelStyle ls = new Label.LabelStyle();
        ls.font = ui;
        ls.fontColor = Color.WHITE;
        skin.add("default", ls);

        TextButton.TextButtonStyle bs = new TextButton.TextButtonStyle();
        bs.up = btn;
        bs.down = down;
        bs.checked = down;
        bs.font = ui;
        skin.add("default", bs);

        ScrollPane.ScrollPaneStyle ss = new ScrollPane.ScrollPaneStyle();
        ss.background = drawable(new Color(45 / 255f, 45 / 255f, 60 / 255f, 1f));
        skin.add("default", ss);

        Window.WindowStyle ws = new Window.WindowStyle();
        ws.titleFont = ui;
        ws.background = panel;
        skin.add("default", ws);
        return skin;
    }

    private BitmapFont loadFont(int size) {
        FileHandle fh = firstExisting(
                "fonts/DejaVuSans.ttf",
                "assets/fonts/DejaVuSans.ttf",
                "fonts/Roboto-Regular.ttf");
        if (fh == null) {
            fh = firstAbsolute(
                    "C:/Windows/Fonts/segoeui.ttf",
                    "C:/Windows/Fonts/arial.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        }
        if (fh == null) {
            Gdx.app.error("UiStage", "TTF не найден, кириллица может пропасть.");
            return new BitmapFont();
        }
        BitmapFont font = generate(fh, size);
        return font != null ? font : new BitmapFont();
    }

    private BitmapFont generate(FileHandle fh, int size) {
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fh);
            FreeTypeFontGenerator.FreeTypeFontParameter p =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = size;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            StringBuilder chars = new StringBuilder(FreeTypeFontGenerator.DEFAULT_CHARS);
            for (char c = '\u0400'; c <= '\u04FF'; c++) chars.append(c);
            chars.append(EXTRA_CHARS);
            p.characters = chars.toString();
            BitmapFont f = gen.generateFont(p);
            gen.dispose();
            return f;
        } catch (Exception e) {
            Gdx.app.error("UiStage", "Ошибка шрифта: " + fh.path(), e);
            return null;
        }
    }

    private FileHandle firstExisting(String... paths) {
        for (String path : paths) {
            FileHandle fh = Gdx.files.internal(path);
            if (fh.exists()) return fh;
            fh = Gdx.files.local(path);
            if (fh.exists()) return fh;
        }
        return null;
    }

    private FileHandle firstAbsolute(String... paths) {
        for (String path : paths) {
            FileHandle fh = Gdx.files.absolute(path);
            if (fh.exists()) return fh;
        }
        return null;
    }

    private TextureRegionDrawable drawable(Color c) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(c);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawable(new TextureRegion(t));
    }
}