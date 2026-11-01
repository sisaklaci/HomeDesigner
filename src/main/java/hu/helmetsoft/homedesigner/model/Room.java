package hu.helmetsoft.homedesigner.model;

import java.util.ArrayList;
import java.util.List;

/** A room: floor polygon with floor and ceiling finish. */
public class Room {
    public enum Vault {
        NONE("Sík mennyezet"), BARREL("Dongaboltozat"), POINTED("Csúcsíves boltozat"),
        ELLIPTICAL("Lapos elliptikus boltozat"), GROIN("Keresztboltozat");
        public final String label;
        Vault(String label) { this.label=label; }
        @Override public String toString() { return hu.helmetsoft.homedesigner.util.I18n.text(label); }
    }
    public Vault vault=Vault.NONE;
    public double vaultRise=0.8;
    public boolean vaultAlongX=true, vaultRibs=true;
    public String name = hu.helmetsoft.homedesigner.util.I18n.text("Szoba");
    public List<Pt> points = new ArrayList<>();
    public Finish floor = new Finish("oak", null);
    public Finish ceiling = new Finish("plaster", "#FFFFFF");
    public boolean hasCeiling = true;
    /** 0 = automatic (highest wall). */
    public double ceilingHeight = 0;
    public String rotundaId;
    public double circleX, circleY, circleRadius;
    public List<SlabOpening> slabOpenings = new ArrayList<>();
    /** Absolute floor datum; ceilings remain at their absolute project height. */
    public double elevation;
    public List<FloorZone> floorZones = new ArrayList<>();

    public void updateCircle(double x, double y, double radius) {
        circleX = x; circleY = y; circleRadius = radius;
        points.clear();
        int segments = Math.max(180,(int)Math.ceil(Math.PI/Math.acos(Math.max(-1,1-0.001/radius))));
        for (int i = 0; i < segments; i++) {
            double a = 2*Math.PI*i/segments;
            points.add(new Pt(x+radius*Math.cos(a),y+radius*Math.sin(a)));
        }
    }

    public double area() {
        if (rotundaId != null) return Math.PI*circleRadius*circleRadius;
        double a = 0;
        for (int i = 0; i < points.size(); i++) {
            Pt p = points.get(i), q = points.get((i + 1) % points.size());
            a += p.x * q.y - q.x * p.y;
        }
        return Math.abs(a) / 2;
    }

    public double[] centroid() {
        if (rotundaId != null) return new double[]{circleX,circleY};
        double cx = 0, cy = 0, a = 0;
        for (int i = 0; i < points.size(); i++) {
            Pt p = points.get(i), q = points.get((i + 1) % points.size());
            double c = p.x * q.y - q.x * p.y;
            a += c;
            cx += (p.x + q.x) * c;
            cy += (p.y + q.y) * c;
        }
        if (Math.abs(a) < 1e-9) {
            double sx = 0, sy = 0;
            for (Pt p : points) { sx += p.x; sy += p.y; }
            return new double[]{sx / Math.max(1, points.size()), sy / Math.max(1, points.size())};
        }
        return new double[]{cx / (3 * a), cy / (3 * a)};
    }

    public boolean contains(double x, double y) {
        if (rotundaId != null) return Math.hypot(x-circleX,y-circleY) <= circleRadius;
        boolean in = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Pt a = points.get(i), b = points.get(j);
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) in = !in;
        }
        return in;
    }
}
