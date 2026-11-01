package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.I18n;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Sloping site with horizontal building pads and actual excavation below rooms. */
public final class TerrainGeometry {
    public record Pad(Room room,double floor) {}
    private record Region(Area footprint,Area surface,double floor) {}
    private static final double[] X={1,0,0},Z={0,0,1},UP={0,1,0};
    private TerrainGeometry() {}

    public static void validate(Environment e) {
        SiteGeometry.validate(e);
        if(e.foundation==null) e.foundation=Environment.Foundation.NONE;
        if(e.foundationFinish==null) e.foundationFinish=new Finish("concrete",null);
        if(!Double.isFinite(e.terrainSlope) || e.terrainSlope<0 || e.terrainSlope>60
                || !Double.isFinite(e.terrainAzimuth) || !Double.isFinite(e.terrainHeight)
                || Math.abs(e.terrainHeight)>100 || !Double.isFinite(e.terrainOriginX)
                || !Double.isFinite(e.terrainOriginY) || !Double.isFinite(e.foundationDepth)
                || e.foundationDepth<0.1 || e.foundationDepth>20)
            throw new IllegalArgumentException(I18n.text("Érvénytelen terep vagy alapozás: lejtés 0–60°, alapmélység 10–2000 cm."));
    }

    /** Height at origin is the datum; azimuth is the uphill direction (0 = plan north). */
    public static double height(Environment e,double x,double z) {
        double a=Math.toRadians(e.terrainAzimuth),s=Math.tan(Math.toRadians(e.terrainSlope));
        return e.terrainHeight+s*((x-e.terrainOriginX)*Math.sin(a)-(z-e.terrainOriginY)*Math.cos(a))-0.01;
    }

    public static List<GeoPart> build(Project p) {
        return build(p.env,p.rooms.stream().map(r -> new Pad(r,r.elevation)).toList(),p.wallBounds());
    }

    public static List<GeoPart> build(Environment e,List<Pad> pads,double[] wallBounds) {
        validate(e);
        List<GeoPart> out=new ArrayList<>();
        double cx=wallBounds==null ? e.terrainOriginX : (wallBounds[0]+wallBounds[2])/2;
        double cz=wallBounds==null ? e.terrainOriginY : (wallBounds[1]+wallBounds[3])/2;
        Area ground=new Area(new Rectangle2D.Double(cx-80,cz-80,160,160));
        List<Region> regions=new ArrayList<>();
        for(Pad pad:pads) {
            Area footprint=SlabGeometry.roomArea(pad.room());
            for(FloorZone zone:pad.room().floorZones) {
                Area zoneArea=FloorLevels.area(zone);
                zoneArea.intersect(SlabGeometry.roomArea(pad.room()));
                footprint.subtract(zoneArea);
                regions.add(new Region(zoneArea,FloorLevels.surface(pad.room(),zone),pad.floor()+zone.offset));
            }
            regions.add(new Region(footprint,FloorLevels.base(pad.room()),pad.floor()));
        }
        List<Region> sorted=regions.stream().sorted(Comparator.comparingDouble(Region::floor)).toList();
        Area claimed=new Area();
        var foundationMaterial=MaterialLibrary.resolve(e.foundationFinish);
        var soilMaterial=MaterialLibrary.resolve("grass".equals(e.ground.preset) ? new Finish("soil",null):e.ground);
        MeshBuilder concrete=new MeshBuilder(foundationMaterial.tileSize),soil=new MeshBuilder(soilMaterial.tileSize);
        boolean earthworks=e.autoEmbankment || !e.siteFills.isEmpty();
        var siteHeight=SiteGeometry.heightSampler(e,pads);
        for(Region pad:sorted) {
            Area footprint=new Area(pad.footprint());
            footprint.subtract(claimed);
            claimed.add(pad.footprint());
            if(footprint.isEmpty()) continue;
            double top=pad.floor()-0.005;
            Rectangle2D b=footprint.getBounds2D();
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(double x:new double[]{b.getMinX(),b.getMaxX()}) for(double z:new double[]{b.getMinY(),b.getMaxY()}) {
                double h=height(e,x,z); min=Math.min(min,h); max=Math.max(max,h);
            }
            if(max>top || e.foundation!=Environment.Foundation.NONE || earthworks) ground.subtract(footprint);
            if(e.foundation==Environment.Foundation.BLOCK) {
                double bottom=Math.min(top-0.15,min-e.foundationDepth);
                Area surface=new Area(pad.surface()); surface.intersect(footprint);
                horizontal(concrete,surface,top);
                sides(concrete,footprint,bottom,(x,z) -> top,false);
                horizontal(concrete,footprint,bottom,true);
            } else if(e.foundation==Environment.Foundation.EMBEDDED) {
                Area surface=new Area(pad.surface()); surface.intersect(footprint);
                horizontal(concrete,surface,top);
                sides(concrete,footprint,top-e.foundationDepth,(x,z) -> top,false);
                sides(soil,footprint,top-e.foundationDepth,
                        (x,z) -> Math.max(top-e.foundationDepth,siteHeight.applyAsDouble(x,z)),true,earthworks ? 0.2:Double.POSITIVE_INFINITY);
            }
            if(earthworks && e.foundation!=Environment.Foundation.EMBEDDED)
                sides(soil,footprint,top,(x,z) -> Math.max(top,siteHeight.applyAsDouble(x,z)),true,0.2);
        }
        if(e.showGround) {
            out.addAll(SiteGeometry.buildSurface(e,pads,ground));
            if(!soil.isEmpty()) out.add(new GeoPart(e,soilMaterial,soil.build()));
        }
        if(!concrete.isEmpty()) out.add(new GeoPart(e,foundationMaterial,concrete.build()));
        return out;
    }

