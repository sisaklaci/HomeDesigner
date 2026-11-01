package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.*;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.*;

/** Room surface subtraction and hole-safe vertical trapezoid decomposition. */
public final class SlabGeometry {
    private SlabGeometry() {}

    public static double[][] boundary(SlabOpening o) {
        if (o.shape == SlabOpening.Shape.POLYGON)
            return o.points.stream().map(p -> o.point(p.x,p.y)).toArray(double[][]::new);
        if (o.shape == SlabOpening.Shape.RECTANGLE)
            return new double[][]{o.point(0,0),o.point(1,0),o.point(1,1),o.point(0,1)};
        double radius = Math.max(o.width,o.depth)/2;
        int segments = Math.max(96,(int)Math.ceil(Math.PI/Math.acos(Math.max(-1,1-0.0005/radius))));
        double[][] points = new double[segments][];
        for (int i = 0; i < segments; i++) {
            double a = 2*Math.PI*i/segments;
            points[i] = o.point(0.5+0.5*Math.cos(a),0.5+0.5*Math.sin(a));
        }
        return points;
    }

    public static Area roomArea(Room r) {
        return RoofOutline.area(r.points.stream().map(p -> new double[]{p.x,p.y}).toArray(double[][]::new));
    }

    public static Area surface(Room r, boolean ceiling) {
        Area result = roomArea(r), holes = new Area();
        for (SlabOpening o : r.slabOpenings) if (ceiling ? o.cutCeiling : o.cutFloor)
            holes.add(RoofOutline.area(boundary(o)));
        result.subtract(holes);
        return result;
    }

    public static boolean contains(SlabOpening o, double x, double y) {
        return RoofOutline.path(boundary(o)).contains(x,y);
    }

    public static void validate(Project p) {
        for (Room r : p.rooms) for (SlabOpening o : r.slabOpenings) validate(o);
    }

    public static void validate(SlabOpening o) {
        if (o == null) throw new IllegalArgumentException(I18n.text("Hiányzó födémnyílás."));
        for (double v : new double[]{o.x,o.y,o.width,o.depth,o.angle})
            if (!Double.isFinite(v)) throw new IllegalArgumentException(I18n.text("A födémnyílás adatai véges számok legyenek."));
        if (o.shape == null || o.width < 0.05 || o.depth < 0.05 || o.width > 200 || o.depth > 200
                || Math.abs(o.x) > 10000 || Math.abs(o.y) > 10000
                || o.shape == SlabOpening.Shape.CIRCLE && Math.abs(o.width-o.depth) > 1e-6)
            throw new IllegalArgumentException(I18n.text("A födémnyílás mérete 5 cm–200 m legyen; körnél a két átmérő egyezzen."));
        if (o.shape == SlabOpening.Shape.POLYGON) {
            if (o.points == null || o.points.size() < 3 || o.points.size() > 512)
                throw new IllegalArgumentException(I18n.text("A födémnyílás sokszöge 3–512 pontból álljon."));
            for (Pt point : o.points) if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)
                    || point.x < -1e-8 || point.x > 1+1e-8 || point.y < -1e-8 || point.y > 1+1e-8)
                throw new IllegalArgumentException(I18n.text("Érvénytelen födémnyílás-sarokpont."));
            double[][] polygon = boundary(o);
            for (int i = 0; i < polygon.length; i++) {
                double[] a = polygon[i], b = polygon[(i+1)%polygon.length];
                if (Math.hypot(a[0]-b[0],a[1]-b[1]) < 0.001)
                    throw new IllegalArgumentException(I18n.text("A nyílás szomszédos pontjai legalább 1 mm-re legyenek egymástól."));
                for (int j = i+1; j < polygon.length; j++) {
                    if (j == i+1 || i == 0 && j == polygon.length-1) continue;
                    double[] c = polygon[j], d = polygon[(j+1)%polygon.length];
                    if (Line2D.linesIntersect(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1]))
                        throw new IllegalArgumentException(I18n.text("A födémnyílás körvonala nem metszheti és nem érintheti önmagát."));
                }
            }
            if (RoofGeometry.area(polygon) < 0.0025)
                throw new IllegalArgumentException(I18n.text("A födémnyílás területe legalább 25 cm² legyen."));
        }
    }

    public static SlabOpening polygon(List<Pt> points) {
        SlabOpening o = new SlabOpening(); o.shape = SlabOpening.Shape.POLYGON;
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (Pt p : points) {
            minX = Math.min(minX,p.x); minY = Math.min(minY,p.y);
            maxX = Math.max(maxX,p.x); maxY = Math.max(maxY,p.y);
        }
        o.x = (minX+maxX)/2; o.y = (minY+maxY)/2; o.width = maxX-minX; o.depth = maxY-minY;
        for (Pt p : points) o.points.add(new Pt((p.x-minX)/o.width,(p.y-minY)/o.depth));
        validate(o);
        return o;
    }

    private record Edge(double[] a, double[] b) {
        double y(double x) { return a[1]+(x-a[0])*(b[1]-a[1])/(b[0]-a[0]); }
    }

    public static void moveVertex(SlabOpening o, int index, double x, double y) {
        List<Pt> points = new ArrayList<>();
        double a = Math.toRadians(o.angle), c = Math.cos(a), s = Math.sin(a);
        for (double[] p : boundary(o)) points.add(new Pt(c*p[0]+s*p[1],-s*p[0]+c*p[1]));
        points.set(index,new Pt(c*x+s*y,-s*x+c*y));
        SlabOpening resized = polygon(points);
        o.x = c*resized.x-s*resized.y; o.y = s*resized.x+c*resized.y;
        o.width = resized.width; o.depth = resized.depth; o.points = resized.points;
    }

    /** Area already resolves overlaps, nested holes and openings crossing the room boundary. */
    public static List<double[][]> triangles(Area area) {
        List<Edge> edges = new ArrayList<>();
        TreeSet<Double> xs = new TreeSet<>();
        for (double[][] contour : RoofOutline.contours(area)) for (int i = 0; i < contour.length; i++) {
            double[] a = contour[i], b = contour[(i+1)%contour.length];
            xs.add(a[0]);
            if (Math.abs(a[0]-b[0]) > 1e-12) edges.add(new Edge(a,b));
        }
        List<double[][]> triangles = new ArrayList<>();
        Double left = null;
        for (double right : xs) {
            if (left != null && right-left > 1e-10) {
                double x0 = left, mid = (left+right)/2;
                List<Edge> active = edges.stream().filter(e -> mid > Math.min(e.a[0],e.b[0]) && mid < Math.max(e.a[0],e.b[0]))
                        .sorted(Comparator.comparingDouble(e -> e.y(mid))).toList();
                if (active.size()%2 != 0) throw new IllegalArgumentException(I18n.text("A födém körvonala nem bontható zárt felületre."));
                for (int i = 0; i < active.size(); i += 2) {
                    Edge low = active.get(i), high = active.get(i+1);
                    double[] a = {x0,low.y(x0)}, b = {right,low.y(right)}, c = {right,high.y(right)}, d = {x0,high.y(x0)};
                    addTriangle(triangles,a,b,c); addTriangle(triangles,a,c,d);
                }
            }
            left = right;
        }
        return triangles;
    }

    private static void addTriangle(List<double[][]> out, double[] a, double[] b, double[] c) {
        if (Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])) > 1e-12)
            out.add(new double[][]{a,b,c});
    }
}
