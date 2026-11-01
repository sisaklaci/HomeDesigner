package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.edit.DemoProject;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.geom.RoofGeometry;
import hu.helmetsoft.homedesigner.geom.SlabGeometry;
import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.edit.RoofFitter;
import hu.helmetsoft.homedesigner.model.*;
import javafx.application.Platform;
import javafx.beans.property.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/** Shared editor state: project, selection, tool, undo/redo and change notification. */
public final class Editor {
    public enum Tool {
        SELECT("Kijelölés / mozgatás", "Kattints egy elemre a kijelöléshez, húzd a mozgatáshoz. Üres területen húzva a nézet mozog, görgővel nagyít. Del = törlés."),
        WALL("Fal", "Kattints a fal kezdőpontjára, majd a következő pontokra. Gépelj számot (m) + Enter a pontos hosszhoz. Az első pontra kattintva bezárul és helyiség jön létre. Esc/jobb klikk = befejezés."),
        ROOM("Helyiség (téglalap)", "Húzd ki a helyiség téglalapját: falak és padló jönnek létre egyszerre."),
        FLOOR("Padló (sokszög)", "Kattints a padló sarokpontjaira, az első pontra kattintva zárul. (Falak nélküli burkolatfelület.)"),
        DOOR("Ajtó", "Kattints egy falra az ajtó elhelyezéséhez."),
        WINDOW("Ablak", "Kattints egy falra az ablak elhelyezéséhez."),
        LAMP("Lámpa", "Kattints a lámpa helyére (a fotó renderben fényforrás)."),
        DIMENSION("Méretvonal", "Kattints a méretvonal két végpontjára."),
        ARC_WALL("Ívelt fal (3 pont)", "Kattints: kezdőpont → az ív egy köztes pontja → végpont. Esc/jobb klikk = megszakítás."),
        ROTUNDA("Rotunda / körfal", "Kattints a kör középpontjára, majd a sugár végére. Szám (m) + Enter = pontos sugár. Körfal és padló együtt készül."),
        SLAB_RECT("Födémnyílás – téglalap", "A helyiségben húzd ki a nyílás téglalapját. A mennyezet és padló külön kapcsolható."),
        SLAB_CIRCLE("Födémnyílás – kör", "A helyiségben kattints a középpontra, majd a sugár végére. Szám (m) + Enter = sugár."),
        SLAB_ELLIPSE("Födémnyílás – ovális", "A helyiségben húzd ki az ovális befoglaló téglalapját."),
        SLAB_POLYGON("Födémnyílás – sokszög", "Kattints a nyílás sarokpontjaira; az első pontra kattintva, Enterrel, dupla vagy jobb kattintással zárod. Esc = megszakítás."),
        FLOOR_LEVEL("Padlószint-terület", "A helyiségben húzz ki egy téglalapot; jobb oldalt állítsd a méretét és a szinteltérését centiméterben. Alapérték: −34 cm."),
        TERRACE("Terasz", "Húzd ki a terasz téglalapját. Vízszintes, korlát nélküli járófelület a helyi padló- vagy terepszinten; az alátámasztása a lejtős terepig ér."),
        RETAINING_WALL("Támfal", "Kattints a támfal két végpontjára. A teteje vízszintes, az alja a terepet követi; a magasság a jobb panelen állítható."),
        FILL("Földfeltöltés", "Húzd ki a feltöltés téglalapját (Shift: ovális). A teteje vízszintes abszolút szint, a széle rézsűvel vagy függőlegesen csatlakozik a terephez.");

        public final String label, hint;