    private static void horizontal(MeshBuilder mesh,Area area,double y) { horizontal(mesh,area,y,false); }
    private static void horizontal(MeshBuilder mesh,Area area,double y,boolean down) {
        for(double[][] t:SlabGeometry.triangles(area)) {
            double[] a={t[0][0],y,t[0][1]},b={t[1][0],y,t[1][1]},c={t[2][0],y,t[2][1]};
            mesh.triangle(a,down ? b:c,down ? c:b,down ? new double[]{0,-1,0}:UP,X,Z);
        }
    }

    private interface Height { double at(double x,double z); }
    private static void sides(MeshBuilder mesh,Area area,double bottom,Height top,boolean inward) {
        sides(mesh,area,bottom,top,inward,Double.POSITIVE_INFINITY);
    }
    private static void sides(MeshBuilder mesh,Area area,double bottom,Height top,boolean inward,double segment) {
        for(double[][] contour:RoofOutline.contours(area)) for(int i=0;i<contour.length;i++) {
            double[] start=contour[i],end=contour[(i+1)%contour.length];
            int steps=Math.max(1,(int)Math.ceil(Math.hypot(end[0]-start[0],end[1]-start[1])/segment));
            for(int j=0;j<steps;j++) {
                double t=(double)j/steps,u=(double)(j+1)/steps;
                double[] a={start[0]+t*(end[0]-start[0]),start[1]+t*(end[1]-start[1])};
                double[] b={start[0]+u*(end[0]-start[0]),start[1]+u*(end[1]-start[1])};
                double dx=b[0]-a[0],dz=b[1]-a[1],length=Math.hypot(dx,dz);
                if(length<1e-8) continue;
                double nx=dz/length,nz=-dx/length;
                boolean inside=area.contains((a[0]+b[0])/2+nx*1e-5,(a[1]+b[1])/2+nz*1e-5);
                double sign=(inside ? -1:1)*(inward ? -1:1);
                double[] p={a[0],bottom,a[1]},q={b[0],bottom,b[1]};
                double[] r={b[0],top.at(b[0],b[1]),b[1]},s={a[0],top.at(a[0],a[1]),a[1]};
                double[] n={sign*nx,0,sign*nz};
                if(sign>0) mesh.quad(p,s,r,q,n,X,Z); else mesh.quad(p,q,r,s,n,X,Z);
            }
        }
    }
}
