package hu.helmetsoft.homedesigner.model;

/** Lamp: spherical light used by the photo renderer (shown as glowing bulb in 3D). */
public class LightSource {
    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Lámpa");
    public double x, y;
    public double height = 2.4;
    public double power = 1.0;
    public String color = "#FFE2B8";
    public double radius = 0.06;
}
