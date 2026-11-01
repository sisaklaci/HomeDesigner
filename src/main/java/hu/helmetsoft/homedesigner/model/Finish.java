package hu.helmetsoft.homedesigner.model;

/** Surface covering (burkolat): a material preset, optional tint color, optional custom texture image. */
public class Finish {
    public String preset = "plaster";
    public String color;      // "#RRGGBB" tint, null = preset default
    public String texture;    // custom texture file (preset "custom")
    public double scale = 1.0; // pattern size multiplier

    public Finish() {
    }

    public Finish(String preset, String color) {
        this.preset = preset;
        this.color = color;
    }

    public Finish copy() {
        Finish f = new Finish(preset, color);
        f.texture = texture;
        f.scale = scale;
        return f;
    }

    public String key() {
        return preset + "|" + color + "|" + texture + "|" + scale;
    }
}
