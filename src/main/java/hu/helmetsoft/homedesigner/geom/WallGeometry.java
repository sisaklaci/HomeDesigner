package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.model.Wall;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Computes mitered wall corner points on the plan so connected walls join cleanly. */
public final class WallGeometry {
    public static final double JOIN_EPS = 0.01;

    public static final class Corners {
        /** Plan points [x,y]: side A (left) start/end, side B (right) start/end. */
        public double[] aS, aE, bS, bE;
        public boolean capStart = true, capEnd = true;

        public double[][] polygon() {
            return new double[][]{aS, aE, bE, bS};
        }
    }

    private WallGeometry() {
    }

    public static Map<Wall, Corners> compute(List<Wall> walls) {
        Map<Wall, Corners> map = new IdentityHashMap<>();
        for (Wall w : walls) {
            double L = w.length();
            Corners c = new Corners();
            double dx = w.tangentX(0), dy = w.tangentY(0), nx = dy, ny = -dx, t = w.thickness / 2;
            c.aS = new double[]{w.x1 + nx * t, w.y1 + ny * t};
            c.bS = new double[]{w.x1 - nx * t, w.y1 - ny * t};
            c.aE = w.point(L,t);
            c.bE = w.point(L,-t);
            if (L < 1e-6) {
                map.put(w, c);
                continue;
            }
            // start joint
            double[] other = joint(walls, w, w.x1, w.y1);
            if (other != null) {
                double[] a = corner(w.x1, w.y1, dx, dy, +1, t, other);
                double[] b = corner(w.x1, w.y1, dx, dy, -1, t, other);
                if (a != null && b != null) {
                    c.aS = a;
                    c.bS = b;
                    c.capStart = false;
                }
            }
            other = joint(walls, w, w.x2, w.y2);
            if (other != null) {
                double[] a = corner(w.x2, w.y2, -w.tangentX(L), -w.tangentY(L), -1, t, other);
                double[] b = corner(w.x2, w.y2, -w.tangentX(L), -w.tangentY(L), +1, t, other);
                if (a != null && b != null) {
                    c.aE = a;
                    c.bE = b;
                    c.capEnd = false;
                }
            }
            map.put(w, c);
        }
        return map;
    }

    /** Finds exactly one other wall with an endpoint at (x,y); returns {awayDirX, awayDirY, halfThickness}. */
    private static double[] joint(List<Wall> walls, Wall self, double x, double y) {
        double[] found = null;
        int count = 0;
        for (Wall o : walls) {
            if (o == self || o.length() < 1e-6) continue;
            if (Math.hypot(o.x1 - x, o.y1 - y) < JOIN_EPS) {
                found = new double[]{o.tangentX(0), o.tangentY(0), o.thickness / 2};
                count++;
            } else if (Math.hypot(o.x2 - x, o.y2 - y) < JOIN_EPS) {
                found = new double[]{-o.tangentX(o.length()), -o.tangentY(o.length()), o.thickness / 2};
                count++;
            }
        }
        return count == 1 ? found : null;
    }

    /** Intersection of the side line (offset s) of direction d1 with the opposite side line of the other wall. */
    private static double[] corner(double px, double py, double d1x, double d1y, int s, double t1, double[] o) {
        double d2x = o[0], d2y = o[1], t2 = o[2];
        // perp(v) = (v.y, -v.x)
        double p1x = px + s * d1y * t1, p1y = py - s * d1x * t1;
        double p2x = px - s * d2y * t2, p2y = py + s * d2x * t2;
        double cross = d1x * d2y - d1y * d2x;
        if (Math.abs(cross) < 1e-4)
            return d1x*d2x+d1y*d2y < -0.9999 && Math.abs(t1-t2) < 1e-6
                    ? new double[]{(p1x+p2x)/2,(p1y+p2y)/2} : null;
        double qx = p2x - p1x, qy = p2y - p1y;
        double a = (qx * d2y - qy * d2x) / cross;
        double rx = p1x + d1x * a, ry = p1y + d1y * a;
        if (Math.hypot(rx - px, ry - py) > 6 * Math.max(t1, t2)) return null;
        return new double[]{rx, ry};
    }

    public static double[][] outline(Wall w, Corners c) {
        if (!w.curved()) return c.polygon();
        double[][] p = w.outline();
        int n = w.segments();
        p[0] = c.aS; p[n] = c.aE; p[n+1] = c.bE; p[2*n+1] = c.bS;
        return p;
    }
}
