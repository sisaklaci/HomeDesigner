package hu.helmetsoft.homedesigner.material;

import hu.helmetsoft.homedesigner.util.Texture;

/** Fully resolved material used by the 3D viewer and the path tracer. */
public final class SurfaceMaterial {
    public final String key;
    public Texture albedo;
    public Texture bump;
    /** Tint color (sRGB 0..1). */
    public double[] color = {1, 1, 1};
    /** Meters covered by one texture repeat (structure UVs are divided by this). */
    public double tileSize = 1;
    public double roughness = 0.6;
    /** Strength of the dielectric reflection (0..1). */
    public double specular = 0.3;
    public boolean metal;
    public boolean glass;
    public double opacity = 1;
    /** Emitted radiance (linear), null = none. */
    public double[] emission;

    public SurfaceMaterial(String key) {
        this.key = key;
    }

    /** Linear-space tint. */
    public float[] linearColor() {
        return new float[]{lin(color[0]), lin(color[1]), lin(color[2])};
    }

    static float lin(double c) {
        return (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    }
}
