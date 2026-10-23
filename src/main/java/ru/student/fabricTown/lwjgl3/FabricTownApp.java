package ru.student.fabricTown.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.CameraController;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.GameRenderer;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.NotificationRenderer;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.ProfilerHud;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.RouteOverlay;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.UiController;
import ru.student.fabricTown.lwjgl3.visualization_lwjgl3.UiStage;
import ru.student.fabricTown.profiler.Profiler;
import ru.student.fabricTown.profiler.ProfilerSnapshot;

public class FabricTownApp extends ApplicationAdapter {
    private Game game;
    private SpriteBatch batch;
    private Texture pixel;
    private CameraController cam;
    private GameRenderer renderer;
    private RouteOverlay overlay;
    private UiController ui;
    private UiStage uiStage;
    private ProfilerSnapshot lastProfilerSnap;
    private int profilerFrameCounter;
    private float lastDt = 1f / 60f;

    @Override
    public void create() {
        game = new Game();
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        cam = new CameraController(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        overlay = new RouteOverlay();
        ui = new UiController(game, cam, overlay);
        uiStage = new UiStage(game, ui);
        renderer = new GameRenderer();

        ui.setInfoUi(uiStage.infoUi);
        ui.setBuildUi(uiStage.buildUi);
        ui.setHud(uiStage.hud);

        InputMultiplexer mp = new InputMultiplexer();
        mp.addProcessor(uiStage.stage);
        mp.addProcessor(ui);
        Gdx.input.setInputProcessor(mp);

        lastProfilerSnap = Profiler.snapshot();
        overlay.rebuildMarkers(game);
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        lastDt = dt;

        try (var s = Profiler.start("render.total")) {
            game.tick(dt);
            NotificationRenderer.tickAnimations(game.getNotifications(), dt);
            cam.update(dt);
            ui.update();
            uiStage.hud.refresh(game);

            Gdx.gl.glClearColor(25 / 255f, 25 / 255f, 30 / 255f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            try (var s2 = Profiler.start("render.world")) {
                renderer.renderWorld(game, cam, ui.mode, ui.selectedBlueprint,
                        ui.hoverCell, ui.hoverValid, ui.hoverBuilding);
            }

            ShapeRenderer shapes = renderer.getShapes();
            shapes.setProjectionMatrix(cam.camera.combined);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            overlay.drawFilled(shapes, game);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            overlay.drawLines(shapes, game, cam);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);

            batch.getProjectionMatrix().setToOrtho2D(0, 0,
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.begin();
            renderer.renderScreenLabels(batch, uiStage.fontTiny, uiStage.fontLabel, uiStage.fontBig,
                    game, cam, ui.mode, ui.selectedBlueprint, ui.hoverCell, ui.hoverBuilding);
            overlay.drawScreen(batch, pixel, uiStage.fontLabel, uiStage.fontSmall, game, cam);
            overlay.drawNoRouteIfNeeded(batch, pixel, uiStage.fontLabel, game, cam);
            renderer.drawHelp(batch, uiStage.fontSmall, uiStage.fontLabel, ui.hoverCell, pixel);
            NotificationRenderer.draw(batch, pixel, uiStage.fontLabel, game.getNotifications(), uiStage);
            if (Profiler.isEnabled() && ui.profilerOverlayVisible && lastProfilerSnap != null) {
                if (++profilerFrameCounter % 10 == 0) lastProfilerSnap = Profiler.snapshot();
                ProfilerHud.draw(batch, pixel, uiStage.fontTiny, lastProfilerSnap,
                        lastDt > 0 ? 1.0 / lastDt : 0);
            }
            batch.end();
        }

        uiStage.render(dt);
    }

    @Override
    public void resize(int width, int height) {
        if (uiStage != null) uiStage.resize(width, height);
        if (cam != null) cam.resize(width, height);
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (pixel != null) pixel.dispose();
        if (renderer != null) renderer.dispose();
        if (uiStage != null) uiStage.dispose();
    }
}
