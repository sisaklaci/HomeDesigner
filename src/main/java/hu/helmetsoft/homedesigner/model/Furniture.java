package hu.helmetsoft.homedesigner.model;

/** Imported 3D object placed in the plan. */
public class Furniture {
    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Bútor");
    public String modelPath;
    /** Center of footprint on the plan. */
    public double x, y;
    public double elevation;
    /** Rotation around the vertical axis in degrees (clockwise on the plan). */
    public double angle;
    public double width = 1, depth = 1, height = 1;
    /** Axis correction for models exported with another "up" axis: 0 none, 1 X+90, 2 X-90, 3 Z+90, 4 Z-90, 5 X180. */
    public int upFix;
    public boolean mirror;
    /** Optional material override for the whole model. */
    public Finish override;
    /** Non-null for an editable built-in staircase instead of an imported model. */
    public Stair stair;
    public Cabinet cabinet;
    public SolarPanel solar;
    /** Not saved: when non-zero, the size is taken from the model once it is loaded (>0 = target height, &lt;0 = natural). */
    public transient double autoSize;
}
