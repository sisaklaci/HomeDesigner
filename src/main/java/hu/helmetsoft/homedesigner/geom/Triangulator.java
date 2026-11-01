package hu.helmetsoft.homedesigner.geom;

import java.util.ArrayList;
import java.util.List;

/** Ear clipping triangulation of a simple polygon given as x[], y[]. Returns index triples. */
public final class Triangulator {
    private Triangulator() {
    }

    public static List<int[]> triangulate(double[] x, double[] y) {
        int n = x.length;
        List<int[]> out = new ArrayList<>();
        if (n < 3) return out;
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += x[i] * y[j] - x[j] * y[i];
        }
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < n; i++) v.add(area > 0 ? i : n - 1 - i);
        int guard = 0;
        while (v.size() > 3 && guard++ < 10000) {
            boolean clipped = false;
            for (int i = 0; i < v.size(); i++) {
                int a = v.get((i + v.size() - 1) % v.size()), b = v.get(i), c = v.get((i + 1) % v.size());
                double cross = (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a]);
                if (cross <= 1e-12) continue;
                boolean inside = false;
                for (int k : v) {
                    if (k == a || k == b || k == c) continue;
                    if (inTri(x[k], y[k], x[a], y[a], x[b], y[b], x[c], y[c])) {
                        inside = true;
                        break;
                    }
                }
                if (inside) continue;
                out.add(new int[]{a, b, c});
                v.remove(i);
                clipped = true;
                break;
            }
            if (!clipped) {
                // degenerate polygon: fall back to fan
                for (int i = 1; i + 1 < v.size(); i++) out.add(new int[]{v.get(0), v.get(i), v.get(i + 1)});
                return out;
            }
        }
        if (v.size() == 3) out.add(new int[]{v.get(0), v.get(1), v.get(2)});
        return out;
    }

    private static boolean inTri(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }
}
