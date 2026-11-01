package hu.helmetsoft.homedesigner.model;

import hu.helmetsoft.homedesigner.util.I18n;

import java.util.ArrayList;
import java.util.List;

/** Straight or circular wall axis. Coordinates in metres; positive arc sweep is clockwise on the plan. */
public class Wall {
    public enum Fence {
        NONE("Épületfal"), PICKET("Függőleges léckerítés"), HORIZONTAL("Vízszintes léckerítés"),
        METAL("Fém pálcás kerítés"), MESH("Rácsos drótkerítés"), SOLID("Tömör falazott kerítés");
        public final String label;
        Fence(String label) { this.label=label; }
        @Override public String toString() { return I18n.display(label); }
    }
    public Fence fence=Fence.NONE;
    public boolean fenceFollowTerrain=true;
    public double fenceElevation, fencePostSpacing=2;
    public boolean isFence() { return fence!=null && fence!=Fence.NONE; }
    public double x1, y1, x2, y2;
    /** Signed central angle in degrees; zero preserves legacy straight walls. */
    public double arcAngle;
    public String rotundaId;
    public double thickness = 0.15;
    public double height = 2.7;
    /** Finish of the left side (looking from start to end) and the right side. */
    public Finish left = new Finish("plaster", "#F3EFE7");
    public Finish right = new Finish("plaster", "#F3EFE7");
    public List<Opening> openings = new ArrayList<>();

    public Wall() {
    }

