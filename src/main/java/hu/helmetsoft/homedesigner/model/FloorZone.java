package hu.helmetsoft.homedesigner.model;

/** A local floor level relative to its parent room, with an independent plan outline. */
public class FloorZone {
    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Padlószint-terület");
    public SlabOpening outline = new SlabOpening();
    public double offset = -0.34;
    public Finish finish = new Finish("oak",null);

    public FloorZone copy() {
        FloorZone z = new FloorZone();
        z.name=name; z.outline=outline.copy(); z.offset=offset; z.finish=finish.copy();
        return z;
    }
}