        Tool(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private Project project;
    private Object selection;
    public final ObjectProperty<Tool> tool = new SimpleObjectProperty<>(Tool.SELECT);
    public final StringProperty status = new SimpleStringProperty("");
    public final StringProperty title = new SimpleStringProperty("");
    public final BooleanProperty showDimensions = new SimpleBooleanProperty(true);
    public final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    public final BooleanProperty canRedo = new SimpleBooleanProperty(false);
    private Path file;
    private boolean dirty;

    private record History(String json,String editedLevelId) {}
    private final Deque<History> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private final List<Runnable> projectListeners = new ArrayList<>();
    private final List<Runnable> levelChangingListeners = new ArrayList<>();
    private boolean changePending;

    public Editor(Project p) {
        project = p;
        project.ensureLevels();
        updateTitle();
        loadModels();
    }

    public Project project() {
        return project;
    }

    public Object selection() {
        return selection;
    }

    public Path file() {
        return file;
    }

    void refreshLanguage() {
        updateTitle();
        status.set("");
    }

    public Runnable onChange(Runnable r) {
        changeListeners.add(r);
        return () -> changeListeners.remove(r);
    }

    public Runnable onSelection(Runnable r) {
        selectionListeners.add(r);
        return () -> selectionListeners.remove(r);
    }

    /** Called when the whole project object is replaced. */
    public Runnable onProject(Runnable r) {
        projectListeners.add(r);
        return () -> projectListeners.remove(r);
    }

    public void select(Object o) {
        BuildingLevel level=project.levelOf(o);
        if(level!=null && !level.id.equals(project.activeLevelId)) switchLevel(level.id);
        if (o == selection) return;
        selection = o;
        selectionListeners.forEach(Runnable::run);
    }

    public void switchLevel(String id) {
        if(project.activeLevel().id.equals(id)) return;
        levelChangingListeners.forEach(Runnable::run);
        project.captureActive();
        project.bindLevel(id);
        selection=null;
        projectListeners.forEach(Runnable::run);
        selectionListeners.forEach(Runnable::run);
        changeListeners.forEach(Runnable::run);
    }

    public void addLevel(String name,double baseHeight) {
        validateLevel(name,baseHeight);
        levelChangingListeners.forEach(Runnable::run);
        edit(() -> {
            project.captureActive();
            BuildingLevel level=new BuildingLevel();
            level.name=name.trim(); level.baseHeight=baseHeight;
            project.levels.add(level);
            project.bindLevel(level.id);
            select(null);
        });
        projectListeners.forEach(Runnable::run);
    }

    public void updateLevel(String name,double baseHeight) {
        validateLevel(name,baseHeight);
        edit(() -> {
            project.activeLevel().name=name.trim();
            project.activeLevel().baseHeight=baseHeight;
        });
        projectListeners.forEach(Runnable::run);
    }

    /** Only empty storeys can be removed; contents must be deliberately removed first. */
    public void deleteLevel() {
        if(project.levels.size()<=1) throw new IllegalArgumentException(I18n.text("Az utolsó épületszint nem törölhető."));
        if(!project.activeLevel().isEmpty()) throw new IllegalArgumentException(I18n.text("Csak üres épületszint törölhető. Előbb töröld az elemeit!"));
        levelChangingListeners.forEach(Runnable::run);
        edit(() -> {
            BuildingLevel level=project.activeLevel();
            project.levels.remove(level);
            project.bindLevel(project.levels.getFirst().id);
            select(null);
        });
        projectListeners.forEach(Runnable::run);
    }

    private static void validateLevel(String name,double baseHeight) {
        if(name==null || name.isBlank() || !Double.isFinite(baseHeight) || Math.abs(baseHeight)>1000)
            throw new IllegalArgumentException(I18n.text("Adj nevet és −1000 és +1000 m közötti véges alapszintet!"));
    }

    public Runnable onLevelChanging(Runnable listener) {
        levelChangingListeners.add(listener);
        return () -> levelChangingListeners.remove(listener);
    }

    /** Store an undo snapshot before a modification. */
    public void checkpoint() {
        pushUndo(ProjectIO.toJson(project));
    }

    public String snapshot() {
        return ProjectIO.toJson(project);
    }

    /** Push a snapshot taken earlier, if the project has changed since. */
    public void commit(String before) {
        if (before != null && !before.equals(ProjectIO.toJson(project))) pushUndo(before);
    }

    private void pushUndo(String s) {
        undo.push(new History(s,project.activeLevelId));
        while (undo.size() > 100) undo.removeLast();
        redo.clear();
        dirty = true;
        updateTitle();
        updateUndo();
    }

    private void updateUndo() {
        canUndo.set(!undo.isEmpty());
        canRedo.set(!redo.isEmpty());
    }

    /** Notify views that the project content changed (coalesced to one notification per pulse). */
    public void changed() {
        dirty = true;
        if (changePending) return;
        changePending = true;
        Platform.runLater(() -> {
            changePending = false;
            changeListeners.forEach(Runnable::run);
        });
    }

    /** Immediate notification (used while dragging for responsiveness). */
    public void changedNow() {
        dirty = true;
        changeListeners.forEach(Runnable::run);
    }

    public void undo() {
        if (undo.isEmpty()) return;
        History change=undo.pop();
        redo.push(new History(ProjectIO.toJson(project),change.editedLevelId()));
        restore(change.json(),null);
    }

    public void redo() {
        if (redo.isEmpty()) return;
        History change=redo.pop();
        undo.push(new History(ProjectIO.toJson(project),change.editedLevelId()));
        restore(change.json(),change.editedLevelId());
    }

    private void restore(String json,String preferredLevel) {
        int[] path = selectionPath();
        String level=project.activeLevelId;
        project = ProjectIO.fromJson(json);
        if(preferredLevel!=null && project.levels.stream().anyMatch(l -> l.id.equals(preferredLevel)))
            project.bindLevel(preferredLevel);
        // global site fills keep their selection on every storey
        selection = java.util.Objects.equals(level,project.activeLevelId) || path!=null && path[0]==12 ? resolvePath(path) : null;
        updateUndo();
        dirty = true;
        updateTitle();
        projectListeners.forEach(Runnable::run);
        selectionListeners.forEach(Runnable::run);
        changeListeners.forEach(Runnable::run);
    }

    private int[] selectionPath() {
        Object s = selection;
        Project p = project;
        if (s instanceof Wall w) return new int[]{0, p.walls.indexOf(w)};
        if (s instanceof Room r) return new int[]{1, p.rooms.indexOf(r)};
        if (s instanceof FloorZone z) for(int i=0;i<p.rooms.size();i++)
            if(p.rooms.get(i).floorZones.contains(z)) return new int[]{10,i,p.rooms.get(i).floorZones.indexOf(z)};
        if (s instanceof Furniture f) return new int[]{2, p.furniture.indexOf(f)};
        if (s instanceof LightSource l) return new int[]{3, p.lights.indexOf(l)};
        if (s instanceof DimensionLine d) return new int[]{4, p.dimensions.indexOf(d)};
        if (s instanceof Roof r) return new int[]{5, p.roofs.indexOf(r)};
        if (s instanceof Balcony b) return new int[]{7, p.balconies.indexOf(b)};
        if (s instanceof RetainingWall w) return new int[]{11, p.retainingWalls.indexOf(w)};
        if (s instanceof SiteFill f) return new int[]{12, p.env.siteFills.indexOf(f)};
        if (s instanceof SlabOpening o) for (int i = 0; i < p.rooms.size(); i++)
            if (p.rooms.get(i).slabOpenings.contains(o)) return new int[]{9,i,p.rooms.get(i).slabOpenings.indexOf(o)};
        if (s instanceof RoofElement e)
            for (int i = 0; i < p.roofs.size(); i++) {
                int k = p.roofs.get(i).elements.indexOf(e);
                if (k >= 0) return new int[]{8, i, k};
            }
        if (s instanceof Opening o)
            for (int i = 0; i < p.walls.size(); i++) {
                int k = p.walls.get(i).openings.indexOf(o);
                if (k >= 0) return new int[]{6, i, k};
            }
        return null;
    }

    private Object resolvePath(int[] a) {
        if (a == null || a[1] < 0) return null;
        Project p = project;
        try {
            return switch (a[0]) {
                case 0 -> p.walls.get(a[1]);
                case 1 -> p.rooms.get(a[1]);
                case 2 -> p.furniture.get(a[1]);
                case 3 -> p.lights.get(a[1]);
                case 4 -> p.dimensions.get(a[1]);
                case 5 -> p.roofs.get(a[1]);
                case 6 -> p.walls.get(a[1]).openings.get(a[2]);
                case 7 -> p.balconies.get(a[1]);
                case 8 -> p.roofs.get(a[1]).elements.get(a[2]);
                case 9 -> p.rooms.get(a[1]).slabOpenings.get(a[2]);
                case 10 -> p.rooms.get(a[1]).floorZones.get(a[2]);
                case 11 -> p.retainingWalls.get(a[1]);
                case 12 -> p.env.siteFills.get(a[1]);
                default -> null;
            };
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public void setProject(Project p, Path f) {
        project = p;
        project.ensureLevels();
        file = f;
        selection = null;
        undo.clear();
        redo.clear();
        dirty = false;
        updateUndo();
        updateTitle();
        projectListeners.forEach(Runnable::run);
        selectionListeners.forEach(Runnable::run);
        changeListeners.forEach(Runnable::run);
        loadModels();
    }

    public void saved(Path f) {
        file = f;
        dirty = false;
        updateTitle();
    }

    public boolean isDirty() {
        return dirty;
    }

    private void updateTitle() {
        title.set("HomeDesigner – " + (file == null ? I18n.text("Új terv") : file.getFileName().toString()) + (dirty ? " *" : ""));
    }

    /** Wall that owns the opening. */
    public Wall wallOf(Opening o) {
        for (Wall w : project.walls) if (w.openings.contains(o)) return w;
        return null;
    }

    public void delete(Object o) {
        if (o == null) return;
        checkpoint();
        Project p = project;
        if (o instanceof Wall w) {
            ProjectOps.detachRotunda(p,w.rotundaId);
            p.walls.remove(w);
        } else if (o instanceof Room r) {
            if (r.rotundaId != null) p.walls.removeIf(w -> r.rotundaId.equals(w.rotundaId));
            p.rooms.remove(r);
        }
        else if (o instanceof Furniture f) p.furniture.remove(f);
        else if (o instanceof LightSource l) p.lights.remove(l);
        else if (o instanceof DimensionLine d) p.dimensions.remove(d);
        else if (o instanceof Roof r) p.roofs.remove(r);
        else if (o instanceof Balcony b) p.balconies.remove(b);
        else if (o instanceof RetainingWall w) p.retainingWalls.remove(w);
        else if (o instanceof SiteFill f) p.env.siteFills.remove(f);
        else if (o instanceof SlabOpening opening) {
            Room room = roomOf(opening);
            if (room != null) room.slabOpenings.remove(opening);
        }
        else if(o instanceof FloorZone zone) {
            Room room=roomOf(zone);
            if(room!=null) room.floorZones.remove(zone);
        }
        else if (o instanceof RoofElement e) {
            Roof r = roofOf(e);
            if (r != null) r.elements.remove(e);
        }
        else if (o instanceof Opening op) {
            Wall w = wallOf(op);
            if (w != null) w.openings.remove(op);
        }
        select(null);
        changed();
    }

    public Roof roofOf(RoofElement e) {
        for (Roof r : project.roofs) if (r.elements.contains(e)) return r;
        return null;
    }

    /** Validates architectural edits before committing them to undo history. */
    public void edit(Runnable change) {
        String before = snapshot();
        try {
            change.run();
        } catch (IllegalArgumentException ex) {
            rejectEdit(before,ex);
            return;
        }
        finishEdit(before);
    }

    public boolean finishEdit(String before) {
        try {
            hu.helmetsoft.homedesigner.geom.TerrainGeometry.validate(project.env);
            project.rooms.forEach(hu.helmetsoft.homedesigner.geom.VaultGeometry::validate);
            RoofGeometry.validate(project);
            ProjectOps.validateCurves(project);
            project.furniture.forEach(hu.helmetsoft.homedesigner.geom.StairGeometry::validate);
            project.furniture.forEach(hu.helmetsoft.homedesigner.geom.CabinetGeometry::validate);
            project.furniture.forEach(hu.helmetsoft.homedesigner.geom.SolarGeometry::validate);
            SlabGeometry.validate(project);
            hu.helmetsoft.homedesigner.geom.FloorLevels.validate(project);
            project.balconies.forEach(hu.helmetsoft.homedesigner.geom.RetainingWallGeometry::validate);
            project.retainingWalls.forEach(hu.helmetsoft.homedesigner.geom.RetainingWallGeometry::validate);
        } catch (IllegalArgumentException ex) {
            rejectEdit(before,ex);
            return false;
        }
        commit(before);
        changed();
        return true;
    }

    private void rejectEdit(String before, IllegalArgumentException ex) {
        int[] path = selectionPath();
        project = ProjectIO.fromJson(before);
        selection = resolvePath(path);
        projectListeners.forEach(Runnable::run);
        selectionListeners.forEach(Runnable::run);
        changeListeners.forEach(Runnable::run);
        status.set(ex.getMessage());
        Dialogs.info(I18n.text("Az elem nem helyezhető el"),ex.getMessage());
    }

    public void addRoofElement(Roof r, RoofElement.Type type) {
        RoofElement e = new RoofElement();
        e.type = type;
        if (type != RoofElement.Type.SKYLIGHT) { e.width = 1.8; e.depth = 1.5; }
        try {
            ProjectOps.placeRoofElement(r,e);
        } catch (IllegalArgumentException ex) {
            Dialogs.info(I18n.text("Tetőelem"),ex.getMessage());
            return;
        }

        checkpoint();
        r.elements.add(e);
        select(e);
        changed();
    }

    public void addRoof() {
        edit(() -> {
            Roof roof = RoofFitter.fit(project,selection);
            project.roofs.add(roof);
            select(roof);
        });
    }

    public Furniture addStair(Stair.Type type, double x, double y) {
        Furniture f = new Furniture();
        f.stair = new Stair(); f.stair.type = type; f.name = type.label;
        f.x = x; f.y = y; f.height = 2.8; f.elevation=hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(project,x,y);
        f.width = type == Stair.Type.SPIRAL ? 2 : 1.1;
        f.depth = type == Stair.Type.SPIRAL ? 2 : 4;
        edit(() -> { project.furniture.add(f); select(f); });
        tool.set(Tool.SELECT);
        return f;
    }

    public Room roomOf(SlabOpening opening) {
        return project.rooms.stream().filter(r -> r.slabOpenings.contains(opening)).findFirst().orElse(null);
    }

    public Room roomOf(FloorZone zone) {
        return project.rooms.stream().filter(r -> r.floorZones.contains(zone)).findFirst().orElse(null);
    }

    public void addFloorZone(Room room,SlabOpening outline) {
        edit(() -> {
            if(room==null || !project.rooms.contains(room)) throw new IllegalArgumentException(I18n.text("Jelöld ki a terület helyiségét!"));
            FloorZone zone=new FloorZone(); zone.outline=outline;
            zone.finish=room.floor.copy(); room.floorZones.add(zone);
            hu.helmetsoft.homedesigner.geom.FloorLevels.validate(project);
            select(zone);
        });
    }

    public Furniture addCabinet(double x, double y) {
        Furniture f = new Furniture(); f.name="Egyedi szekrény"; f.cabinet=new Cabinet();
        f.x=x; f.y=y; f.width=1.2; f.depth=0.55; f.height=2;
        f.elevation=hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(project,x,y);
        edit(() -> { project.furniture.add(f); select(f); });
        return f;
    }

    public Room openingRoom(double x, double y) {
        if (selection instanceof Room r) return r;
        if (selection instanceof FloorZone z) return roomOf(z);
        if (selection instanceof SlabOpening opening) return roomOf(opening);
        List<Room> rooms = project.rooms.stream().filter(r -> r.contains(x,y)).toList();
        if (rooms.size() == 1) return rooms.getFirst();
        throw new IllegalArgumentException(rooms.isEmpty() ? I18n.text("A födémnyíláshoz előbb rajzolj helyiséget vagy padlófelületet, majd azon kezdd a nyílást!")
                : I18n.text("Itt több helyiség/padló fedi egymást. Jelöld ki a kívánt helyiséget, majd válaszd a födémnyílás eszközt!"));
    }

    public boolean addSlabOpening(Room room, SlabOpening opening) {
        String before = snapshot();
        edit(() -> {
            if (room == null || !project.rooms.contains(room)) throw new IllegalArgumentException(I18n.text("Jelöld ki a nyíláshoz tartozó helyiséget!"));
            SlabGeometry.validate(opening);
            var overlap = SlabGeometry.roomArea(room);
            overlap.intersect(hu.helmetsoft.homedesigner.geom.RoofOutline.area(SlabGeometry.boundary(opening)));
            if (overlap.isEmpty()) throw new IllegalArgumentException(I18n.text("A nyílás legalább részben essen a kiválasztott helyiség felületére!"));
            room.slabOpenings.add(opening);
            if (opening.cutCeiling) room.hasCeiling = true;
            select(opening);
        });
        return !before.equals(snapshot());
    }

    public void openingForStair(Furniture f, SlabOpening.Shape shape) {
        List<Room> rooms = project.rooms.stream().filter(r -> r.contains(f.x,f.y)).toList();
        if (rooms.isEmpty()) {
            status.set(I18n.text("A lépcső középpontja alatt nincs helyiség."));
            Dialogs.info(I18n.text("Födémnyílás"),I18n.text("Előbb helyezd a lépcsőt egy helyiség/padló területére, vagy rajzold meg a helyiség felületét!"));
            return;
        }
        Room room = rooms.getFirst();
        if (rooms.size() > 1) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < rooms.size(); i++) names.add((i+1)+". "+rooms.get(i).name);
            var choice = new javafx.scene.control.ChoiceDialog<>(names.getFirst(),names);
            choice.setTitle(I18n.text("Födémnyílás helyisége")); choice.setHeaderText(I18n.text("Válaszd ki a kivágandó helyiséget/padlót!"));
            var result = choice.showAndWait();
            if (result.isEmpty()) return;
            room = rooms.get(names.indexOf(result.get()));
        }
        SlabOpening o = new SlabOpening();
        o.name = f.name+I18n.text(" nyílása"); o.shape = shape; o.x = f.x; o.y = f.y; o.angle = f.angle;
        o.width = f.width+0.2; o.depth = f.depth+0.2;
        if (shape == SlabOpening.Shape.CIRCLE) o.width = o.depth = Math.hypot(f.width,f.depth)+0.2;
        if (shape == SlabOpening.Shape.CIRCLE && f.stair.type == Stair.Type.SPIRAL) o.width = o.depth = f.width+0.2;
        if (shape == SlabOpening.Shape.ELLIPSE && f.stair.type == Stair.Type.STRAIGHT) {
            o.width = Math.sqrt(2)*(f.width+0.2); o.depth = Math.sqrt(2)*(f.depth+0.2);
        }
        if (addSlabOpening(room,o))
            status.set(I18n.text("Födémnyílás létrehozva. A Mennyezet kapcsolót kapcsold be a 3D nézetben; a padló külön kivágható a jobb panelen."));
    }

    public void addBalcony() {
        Balcony b = new Balcony();
        double[] bounds = project.wallBounds();
        if (bounds != null) {
            b.x = (bounds[0]+bounds[2]-b.width)/2;
            b.y = bounds[3]+project.wallThickness/2;
        }
        b.elevation = project.maxWallHeight();
        checkpoint();
        project.balconies.add(b);
        select(b);
        changed();
    }

    /** Ground or storey terrace drawn as a plan rectangle; the slab top sits on the highest local floor/ground under it. */
    public Balcony addTerrace(double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0,x1), minY = Math.min(y0,y1), w = Math.abs(x1-x0), d = Math.abs(y1-y0);
        if (w < 0.3 || d < 0.3) {
            status.set(I18n.text("A terasz legalább 30 × 30 cm legyen."));
            return null;
        }
        double top = Double.NEGATIVE_INFINITY;
        for (double[] q : new double[][]{{minX,minY},{minX+w,minY},{minX,minY+d},{minX+w,minY+d},{minX+w/2,minY+d/2}})
            top = Math.max(top, hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(project,q[0],q[1]));
        Balcony t = Balcony.terrace(minX,minY,w,d,Math.ceil(top*100-1e-6)/100);
        Balcony[] added = {null};
        edit(() -> {
            project.balconies.add(t);
            added[0] = t;
            select(t);
        });
        return project.balconies.contains(t) ? added[0] : null;
    }

