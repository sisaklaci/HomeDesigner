package hu.helmetsoft.homedesigner.material;

import hu.helmetsoft.homedesigner.util.Texture;

import static hu.helmetsoft.homedesigner.util.Noise.*;

import java.util.stream.IntStream;

/** Procedurally generated, seamlessly tiling textures (no external assets needed). */
public final class ProceduralTextures {
    private ProceduralTextures() {
    }

    interface Shader {
        int color(double u, double v);
    }

    static Texture gen(int size, Shader s) {
        int[] px = new int[size * size];
        IntStream.range(0, size).parallel().forEach(y -> {
            for (int x = 0; x < size; x++) px[y * size + x] = s.color((x + 0.5) / size, (y + 0.5) / size);
        });
        return new Texture(size, size, px);
    }

    static double free(double x, double y, int oct, int seed) {
        double s = 0, a = 0.5, n = 0;
        for (int o = 0; o < oct; o++) {
            s += a * value(x, y, 1 << 16, 1 << 16, seed + o * 17);
            n += a;
            a *= 0.5;
            x *= 2.03;
            y *= 2.03;
        }
        return s / n;
    }

    static double smoothstep(double a, double b, double x) {
        double t = clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static int shade(double[] c, double f) {
        return rgb(c[0] * f, c[1] * f, c[2] * f);
    }

    static double[] mixc(double[] a, double[] b, double t) {
        return new double[]{mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)};
    }

    /** Wood grain color in plank local coordinates (lu along grain 0..1, lv across 0..1). */
    static double[] wood(double lu, double lv, double len, int seed, double[] light, double[] dark) {
        double n = free(lu * len * 1.5, lv * 2, 4, seed);
        double rings = 0.5 + 0.5 * Math.sin((lv * 5 + n * 6 + lu * len * 0.15) * Math.PI * 2);
        rings = Math.pow(rings, 2.2);
        double streak = free(lu * len * 18, lv * 30, 3, seed + 7);
        double t = clamp(rings * 0.55 + streak * 0.35 + (n - 0.5) * 0.4, 0, 1);
        double[] c = mixc(light, dark, t);
        double knot = free(lu * len * 0.8, lv * 0.8, 2, seed + 99);
        if (knot > 0.78) c = mixc(c, dark, (knot - 0.78) * 2.5);
        return c;
    }

    static Texture planks(int size, int rows, int perRow, boolean stagger, double[] light, double[] dark, double var, int seed, boolean vertical) {
        return gen(size, (u0, v0) -> {
            double u = vertical ? v0 : u0, v = vertical ? u0 : v0;
            int r = (int) Math.floor(v * rows);
            double lv = v * rows - r;
            double off = stagger ? Math.floor(hash(r, 3, seed) * 4) / (4.0 * perRow) * perRow : 0;
            double x = u * perRow - off;
            int k = (int) Math.floor(x);
            double lu = x - k;
            int id = Math.floorMod(k, perRow);
            int ps = seed + id * 131 + Math.floorMod(r, rows) * 977;
            double tone = hash(id, Math.floorMod(r, rows), seed);
            double[] l = mixc(light, dark, tone * var);
            double[] c = wood(lu, lv, rows / (double) perRow * 6, ps, l, dark);
            double e = Math.min(lv, 1 - lv) * 1.0 / rows * size, e2 = Math.min(lu, 1 - lu) / perRow * size;
            double f = 1.0;
            if (e < 1.2 || e2 < 1.2) f = 0.45;
            else if (e < 3 || e2 < 3) f = 0.85;
            return shade(c, f);
        });
    }

    public static Texture oak() {
        return planks(1024, 8, 2, true, new double[]{0.78, 0.60, 0.40}, new double[]{0.52, 0.34, 0.19}, 0.35, 11, false);
    }

    public static Texture walnut() {
        return planks(1024, 8, 2, true, new double[]{0.45, 0.30, 0.20}, new double[]{0.22, 0.13, 0.08}, 0.4, 23, false);
    }

    public static Texture lightLaminate() {
        return planks(1024, 5, 1, true, new double[]{0.90, 0.82, 0.68}, new double[]{0.70, 0.58, 0.42}, 0.25, 37, false);
    }

