package hu.helmetsoft.homedesigner.edit;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.*;

import java.util.ArrayList;
import java.util.List;

/** Higher-level editing operations shared by the UI and the demo project. */
public final class ProjectOps {
    public static final String EXTERIOR_COLOR = "#ECE4D6";

    private ProjectOps() {
    }

    /** Creates a chain of walls through the points; if closed, also creates a room and sets exterior finishes. */
    public static List<Wall> addWalls(Project p, List<Pt> pts, boolean closed, String roomName) {
        List<Wall> out = new ArrayList<>();
        int n = pts.size();
        int segs = closed ? n : n - 1;
        double area = signedArea(pts);
        for (int i = 0; i < segs; i++) {
            Pt a = pts.get(i), b = pts.get((i + 1) % n);
            if (Math.hypot(b.x - a.x, b.y - a.y) < 0.01) continue;
            Wall w = new Wall(a.x, a.y, b.x, b.y, p.wallThickness, p.wallHeight);
            if (closed) {
                // positive shoelace = clockwise on screen (y down) => left side faces outside
                Finish ext = new Finish("plaster", EXTERIOR_COLOR);
                if (area > 0) w.left = ext;
                else w.right = ext;
            }
            p.walls.add(w);
            out.add(w);
        }
        if (closed && n >= 3) addRoom(p, pts, roomName);
        return out;
    }

    public static Room addRoom(Project p, List<Pt> pts, String name) {
        Room r = new Room();
        r.name = name != null ? name : "Szoba " + (p.rooms.size() + 1);
        for (Pt q : pts) r.points.add(new Pt(q.x, q.y));
        p.rooms.add(r);
        return r;
    }