    /** Storey-owned retaining wall; default crest 1 m above the highest natural terrain at its ends. */
    public RetainingWall addRetainingWall(double x1, double y1, double x2, double y2) {
        if (Math.hypot(x2-x1,y2-y1) < 0.1) {
            status.set(I18n.text("A támfal legalább 10 cm hosszú legyen."));
            return null;
        }
        RetainingWall w = new RetainingWall();
        w.name = I18n.text("Támfal");
        w.x1 = x1; w.y1 = y1; w.x2 = x2; w.y2 = y2;
        double base = project.activeLevel().baseHeight;
        double ground = Math.max(hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.ground(project.env,x1,y1),
                hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.ground(project.env,x2,y2));
        w.top = Math.round((ground - base + 1.0) * 100) / 100.0;
        w.bottom = w.top - 1.5;
        edit(() -> {
            project.retainingWalls.add(w);
            select(w);
        });
        return project.retainingWalls.contains(w) ? w : null;
    }

    /** Global earthfill (all storeys); top is an absolute world datum, 50 cm above the terrain at its centre. */
    public SiteFill addSiteFill(double x0, double y0, double x1, double y1, boolean ellipse) {
        double w = Math.abs(x1-x0), d = Math.abs(y1-y0);
        if (w < 0.3 || d < 0.3) {
            status.set(I18n.text("A feltöltés legalább 30 × 30 cm legyen."));
            return null;
        }
        SiteFill f = new SiteFill();
        f.name = I18n.text("Földfeltöltés");
        f.outline.name = f.name;
        f.outline.shape = ellipse ? SlabOpening.Shape.ELLIPSE : SlabOpening.Shape.RECTANGLE;
        f.outline.x = (x0+x1)/2; f.outline.y = (y0+y1)/2; f.outline.width = w; f.outline.depth = d;
        f.outline.cutCeiling = false;
        f.elevation = Math.round((hu.helmetsoft.homedesigner.geom.TerrainGeometry.height(project.env,f.outline.x,f.outline.y)+0.01+0.5)*100)/100.0;
        edit(() -> {
            project.env.siteFills.add(f);
            select(f);
        });
        return project.env.siteFills.contains(f) ? f : null;
    }

