package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Parametric construction settings on a placed Furniture; its height is floor-to-floor rise. */
public class Stair {
    public enum Type {
        STRAIGHT("Egyenes lépcső"), SPIRAL("Csigalépcső");
        public final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }
    public Type type = Type.STRAIGHT;
    public int steps = 16;
    public double treadThickness = 0.05;
    public double turn = 360;
    public double innerRadius = 0.12;
    public boolean clockwise = true;
    public boolean railing = true;
    public boolean solid;
    public double railingHeight = 0.9;
    public Finish treads = new Finish("oak_wood",null);
    public Finish frame = new Finish("steel", "#3D4248");

    public Stair copy() {
        Stair s = new Stair();
        s.type = type; s.steps = steps; s.treadThickness = treadThickness; s.turn = turn; s.innerRadius = innerRadius;
        s.clockwise = clockwise; s.railing = railing; s.railingHeight = railingHeight; s.solid=solid;
        s.treads = treads.copy(); s.frame = frame.copy();
        return s;
    }
}
