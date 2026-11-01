package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.Noise;

import java.util.*;

/** Converts the architectural model (walls, rooms, roofs, lamps, ground) into world-space meshes. World: Y up, plan (x,y) -> (x, z). */
public final class StructureBuilder {
    public static final class Options {
        public boolean ceilings = false, roofs = true, ground = true, lamps = true;
    }

    private final Map<Object, Map<String, Object[]>> parts = new IdentityHashMap<>();
    private final List<Object> order = new ArrayList<>();

    private MeshBuilder mb(Object owner, SurfaceMaterial m) {
        Map<String, Object[]> map = parts.computeIfAbsent(owner, k -> {
            order.add(k);
            return new LinkedHashMap<>();
        });
        return (MeshBuilder) map.computeIfAbsent(m.key, k -> new Object[]{m, new MeshBuilder(m.tileSize)})[1];
    }

    private List<GeoPart> result() {
        List<GeoPart> out = new ArrayList<>();
        for (Object o : order)
            for (Object[] e : parts.get(o).values()) {
                MeshBuilder b = (MeshBuilder) e[1];
                if (!b.isEmpty()) out.add(new GeoPart(o, (SurfaceMaterial) e[0], b.build()));
            }
        return out;
    }

    public static List<GeoPart> build(Project p, Options opt) {
        p.ensureLevels();
        List<GeoPart> result=new ArrayList<>();
        List<Project> views=p.levelViews();
        for(int i=0;i<views.size();i++) {
            double base=p.levels.get(i).baseHeight;
            List<GeoPart> parts=buildLevel(views.get(i),opt,(x,z) -> SiteGeometry.height(p,x,z)-base);
            if(base!=0) for(GeoPart part:parts)
                for(int v=1;v<part.mesh.pos.length;v+=3) part.mesh.pos[v]+= (float)base;
            result.addAll(parts);
            for(RetainingWall wall:views.get(i).retainingWalls) result.addAll(RetainingWallGeometry.build(wall,p.env,base));
        }
        if(opt.ground) result.addAll(TerrainGeometry.build(p.worldProject()));
        return result;
    }

    private java.util.function.DoubleBinaryOperator groundLocal = (x, z) -> Double.NEGATIVE_INFINITY;

    private static List<GeoPart> buildLevel(Project p, Options opt, java.util.function.DoubleBinaryOperator groundLocal) {
        StructureBuilder sb = new StructureBuilder();
        sb.groundLocal = groundLocal;
        Map<Wall, WallGeometry.Corners> corners = WallGeometry.compute(p.walls.stream().filter(w -> !w.isFence()).toList());
        List<GeoPart> fences=new ArrayList<>();
        for (Wall w : p.walls) {
            if(w.isFence()) fences.addAll(FenceGeometry.build(w,groundLocal));
            else sb.wall(w, corners.get(w));
        }
        List<GeoPart> vaults=new ArrayList<>();
        for (Room r : p.rooms) {
            sb.room(r, p, opt.ceilings && r.vault==Room.Vault.NONE);
            if(opt.ceilings && r.hasCeiling && r.vault!=Room.Vault.NONE)
                vaults.addAll(VaultGeometry.build(r,r.ceilingHeight>0 ? r.ceilingHeight : p.maxWallHeight()));
        }
        if (opt.roofs) for (Roof r : p.roofs) sb.roof(r);
        for (Balcony b : p.balconies) sb.balcony(b);
        if (opt.lamps) for (LightSource l : p.lights) sb.lamp(l);
        List<GeoPart> parts=sb.result();
        parts.addAll(vaults);
        parts.addAll(fences);
        return parts;
    }

    static final double[] UP = {0, 1, 0}, DOWN = {0, -1, 0}, X = {1, 0, 0}, Z = {0, 0, 1}, NEG_Y = {0, -1, 0};

    // ---------------------------------------------------------------- walls

    private void wall(Wall w, WallGeometry.Corners c) {
        double L = w.length();
        if (L < 1e-3) return;
        if(w.openings.stream().anyMatch(o -> o.top!=Opening.Top.RECTANGLE)) {
            profiledWall(w,c);
            return;
        }
        if (w.curved()) {
            curvedWall(w,c);
            return;
        }
        SurfaceMaterial mA = MaterialLibrary.resolve(w.left), mB = MaterialLibrary.resolve(w.right);
        double H = w.height, t = w.thickness / 2;
        double dx = w.dirX(), dy = w.dirY(), nx = w.nx(), ny = w.ny();
        List<double[]> ops = new ArrayList<>();
        for (Opening o : w.openings) {
            double o0 = Math.max(0.01, o.pos - o.width / 2), o1 = Math.min(L - 0.01, o.pos + o.width / 2);
            double s = Math.max(0, o.sill), top = Math.min(H - 0.02, o.sill + o.height);
            if (o1 - o0 > 0.05 && top - s > 0.05) ops.add(new double[]{o0, o1, s, top});
        }
        ops.sort(Comparator.comparingDouble(a -> a[0]));
        side(w, c.aS, c.aE, +1, t, H, ops, mb(w, mA));
        side(w, c.bS, c.bE, -1, t, H, ops, mb(w, mB));
        // top
        MeshBuilder top = mb(w, mA);
        top.quad(p3(c.aS, H), p3(c.aE, H), p3(c.bE, H), p3(c.bS, H), UP, X, Z);
        double[] d3 = {dx, 0, dy}, nd3 = {-dx, 0, -dy}, n3 = {nx, 0, ny};
        if (c.capStart) top.quad(p3(c.aS, 0), p3(c.bS, 0), p3(c.bS, H), p3(c.aS, H), nd3, n3, NEG_Y);
        if (c.capEnd) top.quad(p3(c.aE, 0), p3(c.aE, H), p3(c.bE, H), p3(c.bE, 0), d3, n3, NEG_Y);
        // reveals and inserts
        Iterator<double[]> it = ops.iterator();
        for (Opening o : sortedValid(w, L, H)) {
            double[] r = it.next();
            reveal(w, r, t, mb(w, mA));
            insert(w, o, r, t);
        }
    }

    private List<Opening> sortedValid(Wall w, double L, double H) {
        List<Opening> l = new ArrayList<>();
        for (Opening o : w.openings) {
            double o0 = Math.max(0.01, o.pos - o.width / 2), o1 = Math.min(L - 0.01, o.pos + o.width / 2);
            double s = Math.max(0, o.sill), top = Math.min(H - 0.02, o.sill + o.height);
            if (o1 - o0 > 0.05 && top - s > 0.05) l.add(o);
        }

        l.sort(Comparator.comparingDouble(o -> Math.max(0.01, o.pos - o.width / 2)));
        return l;
    }

