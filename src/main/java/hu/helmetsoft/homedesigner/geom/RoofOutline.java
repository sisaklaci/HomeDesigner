package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.*;
import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/** Plan outline shared by fitting, meshes, hit-testing and roof-element containment. */
public final class RoofOutline {
    private RoofOutline() {}

    public static double[][] boundary(Roof r, boolean eaves) {
        double offset = eaves ? r.overhang : 0;
        if (r.circular) {
            double radius = r.w/2+offset, cx = r.x+r.w/2, cy = r.y+r.d/2;
            int n = Math.max(180,(int)Math.ceil(Math.PI/Math.acos(Math.max(-1,1-0.001/radius))));
            double[][] points = new double[n][2];
            for (int i = 0; i < n; i++) {
                double a = 2*Math.PI*i/n;
                points[i] = new double[]{cx+radius*Math.cos(a),cy+radius*Math.sin(a)};
            }
            return points;
        }
        if (r.footprint.isEmpty())
            return RoofGeometry.rect(r.x-offset,r.y-offset,r.x+r.w+offset,r.y+r.d+offset);
        double[][] points = r.footprint.stream().map(p -> new double[]{r.x+p.x*r.w,r.y+p.y*r.d}).toArray(double[][]::new);
        if (offset == 0) return points;
        Area expanded = area(points);
        expanded.add(new Area(new BasicStroke((float)(2*offset),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
                .createStrokedShape(path(points))));
        List<double[][]> contours = contours(expanded);
        if (contours.size() != 1)
            throw new IllegalArgumentException(I18n.text("A tető túlnyúlása önmagába záródó vagy több körvonalat hoz létre. Csökkentsd a túlnyúlást."));
        return contours.get(0);
    }

    public static Path2D.Double path(double[][] points) {
        Path2D.Double path = new Path2D.Double();
        if (points.length == 0) return path;
        path.moveTo(points[0][0],points[0][1]);
        for (int i = 1; i < points.length; i++) path.lineTo(points[i][0],points[i][1]);
        path.closePath();
        return path;
    }

    public static Area area(double[][] points) { return new Area(path(points)); }

    public static List<double[][]> contours(Area area) {
        List<double[][]> result = new ArrayList<>();
        List<double[]> current = new ArrayList<>();
        double[] coordinates = new double[6];
        for (PathIterator it = area.getPathIterator(null,0.0005); !it.isDone(); it.next()) {
            int type = it.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) current = new ArrayList<>();
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                double[] point = {coordinates[0],coordinates[1]};
                if (current.isEmpty() || Math.hypot(point[0]-current.getLast()[0],point[1]-current.getLast()[1]) > 1e-8)
                    current.add(point);
            } else if (type == PathIterator.SEG_CLOSE && current.size() >= 3) {
                if (Math.hypot(current.getFirst()[0]-current.getLast()[0],current.getFirst()[1]-current.getLast()[1]) < 1e-8)
                    current.removeLast();
                double[][] polygon = current.toArray(double[][]::new);
                if (RoofGeometry.area(polygon) > 1e-8) result.add(polygon);
            }
        }
        return result;
    }

    public static boolean containsRectangle(Roof r, double x, double y, double width, double depth, double margin) {
        Area missing = new Area(new java.awt.geom.Rectangle2D.Double(x-margin,y-margin,width+2*margin,depth+2*margin));
        missing.subtract(area(boundary(r,false)));
        return missing.isEmpty();
    }

    public static boolean contains(Roof r, double x, double y) {
        if (r.circular) return Math.hypot(x-r.x-r.w/2,y-r.y-r.d/2) <= r.w/2+r.overhang;
        return path(boundary(r,true)).contains(x,y);
    }

    public static double distance(Roof r, double x, double y) {
        return distance(boundary(r,true),x,y);
    }

    public static double distance(double[][] boundary, double x, double y) {
        double result = Double.POSITIVE_INFINITY;
        for (int i = 0; i < boundary.length; i++) {
            double[] a = boundary[i], b = boundary[(i+1)%boundary.length];
            double dx = b[0]-a[0], dy = b[1]-a[1], len2 = dx*dx+dy*dy;
            double t = len2 > 0 ? Math.clamp(((x-a[0])*dx+(y-a[1])*dy)/len2,0,1) : 0;
            result = Math.min(result,Math.hypot(x-a[0]-t*dx,y-a[1]-t*dy));
        }
        return result;
    }

    public static List<double[][]> triangles(double[][] polygon) {
        double[] x = new double[polygon.length], y = new double[polygon.length];
        for (int i = 0; i < polygon.length; i++) { x[i] = polygon[i][0]; y[i] = polygon[i][1]; }
        List<double[][]> out = new ArrayList<>();
        for (int[] tri : Triangulator.triangulate(x,y))
            out.add(new double[][]{polygon[tri[0]],polygon[tri[1]],polygon[tri[2]]});
        return out;
    }
}
