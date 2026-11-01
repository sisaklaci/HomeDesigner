package hu.helmetsoft.homedesigner.model;

import java.util.ArrayList;
import java.util.List;

public class Project {
    public int version = 1;
    public List<Wall> walls = new ArrayList<>();
    public List<Room> rooms = new ArrayList<>();
    public List<Furniture> furniture = new ArrayList<>();
    public List<LightSource> lights = new ArrayList<>();
    public List<DimensionLine> dimensions = new ArrayList<>();
    public List<Roof> roofs = new ArrayList<>();
    public List<Balcony> balconies = new ArrayList<>();
    public List<RetainingWall> retainingWalls = new ArrayList<>();
    public Environment env = new Environment();
    public double wallHeight = 2.7;
    public double wallThickness = 0.15;
    public List<BuildingLevel> levels = new ArrayList<>();
    public String activeLevelId;
    private transient java.util.IdentityHashMap<Object,Object> worldSources;

    /** Adopts legacy flat projects as ground floor. Flat lists always edit the active storey. */
    public void ensureLevels() {
        if (levels == null) levels = new ArrayList<>();
        if (levels.isEmpty()) {
            BuildingLevel ground = new BuildingLevel();
            levels.add(ground);
            activeLevelId = ground.id;
            captureActive();
        }
    }

    public BuildingLevel activeLevel() {
        ensureLevels();
        return levels.stream().filter(l -> l.id.equals(activeLevelId)).findFirst().orElse(levels.getFirst());
    }

    public void captureActive() {
        if (levels == null || levels.isEmpty()) return;
        BuildingLevel l = activeLevel();
        l.walls=walls; l.rooms=rooms; l.furniture=furniture; l.lights=lights;
        l.dimensions=dimensions; l.roofs=roofs; l.balconies=balconies; l.retainingWalls=retainingWalls;
    }

    /** Binds deserialized or newly selected storey lists without copying model objects. */
    public void bindLevel(String id) {
        ensureLevels();
        BuildingLevel l=levels.stream().filter(q -> q.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(hu.helmetsoft.homedesigner.util.I18n.text("Ismeretlen épületszint.")));
        activeLevelId=l.id;
        walls=l.walls; rooms=l.rooms; furniture=l.furniture; lights=l.lights;
        dimensions=l.dimensions; roofs=l.roofs; balconies=l.balconies;
        if(l.retainingWalls==null) l.retainingWalls=new ArrayList<>();
        retainingWalls=l.retainingWalls;
    }

    /** Local, non-owning views. Use these for geometry/fitting; never join walls across storeys. */
    public List<Project> levelViews() {
        ensureLevels();
        List<Project> result=new ArrayList<>();
        for(BuildingLevel l:levels) {
            Project p=new Project();
            p.env=env; p.wallHeight=wallHeight; p.wallThickness=wallThickness;
            p.walls=l.walls; p.rooms=l.rooms; p.furniture=l.furniture; p.lights=l.lights;
            p.dimensions=l.dimensions; p.roofs=l.roofs; p.balconies=l.balconies;
            if(l.retainingWalls==null) l.retainingWalls=new ArrayList<>();
            p.retainingWalls=l.retainingWalls;
            result.add(p);
        }
        return result;
    }

    /** Absolute-height snapshot for terrain, furniture, lighting and external whole-building consumers.
     * Structural meshes must still be built per storey, so wall junctions remain independent. */
    public Project worldProject() {
        ensureLevels();
        Project world=new Project();
        world.env=env; world.wallHeight=wallHeight; world.wallThickness=wallThickness;
        world.worldSources=new java.util.IdentityHashMap<>();
        com.google.gson.Gson gson=new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();
        for(BuildingLevel level:levels) {
            BuildingLevel c=gson.fromJson(gson.toJson(level),BuildingLevel.class);
            double base=level.baseHeight;
            for(int i=0;i<c.rooms.size();i++) {
                Room room=c.rooms.get(i);
                room.elevation+=base;
                room.ceilingHeight=base+(room.ceilingHeight>0 ? room.ceilingHeight : localWallHeight(level));
                world.worldSources.put(room,level.rooms.get(i));
            }
            for(int i=0;i<c.furniture.size();i++) {
                c.furniture.get(i).elevation+=base;
                world.worldSources.put(c.furniture.get(i),level.furniture.get(i));
            }
            for(LightSource light:c.lights) light.height+=base;
            for(Roof roof:c.roofs) roof.baseHeight+=base;
            for(int i=0;i<c.balconies.size();i++) {
                c.balconies.get(i).elevation+=base;
                world.worldSources.put(c.balconies.get(i),level.balconies.get(i));
            }
            if(c.retainingWalls==null) c.retainingWalls=new ArrayList<>();
            for(int i=0;i<c.retainingWalls.size();i++) {
                RetainingWall wall=c.retainingWalls.get(i);
                if(!wall.topAbsolute) { wall.top+=base; wall.bottom+=base; wall.topAbsolute=true; }
                world.worldSources.put(wall,level.retainingWalls.get(i));
            }
            world.retainingWalls.addAll(c.retainingWalls);
            world.walls.addAll(c.walls); world.rooms.addAll(c.rooms); world.furniture.addAll(c.furniture);
            world.lights.addAll(c.lights); world.roofs.addAll(c.roofs); world.balconies.addAll(c.balconies);
        }
        return world;
    }

    public Object sourceOf(Object object) {
        return worldSources==null ? object : worldSources.getOrDefault(object,object);
    }

    public BuildingLevel levelOf(Object object) {
        ensureLevels();
        for(BuildingLevel l:levels) {
            if(l.walls.contains(object) || l.rooms.contains(object) || l.furniture.contains(object)
                    || l.lights.contains(object) || l.dimensions.contains(object)
                    || l.roofs.contains(object) || l.balconies.contains(object)
                    || l.retainingWalls!=null && l.retainingWalls.contains(object)) return l;
            for(Wall w:l.walls) if(w.openings.contains(object)) return l;
            for(Room r:l.rooms) if(r.slabOpenings.contains(object) || r.floorZones.contains(object)) return l;
            for(Roof r:l.roofs) if(r.elements.contains(object)) return l;
        }
        return null;
    }

    public double baseOf(Object object) {
        BuildingLevel l=levelOf(object);
        return l==null ? 0 : l.baseHeight;
    }

    private double localWallHeight(BuildingLevel level) {
        return level.walls.stream().filter(w -> !w.isFence()).mapToDouble(w -> w.height).max().orElse(wallHeight);
    }

    public double maxWallHeight() {
        double h = 0;
        for (Wall w : walls) if(!w.isFence()) h = Math.max(h, w.height);
        return h > 0 ? h : wallHeight;
    }

    /** Bounding box of the walls on the plan: {minX, minY, maxX, maxY} or null. */
    public double[] wallBounds() {
        if (walls.isEmpty()) return null;
        double[] b = {1e9, 1e9, -1e9, -1e9};
        for (Wall w : walls) {
            double[] q = w.bounds();
            b[0] = Math.min(b[0],q[0]); b[1] = Math.min(b[1],q[1]);
            b[2] = Math.max(b[2],q[2]); b[3] = Math.max(b[3],q[3]);
        }
        return b;
    }
}
