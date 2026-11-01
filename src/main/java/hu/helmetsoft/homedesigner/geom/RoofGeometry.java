package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.*;
import java.util.ArrayList;
import java.util.List;

/** Shared roof planes for cut-outs, placement validation and architectural meshes. */
public final class RoofGeometry {
    public record Face(double[][] polygon, double ga, double gb) {}
    public final Roof roof;
    public final double a0, a1, b0, b1, ac, bc, halfA, halfB, slope, thickness;
    public final List<Face> faces = new ArrayList<>();
    public final double[][] outline;

    public RoofGeometry(Roof r) {
        roof = r;
        a0 = (r.ridgeAlongX ? r.x : r.y) - r.overhang;
        a1 = (r.ridgeAlongX ? r.x + r.w : r.y + r.d) + r.overhang;
        b0 = (r.ridgeAlongX ? r.y : r.x) - r.overhang;
        b1 = (r.ridgeAlongX ? r.y + r.d : r.x + r.w) + r.overhang;
        ac = (a0 + a1) / 2; bc = (b0 + b1) / 2;
        halfA = (a1 - a0) / 2; halfB = (b1 - b0) / 2;
        slope = r.type == Roof.Type.FLAT ? 0 : Math.tan(Math.toRadians(
                r.type == Roof.Type.CONE ? r.pitch : Math.max(1, Math.min(70, r.pitch))));
        thickness = r.type == Roof.Type.DOME ? r.thickness : r.thickness * Math.sqrt(1 + slope * slope);
        outline = RoofOutline.boundary(r,true);
        switch (r.type) {
            case FLAT -> face(rect(a0, b0, a1, b1), 0, 0);
            case SHED -> face(rect(a0, b0, a1, b1), 0, -1);
            case GABLE -> {
                face(rect(a0, b0, a1, bc), 0, -1);
                face(rect(a0, bc, a1, b1), 0, 1);
            }
            case HIP -> {
                if (halfA >= halfB) {
                    face(new double[][]{{a0,b0},{a1,b0},{a1-halfB,bc},{a0+halfB,bc}}, 0,-1);
                    face(new double[][]{{a0+halfB,bc},{a1-halfB,bc},{a1,b1},{a0,b1}}, 0,1);
                    face(new double[][]{{a0,b0},{a0+halfB,bc},{a0,b1}}, -1,0);
                    face(new double[][]{{a1,b0},{a1,b1},{a1-halfB,bc}}, 1,0);
                } else {
                    face(new double[][]{{a0,b0},{ac,b0+halfA},{ac,b1-halfA},{a0,b1}}, -1,0);
                    face(new double[][]{{ac,b0+halfA},{a1,b0},{a1,b1},{ac,b1-halfA}}, 1,0);
                    face(new double[][]{{a0,b0},{a1,b0},{ac,b0+halfA}}, 0,-1);
                    face(new double[][]{{a0,b1},{ac,b1-halfA},{a1,b1}}, 0,1);
                }
            }
            case CONE, DOME -> { }
        }
    }

    private void face(double[][] p, double ga, double gb) { faces.add(new Face(p, ga, gb)); }

    public double height(double a, double b) {
        double he = roof.baseHeight - slope * roof.overhang;
        return switch (roof.type) {
            case FLAT -> roof.baseHeight;
            case SHED -> he + slope * (b - b0);
            case GABLE -> he + slope * (halfB - Math.abs(b - bc));
            case HIP -> he + slope * Math.min(halfB - Math.abs(b - bc), halfA - Math.abs(a - ac));
            case CONE -> roof.baseHeight+slope*(roof.w/2-Math.hypot(a-ac,b-bc));
            case DOME -> roof.baseHeight+roof.domeRise*Math.sqrt(Math.max(0,1-
                    (Math.pow(a-ac,2)+Math.pow(b-bc,2))/Math.pow(roof.w/2+roof.overhang,2)));
        };
    }

    /** Planar roof faces clipped to the actual outline; all returned pieces are convex. */
    public List<Face> surfaceFaces() {
        if (!roof.circular && roof.footprint.isEmpty()) return faces;
        List<Face> result = new ArrayList<>();
        double[][] local = new double[outline.length][];
        for (int i = 0; i < outline.length; i++)
            local[i] = roof.ridgeAlongX ? outline[i] : new double[]{outline[i][1],outline[i][0]};
        java.awt.geom.Area shape = RoofOutline.area(local);
        for (Face face : faces) {
            java.awt.geom.Area part = RoofOutline.area(face.polygon());
            part.intersect(shape);
            for (double[][] contour : RoofOutline.contours(part))
                for (double[][] triangle : RoofOutline.triangles(contour))
                    result.add(new Face(triangle,face.ga(),face.gb()));
        }
        return result;
    }

