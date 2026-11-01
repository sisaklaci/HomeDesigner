package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Free-standing retaining wall between two plan points, owned by a storey.
 * The top is storey-relative unless {@link #topAbsolute}; {@link #bottom} uses the same datum. */
public class RetainingWall {
    /** Side of the drawn line occupied by the wall body; LEFT is the +normal (-dy, dx) side. */
    public enum Side {
        CENTER("Középen"), LEFT("Bal oldalon"), RIGHT("Jobb oldalon");
        public final String label;
        Side(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }

    public String name = I18n.display("Támfal");
    public double x1, y1, x2 = 3, y2;
    public double thickness = 0.25;
    public Side side = Side.CENTER;
    public double top = 1.0;
    public boolean topAbsolute;
    /** True: bottom follows the natural terrain minus {@link #embedment}; false: fixed {@link #bottom}. */
    public boolean followTerrain = true;
    public double embedment = 0.3;
    public double bottom = -0.5;
    public boolean footing = true;
    public double footingWidth = 0.7, footingHeight = 0.3;
    public Finish finish = new Finish("concrete", null);

    public double length() { return Math.hypot(x2 - x1, y2 - y1); }

    public double angle() { return Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)); }

    /** Plan offsets {from, to} of the wall body measured along the left normal. */
    public double[] offsets() {
        return switch (side == null ? Side.CENTER : side) {
            case LEFT -> new double[]{0, thickness};
            case RIGHT -> new double[]{-thickness, 0};
            default -> new double[]{-thickness / 2, thickness / 2};
        };
    }

    /** Plan point at distance s along the wall and offset o along the left normal. */
    public double[] point(double s, double o) {
        double l = length(), dx = l < 1e-9 ? 1 : (x2 - x1) / l, dy = l < 1e-9 ? 0 : (y2 - y1) / l;
        return new double[]{x1 + dx * s - dy * o, y1 + dy * s + dx * o};
    }

    /** Plan outline of the body: start-from, end-from, end-to, start-to. */
    public double[][] corners() {
        double[] o = offsets();
        double l = length();
        return new double[][]{point(0, o[0]), point(l, o[0]), point(l, o[1]), point(0, o[1])};
    }

    public boolean contains(double px, double py, double tolerance) {
        double l = length();
        if (l < 1e-9) return Math.hypot(px - x1, py - y1) <= tolerance;
        double dx = (x2 - x1) / l, dy = (y2 - y1) / l, u = (px - x1) * dx + (py - y1) * dy, v = -(px - x1) * dy + (py - y1) * dx;
        double[] o = offsets();
        return u >= -tolerance && u <= l + tolerance && v >= o[0] - tolerance && v <= o[1] + tolerance;
    }

    public RetainingWall copy() {
        RetainingWall w = new RetainingWall();
        w.name = name; w.x1 = x1; w.y1 = y1; w.x2 = x2; w.y2 = y2; w.thickness = thickness; w.side = side;
        w.top = top; w.topAbsolute = topAbsolute; w.followTerrain = followTerrain; w.embedment = embedment; w.bottom = bottom;
        w.footing = footing; w.footingWidth = footingWidth; w.footingHeight = footingHeight;
        w.finish = finish == null ? null : finish.copy();
        return w;
    }
}