    /** One undoable step: the fill gets a vertical edge and is ringed by independent, editable retaining walls
     * on the active storey (absolute crest at the fill datum, bottoms on the natural terrain). */
    public List<RetainingWall> addRetainingWallsAroundFill(SiteFill fill) {
        if (fill == null || !project.env.siteFills.contains(fill)) return List.of();
        List<RetainingWall> walls = hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.aroundFill(fill, 0.25);
        String before = snapshot();
        fill.retaining = true;
        project.retainingWalls.addAll(walls);
        if (!finishEdit(before)) return List.of();
        status.set(I18n.text("%d támfal készült a feltöltés köré. Egyenként szerkeszthetők; a feltöltés későbbi módosítása nem mozgatja őket.", walls.size()));
        return walls;
    }

    public void duplicateExtension(Object o) {
        var gson = new com.google.gson.Gson();
        if (o instanceof RetainingWall w) {
            RetainingWall c = w.copy();
            c.x1 += 0.3; c.y1 += 0.3; c.x2 += 0.3; c.y2 += 0.3;
            edit(() -> { project.retainingWalls.add(c); select(c); });
            return;
        }
        if (o instanceof SiteFill f) {
            SiteFill c = f.copy();
            c.outline.x += 0.5; c.outline.y += 0.5;
            edit(() -> { project.env.siteFills.add(c); select(c); });
            return;
        }
        if (o instanceof Balcony b) {
            Balcony c = gson.fromJson(gson.toJson(b), Balcony.class);
            c.x += 0.3; c.y += 0.3;
            checkpoint();
            project.balconies.add(c);
            select(c);
            changed();
        } else if (o instanceof RoofElement e) {
            Roof r = roofOf(e);
            if (r == null) return;
            RoofElement c = gson.fromJson(gson.toJson(e), RoofElement.class);
            try {
                ProjectOps.placeRoofElement(r,c);
            } catch (IllegalArgumentException ex) {
                Dialogs.info(I18n.text("Másolat"),ex.getMessage());
                return;
            }
            checkpoint();
            r.elements.add(c);
            select(c);
            changed();
        }
    }

