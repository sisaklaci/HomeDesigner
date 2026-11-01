package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.*;
import java.awt.geom.Area;

/** Shared floor regions for geometry, placement and walkthrough eye heights. */
public final class FloorLevels {
    private FloorLevels() {}

    public static Area area(FloorZone zone) { return RoofOutline.area(SlabGeometry.boundary(zone.outline)); }

    public static Area base(Room room) {
        Area result=SlabGeometry.surface(room,false);
        for(FloorZone zone:room.floorZones) result.subtract(area(zone));
        return result;
    }

    public static Area surface(Room room,FloorZone zone) {
        Area result=area(zone);
        result.intersect(SlabGeometry.surface(room,false));
        return result;
    }

    public static double floorAt(Project project,double x,double y) {
        for(int i=project.rooms.size()-1;i>=0;i--) {
            Room room=project.rooms.get(i);
            if(!room.contains(x,y)) continue;
            for(FloorZone zone:room.floorZones)
                if(SlabGeometry.contains(zone.outline,x,y)) return room.elevation+zone.offset;
            return room.elevation;
        }
        double terrace=Double.NEGATIVE_INFINITY;
        if(project.balconies!=null) for(Balcony b:project.balconies) if(b.contains(x,y)) terrace=Math.max(terrace,b.elevation);
        if(Double.isFinite(terrace)) return terrace;
        return SiteGeometry.height(project,x,y)+0.01-project.activeLevel().baseHeight;
    }

    public static double walkHeight(Project project,double x,double y) {
        project.ensureLevels();
        double floor=project.activeLevel().baseHeight+floorAt(project,x,y);
        java.util.List<Double> supports=new java.util.ArrayList<>();
        for(Furniture f:project.furniture) if(f.stair!=null)
            stairSupports(f,project.activeLevel().baseHeight,x,y,supports);
        for(double h:supports) floor=Math.max(floor,h);
        return floor;
    }

    /** Walkable balcony/terrace slab tops and retaining-wall crests at a plan point, in world heights. */
    static void slabSupports(BuildingLevel level,Environment env,double x,double y,java.util.List<Double> supports) {
        if(level.balconies!=null) for(Balcony b:level.balconies)
            if(b.contains(x,y)) supports.add(level.baseHeight+b.elevation);
        if(level.retainingWalls!=null) for(RetainingWall w:level.retainingWalls)
            if(w.contains(x,y,0)) supports.add(RetainingWallGeometry.topWorld(w,level.baseHeight));
    }

    /** Reachable support near the previous foot height, never the uppermost overlapping storey.
     * The caller subdivides horizontal motion so individual stair risers cannot be skipped. */
    public static double walkHeight(Project project,double x,double y,double previousFloor) {
        project.ensureLevels();
        java.util.List<Double> supports=new java.util.ArrayList<>();
        java.util.List<Double> stairTreads=new java.util.ArrayList<>();
        double highestBelow=Double.NEGATIVE_INFINITY;
        boolean footprint=false;
        double site=Double.NaN;
        for(BuildingLevel level:project.levels) {
            for(Room room:level.rooms) {
                if(room.contains(x,y)) footprint=true;
                if(!SlabGeometry.surface(room,false).contains(x,y)) continue;
                double h=room.elevation+level.baseHeight;
                for(FloorZone zone:room.floorZones)
                    if(area(zone).contains(x,y)) { h+=zone.offset; break; }
                supports.add(h);
            }
            if(!footprint) {
                if(Double.isNaN(site)) site=SiteGeometry.height(project,x,y)+0.01;
                supports.add(site);
            }
            slabSupports(level,project.env,x,y,supports);
            for(Furniture f:level.furniture) if(f.stair!=null)
                stairSupports(f,level.baseHeight,x,y,stairTreads);
        }
        for(double h:supports) {
            if(h<=previousFloor+0.36 && h>highestBelow) highestBelow=h;
        }
        for(double h:stairTreads) {
            if(h<=previousFloor+0.4+1e-8 && h>highestBelow) highestBelow=h;
        }
        if(Double.isFinite(highestBelow)) return highestBelow;
        // Outdoors remains the legacy zero datum. Do not teleport out of basements through a floor.
        return previousFloor<-.36 ? previousFloor : 0;
    }

    private static void stairSupports(Furniture f,double base,double x,double y,java.util.List<Double> supports) {
        double a=Math.toRadians(f.angle),c=Math.cos(a),s=Math.sin(a);
        double dx=x-f.x,dy=y-f.y,lx=c*dx+s*dy,lz=-s*dx+c*dy;
        Stair stair=f.stair;
        if(stair.type==Stair.Type.STRAIGHT) {
            if(Math.abs(lx)<=f.width/2 && lz>=-f.depth/2 && lz<=f.depth/2) {
                int step=Math.min(stair.steps-1,(int)((lz+f.depth/2)/f.depth*stair.steps));
                supports.add(base+f.elevation+(step+1)*f.height/stair.steps);
            }
        } else {
            double radius=Math.hypot(lx,lz);
            if(radius<stair.innerRadius || radius>f.width/2) return;
            double sign=stair.clockwise ? 1 : -1;
            double angle=sign*(Math.atan2(lz,lx)+Math.PI/2), turn=Math.toRadians(stair.turn);
            angle=(angle%(2*Math.PI)+2*Math.PI)%(2*Math.PI);
            for(double t=angle;t<=turn+1e-8;t+=2*Math.PI) {
                int step=Math.min(stair.steps-1,(int)(t/turn*stair.steps));
                supports.add(base+f.elevation+(step+1)*f.height/stair.steps);
            }
        }
    }