    public Wall(double x1, double y1, double x2, double y2, double thickness, double height) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.thickness = thickness;
        this.height = height;
    }

    public double length() {
        return curved() ? radius() * Math.abs(Math.toRadians(arcAngle)) : chord();
    }

    public boolean curved() { return Math.abs(arcAngle) > 1e-6; }

    public double chord() { return Math.hypot(x2-x1,y2-y1); }

    public double radius() {
        return curved() ? chord() / (2*Math.sin(Math.abs(Math.toRadians(arcAngle))/2)) : Double.POSITIVE_INFINITY;
    }

    public double[] center() {
        double dx = x2-x1, dy = y2-y1, c = chord();
        double offset = c / (2*Math.tan(Math.toRadians(arcAngle)/2));
        return new double[]{(x1+x2)/2-dy/c*offset,(y1+y2)/2+dx/c*offset};
    }

    /** Point at arc-length u and signed left-normal offset. Also supports extension outside the endpoints. */
    public double[] point(double u, double offset) {
        if (!curved()) return new double[]{x1+dirX()*u+nx()*offset,y1+dirY()*u+ny()*offset};
        double[] c = center();
        double radius = radius(), sign = Math.signum(arcAngle);
        double a = Math.atan2(y1-c[1],x1-c[0])+sign*u/radius;
        double r = radius+sign*offset;
        return new double[]{c[0]+r*Math.cos(a),c[1]+r*Math.sin(a)};
    }

    public double tangentX(double u) {
        if (!curved()) return dirX();
        double[] c = center(), p = point(u,0);
        return -Math.signum(arcAngle)*(p[1]-c[1])/radius();
    }

    public double tangentY(double u) {
        if (!curved()) return dirY();
        double[] c = center(), p = point(u,0);
        return Math.signum(arcAngle)*(p[0]-c[0])/radius();
    }

    /** Chord error at most 1 mm, angle step at most 2 degrees. */
    public int segments() {
        if (!curved()) return 1;
        double step = Math.min(Math.toRadians(2),2*Math.acos(Math.max(-1,1-0.001/radius())));
        return Math.max(2,(int)Math.ceil(Math.abs(Math.toRadians(arcAngle))/Math.max(1e-5,step)));
    }

    public double[][] outline() {
        int n = segments();
        double[][] points = new double[2*(n+1)][];
        for (int i = 0; i <= n; i++) {
            points[i] = point(length()*i/n,thickness/2);
            points[2*n+1-i] = point(length()*i/n,-thickness/2);
        }
        return points;
    }

    /** Exact axis bounds, including extrema that are not endpoints. */
    public double[] bounds() {
        double[] b = {Math.min(x1,x2),Math.min(y1,y2),Math.max(x1,x2),Math.max(y1,y2)};
        if (curved()) {
            double[] c = center();
            for (int i = 0; i < 4; i++) {
                double a = i*Math.PI/2, px = c[0]+radius()*Math.cos(a), py = c[1]+radius()*Math.sin(a);
                double u = project(px,py);
                if (u >= -1e-8 && u <= length()+1e-8) {
                    b[0] = Math.min(b[0],px); b[1] = Math.min(b[1],py);
                    b[2] = Math.max(b[2],px); b[3] = Math.max(b[3],py);
                }
            }
        }
        return b;
    }

    public void validate() {
        for (double v : new double[]{x1,y1,x2,y2,arcAngle,height,thickness})
            if (!Double.isFinite(v)) throw new IllegalArgumentException(I18n.text("A fal adatai csak véges számok lehetnek."));
        if (chord() < 0.02 || height < 0.1 || thickness < 0.02 || Math.abs(arcAngle) > 350)
            throw new IllegalArgumentException(I18n.text("A fal túl rövid, vagy az ívszög kívül esik a −350°…350° tartományon."));
        if (curved() && radius() <= thickness/2+0.02)
            throw new IllegalArgumentException(I18n.text("Az ív sugara legyen nagyobb a fal fél vastagságánál legalább 2 cm-rel."));
        if(isFence() && (!Double.isFinite(fenceElevation) || Math.abs(fenceElevation)>100
                || !Double.isFinite(fencePostSpacing) || fencePostSpacing<0.3 || fencePostSpacing>5
                || length()>500 || height>10 || thickness>2 || !openings.isEmpty() || rotundaId!=null))
            throw new IllegalArgumentException(I18n.text("A kerítés legfeljebb 500 m hosszú, 10 m magas lehet; oszlopköze 30–500 cm. Nyílásos vagy rotundafal előbb nem alakítható kerítéssé."));
    }

    public double dirX() {
        double l = chord();
        return l < 1e-9 ? 1 : (x2 - x1) / l;
    }

    public double dirY() {
        double l = chord();
        return l < 1e-9 ? 0 : (y2 - y1) / l;
    }

    /** Unit normal pointing to the left side. */
    public double nx() {
        return dirY();
    }

    public double ny() {
        return -dirX();
    }

    /** Distance from point to wall axis segment. */
    public double distance(double px, double py) {
        double t = project(px, py);
        t = Math.max(0, Math.min(length(), t));
        double[] p = point(t,0);
        return Math.hypot(px-p[0],py-p[1]);
    }

    /** Position of the projection of the point along the axis (meters from start). */
    public double project(double px, double py) {
        if (curved()) {
            double[] c = center();
            double start = Math.atan2(y1-c[1],x1-c[0]);
            double angle = Math.signum(arcAngle)*(Math.atan2(py-c[1],px-c[0])-start);
            angle = (angle % (2*Math.PI)+2*Math.PI)%(2*Math.PI);
            double sweep = Math.abs(Math.toRadians(arcAngle));
            if (angle > sweep && angle-sweep > 2*Math.PI-angle) angle -= 2*Math.PI;
            return angle*radius();
        }
        return (px - x1) * dirX() + (py - y1) * dirY();
    }

    public static Wall through(Pt start, Pt middle, Pt end, double thickness, double height) {
        double bx = middle.x-start.x, by = middle.y-start.y, cx = end.x-start.x, cy = end.y-start.y;
        double det = 2*(bx*cy-by*cx);
        if (Math.abs(det) < 1e-8*Math.max(1,Math.hypot(bx,by)*Math.hypot(cx,cy)))
            throw new IllegalArgumentException(I18n.text("A körív három pontja nem eshet egy egyenesre. Jelölj ki egy másik ívpontot!"));
        double b2 = bx*bx+by*by, c2 = cx*cx+cy*cy;
        double ox = (cy*b2-by*c2)/det, oy = (bx*c2-cx*b2)/det;
        double a0 = Math.atan2(-oy,-ox), am = Math.atan2(by-oy,bx-ox), a1 = Math.atan2(cy-oy,cx-ox);
        double full = 2*Math.PI;
        double endAngle = ((a1-a0)%full+full)%full, midAngle = ((am-a0)%full+full)%full;
        Wall w = new Wall(start.x,start.y,end.x,end.y,thickness,height);
        w.arcAngle = Math.toDegrees(midAngle < endAngle ? endAngle : endAngle-full);
        w.validate();
        return w;
    }
}
