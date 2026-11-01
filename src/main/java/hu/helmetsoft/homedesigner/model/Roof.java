package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Roof with a rectangular, circular or sampled wall-following footprint. */
public class Roof {
    public enum Type {
        GABLE("Nyeregtető"), HIP("Kontyolt tető"), SHED("Félnyereg (fészer) tető"), FLAT("Lapostető"),
        CONE("Kúptető"), DOME("Kupolatető");

        public final String label;

        Type(String l) {
            label = l;
        }

        @Override
        public String toString() {
            return I18n.display(label);
        }
    }

    public Type type = Type.GABLE;
    /** Rectangle on the plan (outer wall line). */
    public double x, y, w = 8, d = 6;
    public double baseHeight = 2.7;
    public double pitch = 35;
    public double overhang = 0.5;
    public double thickness = 0.22;
    public boolean ridgeAlongX = true;
    public boolean gableWalls = true;
    public Finish finish = new Finish("roof_red", null);
    public Finish gable = new Finish("plaster", "#EFE7DA");
    public java.util.List<RoofElement> elements = new java.util.ArrayList<>();
    /** Normalized coordinates within x,y,w,d; empty retains legacy rectangular roofs. */
    public java.util.List<Pt> footprint = new java.util.ArrayList<>();
    public boolean circular;
    public double domeRise = 2;

    public boolean radial() { return type == Type.CONE || type == Type.DOME; }

    public void resize(double width, double depth) {
        w = width;
        d = circular ? width : depth;
    }
}
