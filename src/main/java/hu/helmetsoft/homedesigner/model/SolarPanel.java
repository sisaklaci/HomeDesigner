package hu.helmetsoft.homedesigner.model;

/** Editable panel dimensions; the generated supports follow the tilt. */
public class SolarPanel {
    public enum Type { ROOF, GROUND }
    public Type type=Type.ROOF;
    public double tilt=30;
    public double width=1.1, length=1.8;

    public SolarPanel copy() {
        SolarPanel copy=new SolarPanel();
        copy.type=type; copy.tilt=tilt; copy.width=width; copy.length=length;
        return copy;
    }
}