    /** Adds an imported model at the given plan position; its natural size is applied once loaded. */
    public Furniture addFurniture(String path, double x, double y) {
        SolarPanel.Type solarType = hu.helmetsoft.homedesigner.geom.SolarGeometry.fromPath(path);
        if (solarType != null) return addSolar(solarType, x, y);
        checkpoint();
        Furniture f = new Furniture();
        String fn = Path.of(path).getFileName().toString();
        int dot = fn.lastIndexOf('.');
        f.name = dot > 0 ? fn.substring(0, dot) : fn;
        f.modelPath = path;
        if(hu.helmetsoft.homedesigner.io.HomeModels.isColumn(path)
                || hu.helmetsoft.homedesigner.io.HomeModels.isBeam(path))
            f.override=new Finish("oak_wood",null);
        f.x = x;
        f.y = y;
        f.elevation=hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(project,x,y);
        if(hu.helmetsoft.homedesigner.io.HomeModels.isBeam(path)) {
            f.elevation+=2.4;
            for(Room room:project.rooms) if(room.contains(x,y)) {
                double ceiling=room.ceilingHeight>0 ? room.ceilingHeight:project.maxWallHeight();
                f.elevation=Math.max(room.elevation,ceiling-0.2);
                break;
            }
        }
        f.width = f.depth = f.height = 0.8;
        f.autoSize = -1;
        project.furniture.add(f);
        select(f);
        changed();
        requestModel(f, null);
        return f;
    }

