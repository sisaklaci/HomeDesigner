package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.model.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Saves/loads projects (.hdp = JSON). */
public final class ProjectIO {
    public static final String EXT = "hdp";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    private static final Gson COMPACT = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private ProjectIO() {
    }

    public static String toJson(Project p) {
        p.ensureLevels();
        p.captureActive();
        return COMPACT.toJson(p);
    }

    public static Project fromJson(String s) {
        return fix(COMPACT.fromJson(s, Project.class));
    }

    public static Project copy(Project p) {
        return fromJson(toJson(p));
    }

    public static void save(Project p, Path file) throws IOException {
        p.ensureLevels();
        p.captureActive();
        Files.writeString(file, GSON.toJson(p), StandardCharsets.UTF_8);
    }

    public static Project load(Path file) throws IOException {
        Project p;
        try {
            p = fix(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Project.class));
        } catch (RuntimeException e) {
            throw new IOException(I18n.text("Hibás projektfájl: ") + e.getMessage(), e);
        }
        Path dir = file.toAbsolutePath().getParent();
        for (var level : p.levels) for (Furniture f : level.furniture) {
            if (f.modelPath == null) continue;
            Path mp = Path.of(f.modelPath);
            if (Files.isRegularFile(mp)) continue;
            Path found = locate(dir, mp.getFileName().toString());
            if (found != null) f.modelPath = found.toString();
        }
        return p;
    }

    private static Path locate(Path dir, String name) {
        for (Path root : new Path[]{dir, AppPaths.objectsDir()}) {
            if (root == null || !Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root, 5)) {
                Path hit = s.filter(q -> q.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
                if (hit != null) return hit;
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Project fix(Project p) {
        if (p == null) return new Project();
        if(p.env==null) p.env=new hu.helmetsoft.homedesigner.model.Environment();
        hu.helmetsoft.homedesigner.geom.TerrainGeometry.validate(p.env);
        if(p.levels==null || p.levels.isEmpty()) {
            fixLevel(p);
            p.ensureLevels();
        } else {
            java.util.Set<String> ids=new java.util.HashSet<>();
            for(var level:p.levels) {
                if(level==null || level.id==null || !ids.add(level.id) || level.name==null || level.name.isBlank()
                        || !Double.isFinite(level.baseHeight) || Math.abs(level.baseHeight)>1000)
                    throw new IllegalArgumentException(I18n.text("Érvénytelen épületszint."));
                if(level.walls==null) level.walls=new java.util.ArrayList<>();
                if(level.rooms==null) level.rooms=new java.util.ArrayList<>();
                if(level.furniture==null) level.furniture=new java.util.ArrayList<>();
                if(level.lights==null) level.lights=new java.util.ArrayList<>();
                if(level.dimensions==null) level.dimensions=new java.util.ArrayList<>();
                if(level.roofs==null) level.roofs=new java.util.ArrayList<>();
                if(level.balconies==null) level.balconies=new java.util.ArrayList<>();
                if(level.retainingWalls==null) level.retainingWalls=new java.util.ArrayList<>();
            }
            for(Project view:p.levelViews()) fixLevel(view);
            String requested=p.activeLevelId;
            String active=requested!=null && p.levels.stream().anyMatch(l -> l.id.equals(requested))
                    ? requested : p.levels.getFirst().id;
            p.bindLevel(active);
        }
        return p;
    }

    private static Project fixLevel(Project p) {
        Project d = new Project();
        if (p.walls == null) p.walls = d.walls;
        if (p.rooms == null) p.rooms = d.rooms;
        p.rooms.forEach(r -> {
            hu.helmetsoft.homedesigner.geom.VaultGeometry.validate(r);
            if (r.floorZones == null) r.floorZones = new java.util.ArrayList<>();
            if (r.slabOpenings == null) r.slabOpenings = new java.util.ArrayList<>();
            for (var opening : r.slabOpenings) {
                if (opening != null && opening.points == null && opening.shape != hu.helmetsoft.homedesigner.model.SlabOpening.Shape.POLYGON)
                    opening.points = new java.util.ArrayList<>();
            }
        });
        hu.helmetsoft.homedesigner.geom.SlabGeometry.validate(p);
        hu.helmetsoft.homedesigner.geom.FloorLevels.validate(p);
        if (p.furniture == null) p.furniture = d.furniture;
        p.furniture.forEach(hu.helmetsoft.homedesigner.geom.StairGeometry::validate);
        p.furniture.forEach(hu.helmetsoft.homedesigner.geom.CabinetGeometry::validate);
        p.furniture.forEach(hu.helmetsoft.homedesigner.geom.SolarGeometry::validate);
        if (p.lights == null) p.lights = d.lights;
        if (p.dimensions == null) p.dimensions = d.dimensions;
        if (p.roofs == null) p.roofs = d.roofs;
        if (p.balconies == null) p.balconies = d.balconies;
        for (var balcony : p.balconies) {
            if (balcony.support == null) balcony.support = hu.helmetsoft.homedesigner.model.Balcony.Support.NONE;
            hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.validate(balcony);
        }
        if (p.retainingWalls == null) p.retainingWalls = d.retainingWalls;
        p.retainingWalls.forEach(hu.helmetsoft.homedesigner.geom.RetainingWallGeometry::validate);
        p.roofs.forEach(r -> {
            if (r.elements == null) r.elements = new java.util.ArrayList<>();
            if (r.footprint == null) r.footprint = new java.util.ArrayList<>();
            if (r.circular || !r.footprint.isEmpty() || r.radial())
                hu.helmetsoft.homedesigner.geom.RoofGeometry.validate(r);
        });
        if (p.env == null) p.env = d.env;
        p.walls.forEach(w -> {
            if(w.fence==null) w.fence=hu.helmetsoft.homedesigner.model.Wall.Fence.NONE;
            if (w.openings == null) w.openings = new java.util.ArrayList<>();
            for(var opening:w.openings) {
                if(opening.top==null) opening.top=hu.helmetsoft.homedesigner.model.Opening.Top.RECTANGLE;
                hu.helmetsoft.homedesigner.geom.OpeningGeometry.validate(opening);
            }
        });
        hu.helmetsoft.homedesigner.edit.ProjectOps.validateCurves(p);
        return p;
    }
}