    private void profiledWall(Wall w, WallGeometry.Corners corners) {
        double t=w.thickness/2, length=w.length(), height=w.height;
        double left=Math.min(w.project(corners.aS[0],corners.aS[1]),w.project(corners.bS[0],corners.bS[1]));
        double right=Math.max(w.project(corners.aE[0],corners.aE[1]),w.project(corners.bE[0],corners.bE[1]));
        if(w.curved()) { left=0; right=length; }
        java.awt.geom.Area solid=RoofOutline.area(RoofGeometry.rect(left,0,right,height));
        for(Opening o:w.openings) solid.subtract(RoofOutline.area(OpeningGeometry.profile(o)));
        MeshBuilder a=mb(w,MaterialLibrary.resolve(w.left)), b=mb(w,MaterialLibrary.resolve(w.right));
        TreeSet<Double> cuts=new TreeSet<>(); cuts.add(left); cuts.add(right);
        if(w.curved()) for(int i=0;i<=w.segments();i++) cuts.add(length*i/w.segments());
        Double previous=null;
        for(double u:cuts) {
            if(previous!=null && u-previous>1e-9) {
                java.awt.geom.Area strip=new java.awt.geom.Area(solid);
                strip.intersect(RoofOutline.area(RoofGeometry.rect(previous,0,u,height)));
                for(int side:new int[]{1,-1}) {
                    java.awt.geom.Area skin=new java.awt.geom.Area(strip);
                    if(!w.curved()) {
                        double[] start=side>0 ? corners.aS : corners.bS,end=side>0 ? corners.aE : corners.bE;
                        skin.intersect(RoofOutline.area(RoofGeometry.rect(w.project(start[0],start[1]),0,w.project(end[0],end[1]),height)));
                    }
                    for(double[][] tri:SlabGeometry.triangles(skin)) {
                        double[][] p=new double[3][];
                        for(int i=0;i<3;i++) p[i]=profilePoint(w,corners,tri[i][0],tri[i][1],side*t);
                        double mid=(tri[0][0]+tri[1][0]+tri[2][0])/3;
                        double[] n={side*w.tangentY(mid),0,-side*w.tangentX(mid)};
                        (side>0 ? a : b).triangle(p[0],side>0 ? p[2] : p[1],side>0 ? p[1] : p[2],n,X,NEG_Y);
                    }
                }
            }
            previous=u;
        }
        // Extrude only the final Boolean boundary: no hidden reveal faces in overlaps.
        for(double[][] contour:RoofOutline.contours(solid)) for(int i=0;i<contour.length;i++) {
            double[] p=contour[i],q=contour[(i+1)%contour.length];
            if(!w.curved() && (Math.abs(p[1])<1e-8 && Math.abs(q[1])<1e-8
                    || Math.abs(p[1]-height)<1e-8 && Math.abs(q[1]-height)<1e-8
                    || Math.abs(p[0]-q[0])<1e-8 && (Math.abs(p[0]-left)<1e-8 || Math.abs(p[0]-right)<1e-8))) continue;
            if(Math.abs(p[0]-q[0])<1e-8 && (Math.abs(p[0]-left)<1e-8 && !corners.capStart || Math.abs(p[0]-right)<1e-8 && !corners.capEnd)) continue;
            int count=w.curved() ? Math.max(1,(int)Math.ceil(Math.abs(q[0]-p[0])/(length/w.segments()))) : 1;
            for(int j=0;j<count;j++) {
                double u0=p[0]+(q[0]-p[0])*j/count,u1=p[0]+(q[0]-p[0])*(j+1)/count;
                double h0=p[1]+(q[1]-p[1])*j/count,h1=p[1]+(q[1]-p[1])*(j+1)/count;
                double[] p0=profilePoint(w,corners,u0,h0,t),p1=profilePoint(w,corners,u1,h1,t),
                        p2=profilePoint(w,corners,u1,h1,-t),p3=profilePoint(w,corners,u0,h0,-t);
                a.quad(p0,p1,p2,p3,normal(p0,p1,p2),X,NEG_Y);
            }
        }
        if(!w.curved()) {
            a.quad(p3(corners.aS,height),p3(corners.aE,height),p3(corners.bE,height),p3(corners.bS,height),UP,X,Z);
            if(corners.capStart) a.quad(p3(corners.aS,0),p3(corners.bS,0),p3(corners.bS,height),p3(corners.aS,height),
                    new double[]{-w.dirX(),0,-w.dirY()},X,NEG_Y);
            if(corners.capEnd) a.quad(p3(corners.aE,0),p3(corners.aE,height),p3(corners.bE,height),p3(corners.bE,0),
                    new double[]{w.dirX(),0,w.dirY()},X,NEG_Y);
        }
        for(Opening o:w.openings) {
            if(o.top==Opening.Top.RECTANGLE) {
                if(w.curved()) {
                    curvedFlatInsert(w,o,a,o.pos-o.width/2,o.pos+o.width/2,o.sill,o.sill+o.height);
                } else insert(w,o,new double[]{o.pos-o.width/2,o.pos+o.width/2,o.sill,o.sill+o.height},t);
            } else profiledInsert(w,o,t);
        }
    }

    private static double[] profilePoint(Wall w,WallGeometry.Corners c,double u,double h,double side) {
        if(w.curved()) return curvePoint(w,c,u,h,side);
        double[] start=side>0 ? c.aS : c.bS,end=side>0 ? c.aE : c.bE;
        double u0=w.project(start[0],start[1]),u1=w.project(end[0],end[1]);
        double f=(u-u0)/(u1-u0);
        return new double[]{start[0]+f*(end[0]-start[0]),h,start[1]+f*(end[1]-start[1])};
    }

    private void profiledInsert(Wall wall,Opening o,double t) {
        profiledInsert(wall,o,t,o);
    }

    private void profiledInsert(Wall wall,Opening o,double t,Opening owner) {
        if(o.type==Opening.Type.PASSAGE) return;
        double fw=0.055,fd=Math.max(0.08,2*t+0.02);
        java.awt.geom.Area outline=RoofOutline.area(OpeningGeometry.profile(o));
        java.awt.geom.Area inner=new java.awt.geom.Area(outline);
        inner.subtract(new java.awt.geom.Area(new java.awt.BasicStroke((float)(2*fw),java.awt.BasicStroke.CAP_BUTT,java.awt.BasicStroke.JOIN_ROUND)
                .createStrokedShape(RoofOutline.path(OpeningGeometry.profile(o)))));
        java.awt.geom.Area frame=new java.awt.geom.Area(outline); frame.subtract(inner);
        if(o.type==Opening.Type.DOOR) frame.subtract(RoofOutline.area(RoofGeometry.rect(o.pos-o.width/2+fw,o.sill,o.pos+o.width/2-fw,o.sill+fw)));
        profileExtrusion(wall,o,frame,-fd/2,fd/2,false,mb(owner,MaterialLibrary.resolve(o.frame)));
        if(o.type==Opening.Type.DOOR) {
            java.awt.geom.Area leaf=new java.awt.geom.Area(inner);
            if(o.glazed) {
                var pane=OpeningGeometry.glassArea(o);
                leaf.subtract(pane);
                profileExtrusion(wall,o,pane,-0.003,0.003,true,mb(owner,MaterialLibrary.resolve(new Finish("glass",null))));
            }
            profileExtrusion(wall,o,leaf,-0.02,0.02,true,mb(owner,MaterialLibrary.resolve(o.frame)));
            doorHandles(wall,o,owner,true);
        }
        else profileExtrusion(wall,o,inner,-0.003,0.003,false,mb(o,MaterialLibrary.resolve(new Finish("glass",null))));
        if(o.type==Opening.Type.BALCONY_DOOR) doorHandles(wall,o,owner,false);
    }

    private void doorHandles(Wall wall,Opening o,Opening owner,boolean leaf) {
        double u=o.pos+(o.mirror ? -1 : 1)*(o.width/2-0.09);
        double h=o.sill+Math.min(1.02,o.height*0.5);
        ModelBuilder hardware=new ModelBuilder();
        SurfaceMaterial metal=MaterialLibrary.resolve(new Finish("chrome",null));
        for(int side:new int[]{-1,1}) {
            hardware.rod(metal,insertPoint(wall,o,u,h,side*0.02,leaf),insertPoint(wall,o,u,h,side*0.065,leaf),0.023);
            hardware.rod(metal,insertPoint(wall,o,u,h,side*0.065,leaf),
                    insertPoint(wall,o,u+(o.mirror ? 1 : -1)*Math.min(0.11,o.width*0.2),h,side*0.065,leaf),0.009);
        }
        for(var part:hardware.build("Kilincs").parts) mb(owner,part.material).append(part.mesh);
    }