    public static Texture greyOak() {
        return planks(1024, 6, 2, true, new double[]{0.72, 0.68, 0.62}, new double[]{0.45, 0.42, 0.38}, 0.3, 41, false);
    }

    public static Texture woodGeneric() {
        return planks(512, 3, 1, false, new double[]{0.70, 0.50, 0.30}, new double[]{0.45, 0.28, 0.15}, 0.2, 5, false);
    }

    public static Texture woodPanel() {
        return planks(1024, 8, 1, false, new double[]{0.80, 0.64, 0.44}, new double[]{0.55, 0.38, 0.22}, 0.3, 61, true);
    }

    public static Texture basket() {
        double[] light = {0.80, 0.62, 0.40}, dark = {0.50, 0.33, 0.18};
        return gen(1024, (u, v) -> {
            int n = 4;
            int i = (int) Math.floor(u * n), j = (int) Math.floor(v * n);
            double cu = u * n - i, cv = v * n - j;
            boolean hor = ((i + j) & 1) == 0;
            double a = hor ? cv : cu, b = hor ? cu : cv;
            int s = (int) Math.floor(a * 4);
            double lv = a * 4 - s, lu = b;
            int seed = 300 + i * 7 + j * 13 + s * 3;
            double[] c = wood(lu, lv, 1.5, seed, mixc(light, dark, hash(i * 4 + s, j, 3) * 0.4), dark);
            double ep = Math.min(Math.min(lv, 1 - lv) / 4, Math.min(lu, 1 - lu)) / n * 1024;
            return shade(c, ep < 1.2 ? 0.5 : (ep < 3 ? 0.85 : 1));
        });
    }

    static double[] marbleColor(double u, double v, int seed, double[] base, double[] vein) {
        double t = fbm(u, v, 3, 3, 6, seed);
        double s = Math.sin((u + v * 0.6) * Math.PI * 2 * 2 + t * 12);
        double ve = Math.pow(1 - Math.abs(s), 12);
        double t2 = fbm(u, v, 5, 5, 5, seed + 5);
        double s2 = Math.sin((u * 0.5 - v) * Math.PI * 2 * 3 + t2 * 9);
        double ve2 = Math.pow(1 - Math.abs(s2), 30) * 0.6;
        double cloud = fbm(u, v, 4, 4, 4, seed + 9);
        double[] c = mixc(base, vein, clamp(ve * 0.75 + ve2, 0, 1));
        double f = 0.95 + 0.07 * cloud;
        return new double[]{c[0] * f, c[1] * f, c[2] * f};
    }

    static Texture tiles(int size, int n, double grout, double[] groutColor, ColorFn fn, double var, int seed) {
        return gen(size, (u, v) -> {
            double x = u * n, y = v * n;
            int i = (int) Math.floor(x), j = (int) Math.floor(y);
            double fx = x - i, fy = y - j;
            double edge = Math.min(Math.min(fx, 1 - fx), Math.min(fy, 1 - fy));
            if (edge < grout) {
                double g = 0.9 + 0.1 * fbm(u, v, 32, 32, 2, seed);
                return shade(groutColor, g);
            }
            double[] c = fn.at(u, v, i, j);
            double tone = 1 + (hash(i, j, seed) - 0.5) * var;
            double bevel = 0.86 + 0.14 * smoothstep(0, 0.025, edge - grout);
            return shade(c, tone * bevel);
        });
    }

    interface ColorFn {
        double[] at(double u, double v, int i, int j);
    }

    public static Texture ceramic(double[] base) {
        return tiles(1024, 2, 0.006, new double[]{0.72, 0.71, 0.69}, (u, v, i, j) -> {
            double n = fbm(u, v, 16, 16, 4, 7);
            double f = 0.96 + 0.06 * n;
            return new double[]{base[0] * f, base[1] * f, base[2] * f};
        }, 0.05, 3);
    }

    public static Texture marbleTiles() {
        return tiles(1024, 2, 0.003, new double[]{0.8, 0.79, 0.77},
                (u, v, i, j) -> marbleColor(u, v, 71, new double[]{0.95, 0.94, 0.92}, new double[]{0.42, 0.42, 0.46}), 0.03, 9);
    }

