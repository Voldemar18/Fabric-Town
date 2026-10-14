package ru.student.fabricTown.visualization;

public class Camera {
    public double offsetX = 0, offsetY = 0;
    public double zoom = 32.0;   // пикселей на клетку
    public double targetZoom = 32.0;
    public double targetOffsetX = 0, targetOffsetY = 0;

    public int worldToScreenX(double wx) { return (int)((wx - offsetX) * zoom); }
    public int worldToScreenY(double wy) { return (int)((wy - offsetY) * zoom); }
    public double screenToWorldX(int sx) { return sx / zoom + offsetX; }
    public double screenToWorldY(int sy) { return sy / zoom + offsetY; }

    public void update(double dt) {
        zoom += (targetZoom - zoom) * 0.2;
        offsetX += (targetOffsetX - offsetX) * 0.15;
        offsetY += (targetOffsetY - offsetY) * 0.15;
    }
}