    private static double[] insertPoint(Wall w,Opening o,double u,double h,double side,boolean leaf) {
        if(leaf) {
            double sign=o.flip ? -1 : 1,hinge=o.pos+(o.mirror ? 1 : -1)*(o.width/2-0.055);
            double[] p=w.point(hinge,sign*w.thickness/2);
            double distance=o.mirror ? hinge-u : u-hinge;
            return new double[]{p[0]+sign*w.nx()*distance+w.tangentX(hinge)*side,h,p[1]+sign*w.ny()*distance+w.tangentY(hinge)*side};
        }
        return wp(w,u,h,side);
    }

    private static void profileExtrusion(Wall w,Opening o,java.awt.geom.Area area,double low,double high,boolean leaf,MeshBuilder mesh) {
        List<double[][]> triangles=new ArrayList<>();
        if(w.curved() && !leaf) {
            double lo=o.pos-o.width/2,hi=o.pos+o.width/2;
            int n=Math.max(1,(int)Math.ceil(o.width/(w.length()/w.segments())));
            for(int i=0;i<n;i++) {
                java.awt.geom.Area strip=new java.awt.geom.Area(area);
                strip.intersect(RoofOutline.area(RoofGeometry.rect(lo+(hi-lo)*i/n,o.sill,lo+(hi-lo)*(i+1)/n,o.sill+o.height)));
                triangles.addAll(SlabGeometry.triangles(strip));
            }
        } else triangles=SlabGeometry.triangles(area);
        for(double[][] triangle:triangles) for(double side:new double[]{low,high}) {
            double[] a=insertPoint(w,o,triangle[0][0],triangle[0][1],side,leaf),
                    b=insertPoint(w,o,triangle[1][0],triangle[1][1],side,leaf),
                    c=insertPoint(w,o,triangle[2][0],triangle[2][1],side,leaf);
            if(side==low) { double[] temp=b; b=c; c=temp; }
            mesh.triangle(a,b,c,normal(a,b,c),X,NEG_Y);
        }
        for(double[][] contour:RoofOutline.contours(area)) for(int i=0;i<contour.length;i++) {
            double[] p=contour[i],q=contour[(i+1)%contour.length];
            int n=w.curved() && !leaf ? Math.max(1,(int)Math.ceil(Math.abs(q[0]-p[0])/(w.length()/w.segments()))) : 1;
            for(int j=0;j<n;j++) {
                double u0=p[0]+(q[0]-p[0])*j/n,u1=p[0]+(q[0]-p[0])*(j+1)/n;
                double h0=p[1]+(q[1]-p[1])*j/n,h1=p[1]+(q[1]-p[1])*(j+1)/n;
                double[] a=insertPoint(w,o,u0,h0,low,leaf),b=insertPoint(w,o,u1,h1,low,leaf),
                        c=insertPoint(w,o,u1,h1,high,leaf),d=insertPoint(w,o,u0,h0,high,leaf);
                mesh.quad(a,b,c,d,normal(a,b,c),X,NEG_Y);
            }
        }
    }
    private void curvedWall(Wall w, WallGeometry.Corners c) {
        double length = w.length(), t = w.thickness/2, height = w.height;
        MeshBuilder left = mb(w,MaterialLibrary.resolve(w.left)), right = mb(w,MaterialLibrary.resolve(w.right));
        TreeSet<Double> cuts = new TreeSet<>();
        for (int i = 0; i <= w.segments(); i++) cuts.add(length*i/w.segments());
        List<Opening> openings = sortedValid(w,length,height);
        for (Opening o : openings) {
            cuts.add(Math.max(0.01,o.pos-o.width/2)); cuts.add(Math.min(length-0.01,o.pos+o.width/2));
        }
        Double previous = null;
        for (double u : cuts) {
            if (previous != null && u-previous > 1e-8) {
                double mid = (previous+u)/2;
                List<double[]> solid = new ArrayList<>();
                solid.add(new double[]{0,height});
                // Interval subtraction also handles overlapping openings without hidden wall strips.
                for (Opening o : openings) if (mid > o.pos-o.width/2 && mid < o.pos+o.width/2) {
                    List<double[]> next = new ArrayList<>();
                    double low = Math.max(0,o.sill), high = Math.min(height-0.02,o.sill+o.height);
                    for (double[] band : solid) {
                        if (low >= band[1] || high <= band[0]) next.add(band);
                        else {
                            if (low > band[0]) next.add(new double[]{band[0],low});
                            if (high < band[1]) next.add(new double[]{high,band[1]});
                        }
                    }
                    solid = next;
                }
                for (double[] band : solid) {
                    curvedSide(w,c,left,previous,u,band[0],band[1],1);
                    curvedSide(w,c,right,previous,u,band[0],band[1],-1);
                }
                left.quad(curvePoint(w,c,previous,height,t),curvePoint(w,c,u,height,t),
                        curvePoint(w,c,u,height,-t),curvePoint(w,c,previous,height,-t),UP,X,Z);
                previous = u;
            } else previous = u;
        }
        if (c.capStart) curveCap(w,c,left,0,0,height,-1);
        if (c.capEnd) curveCap(w,c,left,length,0,height,1);
        for (Opening o : openings) {
            double lo = Math.max(0.01,o.pos-o.width/2), hi = Math.min(length-0.01,o.pos+o.width/2);
            double low = Math.max(0,o.sill), top = Math.min(height-0.02,o.sill+o.height);
            curveCap(w,c,left,lo,low,top,1);
            curveCap(w,c,left,hi,low,top,-1);
            int n = Math.max(1,(int)Math.ceil((hi-lo)/(length/w.segments())));
            for (int i = 0; i < n; i++) {
                double a = lo+(hi-lo)*i/n, b = lo+(hi-lo)*(i+1)/n;
                if (low > 0.001)
                    left.quad(wp(w,a,low,t),wp(w,b,low,t),wp(w,b,low,-t),wp(w,a,low,-t),UP,X,Z);
                left.quad(wp(w,a,top,t),wp(w,a,top,-t),wp(w,b,top,-t),wp(w,b,top,t),DOWN,X,Z);
            }
            curvedFlatInsert(w,o,left,lo,hi,low,top);
        }
    }

    private void curvedFlatInsert(Wall w,Opening o,MeshBuilder left,double lo,double hi,double low,double top) {
        double t=w.thickness/2;
        int n=Math.max(1,(int)Math.ceil((hi-lo)/(w.length()/w.segments())));
        // Standard flat joinery sits on the opening chord; curved reveals connect it to both wall skins.
        double innerOffset = -Math.signum(w.arcAngle)*t;
        double[] a = w.point(lo,innerOffset), b = w.point(hi,innerOffset);
        Wall chord = new Wall(a[0],a[1],b[0],b[1],w.thickness,w.height);
        double chordLength = chord.length();
        insert(chord,o,new double[]{0,chordLength,low,top},t);
        if (o.type != Opening.Type.PASSAGE) {
            double[] ax = {chord.dirX(),0,chord.dirY()};
            for (double side : new double[]{-t,t}) {
                double[] ca = wp(chord,0,low,0), cb = wp(chord,chordLength,low,0);
                // Sill/header fan strips bridge the curved wall and the straight frame line.
                for (int i = 0; i < n; i++) {
                    double u0 = lo+(hi-lo)*i/n, u1 = lo+(hi-lo)*(i+1)/n;
                    double f0 = (double)i/n, f1 = (double)(i+1)/n;
                    for (double h : new double[]{low,top}) {
                        if (h == low && low < 0.001) continue;
                        double[] p0 = {ca[0]+(cb[0]-ca[0])*f0,h,ca[2]+(cb[2]-ca[2])*f0};
                        double[] p1 = {ca[0]+(cb[0]-ca[0])*f1,h,ca[2]+(cb[2]-ca[2])*f1};
                        left.quad(wp(w,u0,h,side),wp(w,u1,h,side),p1,p0,h == low ? UP : DOWN,ax,Z);
                    }
                }
            }
        }
    }

