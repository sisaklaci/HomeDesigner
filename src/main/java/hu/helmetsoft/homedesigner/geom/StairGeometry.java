package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;

public final class StairGeometry {
    private StairGeometry() {}

    public static double totalHeight(Furniture f) {
        return f.height+(f.stair != null && f.stair.railing ? f.stair.railingHeight+0.025 : 0);
    }

    public static void validate(Furniture f) {
        Stair s = f.stair;
        if (s == null) return;
        for (double v : new double[]{f.width,f.depth,f.height,f.x,f.y,f.angle,f.elevation,s.treadThickness,s.turn,s.innerRadius,s.railingHeight})
            if (!Double.isFinite(v)) throw new IllegalArgumentException(I18n.text("A lépcső adatai véges számok legyenek."));
        if (s.type == null || s.steps < (s.type==Stair.Type.STRAIGHT ? 1 : 3) || s.steps > 80 || f.width < 0.5 || f.width > 20
                || f.depth < (s.type==Stair.Type.STRAIGHT ? 0.1 : 0.5) || f.depth > 30 || f.height < 0.05 || f.height > 10
                || f.height/s.steps < 0.05 || f.height/s.steps > 0.4
                || s.treadThickness < 0.02 || s.treadThickness > Math.min(0.2,f.height/s.steps)
                || s.railingHeight < 0.5 || s.railingHeight > 1.5 || s.treads == null || s.frame == null)
            throw new IllegalArgumentException(I18n.text("Érvénytelen lépcsőméret: egyenesnél 1–80, csigánál 3–80 fok, 5–40 cm fellépés és a fellépésnél nem vastagabb (2–20 cm) járólap szükséges."));
        if (s.type == Stair.Type.SPIRAL && (Math.abs(f.width-f.depth) > 1e-6 || s.turn < 90 || s.turn > 720
                || s.innerRadius < 0.06 || s.innerRadius > f.width/2-0.3 || s.turn/s.steps > 60))
            throw new IllegalArgumentException(I18n.text("Csigalépcsőnél kör alap, 90–720° fordulat, legalább 30 cm járószélesség és legfeljebb 60°/fok szükséges."));
    }

    public static ModelData build(Furniture f) {
        validate(f);
        Stair s = f.stair;
        ModelBuilder b = new ModelBuilder();
        SurfaceMaterial wood = MaterialLibrary.resolve(s.treads), metal = MaterialLibrary.resolve(s.frame);
        double rise = f.height/s.steps;
        if (s.type == Stair.Type.STRAIGHT) {
            double going = f.depth/s.steps;
            for (int i = 0; i < s.steps; i++) {
                double z = -f.depth/2+i*going, h = (i+1)*rise;
                b.box(wood,-f.width/2,s.solid ? 0 : h-s.treadThickness,z,f.width,s.solid ? h : s.treadThickness,going);
                if (s.railing) for (int side : new int[]{-1,1}) {
                    double x = side*(f.width/2-0.04), center = z+going/2;
                    b.rod(metal,new double[]{x,h,center},new double[]{x,h+s.railingHeight,center},0.018);
                    if (i > 0) b.rod(metal,new double[]{x,h-rise+s.railingHeight,center-going},
                            new double[]{x,h+s.railingHeight,center},0.025);
                }
            }
            if(!s.solid) for (int side : new int[]{-1,1}) {
                double x = side*(f.width/2-0.09);
                b.rod(metal,new double[]{x,0.025,-f.depth/2+0.04},
                        new double[]{x,Math.max(0.025,f.height-s.treadThickness-0.06),f.depth/2-0.04},0.055);
            }
        } else {
            double radius = f.width/2, sign = s.clockwise ? 1 : -1, stepAngle = sign*Math.toRadians(s.turn)/s.steps;
            b.rod(metal,new double[]{0,0,0},new double[]{0,totalHeight(f),0},s.innerRadius);
            for (int i = 0; i < s.steps; i++) {
                double a = -Math.PI/2+i*stepAngle, h = (i+1)*rise;
                int segments = Math.max(4,(int)Math.ceil(Math.abs(Math.toDegrees(stepAngle))/3));
                for (int j = 0; j < segments; j++) {
                    double a0 = a+stepAngle*j/segments, a1 = a+stepAngle*(j+1)/segments;
                    sector(b.part(wood),s.innerRadius,radius,a0,a1,h-s.treadThickness,h);
                }
                if (s.railing) {
                    double mid = a+stepAngle/2, r = radius-0.04;
                    b.rod(metal,point(r,mid,h),point(r,mid,h+s.railingHeight),0.018);
                    if (i > 0) for (int j = 0; j < segments; j++) {
                        double t0 = (double)j/segments, t1 = (double)(j+1)/segments;
                        b.rod(metal,point(r,mid-stepAngle+stepAngle*t0,h-rise+rise*t0+s.railingHeight),
                                point(r,mid-stepAngle+stepAngle*t1,h-rise+rise*t1+s.railingHeight),0.025);
                    }
                }
            }
        }
        ModelData model = b.build("parametric-stair");
        // Keep the construction origin and physical dimensions, including empty space around partial spirals.
        model.minX = (float)-f.width/2; model.maxX = (float)f.width/2;
        model.minZ = (float)-f.depth/2; model.maxZ = (float)f.depth/2; model.minY = 0;
        model.maxY = (float)totalHeight(f);
        return model;
    }

    private static double[] point(double radius, double angle, double y) {
        return new double[]{radius*Math.cos(angle),y,radius*Math.sin(angle)};
    }

    private static void sector(MeshBuilder m, double inner, double outer, double a, double b, double bottom, double top) {
        double[][] p = {point(inner,a,top),point(outer,a,top),point(outer,b,top),point(inner,b,top)};
        double[] up = {0,1,0}, down = {0,-1,0}, x = {1,0,0}, z = {0,0,1};
        if (b > a) m.quad(p[3],p[2],p[1],p[0],up,x,z);
        else m.quad(p[0],p[1],p[2],p[3],up,x,z);
        double[][] q = new double[4][];
        for (int i = 0; i < 4; i++) q[i] = new double[]{p[i][0],bottom,p[i][2]};
        if (b > a) m.quad(q[0],q[1],q[2],q[3],down,x,z);
        else m.quad(q[3],q[2],q[1],q[0],down,x,z);
        for (int i = 0; i < 4; i++) {
            int j = (i+1)%4;
            double dx = p[j][0]-p[i][0], dz = p[j][2]-p[i][2], length = Math.hypot(dx,dz);
            double sign = b > a ? 1 : -1;
            double[] normal = {sign*dz/length,0,-sign*dx/length};
            if (b > a) m.quad(p[i],p[j],q[j],q[i],normal,x,down);
            else m.quad(q[i],q[j],p[j],p[i],normal,x,down);
        }
    }
}
