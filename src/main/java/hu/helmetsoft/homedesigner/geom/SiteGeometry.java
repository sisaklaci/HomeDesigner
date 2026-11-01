package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.I18n;
import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.*;

/** Additive earthworks: existing terrain is never lowered by a fill. */
public final class SiteGeometry {
    private static final double[] X={1,0,0},Z={0,0,1};
    private record Source(Area footprint,List<double[][]> contours,double top,double width,
                          boolean vertical,Object owner,Finish finish) {
        double distance(double x,double z) {
            double result=Double.POSITIVE_INFINITY;
            for(double[][] contour:contours) result=Math.min(result,RoofOutline.distance(contour,x,z));
            return result;
        }
        double at(double x,double z,double terrain) {
            if(footprint.contains(x,z) || distance(x,z)<1e-8) return Math.max(terrain,top);
            if(vertical) return terrain;
            double distance=distance(x,z);
            return distance>=width ? terrain : terrain+Math.max(0,top-terrain)*(1-distance/width);
        }
        Area influence() {
            Area result=new Area(footprint);
            if(!vertical) result.add(new Area(new BasicStroke((float)(2*width),
                    BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(footprint)));
            return result;
        }
    }
    private SiteGeometry() {}

    public static Area area(SiteFill fill) { return RoofOutline.area(SlabGeometry.boundary(fill.outline)); }

    public static void validate(Environment e) {
        if(e.siteFills==null) e.siteFills=new ArrayList<>();
        if(e.embankmentFinish==null) e.embankmentFinish=new Finish("grass",null);
        if(!Double.isFinite(e.embankmentWidth) || e.embankmentWidth<0.1 || e.embankmentWidth>30
                || !Double.isFinite(e.embankmentTopOffset) || e.embankmentTopOffset>0 || e.embankmentTopOffset< -5)
            throw new IllegalArgumentException(I18n.text("A rézsű szélessége 10–3000 cm, felső eltolása −500 és 0 cm között legyen."));
        for(SiteFill fill:e.siteFills) {
            if(fill==null || fill.finish==null || fill.name==null || fill.name.isBlank())
                throw new IllegalArgumentException(I18n.text("Hiányzó földfeltöltés, név vagy burkolat."));
            SlabGeometry.validate(fill.outline);
            if(!Double.isFinite(fill.elevation) || Math.abs(fill.elevation)>100
                    || !Double.isFinite(fill.slopeWidth) || fill.slopeWidth<0.1 || fill.slopeWidth>30)
                throw new IllegalArgumentException(I18n.text("A feltöltés szintje −100 és +100 m, rézsűszélessége 10–3000 cm között legyen."));
        }
    }

    /** Absolute visual surface. Add 1 cm for the legacy walk/placement ground datum. */
    public static double height(Project p,double x,double z) {
        if(p.env.siteFills.isEmpty() && !p.env.autoEmbankment) return TerrainGeometry.height(p.env,x,z);
        List<TerrainGeometry.Pad> pads=new ArrayList<>();
        if(p.env.autoEmbankment) {
            p.ensureLevels();
            for(BuildingLevel level:p.levels) for(Room room:level.rooms)
                pads.add(new TerrainGeometry.Pad(room,level.baseHeight+room.elevation));
        }
        return height(p.env,sources(p.env,pads),x,z);
    }

    private static double height(Environment e,List<Source> sources,double x,double z) {
        double terrain=TerrainGeometry.height(e,x,z),result=terrain;
        for(Source source:sources) result=Math.max(result,source.at(x,z,terrain));
        return result;
    }

    static java.util.function.DoubleBinaryOperator heightSampler(Environment e,List<TerrainGeometry.Pad> pads) {
        List<Source> sources=sources(e,pads);
        return (x,z) -> height(e,sources,x,z);
    }

    private static List<Source> sources(Environment e,List<TerrainGeometry.Pad> pads) {
        List<Source> out=new ArrayList<>();
        for(SiteFill fill:e.siteFills) {
            Area footprint=area(fill);
            out.add(new Source(footprint,RoofOutline.contours(footprint),fill.elevation-0.01,
                    fill.slopeWidth,fill.retaining,fill,fill.finish));
        }
        if(e.autoEmbankment) {
            record Platform(Area footprint,double floor) {}
            List<Platform> platforms=new ArrayList<>();
            for(var pad:pads) {
                Area base=SlabGeometry.roomArea(pad.room());
                for(FloorZone zone:pad.room().floorZones) {
                    Area footprint=RoofOutline.area(SlabGeometry.boundary(zone.outline));
                    footprint.intersect(SlabGeometry.roomArea(pad.room()));
                    base.subtract(footprint);
                    platforms.add(new Platform(footprint,pad.floor()+zone.offset));
                }
                platforms.add(new Platform(base,pad.floor()));
            }
            Area occupied=new Area();
            for(var pad:platforms.stream().sorted(Comparator.comparingDouble(Platform::floor)).toList()) {
                Area footprint=new Area(pad.footprint());
                footprint.subtract(occupied);
                occupied.add(pad.footprint());
                if(!footprint.isEmpty()) out.add(new Source(footprint,RoofOutline.contours(footprint),
                        pad.floor()+e.embankmentTopOffset-0.01,e.embankmentWidth,false,e,e.embankmentFinish));
            }
        }
        return out;
    }

    /** Ground area has already had building/cellar footprints removed. */
    public static List<GeoPart> buildSurface(Environment e,List<TerrainGeometry.Pad> pads,Area ground) {
        validate(e);
        List<Source> sources=sources(e,pads);
        Area earthwork=new Area();
        for(Source source:sources) earthwork.add(source.influence());
        earthwork.intersect(ground);
        Area untouched=new Area(ground); untouched.subtract(earthwork);
        List<GeoPart> out=new ArrayList<>();
        MeshBuilder base=new MeshBuilder(MaterialLibrary.resolve(e.ground).tileSize);
        for(double[][] t:SlabGeometry.triangles(untouched)) triangle(base,e,List.of(),t);
        if(!base.isEmpty()) out.add(new GeoPart(e,MaterialLibrary.resolve(e.ground),base.build()));
        Map<Object,Map<String,MeshBuilder>> meshes=new IdentityHashMap<>();
        Map<String,SurfaceMaterial> materials=new HashMap<>();
        List<Object> owners=new ArrayList<>();
        Area assigned=new Area();
        for(Source source:sources) {
            Area patch=source.influence();
            patch.intersect(earthwork); patch.subtract(assigned); assigned.add(patch);
            if(patch.isEmpty()) continue;
            // Separate sharp vertical fill boundaries before tessellation, avoiding sloped edge triangles.
            List<Area> regions=new ArrayList<>(List.of(patch));
            for(Source vertical:sources) if(vertical.vertical) {
                List<Area> split=new ArrayList<>();
                for(Area region:regions) {
                    Area inside=new Area(region); inside.intersect(vertical.footprint);
                    Area outside=new Area(region); outside.subtract(vertical.footprint);
                    if(!inside.isEmpty()) split.add(inside);
                    if(!outside.isEmpty()) split.add(outside);
                }
                regions=split;
            }
            for(Area region:regions) tessellate(region,(t,cx,cz) -> {
                double terrain=TerrainGeometry.height(e,cx,cz),highest=terrain;
                Source winner=null;
                for(Source candidate:sources) {
                    double h=candidate.at(cx,cz,terrain);
                    if(h>highest+1e-8) { highest=h; winner=candidate; }
                }
                Object owner=winner==null ? e : winner.owner;
                Finish finish=winner==null ? e.ground : winner.finish;
                SurfaceMaterial material=MaterialLibrary.resolve(finish);
                materials.put(material.key,material);
                Map<String,MeshBuilder> parts=meshes.get(owner);
                if(parts==null) { parts=new LinkedHashMap<>(); meshes.put(owner,parts); owners.add(owner); }
                MeshBuilder mesh=parts.computeIfAbsent(material.key,k -> new MeshBuilder(material.tileSize));
                // Use the side's own interior sample for discontinuous vertical boundaries.
                List<Source> applicable=sources.stream().filter(s -> !s.vertical || s.footprint.contains(cx,cz)).toList();
                triangle(mesh,e,applicable,t);
            });
        }
        for(Source source:sources) if(source.vertical) {
            SurfaceMaterial material=MaterialLibrary.resolve("grass".equals(source.finish.preset) ? new Finish("soil",null):source.finish);
            MeshBuilder sides=new MeshBuilder(material.tileSize);
            for(double[][] contour:source.contours) for(int i=0;i<contour.length;i++) {
                double[] a=contour[i],b=contour[(i+1)%contour.length];
                int n=Math.max(1,(int)Math.ceil(Math.hypot(a[0]-b[0],a[1]-b[1])/0.2));
                for(int j=0;j<n;j++) {
                    double t=(double)j/n,u=(double)(j+1)/n;
                    double x=a[0]+t*(b[0]-a[0]),z=a[1]+t*(b[1]-a[1]);
                    double xx=a[0]+u*(b[0]-a[0]),zz=a[1]+u*(b[1]-a[1]);
                    double length=Math.hypot(xx-x,zz-z),nx=(zz-z)/length,nz=-(xx-x)/length;
                    double sign=source.footprint.contains((x+xx)/2+nx*1e-5,(z+zz)/2+nz*1e-5) ? -1:1;
                    if(!ground.contains((x+xx)/2+sign*nx*1e-4,(z+zz)/2+sign*nz*1e-4)) continue;
                    double low=Math.min(source.top,height(e,sources,(x+xx)/2+sign*nx*1e-4,(z+zz)/2+sign*nz*1e-4));
                    if(low>=source.top-1e-5) continue;
                    double[] p={x,Math.min(source.top,TerrainGeometry.height(e,x,z)),z};
                    double[] q={xx,Math.min(source.top,TerrainGeometry.height(e,xx,zz)),zz};
                    double[] r={xx,source.top,zz},s={x,source.top,z},normal={sign*nx,0,sign*nz};
                    double[] along={(xx-x)/length,0,(zz-z)/length},up={0,1,0};
                    if(sign>0) sides.quad(p,s,r,q,normal,along,up); else sides.quad(p,q,r,s,normal,along,up);
                }
            }
            if(!sides.isEmpty()) out.add(new GeoPart(source.owner,material,sides.build()));
        }
        for(Object owner:owners) for(var part:meshes.get(owner).entrySet())
            out.add(new GeoPart(owner,materials.get(part.getKey()),part.getValue().build()));
        return out;
    }

    private interface Cell { void add(double[][] triangle,double x,double z); }
    private static void tessellate(Area area,Cell consumer) {
        Rectangle2D bounds=area.getBounds2D();
        int nx=Math.max(1,Math.min(256,(int)Math.ceil(bounds.getWidth()/0.2)));
        int nz=Math.max(1,Math.min(256,(int)Math.ceil(bounds.getHeight()/0.2)));
        double dx=bounds.getWidth()/nx,dz=bounds.getHeight()/nz;
        for(int x=0;x<nx;x++) for(int z=0;z<nz;z++) {
            Area cell=new Area(new Rectangle2D.Double(bounds.getMinX()+x*dx,bounds.getMinY()+z*dz,dx,dz));
            cell.intersect(area);
            for(double[][] t:SlabGeometry.triangles(cell))
                consumer.add(t,(t[0][0]+t[1][0]+t[2][0])/3,(t[0][1]+t[1][1]+t[2][1])/3);
        }
    }

    private static void triangle(MeshBuilder mesh,Environment e,List<Source> sources,double[][] t) {
        double[][] v=new double[3][];
        for(int i=0;i<3;i++) {
            double h=height(e,sources,t[i][0],t[i][1]);
            // Vertical sources were clipped to this triangle's region before triangulation.
            for(Source source:sources) if(source.vertical) h=Math.max(h,source.top);
            v[i]=new double[]{t[i][0],h,t[i][1]};
        }
        double[] a=v[0],b=v[2],c=v[1];
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length>1e-10) mesh.triangle(a,b,c,new double[]{nx/length,ny/length,nz/length},X,Z);
    }
}
