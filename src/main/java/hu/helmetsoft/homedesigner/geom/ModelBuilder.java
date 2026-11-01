package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small metric primitives shared by the original catalog assets and parametric stairs. */
public final class ModelBuilder {
    private static final double[] X = {1,0,0}, Y = {0,1,0}, Z = {0,0,1};
    private final Map<SurfaceMaterial,MeshBuilder> parts = new LinkedHashMap<>();

    public MeshBuilder part(SurfaceMaterial material) {
        return parts.computeIfAbsent(material,m -> new MeshBuilder(m.tileSize));
    }

    public void box(SurfaceMaterial material, double x, double y, double z, double w, double h, double d) {
        part(material).box(new double[]{x,y,z},X,Y,Z,w,h,d);
    }

    public void rod(SurfaceMaterial material, double[] a, double[] b, double radius) {
        double dx = b[0]-a[0], dy = b[1]-a[1], dz = b[2]-a[2], length = Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (length < 1e-9) throw new IllegalArgumentException(I18n.text("A rúd hossza legyen pozitív."));
        double[] axis = {dx/length,dy/length,dz/length};
        double[] u = Math.abs(axis[1]) < 0.9 ? unit(cross(axis,Y)) : unit(cross(axis,X)), v = cross(axis,u);
        MeshBuilder mesh = part(material);
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a0 = 2*Math.PI*i/segments, a1 = 2*Math.PI*(i+1)/segments;
            double[] n0 = sum(u,Math.cos(a0),v,Math.sin(a0)), n1 = sum(u,Math.cos(a1),v,Math.sin(a1));
            int p = vertex(mesh,offset(a,n0,radius),n0,0,(double)i/segments);
            int q = vertex(mesh,offset(a,n1,radius),n1,0,(double)(i+1)/segments);
            int r = vertex(mesh,offset(b,n1,radius),n1,length,(double)(i+1)/segments);
            int s = vertex(mesh,offset(b,n0,radius),n0,length,(double)i/segments);
            mesh.quad(p,q,r,s);
            mesh.triangle(a,offset(a,n1,radius),offset(a,n0,radius),new double[]{-axis[0],-axis[1],-axis[2]},u,v);
            mesh.triangle(b,offset(b,n0,radius),offset(b,n1,radius),axis,u,v);
        }
    }

    /** Closed elliptical/superelliptical shell; profile rows are {radiusX, height, radiusZ}. */
    public void shell(SurfaceMaterial material, double cx, double cz, double exponent, double[][] profile) {
        MeshBuilder mesh = part(material);
        int segments = 64;
        int[][] indices = new int[profile.length][segments+1];
        for (int j = 0; j < profile.length; j++) for (int i = 0; i <= segments; i++) {
            double phi = 2*Math.PI*(i%segments)/segments;
            double[] p = ring(profile[j],phi,exponent,cx,cz);
            double[] prev = ring(profile[j],phi-0.0001,exponent,cx,cz), next = ring(profile[j],phi+0.0001,exponent,cx,cz);
            int lo = Math.max(0,j-1), hi = Math.min(profile.length-1,j+1);
            double[] down = ring(profile[lo],phi,exponent,cx,cz), up = ring(profile[hi],phi,exponent,cx,cz);
            double[] normal = unit(cross(subtract(up,down),subtract(next,prev)));
            indices[j][i] = vertex(mesh,p,normal,phi/Math.PI,profile[j][1]);
        }
        for (int j = 0; j < profile.length-1; j++) for (int i = 0; i < segments; i++)
            mesh.quad(indices[j][i],indices[j+1][i],indices[j+1][i+1],indices[j][i+1]);
        for (int end : new int[]{0,profile.length-1}) if (profile[end][0] <= 0.001 && profile[end][2] <= 0.001) {
            double[] normal = {0,end == 0 ? -1 : 1,0}, center = {cx,profile[end][1],cz};
            for (int i = 0; i < segments; i++) {
                double[] a = ring(profile[end],2*Math.PI*i/segments,exponent,cx,cz);
                double[] b = ring(profile[end],2*Math.PI*(i+1)/segments,exponent,cx,cz);
                if (end == 0) mesh.triangle(center,a,b,normal,X,Z);
                else mesh.triangle(center,b,a,normal,X,Z);
            }
        }
    }

    public void sphere(SurfaceMaterial material, double x, double y, double z, double rx, double ry, double rz) {
        double[][] profile = new double[25][3];
        for (int j = 0; j < profile.length; j++) {
            double a = -Math.PI/2+Math.PI*j/(profile.length-1);
            profile[j] = new double[]{Math.max(0.00001,rx*Math.cos(a)),y+ry*Math.sin(a),Math.max(0.00001,rz*Math.cos(a))};
        }
        shell(material,x,z,1,profile);
    }

    public ModelData build(String source) {
        ModelData model = new ModelData(source);
        parts.forEach((material,mesh) -> { if (!mesh.isEmpty()) model.parts.add(new ModelData.Part(mesh.build(),material)); });
        model.computeBounds();
        return model;
    }

    public static SurfaceMaterial material(String name, double r, double g, double b, double roughness, boolean metal, boolean glass) {
        SurfaceMaterial m = new SurfaceMaterial(name);
        m.color = new double[]{r,g,b}; m.roughness = roughness; m.specular = 0.8; m.metal = metal;
        m.glass = glass; m.opacity = glass ? 0.22 : 1;
        return m;
    }

    private static double[] ring(double[] profile, double phi, double exponent, double cx, double cz) {
        double c = Math.cos(phi), s = Math.sin(phi);
        return new double[]{cx+profile[0]*Math.copySign(Math.pow(Math.abs(c),exponent),c),profile[1],
                cz+profile[2]*Math.copySign(Math.pow(Math.abs(s),exponent),s)};
    }

    private static int vertex(MeshBuilder b, double[] p, double[] n, double u, double v) {
        return b.vertex(p[0],p[1],p[2],n[0],n[1],n[2],u,v);
    }
    private static double[] subtract(double[] a, double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double[] sum(double[] a, double s, double[] b, double t) { return new double[]{a[0]*s+b[0]*t,a[1]*s+b[1]*t,a[2]*s+b[2]*t}; }
    private static double[] offset(double[] a, double[] n, double r) { return new double[]{a[0]+r*n[0],a[1]+r*n[1],a[2]+r*n[2]}; }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static double[] unit(double[] a) {
        double length = Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);
        if (length < 1e-12) throw new IllegalArgumentException(I18n.text("Elfajult modellfelület."));
        return new double[]{a[0]/length,a[1]/length,a[2]/length};
    }
}
