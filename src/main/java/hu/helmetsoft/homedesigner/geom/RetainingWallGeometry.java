package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/** Solid retaining walls and terrace substructures that reach the actual sloped terrain. World: Y up, plan (x,y) -> (x,z). */
public final class RetainingWallGeometry {
    private RetainingWallGeometry() {}

    static final double STEP = 0.5;
    public static final int CURVE_SEGMENTS = 24;

    public static void validate(RetainingWall w) {
        if (w == null || w.name == null || w.finish == null)
            throw new IllegalArgumentException(I18n.text("Hiányzó támfal, név vagy burkolat."));
        if (w.side == null) w.side = RetainingWall.Side.CENTER;
        for (double v : new double[]{w.x1, w.y1, w.x2, w.y2})
            if (!Double.isFinite(v) || Math.abs(v) > 10000) throw new IllegalArgumentException(I18n.text("Érvénytelen támfal-végpont."));
        if (w.length() < 0.1 || w.length() > 500)
            throw new IllegalArgumentException(I18n.text("A támfal hossza 10 cm és 500 m között legyen."));
        if (!Double.isFinite(w.thickness) || w.thickness < 0.05 || w.thickness > 2)
            throw new IllegalArgumentException(I18n.text("A támfal vastagsága 5–200 cm legyen."));
        if (!Double.isFinite(w.top) || Math.abs(w.top) > 100 || !Double.isFinite(w.bottom) || Math.abs(w.bottom) > 100)
            throw new IllegalArgumentException(I18n.text("A támfal szintjei −100 és +100 m között legyenek."));
        if (!Double.isFinite(w.embedment) || w.embedment < 0 || w.embedment > 5)
            throw new IllegalArgumentException(I18n.text("A támfal földbe nyúlása 0–500 cm legyen."));
        if (!w.followTerrain && w.bottom > w.top - 0.05)
            throw new IllegalArgumentException(I18n.text("A támfal alja legalább 5 cm-rel a teteje alatt legyen."));
        if (w.footing && (!Double.isFinite(w.footingWidth) || w.footingWidth < w.thickness || w.footingWidth > 5
                || !Double.isFinite(w.footingHeight) || w.footingHeight < 0.05 || w.footingHeight > 2))
            throw new IllegalArgumentException(I18n.text("A sávalap legalább falvastag, legfeljebb 5 m széles és 5–200 cm magas legyen."));
    }

    public static void validate(Balcony b) {
        if (b.support == null) b.support = Balcony.Support.NONE;
        if (!Double.isFinite(b.supportSize) || b.supportSize < 0.1 || b.supportSize > 2)
            throw new IllegalArgumentException(I18n.text("Az alátámasztás mérete 10–200 cm legyen."));
    }

    /** Natural (unfilled) visual terrain: the lower, visible side of a wall holding a fill. */
    public static double ground(Environment env, double x, double z) {
        return TerrainGeometry.height(env, x, z);
    }

    public static double topWorld(RetainingWall w, double base) {
        return w.topAbsolute ? w.top : base + w.top;
    }

    /** World bottom of the wall body (footing top) at a plan point. */
    public static double bottomWorld(RetainingWall w, Environment env, double base, double x, double z) {
        double b = w.followTerrain ? ground(env, x, z) - w.embedment : w.topAbsolute ? w.bottom : base + w.bottom;
        return Math.min(b, topWorld(w, base) - 0.05);
    }

    /** Builds a storey-owned wall in world coordinates; owner is the original wall for picking. */
    public static List<GeoPart> build(RetainingWall w, Environment env, double base) {
        List<GeoPart> out = new ArrayList<>();
        double L = w.length();
        if (L < 1e-3) return out;
        MeshBuilder mb = new MeshBuilder(MaterialLibrary.resolve(w.finish).tileSize);
        int n = Math.max(1, (int) Math.ceil(L / STEP));
        double[] s = new double[n + 1], tops = new double[n + 1], bottoms = new double[n + 1];
        double top = topWorld(w, base);
        double[] o = w.offsets();
        double mid = (o[0] + o[1]) / 2;
        for (int i = 0; i <= n; i++) {
            s[i] = L * i / n;
            double[] p = w.point(s[i], mid);
            tops[i] = top;
            bottoms[i] = bottomWorld(w, env, base, p[0], p[1]);
        }
        strip(mb, w, s, o[0], o[1], tops, bottoms);
        if (w.footing) {
            double[] ft = bottoms.clone(), fb = new double[n + 1];
            for (int i = 0; i <= n; i++) fb[i] = ft[i] - w.footingHeight;
            strip(mb, w, s, mid - w.footingWidth / 2, mid + w.footingWidth / 2, ft, fb);
        }
        out.add(new GeoPart(w, MaterialLibrary.resolve(w.finish), mb.build()));
        return out;
    }

