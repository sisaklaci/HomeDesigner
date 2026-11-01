package hu.helmetsoft.homedesigner.model;

/** Parametric visualization, not a manufacturing/cutting plan. */
public class Cabinet {
    public int columns = 2, shelves = 3, drawers = 0;
    public double board = 0.018, plinth = 0.08;
    public boolean doors = true, openDoors;
    public Finish body = new Finish("oak_wood",null);
    public Finish fronts = new Finish("white_plastic",null);
    public Finish handles = new Finish("steel",null);

    public Cabinet copy() {
        Cabinet c = new Cabinet();
        c.columns=columns; c.shelves=shelves; c.drawers=drawers; c.board=board; c.plinth=plinth;
        c.doors=doors; c.openDoors=openDoors; c.body=body.copy(); c.fronts=fronts.copy(); c.handles=handles.copy();
        return c;
    }
}
