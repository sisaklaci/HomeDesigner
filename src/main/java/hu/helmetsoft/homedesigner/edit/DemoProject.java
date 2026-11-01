package hu.helmetsoft.homedesigner.edit;

import hu.helmetsoft.homedesigner.io.AppPaths;
import hu.helmetsoft.homedesigner.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Sample house shown on first start. */
public final class DemoProject {
    private DemoProject() {
    }

    public static Project create() {
        Project p = new Project();
        // outer walls 10 x 7.5 m, clockwise: top, right, bottom, left
        ProjectOps.addWalls(p, ProjectOps.rect(0, 0, 10, 7.5), true, hu.helmetsoft.homedesigner.util.I18n.text("Ház"));
        p.rooms.clear();
        Wall top = p.walls.get(0), right = p.walls.get(1), bottom = p.walls.get(2), left = p.walls.get(3);
        for (Wall w : p.walls) w.thickness = 0.3;

        Wall inner1 = new Wall(6, 0, 6, 7.5, 0.12, p.wallHeight);
        Wall inner2 = new Wall(6, 4.2, 10, 4.2, 0.12, p.wallHeight);
        p.walls.add(inner1);
        p.walls.add(inner2);

        Room living = ProjectOps.addRoom(p, ProjectOps.rect(0, 0, 6, 7.5), hu.helmetsoft.homedesigner.util.I18n.text("Nappali"));
        living.floor = new Finish("oak", null);
        Room bed = ProjectOps.addRoom(p, ProjectOps.rect(6, 0, 4, 4.2), hu.helmetsoft.homedesigner.util.I18n.text("Hálószoba"));
        bed.floor = new Finish("greyoak", null);
        Room bath = ProjectOps.addRoom(p, ProjectOps.rect(6, 4.2, 4, 3.3), hu.helmetsoft.homedesigner.util.I18n.text("Fürdőszoba"));
        bath.floor = new Finish("tile_grey", null);

        // inner2 runs +x: its right side (+y) faces the bathroom. inner1 runs +y: left side faces east.
        inner2.right = new Finish("subway", null);
        inner1.right = new Finish("plaster", "#C9D6CF");

        // openings
        top.openings.add(Opening.create(Opening.Type.WINDOW, 2.0));
        top.openings.add(Opening.create(Opening.Type.WINDOW, 4.4));
        top.openings.add(Opening.create(Opening.Type.WINDOW, 8.0));
        Opening balcony = Opening.create(Opening.Type.BALCONY_DOOR, 1.5);
        left.openings.add(balcony);                    // left wall runs from (0,7.5) up to (0,0)
        Opening entry = Opening.create(Opening.Type.DOOR, 5.0); // bottom wall runs from (10,7.5) to (0,7.5): x = 5.0
        bottom.openings.add(entry);
        bottom.openings.add(Opening.create(Opening.Type.WINDOW, 2.0));
        right.openings.add(Opening.create(Opening.Type.WINDOW, 2.1));
        inner1.openings.add(Opening.create(Opening.Type.DOOR, 3.4));
        inner1.openings.add(Opening.create(Opening.Type.DOOR, 5.0));

        // lamps
        LightSource l1 = new LightSource();
        l1.name = hu.helmetsoft.homedesigner.util.I18n.text("Nappali lámpa");
        l1.x = 3;
        l1.y = 3.75;
        l1.height = 2.45;
        p.lights.add(l1);
        LightSource l2 = new LightSource();
        l2.name = hu.helmetsoft.homedesigner.util.I18n.text("Hálószoba lámpa");
        l2.x = 8;
        l2.y = 2.1;
        l2.height = 2.45;
        l2.power = 0.7;
        p.lights.add(l2);

        // dimension lines
        p.dimensions.add(new DimensionLine(0 - 0.15, -0.15, 10 + 0.15, -0.15));
        p.dimensions.get(0).offset = -0.6;
        p.dimensions.add(new DimensionLine(10.15, -0.15, 10.15, 7.65));
        p.dimensions.get(1).offset = -0.6;

        Roof roof = ProjectOps.fitRoof(p);
        roof.pitch = 30;
        p.roofs.add(roof);

        Path objects = AppPaths.objectsDir();
        if (objects != null) {
            addModel(p, objects.resolve("Sofa.blend"), "Kanapé", 2.4, 6.7, 180, 0.85);
            addModel(p, objects.resolve("bed.blend"), "Ágy", 8.0, 1.25, 180, 1.0);
            addModel(p, objects.resolve("Office chair").resolve("Office Chair.fbx"), "Irodai szék", 1.0, 1.2, 160, 1.1);
            addModel(p, objects.resolve("Koltuk").resolve("Koltuk.obj"), "Fotel", 1.0, 3.5, 90, 0.85);
        }
        return p;
    }

    private static void addModel(Project p, Path file, String name, double x, double y, double angle, double height) {
        if (!Files.isRegularFile(file)) return;
        Furniture f = new Furniture();
        f.name = hu.helmetsoft.homedesigner.util.I18n.text(name);
        f.modelPath = file.toAbsolutePath().toString();
        f.x = x;
        f.y = y;
        f.angle = angle;
        f.width = 1;
        f.depth = 1;
        f.height = height;
        f.autoSize = height;
        p.furniture.add(f);
    }

    /** Applies pending automatic sizes for furniture whose model is loaded. */
    public static boolean applyAutoSizes(List<Furniture> list) {
        boolean changed = false;
        for (Furniture f : list) {
            if (f.autoSize == 0 || f.modelPath == null) continue;
            var md = hu.helmetsoft.homedesigner.io.ModelLibrary.now(f.modelPath);
            if (md == null) continue;
            ProjectOps.applyModelSize(f, md, f.autoSize > 0 ? f.autoSize : 0);
            f.autoSize = 0;
            changed = true;
        }
        return changed;
    }
}