    public static Texture slate() {
        return tiles(1024, 2, 0.006, new double[]{0.2, 0.2, 0.2}, (u, v, i, j) -> {
            double n = fbm(u, v, 8, 8, 6, 17 + i * 3 + j);
            double l = free(u * 40, v * 6, 3, 19 + i + j * 5);
            double f = 0.22 + 0.10 * n + 0.05 * l;
            return new double[]{f, f * 1.02, f * 1.07};
        }, 0.25, 21);
    }

    public static Texture checker() {
        return tiles(1024, 6, 0.004, new double[]{0.6, 0.6, 0.6}, (u, v, i, j) -> {
            boolean w = ((i + j) & 1) == 0;
            return w ? marbleColor(u, v, 5, new double[]{0.94, 0.93, 0.9}, new double[]{0.6, 0.6, 0.62})
                    : marbleColor(u, v, 8, new double[]{0.08, 0.08, 0.09}, new double[]{0.35, 0.35, 0.36});
        }, 0.04, 2);
    }

    public static Texture subway() {
        return gen(1024, (u, v) -> {
            int rows = 12, per = 6;
            int r = (int) Math.floor(v * rows);
            double lv = v * rows - r;
            double x = u * per - ((r & 1) * 0.5);
            int k = (int) Math.floor(x);
            double lu = x - k;
            double eu = Math.min(lu, 1 - lu) / per * 1024, ev = Math.min(lv, 1 - lv) / rows * 1024;
            double e = Math.min(eu, ev);
            if (e < 1.6) return rgb(0.62, 0.62, 0.6);
            double bevel = 0.8 + 0.2 * smoothstep(0, 5, e - 1.6);
            double tone = 0.97 + 0.04 * hash(k, r, 4);
            double f = bevel * tone * (0.98 + 0.02 * fbm(u, v, 16, 16, 3, 1));
            return rgb(0.97 * f, 0.97 * f, 0.96 * f);
        });
    }

    public static Texture carpet() {
        return gen(512, (u, v) -> {
            double n = fbm(u, v, 128, 128, 3, 3) * 0.6 + fbm(u, v, 8, 8, 4, 5) * 0.4;
            double f = 0.78 + 0.25 * n;
            return rgb(f, f, f);
        });
    }

    public static Texture concrete() {
        return gen(1024, (u, v) -> {
            double n = fbm(u, v, 4, 4, 7, 13);
            double sp = fbm(u, v, 128, 128, 2, 31);
            double f = 0.56 + 0.18 * n - (sp > 0.78 ? 0.12 : 0);
            return rgb(f, f * 0.99, f * 0.97);
        });
    }

    public static Texture plaster() {
        return gen(512, (u, v) -> {
            double f = 0.95 + 0.05 * fbm(u, v, 16, 16, 5, 77);
            return rgb(f, f, f);
        });
    }

    public static Texture stripes() {
        return gen(512, (u, v) -> {
            double s = (u * 8) % 1.0;
            double f = s < 0.5 ? 1.0 : 0.86;
            if (Math.abs(s - 0.5) < 0.012 || s < 0.012) f = 0.78;
            f *= 0.97 + 0.03 * fbm(u, v, 32, 32, 3, 2);
            return rgb(f, f, f);
        });
    }

    public static Texture damask() {
        return gen(1024, (u, v) -> {
            double a = Math.abs(Math.sin(u * Math.PI * 2 * 4) * Math.sin(v * Math.PI * 2 * 3));
            double b = Math.abs(Math.cos((u * 4 + v * 3) * Math.PI * 2)) * Math.abs(Math.cos((u * 4 - v * 3) * Math.PI * 2));
            double m = smoothstep(0.42, 0.47, a * 0.6 + b * 0.4);
            double f = mix(1.0, 0.84, m) * (0.97 + 0.03 * fbm(u, v, 32, 32, 3, 8));
            return rgb(f, f, f);
        });
    }