    private static double[] curvePoint(Wall w, WallGeometry.Corners c, double u, double h, double offset) {
        if (Math.abs(u) < 1e-8) return p3(offset > 0 ? c.aS : c.bS,h);
        if (Math.abs(u-w.length()) < 1e-8) return p3(offset > 0 ? c.aE : c.bE,h);
        return wp(w,u,h,offset);
    }

    private static void curvedSide(Wall w, WallGeometry.Corners c, MeshBuilder b,
                                   double a, double d, double low, double high, int side) {
        double t = side*w.thickness/2;
        int[] ids = new int[4];
        double[] distances = {a,d,d,a}, heights = {low,low,high,high};
        for (int i = 0; i < 4; i++) {
            double u = distances[i], h = heights[i];
            double[] p = curvePoint(w,c,u,h,t);
            ids[i] = b.vertex(p[0],p[1],p[2],side*w.tangentY(u),0,-side*w.tangentX(u),side*u,-h);
        }
        if (side > 0) b.quad(ids[0],ids[1],ids[2],ids[3]);
        else b.quad(ids[0],ids[3],ids[2],ids[1]);
    }

    private static void curveCap(Wall w, WallGeometry.Corners c, MeshBuilder b,
                                 double u, double low, double high, int sign) {
        double t = w.thickness/2;
        b.quad(curvePoint(w,c,u,low,t),curvePoint(w,c,u,high,t),
                curvePoint(w,c,u,high,-t),curvePoint(w,c,u,low,-t),
                new double[]{sign*w.tangentX(u),0,sign*w.tangentY(u)},X,NEG_Y);
    }

    private static double[] p3(double[] plan, double h) {
        return new double[]{plan[0], h, plan[1]};
    }

    /** World point at axis position u, height h, across offset a (along left normal). */
    private static double[] wp(Wall w, double u, double h, double a) {
        double[] p = w.point(u,a);
        return new double[]{p[0],h,p[1]};
    }

    private void side(Wall w, double[] s0, double[] s1, int s, double t, double H, List<double[]> ops, MeshBuilder b) {
        double u0 = w.project(s0[0], s0[1]), u1 = w.project(s1[0], s1[1]);
        TreeSet<Double> br = new TreeSet<>();
        br.add(u0);
        br.add(u1);
        for (double[] o : ops) {
            if (o[0] > u0 && o[0] < u1) br.add(o[0]);
            if (o[1] > u0 && o[1] < u1) br.add(o[1]);
        }
        double[] n3 = {w.nx() * s, 0, w.ny() * s};
        double[] tu = {w.dirX() * s, 0, w.dirY() * s};
        Double prev = null;
        for (Double u : br) {
            if (prev != null && u - prev > 1e-5) {
                double mid = (prev + u) / 2;
                double[] op = null;
                for (double[] o : ops) if (mid > o[0] && mid < o[1]) op = o;
                if (op == null) rect(w, b, prev, u, 0, H, s * t, n3, tu, s);
                else {
                    if (op[2] > 0.001) rect(w, b, prev, u, 0, op[2], s * t, n3, tu, s);
                    if (op[3] < H - 0.001) rect(w, b, prev, u, op[3], H, s * t, n3, tu, s);
                }
            }
            prev = u;
        }
    }

    private void rect(Wall w, MeshBuilder b, double u0, double u1, double y0, double y1, double a, double[] n, double[] tu, int s) {
        double[] p0 = wp(w, u0, y0, a), p1 = wp(w, u1, y0, a), p2 = wp(w, u1, y1, a), p3 = wp(w, u0, y1, a);
        if (s > 0) b.quad(p0, p1, p2, p3, n, tu, NEG_Y);
        else b.quad(p0, p3, p2, p1, n, tu, NEG_Y);
    }

    private void reveal(Wall w, double[] r, double t, MeshBuilder b) {
        double o0 = r[0], o1 = r[1], s = r[2], tp = r[3];
        double[] d3 = {w.dirX(), 0, w.dirY()}, nd3 = {-w.dirX(), 0, -w.dirY()}, n3 = {w.nx(), 0, w.ny()};
        b.quad(wp(w, o0, s, t), wp(w, o0, s, -t), wp(w, o0, tp, -t), wp(w, o0, tp, t), d3, n3, NEG_Y);
        b.quad(wp(w, o1, s, t), wp(w, o1, tp, t), wp(w, o1, tp, -t), wp(w, o1, s, -t), nd3, n3, NEG_Y);
        if (s > 0.001) b.quad(wp(w, o0, s, t), wp(w, o1, s, t), wp(w, o1, s, -t), wp(w, o0, s, -t), UP, d3, n3);
        b.quad(wp(w, o0, tp, t), wp(w, o0, tp, -t), wp(w, o1, tp, -t), wp(w, o1, tp, t), DOWN, d3, n3);
    }

    private void insert(Wall w, Opening o, double[] r, double t) {
        if (o.type == Opening.Type.PASSAGE) return;
        SurfaceMaterial fm = MaterialLibrary.resolve(o.frame);
        MeshBuilder b = mb(o, fm);
        double o0 = r[0], o1 = r[1], s = r[2], tp = r[3];
        double[] ax = {w.dirX(), 0, w.dirY()}, ay = {0, 1, 0}, az = {w.nx(), 0, w.ny()};
        if (o.type == Opening.Type.DOOR) {
            Opening leaf=new Opening();
            leaf.type=o.type; leaf.top=o.top; leaf.pos=(o0+o1)/2; leaf.width=o1-o0;
            leaf.height=tp-s; leaf.sill=s; leaf.flip=o.flip; leaf.mirror=o.mirror; leaf.frame=o.frame;
            leaf.glazed=o.glazed; leaf.glassX=o.glassX; leaf.glassY=o.glassY;
            leaf.glassWidth=o.glassWidth; leaf.glassHeight=o.glassHeight;
            profiledInsert(w,leaf,t,o);
            return;
        }
        double fw = 0.065, fd = 0.08;
        b.box(wp(w, o0, s, -fd / 2), ax, ay, az, fw, tp - s, fd);
        b.box(wp(w, o1 - fw, s, -fd / 2), ax, ay, az, fw, tp - s, fd);
        b.box(wp(w, o0 + fw, s, -fd / 2), ax, ay, az, o1 - o0 - 2 * fw, fw, fd);
        b.box(wp(w, o0 + fw, tp - fw, -fd / 2), ax, ay, az, o1 - o0 - 2 * fw, fw, fd);
        double width = o1 - o0;
        int panes = width > 2.2 ? 3 : (width > 1.0 ? 2 : 1);
        for (int i = 1; i < panes; i++) {
            double u = o0 + width * i / panes;
            b.box(wp(w, u - fw / 2, s + fw, -fd / 2), ax, ay, az, fw, tp - s - 2 * fw, fd);
        }
        if (o.type == Opening.Type.WINDOW && s > 0.05) {
            SurfaceMaterial sm = MaterialLibrary.resolve(new Finish("marble", null));
            mb(o, sm).box(wp(w, o0 - 0.04, s - 0.03, 0), ax, ay, az, width + 0.08, 0.03, t + 0.04);
        }
        SurfaceMaterial gm = MaterialLibrary.resolve(new Finish("glass", null));
        mb(o, gm).quad(wp(w, o0 + fw, s + fw, 0), wp(w, o1 - fw, s + fw, 0), wp(w, o1 - fw, tp - fw, 0), wp(w, o0 + fw, tp - fw, 0), az, ax, NEG_Y);
        if(o.type==Opening.Type.BALCONY_DOOR) {
            Opening proxy=Opening.create(o.type,(o0+o1)/2);
            proxy.width=o1-o0; proxy.sill=s; proxy.height=tp-s; proxy.mirror=o.mirror;
            doorHandles(w,proxy,o,false);
        }
    }