    public double heightAt(double x, double z) {
        return roof.ridgeAlongX ? height(x, z) : height(z, x);
    }

    public double[] point(double a, double b, double h) {
        return roof.ridgeAlongX ? new double[]{a,h,b} : new double[]{b,h,a};
    }

    public double[] bounds(RoofElement e) {
        double x = roof.x + e.x, z = roof.y + e.y;
        return roof.ridgeAlongX ? new double[]{x,z,x+e.width,z+e.depth}
                : new double[]{z,x,z+e.depth,x+e.width};
    }

    public Face faceOf(RoofElement e) {
        if (roof.radial() || !RoofOutline.containsRectangle(roof,roof.x+e.x,roof.y+e.y,e.width,e.depth,0.05))
            return null;
        double[] b = bounds(e);
        for (Face f : faces) {
            boolean all = true;
            for (double[] p : rect(b[0], b[1], b[2], b[3]))
                if (!inside(f.polygon, p[0], p[1])) { all = false; break; }
            if (all) return f;
        }
        return null;
    }

    private static boolean inside(double[][] polygon, double a, double b) {
        double sign = 0;
        for (int i = 0; i < polygon.length; i++) {
            double[] p = polygon[i], q = polygon[(i + 1) % polygon.length];
            double c = (q[0]-p[0]) * (b-p[1]) - (q[1]-p[1]) * (a-p[0]);
            if (Math.abs(c) < 1e-8) continue;
            if (sign != 0 && sign * c < 0) return false;
            sign = c;
        }
        return true;
    }

    public static void validate(Project p) {
        for (Roof r : p.roofs) validate(r);
        for (Balcony b : p.balconies) {
            positive(b.width, b.depth, b.thickness, b.railingHeight);
            finite(b.x, b.y, b.angle, b.elevation);
            if (b.elevation < 0) throw new IllegalArgumentException(I18n.text("Az erkély magassága nem lehet negatív."));
        }
    }

    public static void validate(Roof r) {
        positive(r.w, r.d, r.thickness);
        finite(r.x, r.y, r.pitch, r.overhang, r.baseHeight,r.domeRise);
        if (r.type == null || r.overhang < 0 || r.overhang > 10 || r.baseHeight < 0
                || r.pitch < 0 || r.pitch > 75 || r.domeRise <= 0)
            throw new IllegalArgumentException(I18n.text("Érvénytelen tetőadat. A túlnyúlás 0–10 m, a hajlásszög 0–75°, a kupolamagasság pozitív legyen."));
        if (r.circular && Math.abs(r.w-r.d) > 1e-6)
            throw new IllegalArgumentException(I18n.text("Kör alapú tetőn a szélesség és a mélység azonos."));
        if (r.radial() && !r.circular)
            throw new IllegalArgumentException(I18n.text("Kúp- és kupolatetőhöz kör alap szükséges. Válaszd a „Kör alap” beállítást, vagy illeszd a tetőt egy rotundára!"));
        if (r.radial() && !r.elements.isEmpty())
            throw new IllegalArgumentException(I18n.text("Kúp- és kupolatetőre jelenleg nem tehető tetőablak vagy tetőterasz. A meglévő tetőelemeket előbb távolítsd el, vagy válassz síkokból álló tetőt!"));
        if (!r.footprint.isEmpty()) {
            if (r.circular || r.footprint.size() < 3 || r.footprint.size() > 20000)
                throw new IllegalArgumentException(I18n.text("Érvénytelen tetőkörvonal."));
            double[][] polygon = new double[r.footprint.size()][];
            for (int i = 0; i < polygon.length; i++) {
                Pt p = r.footprint.get(i);
                finite(p.x,p.y);
                if (p.x < -1e-8 || p.x > 1+1e-8 || p.y < -1e-8 || p.y > 1+1e-8)
                    throw new IllegalArgumentException(I18n.text("A tetőkörvonalnak a tető mérethatárain belül kell lennie."));
                polygon[i] = new double[]{p.x*r.w,p.y*r.d};
            }
            if (area(polygon) < 0.01 || RoofOutline.contours(RoofOutline.area(polygon)).size() != 1)
                throw new IllegalArgumentException(I18n.text("A tetőkörvonal legyen egyszerű, zárt és legalább 0,01 m² területű."));
        }
        RoofGeometry g = new RoofGeometry(r);
        for (int i = 0; i < r.elements.size(); i++) {
            RoofElement e = r.elements.get(i);
            positive(e.width, e.depth, e.height, e.floorDrop, e.railingHeight);
            finite(e.x, e.y);
            if (e.width < 0.3 || e.depth < 0.3)
                throw new IllegalArgumentException(I18n.text("A tetőelem mindkét alaprajzi mérete legalább 30 cm legyen."));
            if (e.x < 0.05 || e.y < 0.05 || e.x + e.width > r.w - 0.05 || e.y + e.depth > r.d - 0.05 || g.faceOf(e) == null)
                throw new IllegalArgumentException(I18n.text("A tetőelem egyetlen tetősíkon, a falszélektől legalább 5 cm-re helyezhető el. Nem keresztezheti a gerincet vagy a konty élét."));
            for (int j = 0; j < i; j++) {
                RoofElement q = r.elements.get(j);
                if (e.x < q.x + q.width + 0.1 && e.x + e.width + 0.1 > q.x
                        && e.y < q.y + q.depth + 0.1 && e.y + e.depth + 0.1 > q.y)
                    throw new IllegalArgumentException(I18n.text("A tetőelemek között legalább 10 cm hely szükséges."));
            }
        }
    }