    /** Closed prism along the wall line between plan offsets oa<ob, flat or sampled top/bottom. */
    static void strip(MeshBuilder mb, RetainingWall w, double[] s, double oa, double ob, double[] tops, double[] bottoms) {
        int n = s.length - 1;
        double l = w.length(), dx = (w.x2 - w.x1) / l, dz = (w.y2 - w.y1) / l;
        double[] along = {dx, 0, dz}, left = {-dz, 0, dx}, up = {0, 1, 0};
        for (int i = 0; i < n; i++) {
            double[] a0 = w.point(s[i], oa), a1 = w.point(s[i + 1], oa), b0 = w.point(s[i], ob), b1 = w.point(s[i + 1], ob);
            double t0 = tops[i], t1 = tops[i + 1], u0 = bottoms[i], u1 = bottoms[i + 1];
            face(mb, p(b0, u0), p(b1, u1), p(b1, t1), p(b0, t0), left, along, up);
            face(mb, p(a0, u0), p(a1, u1), p(a1, t1), p(a0, t0), neg(left), along, up);
            hface(mb, p(a0, t0), p(a1, t1), p(b1, t1), p(b0, t0), true, along, left);
            hface(mb, p(a0, u0), p(a1, u1), p(b1, u1), p(b0, u0), false, along, left);
        }
        double[] a = w.point(s[0], oa), b = w.point(s[0], ob), c = w.point(s[n], oa), d = w.point(s[n], ob);
        face(mb, p(a, bottoms[0]), p(b, bottoms[0]), p(b, tops[0]), p(a, tops[0]), neg(along), left, up);
        face(mb, p(c, bottoms[n]), p(d, bottoms[n]), p(d, tops[n]), p(c, tops[n]), along, left, up);
    }

    private static double[] p(double[] plan, double y) { return new double[]{plan[0], y, plan[1]}; }

    private static double[] neg(double[] v) { return new double[]{-v[0], -v[1], -v[2]}; }

    /** Planar quad with the given outward normal; winding is flipped to match it (counter-clockwise seen from outside). */
    static void face(MeshBuilder mb, double[] a, double[] b, double[] c, double[] d, double[] normal, double[] tu, double[] tv) {
        double[] cr = cross(sub(b, a), sub(c, a));
        if (len(cr) < 1e-12) cr = cross(sub(c, a), sub(d, a));
        if (len(cr) < 1e-12) return;
        if (dot(cr, normal) < 0) { double[] t = b; b = d; d = t; }
        mb.quad(a, b, c, d, normal, tu, tv);
    }

    static void hface(MeshBuilder mb, double[] a, double[] b, double[] c, double[] d, boolean upward, double[] tu, double[] tv) {
        double[] cr = cross(sub(b, a), sub(c, a));
        if (len(cr) < 1e-12) cr = cross(sub(c, a), sub(d, a));
        if (len(cr) < 1e-12) return;
        double[] nrm = norm(cr);
        if ((nrm[1] < 0) == upward) { nrm = neg(nrm); double[] t = b; b = d; d = t; }
        mb.quad(a, b, c, d, nrm, tu, tv);
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] norm(double[] a) { double l = len(a); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    // ------------------------------------------------------------ terrace substructure

    /** Piers or a solid plinth below a balcony/terrace slab, in storey-local coordinates.
     * {@code groundLocal} returns the actual terrain surface relative to the storey base. */
    public static void support(MeshBuilder mb, Balcony b, DoubleBinaryOperator groundLocal) {
        if (b.support == null || b.support == Balcony.Support.NONE) return;
        double slabBottom = b.elevation - b.thickness;
        double a = Math.toRadians(b.angle);
        double[] u = {Math.cos(a), 0, Math.sin(a)}, v = {-Math.sin(a), 0, Math.cos(a)}, up = {0, 1, 0};
        if (b.support == Balcony.Support.PIERS) {
            double size = Math.min(b.supportSize, Math.min(b.width, b.depth));
            int nu = Math.max(1, (int) Math.ceil((b.width - size) / 2.5)), nv = Math.max(1, (int) Math.ceil((b.depth - size) / 2.5));
            for (int i = 0; i <= nu; i++)
                for (int j = 0; j <= nv; j++) {
                    double pu = (b.width - size) * i / nu, pv = (b.depth - size) * j / nv;
                    double lowest = Double.POSITIVE_INFINITY;
                    for (double[] c : new double[][]{{pu, pv}, {pu + size, pv}, {pu, pv + size}, {pu + size, pv + size}}) {
                        double[] q = b.point(c[0], c[1]);
                        lowest = Math.min(lowest, groundLocal.applyAsDouble(q[0], q[1]));
                    }
                    double bottom = lowest - b.supportSize;
                    if (slabBottom - bottom < 0.01) continue;
                    double[] q = b.point(pu, pv);
                    mb.box(new double[]{q[0], bottom, q[1]}, u, up, v, size, slabBottom - bottom, size);
                }
            return;
        }
        // SOLID: plinth under the whole slab whose bottom follows the terrain
        int nu = Math.max(1, (int) Math.ceil(b.width / STEP)), nv = Math.max(1, (int) Math.ceil(b.depth / STEP));
        double[][] bottom = new double[nu + 1][nv + 1];
        boolean any = false;
        for (int i = 0; i <= nu; i++)
            for (int j = 0; j <= nv; j++) {
                double[] q = b.point(b.width * i / nu, b.depth * j / nv);
                bottom[i][j] = Math.min(slabBottom - 0.01, groundLocal.applyAsDouble(q[0], q[1]) - b.supportSize);
                if (slabBottom - bottom[i][j] > 0.011) any = true;
            }
        if (!any) return;
        java.util.function.BiFunction<Integer, Integer, double[]> lo = (i, j) -> {
            double[] q = b.point(b.width * i / nu, b.depth * j / nv);
            return new double[]{q[0], bottom[i][j], q[1]};
        };
        java.util.function.BiFunction<Integer, Integer, double[]> hi = (i, j) -> {
            double[] q = b.point(b.width * i / nu, b.depth * j / nv);
            return new double[]{q[0], slabBottom, q[1]};
        };
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nv; j++)
                hface(mb, lo.apply(i, j), lo.apply(i + 1, j), lo.apply(i + 1, j + 1), lo.apply(i, j + 1), false, u, v);
        for (int i = 0; i < nu; i++) {
            face(mb, lo.apply(i, 0), lo.apply(i + 1, 0), hi.apply(i + 1, 0), hi.apply(i, 0), neg(v), u, up);
            face(mb, lo.apply(i, nv), lo.apply(i + 1, nv), hi.apply(i + 1, nv), hi.apply(i, nv), v, u, up);
        }
        for (int j = 0; j < nv; j++) {
            face(mb, lo.apply(0, j), lo.apply(0, j + 1), hi.apply(0, j + 1), hi.apply(0, j), neg(u), v, up);
            face(mb, lo.apply(nu, j), lo.apply(nu, j + 1), hi.apply(nu, j + 1), hi.apply(nu, j), u, v, up);
        }
    }

