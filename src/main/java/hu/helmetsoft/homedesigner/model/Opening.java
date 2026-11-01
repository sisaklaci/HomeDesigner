package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Door / window opening inside a wall. */
public class Opening {
    public enum Top {
        RECTANGLE("Egyenes"), SEMICIRCLE("Félkörív"), QUARTER_LEFT("Negyedkörív – balra magas"),
        QUARTER_RIGHT("Negyedkörív – jobbra magas");
        public final String label;
        Top(String label) { this.label=label; }
        @Override public String toString() { return I18n.display(label); }
    }
    public Top top = Top.RECTANGLE;
    public enum Type {
        DOOR("Ajtó"), WINDOW("Ablak"), BALCONY_DOOR("Erkélyajtó"), PASSAGE("Nyílás (ajtó nélkül)");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return I18n.display(label);
        }
    }

    public Type type = Type.WINDOW;
    /** Center position along the wall axis, meters from wall start. */
    public double pos;
    public double width = 1.2;
    public double height = 1.4;
    /** Bottom height above the floor. */
    public double sill = 0.9;
    /** Door opens to the right side of the wall instead of the left. */
    public boolean flip;
    /** Hinge at the other jamb. */
    public boolean mirror;
    public boolean glazed;
    /** Glass rectangle as fractions of the opening width/height, measured from its left/bottom. */
    public double glassX = 0.25, glassY = 0.4, glassWidth = 0.5, glassHeight = 0.4;
    public Finish frame = new Finish("white_plastic", null);

    public static Opening create(Type t, double pos) {
        Opening o = new Opening();
        o.type = t;
        o.pos = pos;
        switch (t) {
            case DOOR -> { o.width = 0.9; o.height = 2.1; o.sill = 0; o.frame = new Finish("oak_wood", null); }
            case BALCONY_DOOR -> { o.width = 1.4; o.height = 2.2; o.sill = 0; }
            case PASSAGE -> { o.width = 1.0; o.height = 2.1; o.sill = 0; }
            default -> { o.width = 1.2; o.height = 1.4; o.sill = 0.9; }
        }
        return o;
    }

    public boolean isDoorLike() {
        return type == Type.DOOR || type == Type.PASSAGE;
    }
}