    public static Room addRotunda(Project p, double x, double y, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(radius) || radius < 0.3 || radius > 100 || radius <= p.wallThickness/2+0.02)
            throw new IllegalArgumentException(I18n.text("A rotunda sugara 30 cm és 100 m közé essen, és legyen nagyobb a fél falvastagságnál."));
        String id = java.util.UUID.randomUUID().toString();
        List<Wall> walls = new ArrayList<>();
        double[][] points = {{x+radius,y},{x,y+radius},{x-radius,y},{x,y-radius}};
        for (int i = 0; i < 4; i++) {
            double[] a = points[i], b = points[(i+1)%4];
            Wall w = new Wall(a[0],a[1],b[0],b[1],p.wallThickness,p.wallHeight);
            w.arcAngle = 90;
            w.rotundaId = id;
            w.left = new Finish("plaster",EXTERIOR_COLOR);
            w.validate();
            walls.add(w);
        }
        Room r = new Room();
        r.name = "Rotunda " + (p.rooms.size()+1);
        r.rotundaId = id;
        r.updateCircle(x,y,radius);
        p.walls.addAll(walls);
        p.rooms.add(r);
        return r;
    }

    public static Room rotundaOf(Project p, Wall w) {
        if (w.rotundaId == null) return null;
        return p.rooms.stream().filter(r -> w.rotundaId.equals(r.rotundaId)).findFirst().orElse(null);
    }

    /** Resizes/moves the four linked arcs and floor together; joinery keeps its physical width. */
    public static void updateRotunda(Project p, Room r, double x, double y, double radius) {
        if (r.rotundaId == null) throw new IllegalArgumentException(I18n.text("Ez a helyiség nem összekapcsolt rotunda."));
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(radius) || radius < 0.3 || radius > 100)
            throw new IllegalArgumentException(I18n.text("A rotunda sugara 30 cm és 100 m közé essen."));
        double factor = radius/r.circleRadius;
        List<Wall> group = p.walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).toList();
        for (Wall w : group) {
            Wall candidate = new Wall(x+(w.x1-r.circleX)*factor,y+(w.y1-r.circleY)*factor,
                    x+(w.x2-r.circleX)*factor,y+(w.y2-r.circleY)*factor,w.thickness,w.height);
            candidate.arcAngle = w.arcAngle;
            candidate.validate();
            for (Opening o : w.openings) {
                double pos = o.pos*factor;
                validateOpening(candidate,o,pos);
            }
        }
        List<double[]> ends = new ArrayList<>();
        for (Wall w : group) {
            double x1 = x+(w.x1-r.circleX)*factor, y1 = y+(w.y1-r.circleY)*factor;
            double x2 = x+(w.x2-r.circleX)*factor, y2 = y+(w.y2-r.circleY)*factor;
            ends.add(new double[]{w.x1,w.y1,x1,y1}); ends.add(new double[]{w.x2,w.y2,x2,y2});
            w.x1 = x1; w.y1 = y1; w.x2 = x2; w.y2 = y2;
            for (Opening o : w.openings) o.pos *= factor;
        }
        for (Wall w : p.walls) if (!group.contains(w)) {
            double ox1 = w.x1, oy1 = w.y1, ox2 = w.x2, oy2 = w.y2;
            for (double[] end : ends) {
                if (Math.hypot(ox1-end[0],oy1-end[1]) < 0.001) { w.x1 = end[2]; w.y1 = end[3]; }
                if (Math.hypot(ox2-end[0],oy2-end[1]) < 0.001) { w.x2 = end[2]; w.y2 = end[3]; }
            }
        }
        for (Room room : p.rooms) if (room != r && room.rotundaId == null)
            for (Pt point : room.points) {
                double ox = point.x, oy = point.y;
                for (double[] end : ends) if (Math.hypot(ox-end[0],oy-end[1]) < 0.001) {
                    point.x = end[2]; point.y = end[3]; break;
                }
            }
        for (SlabOpening o : r.slabOpenings) { o.x += x-r.circleX; o.y += y-r.circleY; }
        for(FloorZone z:r.floorZones) { z.outline.x+=x-r.circleX; z.outline.y+=y-r.circleY; }
        r.updateCircle(x,y,radius);
    }

    public static void detachRotunda(Project p, String id) {
        if (id == null) return;
        for (Wall w : p.walls) if (id.equals(w.rotundaId)) w.rotundaId = null;
        for (Room r : p.rooms) if (id.equals(r.rotundaId)) r.rotundaId = null;
    }

    public static Opening addOpening(Wall w, Opening.Type type, double pos) {
        if(w.isFence()) throw new IllegalArgumentException(I18n.text("Kerítésbe nem helyezhető ajtó vagy ablak. Kapuhelyhez hagyj külön szakaszt üresen."));
        if (w.length() < 0.3)
            throw new IllegalArgumentException(I18n.text("A fal túl rövid egy nyíláshoz."));
        Opening o = Opening.create(type,0);
        o.width = Math.min(o.width,w.length()-0.04);
        if (w.curved()) o.width = Math.min(o.width,w.radius()*Math.toRadians(120));
        o.sill = Math.max(0,Math.min(o.sill,w.height-0.25));
        o.height = Math.min(o.height,w.height-o.sill-0.02);
        o.pos = Math.max(o.width/2+0.02,Math.min(w.length()-o.width/2-0.02,pos));
        if (w.curved()) validateOpening(w,o,o.pos);
        w.openings.add(o);
        return o;
    }

    private static void validateOpening(Wall w, Opening o, double pos) {
        if (!Double.isFinite(o.width) || !Double.isFinite(pos) || !Double.isFinite(o.height) || !Double.isFinite(o.sill)
                || o.width < 0.1 || o.height < 0.1 || o.sill < 0 || o.sill+o.height > w.height-0.009
                || pos-o.width/2 < 0.009 || pos+o.width/2 > w.length()-0.009
                || w.curved() && o.width/w.radius() > Math.toRadians(120)+1e-8)
            throw new IllegalArgumentException(I18n.text("A nyílás férjen a falba (legalább 1 cm szegéllyel). Íves falon legfeljebb 120°-os szakaszt foglalhat el."));
        if (w.curved() && o.type != Opening.Type.PASSAGE
                && 2*(w.radius()-w.thickness/2)*Math.sin(o.width/(2*w.radius())) < 0.2)
            throw new IllegalArgumentException(I18n.text("A belső ív húrján legalább 20 cm hely szükséges a sík nyílászáró keretéhez."));
    }

    public static void setArcAngle(Wall w, double angle) {
        Wall candidate = new Wall(w.x1,w.y1,w.x2,w.y2,w.thickness,w.height);
        candidate.arcAngle = angle;
        candidate.validate();
        for (Opening o : w.openings) validateOpening(candidate,o,o.pos);
        w.arcAngle = angle;
    }

    public static void validateCurves(Project p) {
        for(Wall w:p.walls) if(w.isFence()) w.validate();
        for(Wall w:p.walls) for(Opening o:w.openings) {
            hu.helmetsoft.homedesigner.geom.OpeningGeometry.validate(o);
            if(o.top!=Opening.Top.RECTANGLE) validateOpening(w,o,o.pos);
        }
        for (Wall w : p.walls) if (w.curved() || w.rotundaId != null || !Double.isFinite(w.arcAngle)) {
            w.validate();
            for (int i = 0; i < w.openings.size(); i++) {
                Opening o = w.openings.get(i);
                validateOpening(w,o,o.pos);
                for (int j = 0; j < i; j++) {
                    Opening a = w.openings.get(j);
                    if (Math.abs(o.pos-a.pos) < (o.width+a.width)/2+0.01
                            && o.sill < a.sill+a.height+0.01 && a.sill < o.sill+o.height+0.01)
                        throw new IllegalArgumentException(I18n.text("Íves falon a nyílások között legalább 1 cm hely szükséges."));
                }
            }
        }
        for (Room r : p.rooms) if (r.rotundaId != null) {
            if (!Double.isFinite(r.circleX) || !Double.isFinite(r.circleY)
                    || !Double.isFinite(r.circleRadius) || r.circleRadius < 0.3 || r.circleRadius > 100)
                throw new IllegalArgumentException(I18n.text("Érvénytelen rotundaméret."));
            List<Wall> walls = p.walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).toList();
            if (walls.size() != 4) throw new IllegalArgumentException(I18n.text("A rotunda négy összekapcsolt negyedívből áll."));
            for (Wall w : walls) {
                double[] center = w.center();
                if (Math.abs(w.arcAngle-90) > 1e-6 || Math.abs(w.radius()-r.circleRadius) > 1e-6
                        || Math.hypot(center[0]-r.circleX,center[1]-r.circleY) > 1e-6)
                    throw new IllegalArgumentException(I18n.text("A rotunda ívei csak együtt méretezhetők. Előbb válaszd a „Felbontás önálló ívekre” parancsot."));
            }
        }
    }

    public static void splitWall(Project p, Wall w, double u) {
        double length = w.length();
        if (!Double.isFinite(u) || u < 0.05 || u > length-0.05)
            throw new IllegalArgumentException(I18n.text("A vágás legyen legalább 5 cm-re a fal végeitől."));
        for (Opening o : w.openings) if (u > o.pos-o.width/2-0.01 && u < o.pos+o.width/2+0.01)
            throw new IllegalArgumentException(I18n.text("Ajtón vagy ablakon keresztül nem vágható ketté a fal."));
        double[] mid = w.point(u,0);
        Wall b = new Wall(mid[0],mid[1],w.x2,w.y2,w.thickness,w.height);
        b.left = w.left.copy(); b.right = w.right.copy();
        b.fence=w.fence; b.fenceFollowTerrain=w.fenceFollowTerrain; b.fenceElevation=w.fenceElevation;
        b.fencePostSpacing=w.fencePostSpacing;
        b.arcAngle = w.arcAngle*(1-u/length);
        double angle = w.arcAngle*u/length;
        if (w.curved()) {
            b.validate();
            Wall first = new Wall(w.x1,w.y1,mid[0],mid[1],w.thickness,w.height);
            first.arcAngle = angle;
            first.validate();
        }
        detachRotunda(p,w.rotundaId);
        w.x2 = mid[0]; w.y2 = mid[1]; w.arcAngle = angle;
        for (Opening o : new ArrayList<>(w.openings)) if (o.pos > u) {
            w.openings.remove(o); o.pos -= u; b.openings.add(o);
        }
        p.walls.add(p.walls.indexOf(w)+1,b);
    }

    public static double signedArea(List<Pt> pts) {
        double a = 0;
        for (int i = 0; i < pts.size(); i++) {
            Pt p = pts.get(i), q = pts.get((i + 1) % pts.size());
            a += p.x * q.y - q.x * p.y;
        }
        return a / 2;
    }

    public static List<Pt> rect(double x, double y, double w, double d) {
        List<Pt> l = new ArrayList<>();
        l.add(new Pt(x, y));
        l.add(new Pt(x + w, y));
        l.add(new Pt(x + w, y + d));
        l.add(new Pt(x, y + d));
        return l;
    }

    /** Roof fitted around all walls (outer faces). */
    public static Roof fitRoof(Project p) {
        double[] b = p.wallBounds();
        Roof r = new Roof();
        if (b == null) b = new double[]{0, 0, 8, 6};
        double t = p.wallThickness / 2;
        r.x = b[0] - t;
        r.y = b[1] - t;
        r.w = b[2] - b[0] + 2 * t;
        r.d = b[3] - b[1] + 2 * t;
        r.baseHeight = p.maxWallHeight();
        r.ridgeAlongX = r.w >= r.d;
        return r;
    }

    /** Finds a free position on one roof face without changing the requested dimensions. */
    public static void placeRoofElement(Roof r, RoofElement e) {
        if (r.radial()) throw new IllegalArgumentException(I18n.text("Kúp- és kupolatetőre jelenleg nem helyezhető tetőablak vagy tetőterasz. Válassz síkokból álló tetőt!"));
        var g = new hu.helmetsoft.homedesigner.geom.RoofGeometry(r);
        double spanX = r.w-e.width-0.25, spanY = r.d-e.depth-0.25;
        for (int iy = 0; spanY > 0 && iy <= Math.ceil(spanY/0.2); iy++)
            for (int ix = 0; spanX > 0 && ix <= Math.ceil(spanX/0.2); ix++) {
                double x = 0.2 + (spanX/2+ix*0.2)%spanX;
                double y = 0.2 + (spanY*0.8+iy*0.2)%spanY;
                e.x = x; e.y = y;
                if (g.faceOf(e) == null) continue;
                boolean overlap = r.elements.stream().anyMatch(q -> e.x < q.x+q.width+0.1 && e.x+e.width+0.1 > q.x
                        && e.y < q.y+q.depth+0.1 && e.y+e.depth+0.1 > q.y);
                if (!overlap) return;
            }
        throw new IllegalArgumentException(I18n.text("Nincs elegendő szabad hely ezen a tetőn ehhez az elemhez. Növeld a tetőt, vagy csökkentsd a meglévő elemek méretét."));
    }

    /**
     * Sets the furniture size from the model's natural bounds. If targetHeight > 0 the model is scaled
     * proportionally to that height; otherwise implausible sizes (longest side over 4 m) are scaled down.
     */
    public static void applyModelSize(Furniture f, hu.helmetsoft.homedesigner.io.ModelData md, double targetHeight) {
        double[] b = hu.helmetsoft.homedesigner.geom.FurnitureTransform.fixedBounds(md, f.upFix);
        double sx = Math.max(0.01, b[0]), sy = Math.max(0.01, b[1]), sz = Math.max(0.01, b[2]);
        double k = 1;
        if (targetHeight > 0) k = targetHeight / sy;
        else {
            double m = Math.max(sx, Math.max(sy, sz));
            if (m > 4 && (f.modelPath==null || hu.helmetsoft.homedesigner.io.HomeModels.category(
                    java.nio.file.Path.of(f.modelPath))!=hu.helmetsoft.homedesigner.io.HomeModels.Category.CARAVANS))
                k = 2.0 / m;
        }
        f.width = sx * k;
        f.height = sy * k;
        f.depth = sz * k;
    }

    /**
     * Creates a room with walls around it. Edges already covered by existing collinear walls are not duplicated
     * (adjacent rooms share walls); the shared wall side facing the new room gets an interior finish.
     */
    public static Room addRoomWithWalls(Project p, List<Pt> pts, String name) {
        double area = signedArea(pts);
        List<Wall> existing = new ArrayList<>(p.walls);
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            Pt a = pts.get(i), b = pts.get((i + 1) % n);
            double L = Math.hypot(b.x - a.x, b.y - a.y);
            if (L < 0.01) continue;
            double dx = (b.x - a.x) / L, dy = (b.y - a.y) / L;
            // interior normal of this edge
            double inX = area > 0 ? -dy : dy, inY = area > 0 ? dx : -dx;
            List<double[]> cov = new ArrayList<>();
            for (Wall w : existing) {
                if (w.curved() || w.isFence()) continue;
                if (Math.abs(dx * w.dirY() - dy * w.dirX()) > 0.01) continue;
                double dist = Math.abs((w.x1 - a.x) * -dy + (w.y1 - a.y) * dx);
                if (dist > 0.02) continue;
                double t1 = (w.x1 - a.x) * dx + (w.y1 - a.y) * dy, t2 = (w.x2 - a.x) * dx + (w.y2 - a.y) * dy;
                double lo = Math.max(0, Math.min(t1, t2)), hi = Math.min(L, Math.max(t1, t2));
                if (hi - lo < 0.01) continue;
                cov.add(new double[]{lo, hi});
                boolean leftIn = w.nx() * inX + w.ny() * inY > 0;
                Finish f = leftIn ? w.left : w.right;
                if (isExterior(f)) {
                    if (leftIn) w.left = new Finish("plaster", "#F3EFE7");
                    else w.right = new Finish("plaster", "#F3EFE7");
                }
            }
            cov.sort((u, v) -> Double.compare(u[0], v[0]));
            double cur = 0;
            List<double[]> gaps = new ArrayList<>();
            for (double[] c : cov) {
                if (c[0] > cur + 0.02) gaps.add(new double[]{cur, c[0]});
                cur = Math.max(cur, c[1]);
            }
            if (L > cur + 0.02) gaps.add(new double[]{cur, L});
            for (double[] g : gaps) {
                Wall w = new Wall(a.x + dx * g[0], a.y + dy * g[0], a.x + dx * g[1], a.y + dy * g[1], p.wallThickness, p.wallHeight);
                Finish ext = new Finish("plaster", EXTERIOR_COLOR);
                if (area > 0) w.left = ext;
                else w.right = ext;
                p.walls.add(w);
            }
        }
        return addRoom(p, pts, name);
    }

    public static boolean isExterior(Finish f) {
        return f != null && "plaster".equals(f.preset) && EXTERIOR_COLOR.equalsIgnoreCase(f.color);
    }

    /** Wall nearest to the point within maxDist, or null. */
    public static Wall nearestWall(Project p, double x, double y, double maxDist) {
        Wall best = null;
        double bd = maxDist;
        for (Wall w : p.walls) {
            double d = w.distance(x, y);
            if (d < bd) {
                bd = d;
                best = w;
            }
        }
        return best;
    }
}
