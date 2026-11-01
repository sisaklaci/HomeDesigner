package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

import java.util.ArrayList;
import java.util.List;

/** Independent plan-space cutout, owned by exactly one room. */
public class SlabOpening {
    public enum Shape {
        RECTANGLE("Téglalap"), CIRCLE("Kör"), ELLIPSE("Ovális"), POLYGON("Szabad sokszög");
        public final String label;
        Shape(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }
    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Födémnyílás");
    public Shape shape = Shape.RECTANGLE;
    /** Centre, dimensions and clockwise plan rotation, in metres/degrees. */
    public double x, y, width = 1.2, depth = 3, angle;
    public boolean cutCeiling = true, cutFloor;
    /** Polygon coordinates in the unit square; transformed with the opening bounds. */
    public List<Pt> points = new ArrayList<>();

    public double[] point(double u, double v) {
        double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
        double dx = (u-0.5)*width, dy = (v-0.5)*depth;
        return new double[]{x+c*dx-s*dy,y+s*dx+c*dy};
    }

    public SlabOpening copy() {
        SlabOpening o = new SlabOpening();
        o.name = name; o.shape = shape; o.x = x; o.y = y; o.width = width; o.depth = depth; o.angle = angle;
        o.cutCeiling = cutCeiling; o.cutFloor = cutFloor;
        for (Pt p : points) o.points.add(new Pt(p.x,p.y));
        return o;
    }
}
