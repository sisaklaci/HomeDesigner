package hu.helmetsoft.homedesigner.util;

/** Tileable value noise helpers for procedural textures. */
public final class Noise {
    private Noise() {
    }

    public static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0x7fffffff) / (double) 0x7fffffff;
    }

    private static double smooth(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Value noise in [0,1], periodic with period px, py (lattice units). */
    public static double value(double x, double y, int px, int py, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        int ax = Math.floorMod(x0, px), bx = Math.floorMod(x0 + 1, px);
        int ay = Math.floorMod(y0, py), by = Math.floorMod(y0 + 1, py);
        double a = hash(ax, ay, seed), b = hash(bx, ay, seed), c = hash(ax, by, seed), d = hash(bx, by, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    /** Fractal noise of texture coordinates u,v in [0,1) tiling, base frequency fx, fy (integers). */
    public static double fbm(double u, double v, int fx, int fy, int octaves, int seed) {
        double s = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++) {
            s += amp * value(u * fx, v * fy, fx, fy, seed + o * 31);
            norm += amp;
            amp *= 0.5;
            fx *= 2;
            fy *= 2;
        }
        return s / norm;
    }

    public static int rgb(double r, double g, double b) {
        return 0xFF000000 | c8(r) << 16 | c8(g) << 8 | c8(b);
    }

    public static int c8(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    public static double clamp(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }

    public static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double[] hex(String hex, double[] def) {
        if (hex == null || hex.length() < 7) return def;
        try {
            int v = Integer.parseInt(hex.substring(1, 7), 16);
            return new double[]{((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0};
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String toHex(double r, double g, double b) {
        return String.format("#%02X%02X%02X", c8(r), c8(g), c8(b));
    }
}
