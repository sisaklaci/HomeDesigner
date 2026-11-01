package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

/** Rectangular opening, in plan metres relative to the roof's wall-line origin. */
public class RoofElement {
    public enum Type {
        SKYLIGHT("Tetősíkablak"), DORMER("Kiugró tetőablak"), TERRACE("Süllyesztett tetőterasz");
        public final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }

    public enum Cover {
        GABLE("Nyeregtetős"), SHED("Félnyeregtetős"), FLAT("Lapos");
        private final String label;
        Cover(String label) { this.label = label; }
        @Override public String toString() { return I18n.display(label); }
    }

    public Type type = Type.SKYLIGHT;
    public Cover cover = Cover.GABLE;
    public double x = 1, y = 1, width = 0.9, depth = 1.2;
    public double height = 0.6;
    public double floorDrop = 0.7;
    public double railingHeight = 1.1;
    public Balcony.Railing railing = Balcony.Railing.BARS;
    public Finish frame = new Finish("white_plastic", null);
    public Finish wall = new Finish("plaster", "#EFE7DA");
    public Finish floor = new Finish("tile_grey", null);
}