    public static void validate(Project project) {
        for(Room room:project.rooms) {
            if(!Double.isFinite(room.elevation) || Math.abs(room.elevation)>10)
                throw new IllegalArgumentException(I18n.text("A padlószint −1000 és +1000 cm közötti véges szám legyen."));
            double ceiling=room.ceilingHeight>0 ? room.ceilingHeight : project.maxWallHeight();
            if(room.hasCeiling && room.elevation>=ceiling-0.1)
                throw new IllegalArgumentException(I18n.text("A padló felett legalább 10 cm maradjon a mennyezetig."));
            Area occupied=new Area(),parent=SlabGeometry.roomArea(room);
            for(FloorZone zone:room.floorZones) {
                if(zone==null || zone.outline==null || zone.finish==null)
                    throw new IllegalArgumentException(I18n.text("Hiányzó padlószint-terület vagy burkolat."));
                SlabGeometry.validate(zone.outline);
                if(!Double.isFinite(zone.offset) || Math.abs(zone.offset)>10 || Math.abs(room.elevation+zone.offset)>10)
                    throw new IllegalArgumentException(I18n.text("A helyi padlószint −1000 és +1000 cm között legyen."));
                if(room.hasCeiling && room.elevation+zone.offset>=ceiling-0.1)
                    throw new IllegalArgumentException(I18n.text("A terület felett legalább 10 cm maradjon a mennyezetig."));
                Area region=area(zone),outside=new Area(region);
                outside.subtract(parent);
                if(!outside.isEmpty()) throw new IllegalArgumentException(I18n.text("A padlószint-terület teljesen a saját helyiségén belül legyen."));
                Area overlap=new Area(region); overlap.intersect(occupied);
                if(!overlap.isEmpty()) throw new IllegalArgumentException(I18n.text("A padlószint-területek nem fedhetik egymást."));
                occupied.add(region);
            }
        }
    }

    public static Furniture steps(Project project,Room room,FloorZone zone,int edge,int count,double width,double going) {
        double[][] polygon=zone==null ? room.points.stream().map(p -> new double[]{p.x,p.y}).toArray(double[][]::new)
                : SlabGeometry.boundary(zone.outline);
        if(edge<0 || edge>=polygon.length)
            throw new IllegalArgumentException(I18n.text("Válassz érvényes oldalt a lépcsőhöz!"));
        double[] a=polygon[edge],b=polygon[(edge+1)%polygon.length];
        double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
        if(width>length || width<0.5 || going<0.1 || going>1)
            throw new IllegalArgumentException(I18n.text("A lépcső legalább 50 cm széles legyen és férjen el a kiválasztott oldalon; a belépés 10–100 cm legyen."));
        double mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2,nx=-(b[1]-a[1])/length,ny=(b[0]-a[0])/length;
        Area region=zone==null ? SlabGeometry.roomArea(room) : area(zone);
        if(!region.contains(mx+nx*0.001,my+ny*0.001)) { nx=-nx; ny=-ny; }
        double inside=room.elevation+(zone==null ? 0 : zone.offset);
        double outside=zone==null ? floorAt(project,mx-nx*0.01,my-ny*0.01) : room.elevation;
        double difference=inside-outside;
        if(Math.abs(difference)<0.05) throw new IllegalArgumentException(I18n.text("A kiválasztott oldalon legalább 5 cm szintkülönbség szükséges."));
        boolean lowered=difference<0;
        double ux=lowered ? -nx : nx,uy=lowered ? -ny : ny;
        Furniture f=new Furniture(); f.stair=new Stair(); f.stair.steps=count;
        f.stair.railing=false; f.stair.solid=true;
        f.height=Math.abs(difference); f.width=width; f.depth=count*going;
        f.stair.treadThickness=Math.min(0.04,f.height/count);
        f.stair.treads=(zone==null ? room.floor : zone.finish).copy();
        f.elevation=Math.min(inside,outside);
        f.x=mx-ux*f.depth/2; f.y=my-uy*f.depth/2;
        f.angle=Math.toDegrees(Math.atan2(-ux,uy)); f.name="Szintkülönbség lépcsője";
        StairGeometry.validate(f);
        if(zone!=null) {
            SlabOpening footprint=new SlabOpening();
            footprint.x=f.x; footprint.y=f.y; footprint.width=f.width; footprint.depth=f.depth; footprint.angle=f.angle;
            Area allowed=lowered ? surface(room,zone) : base(room);
            Area stepArea=RoofOutline.area(SlabGeometry.boundary(footprint));
            stepArea.subtract(allowed);
            // Avoid false failures from round-off at the shared landing edge.
            if(areaSize(stepArea)>1e-7) throw new IllegalArgumentException(I18n.text("A lépcső nem fér el a terület alacsonyabb oldalán, vagy nyílással/másik szintterülettel ütközik."));
        }
        return f;
    }

    private static double areaSize(Area area) {
        double sum=0;
        for(double[][] t:SlabGeometry.triangles(area))
            sum+=Math.abs((t[1][0]-t[0][0])*(t[2][1]-t[0][1])-(t[1][1]-t[0][1])*(t[2][0]-t[0][0]))/2;
        return sum;
    }
}