    public Furniture addSolar(SolarPanel.Type type, double x, double y) {
        Furniture f = new Furniture();
        f.solar = new SolarPanel();
        f.solar.type = type;
        f.name = type == SolarPanel.Type.ROOF ? "Tetőre szerelhető napelem" : "Talajra szerelhető napelem";
        f.x = x;
        f.y = y;
        f.elevation = hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(project, x, y);
        hu.helmetsoft.homedesigner.geom.SolarGeometry.updateSize(f);
        edit(() -> { project.furniture.add(f); select(f); });
        tool.set(Tool.SELECT);
        return f;
    }

    /** Starts loading all furniture models in the background and refreshes the views when they arrive. */
    public void loadModels() {
        project.ensureLevels();
        for (BuildingLevel level:project.levels) for (Furniture f : level.furniture) requestModel(f, null);
    }

    public void requestModel(Furniture f, Consumer<Furniture> done) {
        if (f.modelPath == null) return;
        String path = f.modelPath;
        if (ModelLibrary.now(path) != null) {
            if (DemoProject.applyAutoSizes(List.of(f))) changed();
            if (done != null) done.accept(f);
            return;
        }
        String name = Path.of(path).getFileName().toString();
        status.set(I18n.text("Betöltés: ") + name + " …");
        ModelLibrary.get(path, msg -> Platform.runLater(() -> status.set(name + ": " + msg))).whenComplete((md, ex) ->
                Platform.runLater(() -> {
                    if (ex != null || md == null) {
                        String err = ModelLibrary.error(path);
                        status.set(I18n.text("Hiba: ") + name);
                        Dialogs.error(I18n.text("A modell nem tölthető be"), name + "\n\n" + (err != null ? err : String.valueOf(ex)));
                        return;
                    }
                    status.set(name + I18n.text(" betöltve (") + String.format(I18n.locale(), "%,d", md.triangleCount()) + I18n.text(" háromszög)")
                            + (md.note != null ? " – " + md.displayNote() : ""));
                    for(BuildingLevel level:project.levels) DemoProject.applyAutoSizes(level.furniture);
                    if (done != null) done.accept(f);
                    changeListeners.forEach(Runnable::run);
                    selectionListeners.forEach(Runnable::run);
                }));
    }
}