    // ------------------------------------------------------------ fill perimeter

    /** Plan outline of a fill, circles/ellipses tessellated to {@link #CURVE_SEGMENTS} sides, counter-clockwise in (x,y). */
    public static double[][] outline(SiteFill fill) {
        SlabOpening o = fill.outline;
        double[][] ring;
        if (o.shape == SlabOpening.Shape.CIRCLE || o.shape == SlabOpening.Shape.ELLIPSE) {
            ring = new double[CURVE_SEGMENTS][];
            for (int i = 0; i < CURVE_SEGMENTS; i++) {
                double t = 2 * Math.PI * i / CURVE_SEGMENTS;
                ring[i] = o.point(0.5 + 0.5 * Math.cos(t), 0.5 + 0.5 * Math.sin(t));
            }
        } else ring = SlabGeometry.boundary(o);
        double area = 0;
        for (int i = 0; i < ring.length; i++) {
            double[] p = ring[i], q = ring[(i + 1) % ring.length];
            area += p[0] * q[1] - q[0] * p[1];
        }
        if (area < 0) {
            double[][] r = new double[ring.length][];
            for (int i = 0; i < ring.length; i++) r[i] = ring[ring.length - 1 - i];
            ring = r;
        }
        return ring;
    }

    /** Walls around a fill, body outside the outline so the flat fill top stays visible. Tops are absolute
     * at the fill datum; bottoms follow the natural terrain. The caller decides ownership and the vertical fill edge. */
    public static List<RetainingWall> aroundFill(SiteFill fill, double thickness) {
        double[][] ring = outline(fill);
        List<RetainingWall> out = new ArrayList<>();
        int n = ring.length;
        for (int i = 0; i < n; i++) {
            double[] p = ring[i], q = ring[(i + 1) % n];
            double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
            if (len < 0.1) continue;
            double dx = (q[0] - p[0]) / len, dy = (q[1] - p[1]) / len;
            // extend into convex corners so the outside bodies of neighbouring walls meet without a gap
            double start = cornerExtension(ring[(i + n - 1) % n], p, q, thickness);
            double end = cornerExtension(p, q, ring[(i + 2) % n], thickness);
            RetainingWall w = new RetainingWall();
            w.name = fill.name + " – " + I18n.display("Támfal") + " " + (out.size() + 1);
            w.x1 = p[0] - dx * start; w.y1 = p[1] - dy * start; w.x2 = q[0] + dx * end; w.y2 = q[1] + dy * end;
            w.thickness = thickness;
            // counter-clockwise ring: interior on the left, so the body goes to the right (outside)
            w.side = RetainingWall.Side.RIGHT;
            w.topAbsolute = true;
            w.top = fill.elevation;
            w.bottom = fill.elevation - 1;
            w.followTerrain = true;
            w.footingWidth = Math.max(w.footingWidth, thickness);
            out.add(w);
        }
        return out;
    }

    private static double cornerExtension(double[] a, double[] b, double[] c, double t) {
        double ux = b[0] - a[0], uy = b[1] - a[1], vx = c[0] - b[0], vy = c[1] - b[1];
        double lu = Math.hypot(ux, uy), lv = Math.hypot(vx, vy);
        if (lu < 1e-9 || lv < 1e-9) return 0;
        double turn = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (turn <= 0) return 0;
        return t * Math.tan(Math.min(turn, Math.PI / 2) / 2);
    }
}