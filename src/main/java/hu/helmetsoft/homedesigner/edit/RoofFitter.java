package hu.helmetsoft.homedesigner.edit;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.model.*;
import java.awt.geom.Area;
import java.util.*;

/** Fits a single building rather than the bounding rectangle of every building in a project. */
public final class RoofFitter {
    private RoofFitter() {}

    public static List<List<Wall>> buildings(Project p) {
        List<List<Wall>> out = new ArrayList<>();
        Set<Wall> remaining = new LinkedHashSet<>(p.walls.stream().filter(w -> !w.isFence()).toList());
        while (!remaining.isEmpty()) {
            List<Wall> group = new ArrayList<>();
            group.add(remaining.iterator().next()); remaining.remove(group.getFirst());
            for (int i = 0; i < group.size(); i++) {
                Wall a = group.get(i);
                for (Wall b : new ArrayList<>(remaining))
                    if (connected(a,b)) { group.add(b); remaining.remove(b); }
            }
            out.add(group);
        }
        return out;
    }

    private static boolean connected(Wall a, Wall b) {
        return a.distance(b.x1,b.y1) < WallGeometry.JOIN_EPS || a.distance(b.x2,b.y2) < WallGeometry.JOIN_EPS
                || b.distance(a.x1,a.y1) < WallGeometry.JOIN_EPS || b.distance(a.x2,a.y2) < WallGeometry.JOIN_EPS;
    }

    public static List<Wall> selectedBuilding(Project p, Object selection) {
        List<List<Wall>> groups = buildings(p);
        if (groups.isEmpty()) throw new IllegalArgumentException(I18n.text("Előbb rajzold meg az épület falait!"));
        Wall seed = selection instanceof Wall w ? w : null;
        if (selection instanceof Opening o)
            seed = p.walls.stream().filter(w -> w.openings.contains(o)).findFirst().orElse(null);
        if (seed != null) for (List<Wall> group : groups) if (group.contains(seed)) return group;
        double[] point = null;
        if (selection instanceof Room room && !room.points.isEmpty()) point = new double[]{room.points.getFirst().x,room.points.getFirst().y};
        if (selection instanceof Roof roof) point = new double[]{roof.x+roof.w/2,roof.y+roof.d/2};
        if (selection instanceof RoofElement element)
            for (Roof roof : p.roofs) if (roof.elements.contains(element)) point = new double[]{roof.x+element.x,roof.y+element.y};
        if (point != null) {
            List<Wall> best = null;
            double distance = Double.POSITIVE_INFINITY;
            double bestOverlap = -1;
            for (List<Wall> group : groups) {
                double d = Double.POSITIVE_INFINITY;
                for (Wall w : group) d = Math.min(d,w.distance(point[0],point[1]));
                double overlap = 0;
                if (selection instanceof Roof roof) {
                    var contours = wallContours(group);
                    if (!contours.isEmpty()) {
                        Area covered = RoofOutline.area(contours.stream().max(Comparator.comparingDouble(RoofGeometry::area)).orElseThrow());
                        covered.intersect(RoofOutline.area(RoofOutline.boundary(roof,false)));
                        overlap = RoofOutline.contours(covered).stream().mapToDouble(RoofGeometry::area).sum();
                    }
                }
                if (overlap > bestOverlap+1e-8 || Math.abs(overlap-bestOverlap) <= 1e-8 && d < distance) {
                    distance = d; bestOverlap = overlap; best = group;
                }
            }
            return best;
        }
        if (groups.size() == 1) return groups.getFirst();
        throw new IllegalArgumentException(I18n.text("Több különálló épület van a tervben. Jelöld ki a kívánt épület egyik falát vagy helyiségét, majd add hozzá a tetőt!"));
    }

    private static List<double[][]> wallContours(List<Wall> group) {
        Map<Wall,WallGeometry.Corners> corners = WallGeometry.compute(group);
        Area walls = new Area();
        for (Wall w : group) {
            w.validate();
            double[][] outline = WallGeometry.outline(w,corners.get(w));
            // Shared arc endpoints must remain identical during polygon boolean operations.
            for (int i = 0; i < outline.length; i++)
                outline[i] = new double[]{Math.rint(outline[i][0]*1e7)/1e7,Math.rint(outline[i][1]*1e7)/1e7};
            walls.add(RoofOutline.area(outline));
        }
        return RoofOutline.contours(walls);
    }

    public static Roof fit(Project p, Object selection) {
        List<Wall> group = selectedBuilding(p,selection);
        List<double[][]> contours = wallContours(group);
        if (contours.size() < 2)
            throw new IllegalArgumentException(I18n.text("Az épület külső falai nem alkotnak zárt körvonalat. Zárd a falak végeit a tetőillesztés előtt!"));
        double[][] outer = contours.stream().max(Comparator.comparingDouble(RoofGeometry::area)).orElseThrow();
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (double[] q : outer) {
            minX = Math.min(minX,q[0]); minY = Math.min(minY,q[1]);
            maxX = Math.max(maxX,q[0]); maxY = Math.max(maxY,q[1]);
        }
        Roof r = new Roof();
        r.x = minX; r.y = minY; r.w = maxX-minX; r.d = maxY-minY;
        r.baseHeight = group.stream().mapToDouble(w -> w.height).max().orElse(p.wallHeight);
        r.ridgeAlongX = r.w >= r.d;
        double cx = (minX+maxX)/2, cy = (minY+maxY)/2, radius = r.w/2;
        boolean circle = Math.abs(r.w-r.d) < 0.003;
        for (double[] q : outer) if (Math.abs(Math.hypot(q[0]-cx,q[1]-cy)-radius) > 0.003) circle = false;
        r.circular = circle;
        if (circle) {
            r.d = r.w;
            r.type = Roof.Type.CONE;
            r.domeRise = radius*0.6;
        } else {
            double area = RoofGeometry.area(outer);
            if (Math.abs(area-r.w*r.d) > 1e-6)
                for (double[] q : outer) r.footprint.add(new Pt((q[0]-r.x)/r.w,(q[1]-r.y)/r.d));
        }
        RoofGeometry.validate(r);
        return r;
    }

    public static void refit(Project p, Roof r) {
        Roof n = fit(p,r);
        if (r.radial() && !n.circular)
            throw new IllegalArgumentException(I18n.text("Kúp- és kupolatető csak kör alapra illeszthető. Előbb válassz más tetőtípust!"));
        r.x = n.x; r.y = n.y; r.w = n.w; r.d = n.d;
        r.footprint = n.footprint; r.circular = n.circular;
        r.baseHeight = n.baseHeight; r.ridgeAlongX = n.ridgeAlongX;
    }
}