    // ---------------------------------------------------------------- rooms

    private void room(Room r, Project p, boolean ceilings) {
        if (!r.slabOpenings.isEmpty() || !r.floorZones.isEmpty() || r.elevation!=0) {
            horizontal(mb(r,MaterialLibrary.resolve(r.floor)),FloorLevels.base(r),r.elevation+0.002,false);
            if(r.elevation!=0) risers(r,SlabGeometry.roomArea(r),0,r.elevation,MaterialLibrary.resolve(r.floor));
            for(FloorZone zone:r.floorZones) {
                SurfaceMaterial material=MaterialLibrary.resolve(zone.finish);
                horizontal(mb(zone,material),FloorLevels.surface(r,zone),r.elevation+zone.offset+0.002,false);
                risers(zone,FloorLevels.surface(r,zone),r.elevation,r.elevation+zone.offset,material);
            }
            if (ceilings && r.hasCeiling)
                slabSurface(r,true,r.ceilingHeight > 0 ? r.ceilingHeight : p.maxWallHeight());
            return;
        }
        int n = r.points.size();
        if (n < 3) return;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = r.points.get(i).x;
            y[i] = r.points.get(i).y;
        }
        List<int[]> tris = Triangulator.triangulate(x, y);
        MeshBuilder f = mb(r, MaterialLibrary.resolve(r.floor));
        int[] vi = new int[n];
        for (int i = 0; i < n; i++) vi[i] = f.vertex(x[i], 0.002, y[i], 0, 1, 0, x[i], y[i]);
        for (int[] t : tris) f.tri(vi[t[0]], vi[t[2]], vi[t[1]]);
        if (ceilings && r.hasCeiling) {
            double h = r.ceilingHeight > 0 ? r.ceilingHeight : p.maxWallHeight();
            SurfaceMaterial cm = MaterialLibrary.resolve(r.ceiling);
            MeshBuilder c = mb(r, cm);
            for (int i = 0; i < n; i++) vi[i] = c.vertex(x[i], h, y[i], 0, -1, 0, x[i], y[i]);
            for (int[] t : tris) c.tri(vi[t[0]], vi[t[1]], vi[t[2]]);
        }
    }

    private void slabSurface(Room r, boolean ceiling, double height) {
        MeshBuilder mesh = mb(r,MaterialLibrary.resolve(ceiling ? r.ceiling : r.floor));
        horizontal(mesh,SlabGeometry.surface(r,ceiling),height,ceiling);
    }

    private static void horizontal(MeshBuilder mesh,java.awt.geom.Area area,double height,boolean ceiling) {
        for (double[][] triangle : SlabGeometry.triangles(area)) {
            double[][] v = new double[3][];
            for (int i = 0; i < 3; i++) v[i] = new double[]{triangle[i][0],height,triangle[i][1]};
            mesh.triangle(v[0],ceiling ? v[1] : v[2],ceiling ? v[2] : v[1],ceiling ? DOWN : UP,X,Z);
        }
    }

    private void risers(Object owner,java.awt.geom.Area area,double a,double b,SurfaceMaterial material) {
        if(Math.abs(a-b)<1e-8) return;
        MeshBuilder mesh=mb(owner,material);
        double low=Math.min(a,b),high=Math.max(a,b);
        for(double[][] contour:RoofOutline.contours(area)) for(int i=0;i<contour.length;i++) {
            double[] p=contour[i],q=contour[(i+1)%contour.length];
            double dx=q[0]-p[0],dz=q[1]-p[1],length=Math.hypot(dx,dz);
            if(length<1e-9) continue;
            double nx=dz/length,nz=-dx/length;
            double sign=area.contains((p[0]+q[0])/2+nx*0.00001,(p[1]+q[1])/2+nz*0.00001) ? -1 : 1;
            // Sunk regions expose their inward face, raised platforms their outward face.
            if(b<a) sign=-sign;
            double[] n={sign*nx,0,sign*nz};
            double[] v0={p[0],low,p[1]},v1={q[0],low,q[1]},v2={q[0],high,q[1]},v3={p[0],high,p[1]};
            if(sign>0) { mesh.triangle(v0,v2,v1,n,X,Z); mesh.triangle(v0,v3,v2,n,X,Z); }
            else { mesh.triangle(v0,v1,v2,n,X,Z); mesh.triangle(v0,v2,v3,n,X,Z); }
        }
    }

    private void lamp(LightSource l) {
        double[] c = Noise.hex(l.color, new double[]{1, 0.9, 0.7});
        SurfaceMaterial m = MaterialLibrary.emissive(c, 4 + 6 * l.power);
        sphere(mb(l, m), l.x, l.height, l.y, Math.max(0.03, l.radius), 16, 10);
    }

    static void sphere(MeshBuilder b, double cx, double cy, double cz, double r, int seg, int rings) {
        int[][] id = new int[rings + 1][seg + 1];
        for (int j = 0; j <= rings; j++) {
            double th = Math.PI * j / rings;
            for (int i = 0; i <= seg; i++) {
                double ph = 2 * Math.PI * i / seg;
                double nx = Math.sin(th) * Math.cos(ph), ny = Math.cos(th), nz = Math.sin(th) * Math.sin(ph);
                id[j][i] = b.vertex(cx + nx * r, cy + ny * r, cz + nz * r, nx, ny, nz, (double) i / seg, (double) j / rings);
            }
        }
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++) b.quad(id[j][i], id[j][i + 1], id[j + 1][i + 1], id[j + 1][i]);
    }

    // ---------------------------------------------------------------- roofs

    private void roof(Roof r) {
        RoofGeometry geometry = new RoofGeometry(r);
        if (r.radial()) {
            radialRoof(r,geometry);
            return;
        }
        boolean ax = r.ridgeAlongX;
        double a0 = ax ? r.x : r.y, a1 = ax ? r.x + r.w : r.y + r.d, b0 = ax ? r.y : r.x, b1 = ax ? r.y + r.d : r.x + r.w;
        double o = r.overhang;
        double A0 = a0 - o, A1 = a1 + o, B0 = b0 - o, B1 = b1 + o;
        double ac = (A0 + A1) / 2, bc = (B0 + B1) / 2;
        double k = r.type == Roof.Type.FLAT ? 0 : Math.tan(Math.toRadians(Math.max(1, Math.min(70, r.pitch))));
        double cos = 1 / Math.sqrt(1 + k * k);
        double base = r.baseHeight;
        RoofFn H = geometry::height;
        var outlinePath = RoofOutline.path(geometry.outline);
        List<double[][]> polys = new ArrayList<>();
        List<double[]> grads = new ArrayList<>();
        for (RoofGeometry.Face face : geometry.surfaceFaces()) {
            List<double[][]> pieces = new ArrayList<>();
            pieces.add(face.polygon());
            for (RoofElement e : r.elements) {
                List<double[][]> next = new ArrayList<>();
                for (double[][] piece : pieces) next.addAll(RoofGeometry.subtract(piece, geometry.bounds(e)));
                pieces = next;
            }
            for (double[][] piece : pieces) {
                polys.add(piece);
                grads.add(face.ga() == 0 && face.gb() == 0 ? new double[]{0, 1} : new double[]{face.ga(), face.gb()});
            }
        }
        SurfaceMaterial rm = MaterialLibrary.resolve(r.finish);
        MeshBuilder b = mb(r, rm);
        SurfaceMaterial under = MaterialLibrary.resolve(new Finish("oak_wood", null));
        MeshBuilder bu = mb(r, under);
        double T = r.thickness / cos;
        for (int pi = 0; pi < polys.size(); pi++) {
            double[][] poly = polys.get(pi);
            double[] g = grads.get(pi);
            double[] gw = world(g[0], g[1], 0, ax), pw = world(-g[1], g[0], 0, ax);
            double[] tv = {gw[0] / cos, 0, gw[2] / cos}, tu = {pw[0], 0, pw[2]};
            double[][] top = new double[poly.length][], bot = new double[poly.length][];
            for (int i = 0; i < poly.length; i++) {
                double h = H.at(poly[i][0], poly[i][1]);
                top[i] = world(poly[i][0], poly[i][1], h + T, ax);
                bot[i] = world(poly[i][0], poly[i][1], h, ax);
            }
            double[] nTop = normal(top[0], top[1], top[2]);
            if (nTop[1] < 0) nTop = MeshBuilder.neg(nTop);
            double[] nBot = MeshBuilder.neg(nTop);
            for (int i = 1; i + 1 < poly.length; i++) {
                b.triangle(top[0], top[i], top[i + 1], nTop, tu, tv);
                bu.triangle(bot[0], bot[i + 1], bot[i], nBot, tu, tv);
            }
            for (int i = 0; i < poly.length; i++) {
                int j = (i + 1) % poly.length;
                double[] p = poly[i], q = poly[j];
                boolean onEdge = (near(p[0], A0) && near(q[0], A0)) || (near(p[0], A1) && near(q[0], A1))
                        || (near(p[1], B0) && near(q[1], B0)) || (near(p[1], B1) && near(q[1], B1));
                if (r.circular || !r.footprint.isEmpty()) {
                    double[] pWorld = geometry.point(p[0],p[1],0), qWorld = geometry.point(q[0],q[1],0);
                    onEdge = RoofOutline.distance(geometry.outline,(pWorld[0]+qWorld[0])/2,(pWorld[2]+qWorld[2])/2) < 1e-6;
                }
                if (!onEdge) continue;
                double[] mid = {(p[0] + q[0]) / 2 - (ac), (p[1] + q[1]) / 2 - bc};
                double[] e = {q[0] - p[0], q[1] - p[1]};
                double[] on = {e[1], -e[0]};
                if (r.circular || !r.footprint.isEmpty()) {
                    double[] probe = geometry.point((p[0]+q[0])/2+on[0]*0.0001,(p[1]+q[1])/2+on[1]*0.0001,0);
                    if (outlinePath.contains(probe[0],probe[2])) on = new double[]{-on[0],-on[1]};
                } else if (on[0] * mid[0] + on[1] * mid[1] < 0) on = new double[]{-on[0], -on[1]};
                double ol = Math.hypot(on[0], on[1]);
                double[] nw = world(on[0] / ol, on[1] / ol, 0, ax);
                double[] ew = world(e[0], e[1], 0, ax);
                double el = Math.hypot(ew[0], ew[2]);
                b.quad(bot[i], bot[j], top[j], top[i], nw, new double[]{ew[0] / el, 0, ew[2] / el}, NEG_Y);
            }
        }
        for (RoofElement e : r.elements) roofElement(geometry, e);
        if (r.circular || !r.footprint.isEmpty()) {
            if (r.gableWalls && r.type != Roof.Type.FLAT) outlineWalls(r,geometry);
            return;
        }
        if (r.gableWalls && (r.type == Roof.Type.GABLE || r.type == Roof.Type.SHED)) {
            MeshBuilder gb = mb(r, MaterialLibrary.resolve(r.gable));
            double[][] c = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
            for (int i = 0; i < 4; i++) {
                double[] p = c[i], q = c[(i + 1) % 4];
                double[] m = {(p[0] + q[0]) / 2, (p[1] + q[1]) / 2};
                double[] e = {q[0] - p[0], q[1] - p[1]};
                double el = Math.hypot(e[0], e[1]);
                double[] on = {e[1] / el, -e[0] / el};
                if (on[0] * (m[0] - ac) + on[1] * (m[1] - bc) < 0) on = new double[]{-on[0], -on[1]};
                double[] nw = world(on[0], on[1], 0, ax), ew = world(e[0] / el, e[1] / el, 0, ax);
                double[][] seg = {p, m, q};
                for (int sgi = 0; sgi < 2; sgi++) {
                    double[] s0 = seg[sgi], s1 = seg[sgi + 1];
                    double h0 = H.at(s0[0], s0[1]), h1 = H.at(s1[0], s1[1]);
                    if (Math.max(h0, h1) - base < 0.01) continue;
                    gb.quad(world(s0[0], s0[1], base, ax), world(s1[0], s1[1], base, ax), world(s1[0], s1[1], Math.max(base, h1), ax),
                            world(s0[0], s0[1], Math.max(base, h0), ax), nw, ew, NEG_Y);
                }
            }

        }
    }

    private void radialRoof(Roof r, RoofGeometry geometry) {
        MeshBuilder top = mb(r,MaterialLibrary.resolve(r.finish));
        MeshBuilder bottom = mb(r,MaterialLibrary.resolve(new Finish("oak_wood",null)));
        int segments = geometry.outline.length, rings = r.type == Roof.Type.DOME ? 60 : 1;
        double radius = r.w/2+r.overhang, cx = r.x+r.w/2, cz = r.y+r.d/2;
        int[][] upper = new int[rings+1][segments+1], lower = new int[rings+1][segments+1];
        for (int j = 0; j <= rings; j++) {
            double theta = j*Math.PI/(2*rings);
            double rho = r.type == Roof.Type.DOME ? radius*Math.sin(theta) : radius*j/rings;
            for (int i = 0; i <= segments; i++) {
                double phi = 2*Math.PI*(i%segments)/segments;
                double dx = Math.cos(phi), dz = Math.sin(phi), x = cx+rho*dx, z = cz+rho*dz;
                double height = geometry.heightAt(x,z);
                double nx, ny, nz;
                if (r.type == Roof.Type.CONE) {
                    nx = geometry.slope*dx; ny = 1; nz = geometry.slope*dz;
                } else {
                    nx = r.domeRise*Math.sin(theta)*dx; ny = radius*Math.cos(theta); nz = r.domeRise*Math.sin(theta)*dz;
                }
                double length = Math.sqrt(nx*nx+ny*ny+nz*nz);
                nx /= length; ny /= length; nz /= length;
                upper[j][i] = top.vertex(x,height+geometry.thickness,z,nx,ny,nz,x,z);
                lower[j][i] = bottom.vertex(x,height,z,-nx,-ny,-nz,x,z);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < segments; i++) {
            if (j == 0) {
                top.tri(upper[0][i],upper[1][i+1],upper[1][i]);
                bottom.tri(lower[0][i],lower[1][i],lower[1][i+1]);
            } else {
                top.quad(upper[j][i],upper[j][i+1],upper[j+1][i+1],upper[j+1][i]);
                bottom.quad(lower[j][i],lower[j+1][i],lower[j+1][i+1],lower[j][i+1]);
            }
        }
        for (int i = 0; i < segments; i++) {
            double a = 2*Math.PI*i/segments, b = 2*Math.PI*(i+1)/segments;
            double[] p = {cx+radius*Math.cos(a),cz+radius*Math.sin(a)};
            double[] q = {cx+radius*Math.cos(b),cz+radius*Math.sin(b)};
            double h = geometry.heightAt(p[0],p[1]);
            top.quad(new double[]{p[0],h,p[1]},new double[]{q[0],h,q[1]},
                    new double[]{q[0],h+geometry.thickness,q[1]},new double[]{p[0],h+geometry.thickness,p[1]},
                    new double[]{Math.cos((a+b)/2),0,Math.sin((a+b)/2)},X,NEG_Y);
        }
        if (r.gableWalls) outlineWalls(r,geometry);
    }

    private void outlineWalls(Roof r, RoofGeometry geometry) {
        MeshBuilder wall = mb(r,MaterialLibrary.resolve(r.gable));
        double[][] boundary = RoofOutline.boundary(r,false);
        for (int i = 0; i < boundary.length; i++) {
            double[] a = boundary[i], b = boundary[(i+1)%boundary.length];
            java.util.TreeSet<Double> cuts = new java.util.TreeSet<>();
            cuts.add(0.0); cuts.add(1.0);
            double ax = r.ridgeAlongX ? a[0] : a[1], ay = r.ridgeAlongX ? a[1] : a[0];
            double bx = r.ridgeAlongX ? b[0] : b[1], by = r.ridgeAlongX ? b[1] : b[0];
            addCrossing(cuts,ax-geometry.ac,bx-geometry.ac);
            addCrossing(cuts,ay-geometry.bc,by-geometry.bc);
            if (r.type == Roof.Type.HIP) {
                double delta = geometry.halfA-geometry.halfB;
                for (int sa : new int[]{-1,1}) for (int sb : new int[]{-1,1})
                    addCrossing(cuts,sa*(ax-geometry.ac)-sb*(ay-geometry.bc)-delta,
                            sa*(bx-geometry.ac)-sb*(by-geometry.bc)-delta);
            }
            Double last = null;
            for (double t : cuts) {
                if (last != null) {
                    double x0 = a[0]+(b[0]-a[0])*last, z0 = a[1]+(b[1]-a[1])*last;
                    double x1 = a[0]+(b[0]-a[0])*t, z1 = a[1]+(b[1]-a[1])*t;
                    double h0 = Math.max(r.baseHeight,geometry.heightAt(x0,z0)), h1 = Math.max(r.baseHeight,geometry.heightAt(x1,z1));
                    if (Math.max(h0,h1) > r.baseHeight+0.0001)
                        panel(wall,new double[]{x0,r.baseHeight,z0},new double[]{x1,r.baseHeight,z1},
                                new double[]{x1,h1,z1},new double[]{x0,h0,z0});
                }
                last = t;
            }
        }
    }

    private static void addCrossing(java.util.Set<Double> cuts, double a, double b) {
        if (a*b < 0) cuts.add(a/(a-b));
    }

    private void roofElement(RoofGeometry g, RoofElement e) {
        Roof r = g.roof;
        double x = r.x + e.x, z = r.y + e.y;
        MeshBuilder reveal = mb(e, MaterialLibrary.resolve(e.wall));
        double[][] corners = {{x,z},{x+e.width,z},{x+e.width,z+e.depth},{x,z+e.depth}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i], b = corners[(i+1)%4];
            double ha = g.heightAt(a[0],a[1]), hb = g.heightAt(b[0],b[1]);
            panel(reveal, new double[]{a[0],ha,a[1]}, new double[]{b[0],hb,b[1]},
                    new double[]{b[0],hb+g.thickness,b[1]}, new double[]{a[0],ha+g.thickness,a[1]});
        }
        if (e.type == RoofElement.Type.SKYLIGHT) {
            double h = g.heightAt(x,z) + g.thickness;
            double sx = (g.heightAt(x+e.width,z)-g.heightAt(x,z))/e.width;
            double sz = (g.heightAt(x,z+e.depth)-g.heightAt(x,z))/e.depth;
            double lx = Math.sqrt(1+sx*sx), lz = Math.sqrt(1+sz*sz);
            double[] u = {1/lx,sx/lx,0}, v = {0,sz/lz,1/lz};
            double[] n = normal(new double[]{0,0,0}, v, u);
            window(e, new double[]{x,h+0.01,z}, u, v, n, e.width*lx, e.depth*lz, e.frame);
            return;
        }
        RoofGeometry.Face face = g.faceOf(e);
        if (face == null) throw new IllegalArgumentException(I18n.text("A tetőelem nem egyetlen tetősíkon található."));
        double outX = r.ridgeAlongX ? face.ga() : face.gb();
        double outZ = r.ridgeAlongX ? face.gb() : face.ga();
        if (outX == 0 && outZ == 0) outZ = 1;
        double vx = -outX, vz = -outZ, ux = vz, uz = -vx;
        double w = ux != 0 ? e.width : e.depth, d = vx != 0 ? e.width : e.depth;
        double ox = x+e.width/2-ux*w/2-vx*d/2, oz = z+e.depth/2-uz*w/2-vz*d/2;
        double[] u = {ux,0,uz}, v = {vx,0,vz};
        double hFront = g.heightAt(ox,oz), hBack = g.heightAt(ox+vx*d,oz+vz*d);
        double low = Math.min(hFront,hBack), high = Math.max(hFront,hBack);
        MeshBuilder walls = mb(e, MaterialLibrary.resolve(e.wall));
        if (e.type == RoofElement.Type.DORMER) {
            double top = high+g.thickness+e.height;
            double sill = hFront+g.thickness+0.12, lintel = top-0.12;
            double jamb = Math.min(0.16,w*0.15);
            // The front wall is split around the window; no opaque polygon covers its glass.
            localPanel(walls,ox,oz,u,v,0,0,hFront,w,0,sill);
            localPanel(walls,ox,oz,u,v,0,0,sill,jamb,0,top);
            localPanel(walls,ox,oz,u,v,w-jamb,0,sill,w,0,top);
            localPanel(walls,ox,oz,u,v,jamb,0,lintel,w-jamb,0,top);
            window(e, at(ox,oz,u,v,jamb,0,sill),u,UP,new double[]{outX,0,outZ},w-2*jamb,lintel-sill,e.frame);
            for (double side : new double[]{0,w}) {
                panel(walls,at(ox,oz,u,v,side,0,hFront),at(ox,oz,u,v,side,d,hBack),
                        at(ox,oz,u,v,side,d,top),at(ox,oz,u,v,side,0,top));
            }
            localPanel(walls,ox,oz,u,v,0,d,hBack,w,d,top);
            MeshBuilder cover = mb(e, MaterialLibrary.resolve(r.finish));
            if (e.cover == RoofElement.Cover.GABLE) {
                double ridge = top + w*0.3;
                for (double side : new double[]{0,w})
                    panel(cover,at(ox,oz,u,v,side,0,top),at(ox,oz,u,v,w/2,0,ridge),
                            at(ox,oz,u,v,w/2,d,ridge),at(ox,oz,u,v,side,d,top));
                for (double end : new double[]{0,d}) {
                    double[] a = at(ox,oz,u,v,0,end,top), b = at(ox,oz,u,v,w,end,top), c = at(ox,oz,u,v,w/2,end,ridge);
                    walls.triangle(a,b,c,normal(a,b,c),u,NEG_Y);
                }
            } else {
                double rise = e.cover == RoofElement.Cover.SHED ? d*0.15 : 0;
                panel(cover,at(ox,oz,u,v,0,0,top),at(ox,oz,u,v,w,0,top),
                        at(ox,oz,u,v,w,d,top+rise),at(ox,oz,u,v,0,d,top+rise));
                if (rise > 0) {
                    localPanel(walls,ox,oz,u,v,0,d,top,w,d,top+rise);
                    for (double side : new double[]{0,w}) {
                        double[] a = at(ox,oz,u,v,side,0,top), b = at(ox,oz,u,v,side,d,top), c = at(ox,oz,u,v,side,d,top+rise);
                        walls.triangle(a,b,c,normal(a,b,c),v,NEG_Y);
                    }
                }
            }
        } else {
            double floor = low-e.floorDrop;
            MeshBuilder deck = mb(e, MaterialLibrary.resolve(e.floor));
            deck.box(at(ox,oz,u,v,0,0,floor-0.18),u,UP,v,w,0.18,d);
            for (double side : new double[]{0,w})
                panel(walls,at(ox,oz,u,v,side,0,floor),at(ox,oz,u,v,side,d,floor),
                        at(ox,oz,u,v,side,d,hBack+g.thickness),at(ox,oz,u,v,side,0,hFront+g.thickness));
            localPanel(walls,ox,oz,u,v,0,0,floor,w,0,hFront+g.thickness);
            // Rear access is glazed, with a door-sized opening where the available height permits.
            double doorW = Math.min(0.95,w-0.24), doorH = Math.min(2.1,hBack+g.thickness-floor-0.12);
            double jamb = (w-doorW)/2;
            localPanel(walls,ox,oz,u,v,0,d,floor,jamb,d,hBack+g.thickness);
            localPanel(walls,ox,oz,u,v,w-jamb,d,floor,w,d,hBack+g.thickness);
            localPanel(walls,ox,oz,u,v,jamb,d,floor+doorH,w-jamb,d,hBack+g.thickness);
            window(e,at(ox,oz,u,v,jamb,d,floor),u,UP,v,doorW,doorH,e.frame);
            double railBase = Math.max(floor,hFront+g.thickness);
            double railHeight = floor+e.railingHeight-railBase;
            if (railHeight > 0.08)
                railing(e, at(ox,oz,u,v,0,0,railBase), at(ox,oz,u,v,w,0,railBase), railHeight, e.railing, e.frame);
        }
    }

    private static double[] at(double x, double z, double[] u, double[] v, double a, double b, double h) {
        return new double[]{x+u[0]*a+v[0]*b,h,z+u[2]*a+v[2]*b};
    }

    private static void localPanel(MeshBuilder b, double x, double z, double[] u, double[] v,
                                   double a0, double b0, double h0, double a1, double b1, double h1) {
        if (h1-h0 < 1e-6 || Math.hypot(a1-a0,b1-b0) < 1e-6) return;
        panel(b,at(x,z,u,v,a0,b0,h0),at(x,z,u,v,a1,b1,h0),at(x,z,u,v,a1,b1,h1),at(x,z,u,v,a0,b0,h1));
    }

    private static void panel(MeshBuilder b, double[] a, double[] c, double[] d, double[] e) {
        double[] n = normal(a,c,d);
        b.quad(a,c,d,e,n,X,NEG_Y);
    }

    private void window(Object owner, double[] origin, double[] u, double[] v, double[] n,
                        double w, double h, Finish finish) {
        double f = Math.min(0.065,Math.min(w,h)/5);
        MeshBuilder frame = mb(owner,MaterialLibrary.resolve(finish));
        frame.box(origin,u,v,n,f,h,0.08);
        frame.box(add(origin,u,w-f),u,v,n,f,h,0.08);
        frame.box(add(origin,u,f),u,v,n,w-2*f,f,0.08);
        frame.box(add(add(origin,u,f),v,h-f),u,v,n,w-2*f,f,0.08);
        MeshBuilder glass = mb(owner,MaterialLibrary.resolve(new Finish("glass",null)));
        glass.box(add(add(add(origin,u,f),v,f),n,0.035),u,v,n,w-2*f,h-2*f,0.01);
    }

    private static double[] add(double[] a, double[] b, double k) {
        return new double[]{a[0]+b[0]*k,a[1]+b[1]*k,a[2]+b[2]*k};
    }

    private void balcony(Balcony b) {
        double a = Math.toRadians(b.angle);
        double[] u = {Math.cos(a),0,Math.sin(a)}, v = {-Math.sin(a),0,Math.cos(a)};
        mb(b,MaterialLibrary.resolve(b.floor)).box(new double[]{b.x,b.elevation-b.thickness,b.y},u,UP,v,b.width,b.thickness,b.depth);
        if (b.support != null && b.support != Balcony.Support.NONE)
            RetainingWallGeometry.support(mb(b,MaterialLibrary.resolve(new Finish("concrete",null))),b,groundLocal);
        double[][] c = b.corners();
        boolean[] rails = {b.backRail,b.rightRail,b.frontRail,b.leftRail};
        for (int i = 0; i < 4; i++) if (rails[i]) {
            double[] p = c[i], q = c[(i+1)%4];
            railing(b,new double[]{p[0],b.elevation,p[1]},new double[]{q[0],b.elevation,q[1]},b.railingHeight,b.railing,b.railFinish);
        }
    }

    private void railing(Object owner, double[] a, double[] b, double height, Balcony.Railing style, Finish finish) {
        double length = Math.hypot(b[0]-a[0],b[2]-a[2]);
        double[] u = {(b[0]-a[0])/length,0,(b[2]-a[2])/length}, v = {-u[2],0,u[0]};
        MeshBuilder bars = mb(owner,MaterialLibrary.resolve(finish));
        double[] o = add(a,v,-0.025);
        bars.box(add(o,UP,height-0.05),u,UP,v,length,0.05,0.05);
        if (style == Balcony.Railing.SOLID) {
            bars.box(add(a,v,-0.06),u,UP,v,length,height-0.05,0.12);
        } else {
            int count = Math.max(1,(int)Math.ceil(length/(style == Balcony.Railing.BARS ? 0.12 : 1)));
            for (int i = 0; i <= count; i++)
                bars.box(add(o,u,Math.min(length-0.035,length*i/count)),u,UP,v,0.035,height,0.05);
            if (style == Balcony.Railing.GLASS) {
                MeshBuilder glass = mb(owner,MaterialLibrary.resolve(new Finish("glass",null)));
                glass.box(add(add(a,UP,0.08),v,-0.006),u,UP,v,length,height-0.15,0.012);
            }
        }
    }

    interface RoofFn {
        double at(double a, double b);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static double[] world(double a, double b, double h, boolean alongX) {
        return alongX ? new double[]{a, h, b} : new double[]{b, h, a};
    }

    private static double[] normal(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-12 ? new double[]{0, 1, 0} : new double[]{nx / l, ny / l, nz / l};
    }
}
