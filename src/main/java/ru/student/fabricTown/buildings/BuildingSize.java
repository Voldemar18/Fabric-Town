package ru.student.fabricTown.buildings;

public enum BuildingSize {
    S1x1(1,1), S1x2(1,2), S2x1(2,1), S2x2(2,2), S3x3(3,3),
    S4x4(4,4), S5x5(5,5), S10x10(10,10);

    public final int w, h;
    BuildingSize(int w, int h) { this.w = w; this.h = h; }
    public int area() { return w * h; }
}