    static Texture bricks(double[] a, double[] b, double[] mortar, int seed) {
        return gen(1024, (u, v) -> {
            int rows = 14, per = 4;
            int r = (int) Math.floor(v * rows);
            double lv = v * rows - r;
            double x = u * per - ((r & 1) * 0.5);
            int k = (int) Math.floor(x);
            double lu = x - k;
            double eu = Math.min(lu, 1 - lu) / per, ev = Math.min(lv, 1 - lv) / rows;
            double e = Math.min(eu, ev) * 1.04;
            if (e < 0.005) {
                double g = 0.85 + 0.2 * fbm(u, v, 64, 64, 3, seed);
                return shade(mortar, g);
            }
            double[] c = mixc(a, b, hash(Math.floorMod(k, per), r, seed));
            double n = 0.82 + 0.25 * fbm(u, v, 32, 32, 5, seed + 3);
            double sp = fbm(u, v, 256, 256, 1, seed + 9) > 0.8 ? 0.85 : 1;
            double bevel = 0.8 + 0.2 * smoothstep(0, 0.004, e - 0.005);
            return shade(c, n * sp * bevel);
        });
    }

    public static Texture brickRed() {
        return bricks(new double[]{0.58, 0.27, 0.18}, new double[]{0.42, 0.18, 0.12}, new double[]{0.75, 0.73, 0.68}, 3);
    }

    public static Texture brickWhite() {
        return bricks(new double[]{0.93, 0.92, 0.89}, new double[]{0.82, 0.80, 0.77}, new double[]{0.7, 0.69, 0.67}, 8);
    }

    public static Texture stone() {
        int n = 5;
        return gen(1024, (u, v) -> {
            double x = u * n, y = v * n;
            int ci = (int) Math.floor(x), cj = (int) Math.floor(y);
            double f1 = 9, f2 = 9;
            int id = 0;
            for (int dj = -1; dj <= 1; dj++)
                for (int di = -1; di <= 1; di++) {
                    int i = ci + di, j = cj + dj;
                    int wi = Math.floorMod(i, n), wj = Math.floorMod(j, n);
                    double px = i + 0.15 + 0.7 * hash(wi, wj, 11), py = j + 0.15 + 0.7 * hash(wi, wj, 12);
                    double d = Math.hypot((x - px) * 0.8, y - py);
                    if (d < f1) { f2 = f1; f1 = d; id = wi * 31 + wj; }
                    else if (d < f2) f2 = d;
                }
            double gap = f2 - f1;
            if (gap < 0.06) return rgb(0.32, 0.31, 0.29);
            double t = hash(id, 1, 5);
            double[] c = mixc(new double[]{0.72, 0.68, 0.6}, new double[]{0.5, 0.48, 0.45}, t);
            double nn = 0.8 + 0.3 * fbm(u, v, 16, 16, 5, id);
            double bevel = 0.7 + 0.3 * smoothstep(0.06, 0.2, gap);
            return shade(c, nn * bevel);
        });
    }

    static Texture roofTiles(double[] a, double[] b, int seed) {
        return gen(1024, (u, v) -> {
            int rows = 6, per = 6;
            int r = (int) Math.floor(v * rows);
            double lv = v * rows - r;
            double x = u * per - ((r & 1) * 0.5);
            int k = (int) Math.floor(x);
            double lu = x - k;
            double curve = 0.62 + 0.38 * Math.sin(Math.PI * lu);
            double overlap = 0.45 + 0.55 * smoothstep(0, 0.28, lv);
            double edge = lv > 0.96 ? 0.7 : 1;
            double[] c = mixc(a, b, hash(Math.floorMod(k, per), Math.floorMod(r, rows), seed) * 0.8);
            double n = 0.88 + 0.18 * fbm(u, v, 32, 32, 4, seed + 1);
            return shade(c, curve * overlap * edge * n);
        });
    }

    public static Texture roofRed() {
        return roofTiles(new double[]{0.66, 0.27, 0.16}, new double[]{0.48, 0.18, 0.11}, 4);
    }

    public static Texture roofAnthracite() {
        return roofTiles(new double[]{0.27, 0.28, 0.30}, new double[]{0.16, 0.17, 0.18}, 6);
    }

