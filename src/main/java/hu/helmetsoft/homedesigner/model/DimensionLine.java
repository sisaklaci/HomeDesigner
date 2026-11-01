package hu.helmetsoft.homedesigner.model;

/** Free measurement line (méretvonal). */
public class DimensionLine {
    public double x1, y1, x2, y2;
    public double offset = 0;

    public DimensionLine() {
    }

    public DimensionLine(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public double length() {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
