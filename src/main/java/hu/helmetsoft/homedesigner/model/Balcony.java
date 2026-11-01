package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Local X runs along the facade; local +Z points away from it. Plan origin is the rear left corner. */
public class Balcony {
    public enum Railing {
        BARS("Pálcás"), GLASS("Üveges"), SOLID("Tömör mellvéd");
        private final String label;
        Railing(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }

    /** Substructure below the slab, reaching down to the actual (sloped) terrain. */
    public enum Support {
        NONE("Nincs alátámasztás"), PIERS("Pillérek"), SOLID("Tömör lábazat");
        private final String label;
        Support(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }

    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Erkély");
    /** Ground or storey terrace: horizontal walkable slab, not a facade balcony and not a rooftop terrace. */
    public boolean terrace;
    public Support support = Support.NONE;
    /** Pier width, also the embedment of piers/plinth below the terrain. */
    public double supportSize = 0.3;
    public double x, y, width = 3, depth = 1.5, elevation = 2.7, angle;
    public double thickness = 0.2, railingHeight = 1.1;
    public Railing railing = Railing.BARS;
    public boolean leftRail = true, rightRail = true, frontRail = true, backRail;
    public Finish floor = new Finish("tile_grey", null);
    public Finish railFinish = new Finish("steel", "#50545A");

    public double[] point(double u, double v) {
        double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
        return new double[]{x + u * c - v * s, y + u * s + v * c};
    }

    public double[][] corners() {
        return new double[][]{point(0, 0), point(width, 0), point(width, depth), point(0, depth)};
    }

    /** Terrace defaults: no railings, thin paved slab, solid plinth down to the terrain. */
    public static Balcony terrace(double x, double y, double width, double depth, double elevation) {
        Balcony t = new Balcony();
        t.terrace = true;
        t.name = I18n.display("Terasz");
        t.x = x; t.y = y; t.width = width; t.depth = depth; t.elevation = elevation;
        t.thickness = 0.15;
        t.leftRail = t.rightRail = t.frontRail = t.backRail = false;
        t.support = Support.SOLID;
        t.floor = new Finish("tile_grey", null);
        return t;
    }

    public boolean contains(double px, double py) {
        double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
        double u = (px - x) * c + (py - y) * s, v = -(px - x) * s + (py - y) * c;
        return u >= 0 && u <= width && v >= 0 && v <= depth;
    }
}