    public static Texture shingles() {
        return gen(1024, (u, v) -> {
            int rows = 8, per = 5;
            int r = (int) Math.floor(v * rows);
            double lv = v * rows - r;
            double off = hash(r, 0, 3) * 0.6;
            double x = u * per - off;
            int k = (int) Math.floor(x);
            double lu = x - k;
            if (Math.min(lu, 1 - lu) * 1024 / per < 2.5 && lv > 0.3) return rgb(0.05, 0.05, 0.05);
            double t = hash(Math.floorMod(k, per), Math.floorMod(r, rows), 9);
            double base = 0.18 + 0.12 * t;
            double over = 0.55 + 0.45 * smoothstep(0, 0.35, lv);
            double n = 0.85 + 0.3 * fbm(u, v, 64, 64, 3, 2);
            double f = base * over * n;
            return rgb(f * 1.15, f * 0.95, f * 0.85);
        });
    }

    public static Texture metalRoof() {
        return gen(512, (u, v) -> {
            double s = (u * 6) % 1.0;
            double f = 0.9 + 0.04 * fbm(u, v, 2, 64, 3, 6);
            if (s < 0.025) f = 1.15;
            else if (s < 0.05) f = 0.6;
            return rgb(f * 0.85, f * 0.85, f * 0.85);
        });
    }

    public static Texture grass() {
        return gen(1024, (u, v) -> {
            double n = fbm(u, v, 8, 8, 5, 3);
            double blades = fbm(u, v, 256, 96, 2, 9);
            double t = clamp(n * 0.6 + blades * 0.5, 0, 1);
            double[] c = mixc(new double[]{0.10, 0.22, 0.05}, new double[]{0.36, 0.52, 0.16}, t);
            double dry = fbm(u, v, 4, 4, 3, 21);
            if (dry > 0.62) c = mixc(c, new double[]{0.45, 0.45, 0.22}, (dry - 0.62) * 1.5);
            return rgb(c[0], c[1], c[2]);
        });
    }

    public static Texture soil() {
        return gen(512,(u,v) -> {
            double lumps=fbm(u,v,12,12,5,73),grit=fbm(u,v,128,128,2,91);
            double[] c=mixc(new double[]{0.12,0.065,0.025},new double[]{0.43,0.29,0.15},
                    clamp(lumps*0.75+grit*0.35,0,1));
            return rgb(c[0],c[1],c[2]);
        });
    }

    public static Texture fabric() {
        return gen(512, (u, v) -> {
            double wx = Math.sin(u * Math.PI * 2 * 96), wy = Math.sin(v * Math.PI * 2 * 96);
            double weave = ((wx > 0) ^ (wy > 0)) ? 0.92 + 0.08 * Math.abs(wx) : 0.84 + 0.08 * Math.abs(wy);
            double n = 0.9 + 0.1 * fbm(u, v, 16, 16, 4, 12);
            double f = weave * n;
            return rgb(f, f, f);
        });
    }

    public static Texture leather() {
        return gen(512, (u, v) -> {
            double n = fbm(u, v, 48, 48, 3, 14);
            double c = Math.abs(n - 0.5) < 0.03 ? 0.82 : 0.95;
            double f = c * (0.92 + 0.08 * fbm(u, v, 8, 8, 3, 2));
            return rgb(f, f, f);
        });
    }

    /** Converts a height image (luminance) into a tangent space normal map. */
    public static Texture normalMap(Texture t, double strength) {
        int w = t.w, h = t.h;
        double[] hg = new double[w * h];
        for (int i = 0; i < w * h; i++) {
            int c = t.argb[i];
            hg[i] = (((c >> 16) & 255) * 0.3 + ((c >> 8) & 255) * 0.59 + (c & 255) * 0.11) / 255.0;
        }
        int[] o = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double dx = hg[y * w + (x + 1) % w] - hg[y * w + (x - 1 + w) % w];
                double dy = hg[((y + 1) % h) * w + x] - hg[((y - 1 + h) % h) * w + x];
                double nx = -dx * strength, ny = -dy * strength, nz = 1;
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                o[y * w + x] = rgb(nx / l * 0.5 + 0.5, ny / l * 0.5 + 0.5, nz / l * 0.5 + 0.5);
            }
        return new Texture(w, h, o);
    }
}