    private static void positive(double... values) {
        finite(values);
        for (double v : values) if (v <= 0) throw new IllegalArgumentException(I18n.text("A méreteknek pozitívnak kell lenniük."));
    }

    private static void finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) throw new IllegalArgumentException(I18n.text("Csak véges szám adható meg."));
    }

    public static double[][] rect(double a0, double b0, double a1, double b1) {
        return new double[][]{{a0,b0},{a1,b0},{a1,b1},{a0,b1}};
    }

    /** Subtracts an axis-aligned rectangle, keeping a non-overlapping set of convex polygons. */
    public static List<double[][]> subtract(double[][] polygon, double[] hole) {
        List<double[][]> out = new ArrayList<>();
        double[][] remaining = polygon;
        int[] axes = {0,0,1,1};
        double[] limits = {hole[0],hole[2],hole[1],hole[3]};
        boolean[] keepGreater = {true,false,true,false};
        for (int i = 0; i < 4 && remaining.length >= 3; i++) {
            double[][] outside = clip(remaining, axes[i], limits[i], !keepGreater[i]);
            if (area(outside) > 1e-9) out.add(outside);
            remaining = clip(remaining, axes[i], limits[i], keepGreater[i]);
        }
        return out;
    }

    private static double[][] clip(double[][] p, int axis, double limit, boolean greater) {
        List<double[]> out = new ArrayList<>();
        if (p.length == 0) return new double[0][];
        double[] a = p[p.length - 1];
        boolean ai = greater ? a[axis] >= limit : a[axis] <= limit;
        for (double[] b : p) {
            boolean bi = greater ? b[axis] >= limit : b[axis] <= limit;
            if (ai != bi) {
                double t = (limit - a[axis]) / (b[axis] - a[axis]);
                out.add(new double[]{a[0] + t*(b[0]-a[0]), a[1] + t*(b[1]-a[1])});
            }
            if (bi) out.add(b);
            a = b; ai = bi;
        }
        for (int i = out.size()-1; i >= 0 && out.size() > 1; i--) {
            double[] q = out.get(i), r = out.get((i+1)%out.size());
            if (Math.hypot(q[0]-r[0],q[1]-r[1]) < 1e-9) out.remove(i);
        }
        boolean changed = true;
        while (changed && out.size() >= 3) {
            changed = false;
            for (int i = 0; i < out.size(); i++) {
                double[] q = out.get((i+out.size()-1)%out.size()), r = out.get(i), s = out.get((i+1)%out.size());
                if (Math.abs((r[0]-q[0])*(s[1]-r[1])-(r[1]-q[1])*(s[0]-r[0])) < 1e-10) {
                    out.remove(i); changed = true; break;
                }
            }
        }
        return out.toArray(double[][]::new);
    }

    public static double area(double[][] p) {
        double a = 0;
        for (int i = 0; i < p.length; i++) {
            double[] q = p[i], r = p[(i+1)%p.length];
            a += q[0]*r[1] - r[0]*q[1];
        }
        return Math.abs(a)/2;
    }
}
