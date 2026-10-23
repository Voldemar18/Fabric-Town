package ru.student.fabricTown.lwjgl3.visualization_lwjgl3;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import ru.student.fabricTown.core.Game;
import ru.student.fabricTown.map.Point;

public class HudRenderer {
    public final Table bar;
    private final UiStage uiStage;
    private final Label moneyLabel;
    private final Label statusLabel;
    private final Label coordLabel;
    private final Label hintLabel;

    public HudRenderer(UiStage uiStage) {
        this.uiStage = uiStage;
        bar = new Table(uiStage.getSkin());
        bar.setBackground("hud");
        bar.pad(6, 12, 6, 12);

        moneyLabel = new Label(filter("💰 0"), uiStage.getSkin());
        moneyLabel.setColor(new Color(255 / 255f, 220 / 255f, 100 / 255f, 1f));

        statusLabel = new Label("Готов", uiStage.getSkin());
        statusLabel.setColor(new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));

        coordLabel = new Label("—", uiStage.getSkin());
        coordLabel.setColor(new Color(180 / 255f, 220 / 255f, 255 / 255f, 1f));

        hintLabel = new Label("Выберите объект слева", uiStage.getSkin());
        hintLabel.setColor(new Color(180 / 255f, 180 / 255f, 180 / 255f, 1f));

        bar.add(moneyLabel).left().padRight(16);
        bar.add(statusLabel).expandX().center();
        bar.add(coordLabel).right().padRight(12);
        bar.add(hintLabel).right();
    }

    public void refresh(Game game) {
        moneyLabel.setText(filter("💰 " + (int) game.getMoney()));
    }

    public void setStatus(String s) {
        statusLabel.setText(filter(s));
        statusLabel.setColor(new Color(200 / 255f, 240 / 255f, 200 / 255f, 1f));
    }

    public void setStatus(String s, Color c) {
        statusLabel.setText(filter(s));
        statusLabel.setColor(c);
    }

    public void setHint(String s) {
        hintLabel.setText(filter(s));
    }

    public void setCoord(Point cell) {
        coordLabel.setText(cell == null ? "—" : "(" + cell.x + ", " + cell.y + ")");
    }

    private String filter(String s) {
        return uiStage.filterEmojis(s);
    }
}
