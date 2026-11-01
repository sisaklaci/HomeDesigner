package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.WallGeometry;
import hu.helmetsoft.homedesigner.geom.RoofGeometry;
import hu.helmetsoft.homedesigner.geom.RoofOutline;
import hu.helmetsoft.homedesigner.geom.SlabGeometry;
import hu.helmetsoft.homedesigner.edit.RoofFitter;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ModelLoader;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.Texture;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.util.*;

/** 2D floor plan editor (alaprajz). World units are meters, plan y axis points down. */
public final class PlanView extends Pane {
    private static final Color BG = Color.web("#F6F5F2"), GRID = Color.web("#E6E3DD"), GRID_MAJOR = Color.web("#D4D0C8");
    private static final Color WALL = Color.web("#3E434B"), SEL = Color.web("#1F78E0"), DIM = Color.web("#C2410C");
    private static final double HANDLE = 5;

    private final Editor ed;
    private final Canvas canvas = new Canvas();
    private double scale = 45, offX = 80, offY = 80;
    private boolean fitted;

    // drawing state
    private final List<Pt> pts = new ArrayList<>();
    private final List<Wall> chain = new ArrayList<>();
    private double curX, curY;
    private boolean snappedPoint;
    private String typed = "";
    private Pt dragStart;
    private boolean fillEllipse;
    private Room drawingRoom;

    // drag state
    private enum Drag { NONE, PAN, MOVE, WALL_END, WALL_BEND, CIRCLE_RADIUS, ROOM_VERTEX, FURN_ROTATE, FURN_RESIZE, DIM_END, ROOF_CORNER, SLAB_ROTATE, SLAB_RESIZE, SLAB_VERTEX, RW_END }

    private Drag drag = Drag.NONE;
    private double pressSX, pressSY, lastWX, lastWY;
    private String before;
    private int handleIndex;
    private final List<double[]> attached = new ArrayList<>(); // {kind, index/obj ref id, which}
    private final List<Object> attachedObj = new ArrayList<>();
    private ContextMenu menu;

    private final Map<String, Image> tinted = new HashMap<>();

    public PlanView(Editor ed) {
        this.ed = ed;
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(o -> redraw());
        canvas.heightProperty().addListener(o -> {
            if (!fitted && getHeight() > 50 && getWidth() > 50) {
                fitted = true;
                fit();
            }
            redraw();
        });
        setMinSize(100, 100);
        setFocusTraversable(true);
        ed.onChange(this::redraw);
        ed.onSelection(this::redraw);
        ed.onLevelChanging(this::cancelDrawing);
        ed.onProject(() -> {
            clearDrawing();
            fit();
        });
        ed.tool.addListener((o, a, b) -> {
            cancelDrawing();
            setCursor(b == Editor.Tool.SELECT ? Cursor.DEFAULT : Cursor.CROSSHAIR);
            redraw();
        });
        ed.showDimensions.addListener(o -> redraw());

        setOnMousePressed(this::pressed);
        setOnMouseDragged(this::dragged);
        setOnMouseReleased(this::released);
        setOnMouseMoved(this::moved);
        setOnScroll(this::scroll);
        setOnKeyPressed(this::key);
        setOnKeyTyped(this::keyTyped);
        setOnDragOver(this::dragOver);
        setOnDragDropped(this::dragDropped);
    }

    void localize() {
        if (menu != null) menu.hide();
        redraw();
    }

    // ------------------------------------------------------------------ coordinates

    double sx(double x) {
        return x * scale + offX;
    }

    double sy(double y) {
        return y * scale + offY;
    }

    private double wx(double sx) {
        return (sx - offX) / scale;
    }

    private double wy(double sy) {
        return (sy - offY) / scale;
    }

    /** Fit the whole project into the view. */
    public void fit() {
        double[] b = bounds();
        double w = Math.max(200, getWidth()), h = Math.max(200, getHeight());
        if (b == null) {
            scale = 45;
            offX = 60;
            offY = 60;
        } else {
            double bw = Math.max(2, b[2] - b[0]), bh = Math.max(2, b[3] - b[1]);
            scale = Math.min((w - 120) / bw, (h - 120) / bh);
            scale = Math.max(5, Math.min(400, scale));
            offX = w / 2 - (b[0] + b[2]) / 2 * scale;
            offY = h / 2 - (b[1] + b[3]) / 2 * scale;
        }
        redraw();
    }

    /** Plan coordinates of the view center. */
    public double[] center() {
        return new double[]{wx(getWidth() / 2), wy(getHeight() / 2)};
    }

    private double[] bounds() {
        Project p = ed.project();
        double[] b = {1e9, 1e9, -1e9, -1e9};
        boolean any = false;
        for (Wall w : p.walls) {
            any = true;
            double[] q = w.bounds();
            ext(b,q[0],q[1]); ext(b,q[2],q[3]);
        }
        for (Room r : p.rooms) for (Pt q : r.points) {
            any = true;
            ext(b, q.x, q.y);
        }
        for (Room r : p.rooms) for (SlabOpening o : r.slabOpenings) {
            any = true;
            for (double[] q : SlabGeometry.boundary(o)) ext(b,q[0],q[1]);
        }
        for (Furniture f : p.furniture) {
            any = true;
            ext(b, f.x, f.y);
        }
        for (Roof r : p.roofs) {
            any = true;
            ext(b,r.x-r.overhang,r.y-r.overhang);
            ext(b,r.x+r.w+r.overhang,r.y+r.d+r.overhang);
        }
        for (Balcony balcony : p.balconies) {
            any = true;
            for (double[] q : balcony.corners()) ext(b,q[0],q[1]);
        }
        return any ? b : null;
    }

    private static void ext(double[] b, double x, double y) {
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.max(b[2], x);
        b[3] = Math.max(b[3], y);
    }

    // ------------------------------------------------------------------ snapping

    private double[] snap(double x, double y, Pt from, boolean free) {
        snappedPoint = false;
        double best = 12 / scale;
        double[] r = null;
        Project p = ed.project();
        for (Wall w : p.walls) {
            if (Math.hypot(w.x1 - x, w.y1 - y) < best) {
                best = Math.hypot(w.x1 - x, w.y1 - y);
                r = new double[]{w.x1, w.y1};
            }
            if (Math.hypot(w.x2 - x, w.y2 - y) < best) {
                best = Math.hypot(w.x2 - x, w.y2 - y);
                r = new double[]{w.x2, w.y2};
            }
        }
        for (Room rm : p.rooms) if (rm.rotundaId == null)
            for (Pt q : rm.points)
                if (Math.hypot(q.x - x, q.y - y) < best) {
                    best = Math.hypot(q.x - x, q.y - y);
                    r = new double[]{q.x, q.y};
                }
        if (!pts.isEmpty() && Math.hypot(pts.get(0).x - x, pts.get(0).y - y) < 14 / scale) r = new double[]{pts.get(0).x, pts.get(0).y};
        if (r != null) {
            snappedPoint = true;
            return r;
        }
        if (!free) {
            double g = scale > 120 ? 0.01 : 0.05;
            x = Math.round(x / g) * g;
            y = Math.round(y / g) * g;
        }
        if (from != null) {
            if (Math.abs(x - from.x) * scale < 10) x = from.x;
            if (Math.abs(y - from.y) * scale < 10) y = from.y;
        }
        return new double[]{x, y};
    }

    // ------------------------------------------------------------------ mouse

    private void pressed(MouseEvent e) {
        requestFocus();
        if (menu != null) menu.hide();
        pressSX = e.getX();
        pressSY = e.getY();
        lastWX = wx(e.getX());
        lastWY = wy(e.getY());
        if (e.getButton() == MouseButton.MIDDLE) {
            drag = Drag.PAN;
            return;
        }
        Editor.Tool tool = ed.tool.get();
        if (e.getButton() == MouseButton.SECONDARY) {
            if (tool == Editor.Tool.SELECT) {
                Object hit = hit(lastWX, lastWY);
                if (hit != null) ed.select(hit);
                showMenu(e, hit);
            } else if (!pts.isEmpty()) {
                finishDrawing();
            } else ed.tool.set(Editor.Tool.SELECT);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;
        Project p = ed.project();
        switch (tool) {
            case SELECT -> pressSelect(e);
            case WALL, FLOOR, SLAB_POLYGON -> {
                double[] s = snap(lastWX, lastWY, pts.isEmpty() ? null : pts.get(pts.size() - 1), e.isAltDown());
                if (tool == Editor.Tool.SLAB_POLYGON && pts.isEmpty() && !startOpening(s[0],s[1])) return;
                if (e.getClickCount() == 2 && !pts.isEmpty()) {
                    finishDrawing();
                    return;
                }
                addPoint(s[0], s[1]);
            }
            case SLAB_RECT, SLAB_ELLIPSE, FLOOR_LEVEL -> {
                double[] s = snap(lastWX,lastWY,null,e.isAltDown());
                if (!startOpening(s[0],s[1])) return;
                dragStart = new Pt(s[0],s[1]); curX = s[0]; curY = s[1];
            }
            case TERRACE, FILL -> {
                double[] s = snap(lastWX,lastWY,null,e.isAltDown());
                dragStart = new Pt(s[0],s[1]); curX = s[0]; curY = s[1];
                fillEllipse = e.isShiftDown();
            }
            case RETAINING_WALL -> {
                double[] s = snap(lastWX, lastWY, pts.isEmpty() ? null : pts.get(0), e.isAltDown());
                if (pts.isEmpty()) pts.add(new Pt(s[0], s[1]));
                else {
                    Pt a = pts.get(0);
                    pts.clear();
                    if (ed.addRetainingWall(a.x, a.y, s[0], s[1]) != null) ed.tool.set(Editor.Tool.SELECT);
                }
            }
            case SLAB_CIRCLE -> {
                double[] s = snap(lastWX,lastWY,null,e.isAltDown());
                if (pts.isEmpty()) {
                    if (startOpening(s[0],s[1])) pts.add(new Pt(s[0],s[1]));
                } else circleOpening(s[0],s[1]);
            }
            case ARC_WALL, ROTUNDA -> {
                double[] s = snap(lastWX,lastWY,null,e.isAltDown());
                curvedPoint(s[0],s[1]);
            }
            case ROOM -> {
                double[] s = snap(lastWX, lastWY, null, e.isAltDown());
                dragStart = new Pt(s[0], s[1]);
                curX = s[0];
                curY = s[1];
            }
            case DOOR, WINDOW -> {
                Wall w = ProjectOps.nearestWall(p, lastWX, lastWY, 0.25 + 8 / scale);
                if (w == null) {
                    ed.status.set(I18n.text("Kattints egy falra!"));
                    return;
                }
                ed.edit(() -> ed.select(ProjectOps.addOpening(w,
                        tool == Editor.Tool.DOOR ? Opening.Type.DOOR : Opening.Type.WINDOW,w.project(lastWX,lastWY))));
            }
            case LAMP -> {
                ed.checkpoint();
                LightSource l = new LightSource();
                l.x = lastWX;
                l.y = lastWY;
                l.height = Math.max(1, p.maxWallHeight() - 0.3);
                l.name = "Lámpa " + (p.lights.size() + 1);
                p.lights.add(l);
                ed.select(l);
                ed.changed();
            }
            case DIMENSION -> {
                double[] s = snap(lastWX, lastWY, pts.isEmpty() ? null : pts.get(0), e.isAltDown());
                if (pts.isEmpty()) pts.add(new Pt(s[0], s[1]));
                else {
                    ed.checkpoint();
                    Pt a = pts.get(0);
                    if (Math.hypot(s[0] - a.x, s[1] - a.y) > 0.02) {
                        DimensionLine d = new DimensionLine(a.x, a.y, s[0], s[1]);
                        p.dimensions.add(d);
                        ed.select(d);
                    }
                    pts.clear();
                    ed.changed();
                }
            }
        }
        redraw();
    }

    private static double clampPos(Wall w, Opening o, double t) {
        double L = w.length();
        return Math.max(o.width / 2 + 0.02, Math.min(L - o.width / 2 - 0.02, t));
    }

    private static boolean slabTool(Editor.Tool t) { return t.name().startsWith("SLAB_") || t==Editor.Tool.FLOOR_LEVEL; }

    private static SlabOpening outline(Object selection) {
        if(selection instanceof FloorZone zone) return zone.outline;
        if(selection instanceof SiteFill fill) return fill.outline;
        return selection instanceof SlabOpening opening ? opening : null;
    }

    private boolean startOpening(double x, double y) {
        try { drawingRoom = ed.openingRoom(x,y); return true; }
        catch (IllegalArgumentException ex) { ed.status.set(ex.getMessage()); return false; }
    }

    private void completeOpening(SlabOpening o) {
        try {
            SlabGeometry.validate(o);
            if (ed.addSlabOpening(drawingRoom,o)) {
                pts.clear(); typed = ""; dragStart = null; drawingRoom = null;
                ed.tool.set(Editor.Tool.SELECT);
                ed.status.set(I18n.text("Födémnyílás kész. A jobb panelen a mennyezet és a padló külön kapcsolható; 3D-ben kapcsold be a Mennyezetet!"));
            }
        } catch (IllegalArgumentException ex) { ed.status.set(ex.getMessage()); }
    }

    private void circleOpening(double x, double y) {
        Pt c = pts.getFirst();
        SlabOpening o = new SlabOpening(); o.shape = SlabOpening.Shape.CIRCLE; o.x = c.x; o.y = c.y;
        try {
            double radius = typed.isEmpty() ? Math.hypot(x-c.x,y-c.y) : Double.parseDouble(typed.replace(',','.'));
            o.width = o.depth = 2*radius; completeOpening(o);
        } catch (NumberFormatException ex) { ed.status.set(I18n.text("Érvénytelen sugár: ")+typed); }
    }

    private void curvedPoint(double x, double y) {
        Project p = ed.project();
        if (pts.isEmpty() || ed.tool.get() == Editor.Tool.ARC_WALL && pts.size() == 1) {
            if (!pts.isEmpty() && Math.hypot(x-pts.get(0).x,y-pts.get(0).y) < 0.02) {
                ed.status.set(I18n.text("Jelölj ki egy másik, legalább 2 cm-re lévő pontot!"));
                return;
            }
            pts.add(new Pt(x,y));
            typed = "";
            return;
        }
        try {
            if (ed.tool.get() == Editor.Tool.ROTUNDA) {
                Pt c = pts.get(0);
                double radius;
                try { radius = typed.isEmpty() ? Math.hypot(x-c.x,y-c.y) : Double.parseDouble(typed.replace(',','.')); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException(I18n.text("Érvénytelen sugár: ")+typed); }
                String old = ed.snapshot();
                Room r = ProjectOps.addRotunda(p,c.x,c.y,radius);
                ed.commit(old);
                ed.select(r);
            } else {
                Wall w = Wall.through(pts.get(0),pts.get(1),new Pt(x,y),p.wallThickness,p.wallHeight);
                ed.checkpoint();
                p.walls.add(w);
                ed.select(w);
            }
            pts.clear(); typed = "";
            ed.changed();
        } catch (IllegalArgumentException ex) {
            ed.status.set(ex.getMessage());
        }
    }

    private void addPoint(double x, double y) {
        Editor.Tool tool = ed.tool.get();
        Project p = ed.project();
        if (!pts.isEmpty()) {
            Pt last = pts.get(pts.size() - 1);
            if (Math.hypot(last.x - x, last.y - y) < 0.02) return;
        }
        boolean closing = pts.size() >= 3 && Math.hypot(pts.get(0).x - x, pts.get(0).y - y) < 1e-6;
        if (tool == Editor.Tool.SLAB_POLYGON && closing) {
            finishDrawing(); return;
        }
        if (tool == Editor.Tool.WALL && !pts.isEmpty()) {
            if (chain.isEmpty()) before = ed.snapshot();
            Pt last = pts.get(pts.size() - 1);
            Wall w = new Wall(last.x, last.y, x, y, p.wallThickness, p.wallHeight);
            p.walls.add(w);
            chain.add(w);
            ed.changed();
        }
        if (closing) {
            if (tool == Editor.Tool.WALL) {
                double area = ProjectOps.signedArea(pts);
                for (Wall w : chain) {
                    Finish ext = new Finish("plaster", ProjectOps.EXTERIOR_COLOR);
                    if (area > 0) w.left = ext;
                    else w.right = ext;
                }
                Room r = ProjectOps.addRoom(p, pts, null);
                ed.commit(before);
                ed.select(r);
            } else {
                String b = ed.snapshot();
                Room r = ProjectOps.addRoom(p, pts, null);
                r.hasCeiling = false;
                ed.commit(b);
                ed.select(r);
            }
            pts.clear();
            chain.clear();
            typed = "";
            ed.changed();
            return;
        }
        pts.add(new Pt(x, y));
        typed = "";
    }

    private void finishDrawing() {
        if (slabTool(ed.tool.get())) {
            if (ed.tool.get() == Editor.Tool.SLAB_POLYGON && pts.size() >= 3) {
                try { completeOpening(SlabGeometry.polygon(pts)); }
                catch (IllegalArgumentException ex) { ed.status.set(ex.getMessage()); }
            } else cancelDrawing();
            redraw(); return;
        }
        if (ed.tool.get() == Editor.Tool.ARC_WALL || ed.tool.get() == Editor.Tool.ROTUNDA) {
            pts.clear(); typed = "";
            redraw();
            return;
        }
        if (ed.tool.get() == Editor.Tool.WALL && !chain.isEmpty()) {
            ed.commit(before);
            ed.select(chain.get(chain.size() - 1));
        } else if (ed.tool.get() == Editor.Tool.FLOOR && pts.size() >= 3) {
            String b = ed.snapshot();
            Room r = ProjectOps.addRoom(ed.project(), pts, null);
            r.hasCeiling = false;
            ed.commit(b);
            ed.select(r);
        }
        pts.clear();
        chain.clear();
        typed = "";
        ed.changed();
        redraw();
    }

    private void cancelDrawing() {
        if (!chain.isEmpty() || drag!=Drag.NONE && drag!=Drag.PAN) ed.commit(before);
        clearDrawing();
    }

    private void clearDrawing() {
        pts.clear();
        chain.clear();
        typed = "";
        dragStart = null;
        drawingRoom = null;
        drag = Drag.NONE;
        before = null;
        attached.clear();
        attachedObj.clear();
    }

    private void pressSelect(MouseEvent e) {
        double x = lastWX, y = lastWY;
        Object sel = ed.selection();
        attached.clear();
        attachedObj.clear();
        // handles of the current selection first
        if (sel != null && handleHit(sel, e.getX(), e.getY())) {
            before = ed.snapshot();
            return;
        }
        Object hit = hit(x, y);
        ed.select(hit);
        if (hit == null) {
            drag = Drag.PAN;
            setCursor(Cursor.CLOSED_HAND);
            return;
        }
        before = ed.snapshot();
        drag = Drag.MOVE;
        if (hit instanceof Wall w) {
            collectAttached(w.x1, w.y1, w, 1);
            collectAttached(w.x2, w.y2, w, 2);
        }
    }

    /** Records wall ends and room vertices located at (x,y) so they move together with the dragged point. */
    private void collectAttached(double x, double y, Object self, int selfEnd) {
        Project p = ed.project();
        for (Wall o : p.walls) {
            if (o == self) continue;
            if (Math.hypot(o.x1 - x, o.y1 - y) < WallGeometry.JOIN_EPS) {
                attachedObj.add(o);
                attached.add(new double[]{1, selfEnd});
            }
            if (Math.hypot(o.x2 - x, o.y2 - y) < WallGeometry.JOIN_EPS) {
                attachedObj.add(o);
                attached.add(new double[]{2, selfEnd});
            }
        }
        for (Room r : p.rooms)
            for (Pt q : r.points)
                if (Math.hypot(q.x - x, q.y - y) < WallGeometry.JOIN_EPS) {
                    attachedObj.add(q);
                    attached.add(new double[]{0, selfEnd});
                }
    }

    private void moveAttached(int end, double nx, double ny) {
        for (int i = 0; i < attached.size(); i++) {
            double[] a = attached.get(i);
            if ((int) a[1] != end) continue;
            Object o = attachedObj.get(i);
            if (o instanceof Pt q) {
                q.x = nx;
                q.y = ny;
            } else if (o instanceof Wall w) {
                if (a[0] == 1) {
                    w.x1 = nx;
                    w.y1 = ny;
                } else {
                    w.x2 = nx;
                    w.y2 = ny;
                }
            }
        }
    }

    private boolean handleHit(Object sel, double px, double py) {
        double r = HANDLE + 3;
        if (sel instanceof Wall w) {
            if (w.rotundaId != null) return false;
            double[] mid = w.point(w.length()/2,0);
            if (Math.hypot(sx(mid[0])-px,sy(mid[1])-py) < r) {
                drag = Drag.WALL_BEND;
                return true;
            }
            if (Math.hypot(sx(w.x1) - px, sy(w.y1) - py) < r) {
                drag = Drag.WALL_END;
                handleIndex = 1;
                collectAttached(w.x1, w.y1, w, 1);
                return true;
            }
            if (Math.hypot(sx(w.x2) - px, sy(w.y2) - py) < r) {
                drag = Drag.WALL_END;
                handleIndex = 2;
                collectAttached(w.x2, w.y2, w, 2);
                return true;
            }
        } else if (sel instanceof Room rm) {
            if (rm.rotundaId != null) {
                if (Math.hypot(sx(rm.circleX+rm.circleRadius)-px,sy(rm.circleY)-py) < r) {
                    drag = Drag.CIRCLE_RADIUS;
                    return true;
                }
                return false;
            }
            for (int i = 0; i < rm.points.size(); i++) {
                Pt q = rm.points.get(i);
                if (Math.hypot(sx(q.x) - px, sy(q.y) - py) < r) {
                    drag = Drag.ROOM_VERTEX;
                    handleIndex = i;
                    return true;
                }
            }
        } else if (outline(sel)!=null) {
            SlabOpening o=outline(sel);
            double[][] handles = openingHandles(o);
            if (Math.hypot(handles[4][0]-px,handles[4][1]-py) < r+2) {
                drag = Drag.SLAB_ROTATE; return true;
            }
            if (o.shape == SlabOpening.Shape.POLYGON) {
                double[][] points = SlabGeometry.boundary(o);
                for (int i = 0; i < points.length; i++)
                    if (Math.hypot(sx(points[i][0])-px,sy(points[i][1])-py) < r) {
                        drag = Drag.SLAB_VERTEX; handleIndex = i; return true;
                    }
            } else for (int i = 0; i < 4; i++)
                if (Math.hypot(handles[i][0]-px,handles[i][1]-py) < r) {
                    drag = Drag.SLAB_RESIZE; handleIndex = i; return true;
                }
        } else if (sel instanceof Furniture f) {
            double[][] h = furnitureHandles(f);
            for (int i = 0; f.solar == null && i < 4; i++)
                if (Math.hypot(h[i][0] - px, h[i][1] - py) < r) {
                    drag = Drag.FURN_RESIZE;
                    handleIndex = i;
                    return true;
                }
            if (Math.hypot(h[4][0] - px, h[4][1] - py) < r + 2) {
                drag = Drag.FURN_ROTATE;
                return true;
            }
        } else if (sel instanceof RetainingWall w) {
            if (Math.hypot(sx(w.x1) - px, sy(w.y1) - py) < r) { drag = Drag.RW_END; handleIndex = 1; return true; }
            if (Math.hypot(sx(w.x2) - px, sy(w.y2) - py) < r) { drag = Drag.RW_END; handleIndex = 2; return true; }
        } else if (sel instanceof DimensionLine d) {
            double[][] e = dimPoints(d);
            if (Math.hypot(sx(e[2][0]) - px, sy(e[2][1]) - py) < r) {
                drag = Drag.DIM_END;
                handleIndex = 1;
                return true;
            }
            if (Math.hypot(sx(e[3][0]) - px, sy(e[3][1]) - py) < r) {
                drag = Drag.DIM_END;
                handleIndex = 2;
                return true;
            }
        } else if (sel instanceof Roof rf) {
            double[][] c = {{rf.x, rf.y}, {rf.x + rf.w, rf.y}, {rf.x + rf.w, rf.y + rf.d}, {rf.x, rf.y + rf.d}};
            for (int i = 0; i < 4; i++)
                if (Math.hypot(sx(c[i][0]) - px, sy(c[i][1]) - py) < r) {
                    drag = Drag.ROOF_CORNER;
                    handleIndex = i;
                    return true;
                }
        }
        return false;
    }

    /** Screen positions: 4 corners (local -,- / +,- / +,+ / -,+) and the rotation knob. */
    private double[][] furnitureHandles(Furniture f) {
        double a = Math.toRadians(f.angle), c = Math.cos(a), s = Math.sin(a);
        double[][] loc = {{-f.width / 2, -f.depth / 2}, {f.width / 2, -f.depth / 2}, {f.width / 2, f.depth / 2}, {-f.width / 2, f.depth / 2},
                {0, f.depth / 2 + 22 / scale}};
        double[][] out = new double[5][];
        for (int i = 0; i < 5; i++) {
            double lx = loc[i][0], ly = loc[i][1];
            out[i] = new double[]{sx(f.x + lx * c - ly * s), sy(f.y + lx * s + ly * c)};
        }
        return out;
    }

    private double[][] openingHandles(SlabOpening o) {
        Furniture f = new Furniture();
        f.x = o.x; f.y = o.y; f.width = o.width; f.depth = o.depth; f.angle = o.angle;
        return furnitureHandles(f);
    }

    private void dragged(MouseEvent e) {
        double x = wx(e.getX()), y = wy(e.getY());
        if (drag == Drag.PAN) {
            offX += e.getX() - pressSX;
            offY += e.getY() - pressSY;
            pressSX = e.getX();
            pressSY = e.getY();
            redraw();
            return;
        }
        Editor.Tool tool = ed.tool.get();
        if ((tool == Editor.Tool.ROOM || tool == Editor.Tool.SLAB_RECT || tool == Editor.Tool.SLAB_ELLIPSE || tool==Editor.Tool.FLOOR_LEVEL
                || tool == Editor.Tool.TERRACE || tool == Editor.Tool.FILL) && dragStart != null) {
            double[] s = snap(x, y, null, e.isAltDown());
            curX = s[0];
            curY = s[1];
            redraw();
            return;
        }
        if (tool != Editor.Tool.SELECT) {
            moved(e);
            return;
        }
        Object sel = ed.selection();
        double dx = x - lastWX, dy = y - lastWY;
        boolean free = e.isAltDown();
        guardCurveDrag(() -> {
        switch (drag) {
            case SLAB_VERTEX -> {
                double[] point = snap(x,y,null,free);
                SlabGeometry.moveVertex(outline(sel),handleIndex,point[0],point[1]);
            }
            case SLAB_ROTATE -> {
                SlabOpening o = outline(sel);
                double angle = Math.toDegrees(Math.atan2(-(x-o.x),y-o.y));
                o.angle = free ? angle : Math.round(angle/15)*15;
            }
            case SLAB_RESIZE -> {
                SlabOpening o = outline(sel);
                int opposite = (handleIndex+2)%4;
                double[][] corners = {{0,0},{1,0},{1,1},{0,1}};
                double[] fixed = o.point(corners[opposite][0],corners[opposite][1]);
                double a = Math.toRadians(o.angle), c = Math.cos(a), s = Math.sin(a);
                double lx = c*(x-fixed[0])+s*(y-fixed[1]), ly = -s*(x-fixed[0])+c*(y-fixed[1]);
                double width = Math.max(0.05,Math.abs(lx)), depth = Math.max(0.05,Math.abs(ly));
                if (!free) { width = Math.round(width*100)/100.0; depth = Math.round(depth*100)/100.0; }
                if (o.shape == SlabOpening.Shape.CIRCLE) width = depth = Math.max(width,depth);
                double cx = (0.5-corners[opposite][0])*width, cy = (0.5-corners[opposite][1])*depth;
                o.width = width; o.depth = depth; o.x = fixed[0]+c*cx-s*cy; o.y = fixed[1]+s*cx+c*cy;
            }
            case MOVE -> moveSelection(sel, x, y, dx, dy, free);
            case WALL_BEND -> {
                Wall w = (Wall) sel;
                Pt a = new Pt(w.x1,w.y1), b = new Pt(w.x2,w.y2);
                double distance = Math.abs((x-w.x1)*w.nx()+(y-w.y1)*w.ny());
                ProjectOps.setArcAngle(w,distance < 0.005 ? 0 : Wall.through(a,new Pt(x,y),b,w.thickness,w.height).arcAngle);
            }
            case CIRCLE_RADIUS -> {
                Room r = (Room) sel;
                double radius = Math.hypot(x-r.circleX,y-r.circleY);
                if (!free) radius = Math.round(radius*100)/100.0;
                ProjectOps.updateRotunda(ed.project(),r,r.circleX,r.circleY,radius);
            }
            case WALL_END -> {
                Wall w = (Wall) sel;
                double[] s = snapExcluding(x, y, handleIndex == 1 ? new Pt(w.x2, w.y2) : new Pt(w.x1, w.y1), free, w);
                if (handleIndex == 1) {
                    w.x1 = s[0];
                    w.y1 = s[1];
                } else {
                    w.x2 = s[0];
                    w.y2 = s[1];
                }
                moveAttached(handleIndex, s[0], s[1]);
            }
            case ROOM_VERTEX -> {
                Room r = (Room) sel;
                double[] s = snapExcluding(x, y, null, free, r);
                r.points.get(handleIndex).x = s[0];
                r.points.get(handleIndex).y = s[1];
            }
            case FURN_ROTATE -> {
                Furniture f = (Furniture) sel;
                double ang = Math.toDegrees(Math.atan2(-(x - f.x), y - f.y));
                if (!free) ang = Math.round(ang / 15) * 15;
                f.angle = ((ang % 360) + 360) % 360;
            }
            case FURN_RESIZE -> {
                Furniture f = (Furniture) sel;
                double a = Math.toRadians(f.angle), c = Math.cos(a), s = Math.sin(a);
                int opp = (handleIndex + 2) % 4;
                double[][] loc = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                // opposite corner stays fixed
                double ox = loc[opp][0] * f.width / 2, oy = loc[opp][1] * f.depth / 2;
                double fx = f.x + ox * c - oy * s, fy = f.y + ox * s + oy * c;
                double vx = x - fx, vy = y - fy;
                double lx = vx * c + vy * s, ly = -vx * s + vy * c;
                double nw = Math.max(0.05, Math.abs(lx)), nd = Math.max(0.05, Math.abs(ly));
                if (!free) {
                    nw = Math.round(nw * 100) / 100.0;
                    nd = Math.round(nd * 100) / 100.0;
                }
                if (e.isShiftDown()) {
                    double k = Math.max(nw / f.width, nd / f.depth);
                    nw = f.width * k;
                    nd = f.depth * k;
                    f.height *= k;
                }
                if (f.stair != null && f.stair.type == Stair.Type.SPIRAL) nw = nd = Math.max(nw,nd);
                // new center = fixed corner + half diagonal toward the dragged corner (local coords)
                double offLx = -loc[opp][0] * nw / 2, offLy = -loc[opp][1] * nd / 2;
                f.width = nw;
                f.depth = nd;
                f.x = fx + offLx * c - offLy * s;
                f.y = fy + offLx * s + offLy * c;
            }
            case RW_END -> {
                RetainingWall w = (RetainingWall) sel;
                double[] s = snap(x, y, handleIndex == 1 ? new Pt(w.x2, w.y2) : new Pt(w.x1, w.y1), free);
                double ox = handleIndex == 1 ? w.x2 : w.x1, oy = handleIndex == 1 ? w.y2 : w.y1;
                if (Math.hypot(s[0] - ox, s[1] - oy) >= 0.1) {
                    if (handleIndex == 1) { w.x1 = s[0]; w.y1 = s[1]; }
                    else { w.x2 = s[0]; w.y2 = s[1]; }
                }
            }
            case DIM_END -> {
                DimensionLine d = (DimensionLine) sel;
                double[] s = snap(x, y, handleIndex == 1 ? new Pt(d.x2, d.y2) : new Pt(d.x1, d.y1), free);
                if (handleIndex == 1) {
                    d.x1 = s[0];
                    d.y1 = s[1];
                } else {
                    d.x2 = s[0];
                    d.y2 = s[1];
                }
            }
            case ROOF_CORNER -> {
                Roof r = (Roof) sel;
                double[] old = {r.x,r.y,r.w,r.d};
                double[] s = snap(x, y, null, free);
                double x0 = r.x, y0 = r.y, x1 = r.x + r.w, y1 = r.y + r.d;
                if (handleIndex == 0 || handleIndex == 3) x0 = s[0];
                else x1 = s[0];
                if (handleIndex == 0 || handleIndex == 1) y0 = s[1];
                else y1 = s[1];
                r.x = Math.min(x0, x1);
                r.y = Math.min(y0, y1);
                r.w = Math.max(0.5, Math.abs(x1 - x0));
                r.d = Math.max(0.5, Math.abs(y1 - y0));
                if (r.circular) {
                    double size = Math.max(r.w,r.d);
                    r.w = size; r.d = size;
                    r.x = handleIndex == 0 || handleIndex == 3 ? old[0]+old[2]-size : old[0];
                    r.y = handleIndex == 0 || handleIndex == 1 ? old[1]+old[3]-size : old[1];
                }
                try {
                    RoofGeometry.validate(r);
                } catch (IllegalArgumentException ex) {
                    r.x = old[0]; r.y = old[1]; r.w = old[2]; r.d = old[3];
                    ed.status.set(ex.getMessage());
                }
            }
            default -> {
            }
        }
        });
        lastWX = x;
        lastWY = y;
        ed.changedNow();
    }

    /** Keep live previews valid without replacing objects held by the current drag. */
    private void guardCurveDrag(Runnable change) {
        Project p = ed.project();
        Map<Wall,double[]> walls = new IdentityHashMap<>();
        Map<Pt,double[]> points = new IdentityHashMap<>();
        Map<Room,double[]> circles = new IdentityHashMap<>();
        Map<Opening,Double> positions = new IdentityHashMap<>();
        Map<Furniture,double[]> stairs = new IdentityHashMap<>();
        Map<SlabOpening,SlabOpening> slabOpenings = new IdentityHashMap<>();
        for (Room r : p.rooms) for (SlabOpening o : r.slabOpenings) slabOpenings.put(o,o.copy());
        for(Room r:p.rooms) for(FloorZone z:r.floorZones) slabOpenings.put(z.outline,z.outline.copy());
        for (SiteFill fill : p.env.siteFills) slabOpenings.put(fill.outline,fill.outline.copy());
        for (Furniture f : p.furniture) if (f.stair != null || f.cabinet != null || f.solar != null)
            stairs.put(f,new double[]{f.x,f.y,f.width,f.depth,f.height,f.angle});
        for (Wall w : p.walls) {
            walls.put(w,new double[]{w.x1,w.y1,w.x2,w.y2,w.arcAngle});
            for (Opening o : w.openings) positions.put(o,o.pos);
        }
        for (Room r : p.rooms) {
            if (r.rotundaId != null) circles.put(r,new double[]{r.circleX,r.circleY,r.circleRadius});
            else for (Pt pt : r.points) points.put(pt,new double[]{pt.x,pt.y});
        }
        try {
            change.run();
            ProjectOps.validateCurves(p);
            p.furniture.forEach(hu.helmetsoft.homedesigner.geom.StairGeometry::validate);
            p.furniture.forEach(hu.helmetsoft.homedesigner.geom.CabinetGeometry::validate);
            p.furniture.forEach(hu.helmetsoft.homedesigner.geom.SolarGeometry::validate);
            SlabGeometry.validate(p);
            hu.helmetsoft.homedesigner.geom.FloorLevels.validate(p);
            hu.helmetsoft.homedesigner.geom.SiteGeometry.validate(p.env);
        } catch (IllegalArgumentException ex) {
            walls.forEach((w,v) -> { w.x1=v[0]; w.y1=v[1]; w.x2=v[2]; w.y2=v[3]; w.arcAngle=v[4]; });
            points.forEach((pt,v) -> { pt.x=v[0]; pt.y=v[1]; });
            circles.forEach((r,v) -> r.updateCircle(v[0],v[1],v[2]));
            positions.forEach((o,v) -> o.pos=v);
            stairs.forEach((f,v) -> { f.x=v[0]; f.y=v[1]; f.width=v[2]; f.depth=v[3]; f.height=v[4]; f.angle=v[5]; });
            slabOpenings.forEach((o,v) -> { o.x=v.x; o.y=v.y; o.width=v.width; o.depth=v.depth; o.angle=v.angle; o.points=v.points; });
            ed.status.set(ex.getMessage());
        }
    }

    private double[] snapExcluding(double x, double y, Pt from, boolean free, Object self) {
        // temporarily hide the dragged object from endpoint snapping
        Project p = ed.project();
        List<Wall> saveW = null;
        List<Room> saveR = null;
        if (self instanceof Wall w) {
            saveW = p.walls;
            p.walls = new ArrayList<>(p.walls);
            p.walls.remove(w);
            for (Object o : attachedObj) if (o instanceof Wall ow) p.walls.remove(ow);
            saveR = p.rooms;
            p.rooms = new ArrayList<>();
        } else if (self instanceof Room r) {
            saveR = p.rooms;
            p.rooms = new ArrayList<>(p.rooms);
            p.rooms.remove(r);
        }
        double[] s = snap(x, y, from, free);
        if (saveW != null) p.walls = saveW;
        if (saveR != null) p.rooms = saveR;
        return s;
    }

    private void moveSelection(Object sel, double x, double y, double dx, double dy, boolean free) {
        if (outline(sel)!=null) {
            SlabOpening o=outline(sel);
            o.x += dx; o.y += dy;
        } else if (sel instanceof Furniture f) {
            f.x += dx;
            f.y += dy;
        } else if (sel instanceof LightSource l) {
            l.x += dx;
            l.y += dy;
        } else if (sel instanceof DimensionLine d) {
            // dragging a dimension line changes its offset
            double L = d.length();
            if (L > 1e-6) {
                double nx = (d.y2 - d.y1) / L, ny = -(d.x2 - d.x1) / L;
                d.offset += dx * nx + dy * ny;
            }
        } else if (sel instanceof Opening o) {
            Wall w = ed.wallOf(o);
            if (w != null) {
                double t = w.project(x, y);
                if (!free) t = Math.round(t * 100) / 100.0;
                o.pos = clampPos(w, o, t);
            }
        } else if (sel instanceof Wall w) {
            Room circle = ProjectOps.rotundaOf(ed.project(),w);
            if (circle != null) {
                ProjectOps.updateRotunda(ed.project(),circle,circle.circleX+dx,circle.circleY+dy,circle.circleRadius);
                return;
            }
            w.x1 += dx;
            w.y1 += dy;
            w.x2 += dx;
            w.y2 += dy;
            moveAttached(1, w.x1, w.y1);
            moveAttached(2, w.x2, w.y2);
        } else if (sel instanceof Room r) {
            if (r.rotundaId != null) {
                ProjectOps.updateRotunda(ed.project(),r,r.circleX+dx,r.circleY+dy,r.circleRadius);
                return;
            }
            for (SlabOpening o : r.slabOpenings) { o.x += dx; o.y += dy; }
            for(FloorZone z:r.floorZones) { z.outline.x+=dx; z.outline.y+=dy; }
            for (Pt q : r.points) {
                q.x += dx;
                q.y += dy;
            }
        } else if (sel instanceof Roof r) {
            r.x += dx;
            r.y += dy;
        } else if (sel instanceof Balcony b) {
            b.x += dx; b.y += dy;
        } else if (sel instanceof RetainingWall w) {
            w.x1 += dx; w.y1 += dy; w.x2 += dx; w.y2 += dy;
        } else if (sel instanceof RoofElement e) {
            Roof r = ed.roofOf(e);
            double ox = e.x, oy = e.y;
            e.x += dx; e.y += dy;
            try {
                RoofGeometry.validate(r);
            } catch (IllegalArgumentException ex) {
                e.x = ox; e.y = oy;
                ed.status.set(ex.getMessage());
            }
        }
    }

    private void released(MouseEvent e) {
        Editor.Tool tool = ed.tool.get();
        if (e.getButton() == MouseButton.PRIMARY && (tool == Editor.Tool.SLAB_RECT || tool == Editor.Tool.SLAB_ELLIPSE || tool==Editor.Tool.FLOOR_LEVEL) && dragStart != null) {
            SlabOpening o = new SlabOpening();
            o.shape = tool == Editor.Tool.SLAB_ELLIPSE ? SlabOpening.Shape.ELLIPSE : SlabOpening.Shape.RECTANGLE;
            o.x = (dragStart.x+curX)/2; o.y = (dragStart.y+curY)/2;
            o.width = Math.abs(curX-dragStart.x); o.depth = Math.abs(curY-dragStart.y);
            if(tool==Editor.Tool.FLOOR_LEVEL) {
                ed.addFloorZone(drawingRoom,o);
                ed.tool.set(Editor.Tool.SELECT); drawingRoom=null;
            } else completeOpening(o);
            dragStart = null; redraw(); return;
        }
        if (e.getButton() == MouseButton.PRIMARY && (tool == Editor.Tool.TERRACE || tool == Editor.Tool.FILL) && dragStart != null) {
            Pt a = dragStart;
            dragStart = null;
            Object made = tool == Editor.Tool.TERRACE ? ed.addTerrace(a.x,a.y,curX,curY)
                    : ed.addSiteFill(a.x,a.y,curX,curY,fillEllipse || e.isShiftDown());
            if (made != null) ed.tool.set(Editor.Tool.SELECT);
            redraw(); return;
        }
        if (tool == Editor.Tool.ROOM && dragStart != null) {
            double x0 = Math.min(dragStart.x, curX), x1 = Math.max(dragStart.x, curX);
            double y0 = Math.min(dragStart.y, curY), y1 = Math.max(dragStart.y, curY);
            dragStart = null;
            if (x1 - x0 > 0.3 && y1 - y0 > 0.3) {
                ed.checkpoint();
                Room r = ProjectOps.addRoomWithWalls(ed.project(), ProjectOps.rect(x0, y0, x1 - x0, y1 - y0), null);
                ed.select(r);
                ed.changed();
            }
            redraw();
            return;
        }
        if (drag != Drag.NONE && drag != Drag.PAN) {
            ed.finishEdit(before);
        }
        if (drag == Drag.PAN) setCursor(tool == Editor.Tool.SELECT ? Cursor.DEFAULT : Cursor.CROSSHAIR);
        drag = Drag.NONE;
        before = null;
    }

    private void moved(MouseEvent e) {
        double x = wx(e.getX()), y = wy(e.getY());
        Editor.Tool tool = ed.tool.get();
        if (tool == Editor.Tool.WALL || tool == Editor.Tool.FLOOR || tool == Editor.Tool.DIMENSION || tool == Editor.Tool.ROOM
                || tool == Editor.Tool.ARC_WALL || tool == Editor.Tool.ROTUNDA || slabTool(tool)
                || tool == Editor.Tool.TERRACE || tool == Editor.Tool.FILL || tool == Editor.Tool.RETAINING_WALL) {
            Pt from = pts.isEmpty() ? null : pts.get(pts.size() - 1);
            if (tool == Editor.Tool.DIMENSION || tool == Editor.Tool.RETAINING_WALL) from = pts.isEmpty() ? null : pts.get(0);
            if (tool == Editor.Tool.ARC_WALL || tool == Editor.Tool.ROTUNDA || tool == Editor.Tool.SLAB_CIRCLE) from = null;
            double[] s = snap(x, y, from, e.isAltDown());
            curX = s[0];
            curY = s[1];
        } else {
            curX = x;
            curY = y;
        }
        ed.status.set(String.format(I18n.locale(), "x: %.2f m   y: %.2f m", curX, curY));
        if (tool == Editor.Tool.SELECT) updateHoverCursor(e.getX(), e.getY());
        redraw();
    }

    private void updateHoverCursor(double px, double py) {
        Object sel = ed.selection();
        Cursor c = Cursor.DEFAULT;
        if (sel instanceof Furniture f) {
            double[][] h = furnitureHandles(f);
            if (Math.hypot(h[4][0] - px, h[4][1] - py) < HANDLE + 5) c = Cursor.HAND;
            for (int i = 0; f.solar == null && i < 4; i++) if (Math.hypot(h[i][0] - px, h[i][1] - py) < HANDLE + 3) c = Cursor.CROSSHAIR;
        }
        if (c == Cursor.DEFAULT && hit(wx(px), wy(py)) != null) c = Cursor.MOVE;
        setCursor(c);
    }

    private void scroll(ScrollEvent e) {
        double f = Math.pow(1.0015, e.getDeltaY());
        double ns = Math.max(4, Math.min(600, scale * f));
        double x = wx(e.getX()), y = wy(e.getY());
        scale = ns;
        offX = e.getX() - x * scale;
        offY = e.getY() - y * scale;
        redraw();
    }

    // ------------------------------------------------------------------ keyboard

    private void key(KeyEvent e) {
        Editor.Tool tool = ed.tool.get();
        if (e.getCode() == KeyCode.ESCAPE) {
            if ((slabTool(tool) || tool == Editor.Tool.TERRACE || tool == Editor.Tool.FILL || tool == Editor.Tool.RETAINING_WALL)
                    && (!pts.isEmpty() || dragStart != null)) { cancelDrawing(); redraw(); }
            else if (!pts.isEmpty() || dragStart != null) finishDrawing();
            else if (tool != Editor.Tool.SELECT) ed.tool.set(Editor.Tool.SELECT);
            else ed.select(null);
            e.consume();
        } else if (e.getCode() == KeyCode.ENTER) {
            if (tool == Editor.Tool.SLAB_CIRCLE && !typed.isEmpty() && !pts.isEmpty()) {
                circleOpening(curX,curY);
            } else if (tool == Editor.Tool.ROTUNDA && !typed.isEmpty() && !pts.isEmpty()) {
                try {
                    double radius = Double.parseDouble(typed.replace(',','.'));
                    if (!Double.isFinite(radius) || radius <= 0) throw new NumberFormatException();
                    Pt c = pts.get(0);
                    curvedPoint(c.x+radius,c.y);
                } catch (NumberFormatException ex) {
                    ed.status.set(I18n.text("Érvénytelen sugár: ")+typed);
                }
            } else if (!typed.isEmpty() && !pts.isEmpty()) {
                try {
                    double len = Double.parseDouble(typed.replace(',', '.'));
                    Pt last = pts.get(pts.size() - 1);
                    double dx = curX - last.x, dy = curY - last.y, l = Math.hypot(dx, dy);
                    if (l < 1e-6) {
                        dx = 1;
                        dy = 0;
                        l = 1;
                    }
                    addPoint(last.x + dx / l * len, last.y + dy / l * len);
                } catch (NumberFormatException ex) {
                    ed.status.set(I18n.text("Érvénytelen szám: ") + typed);
                }
                typed = "";
            } else if (!pts.isEmpty()) finishDrawing();
            redraw();
            e.consume();
        } else if (e.getCode() == KeyCode.BACK_SPACE && !typed.isEmpty()) {
            typed = typed.substring(0, typed.length() - 1);
            redraw();
            e.consume();
        } else if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && tool == Editor.Tool.SELECT) {
            ed.delete(ed.selection());
            e.consume();
        } else if (tool == Editor.Tool.SELECT && e.getCode().isArrowKey() && ed.selection() != null) {
            double st = e.isShiftDown() ? 0.1 : 0.01;
            double dx = e.getCode() == KeyCode.LEFT ? -st : e.getCode() == KeyCode.RIGHT ? st : 0;
            double dy = e.getCode() == KeyCode.UP ? -st : e.getCode() == KeyCode.DOWN ? st : 0;
            String old = ed.snapshot();
            attached.clear();
            attachedObj.clear();
            Object sel = ed.selection();
            if (sel instanceof Wall w) {
                collectAttached(w.x1, w.y1, w, 1);
                collectAttached(w.x2, w.y2, w, 2);
            }
            Object o = sel;
            if (o instanceof Opening op) {
                Wall w = ed.wallOf(op);
                if (w != null) op.pos = clampPos(w, op, op.pos + (dx * w.tangentX(op.pos) + dy * w.tangentY(op.pos)));
            } else guardCurveDrag(() -> moveSelection(sel, 0, 0, dx, dy, true));
            ed.finishEdit(old);
            e.consume();
        } else if (tool == Editor.Tool.SELECT && e.getCode() == KeyCode.R && outline(ed.selection())!=null) {
            SlabOpening o=outline(ed.selection());
            ed.edit(() -> o.angle = (o.angle+(e.isShiftDown() ? 270 : 90))%360);
            e.consume();
        } else if (tool == Editor.Tool.SELECT && e.getCode() == KeyCode.R && ed.selection() instanceof Furniture f) {
            ed.checkpoint();
            f.angle = (f.angle + (e.isShiftDown() ? 270 : 90)) % 360;
            ed.changed();
            e.consume();
        }
    }

    private void keyTyped(KeyEvent e) {
        Editor.Tool tool = ed.tool.get();
        if ((tool == Editor.Tool.WALL || tool == Editor.Tool.FLOOR || tool == Editor.Tool.ROTUNDA
                || tool == Editor.Tool.SLAB_CIRCLE || tool == Editor.Tool.SLAB_POLYGON) && !pts.isEmpty()) {
            String c = e.getCharacter();
            if (c.length() == 1 && (Character.isDigit(c.charAt(0)) || c.equals(",") || c.equals("."))) {
                typed += c;
                redraw();
            }
        }
    }

    // ------------------------------------------------------------------ context menu

    private void showMenu(MouseEvent e, Object hit) {
        menu = new ContextMenu();
        double x = wx(e.getX()), y = wy(e.getY());
        if (hit instanceof Roof r) {
            menu.getItems().add(item(I18n.text("Falakhoz igazítás"),() -> ed.edit(() -> RoofFitter.refit(ed.project(),r))));
            if (!r.radial()) for (RoofElement.Type type : RoofElement.Type.values())
                    menu.getItems().add(item("+ " + I18n.display(type.label), () -> ed.addRoofElement(r,type)));
        }
        if (hit instanceof Room || hit instanceof Wall w && !w.isFence())
            menu.getItems().add(item(I18n.text("Tető erre az épületre"),ed::addRoof));
        if (hit instanceof RoofElement element) {
            menu.getItems().add(item(I18n.text("Szülő tető kijelölése"), () -> ed.select(ed.roofOf(element))));
            menu.getItems().add(item(I18n.text("Másolat"), () -> duplicate(element)));
        }
        if (hit instanceof Balcony b) menu.getItems().add(item(I18n.text("Másolat"), () -> duplicate(b)));
        if (hit instanceof RetainingWall w) menu.getItems().add(item(I18n.text("Másolat"), () -> duplicate(w)));
        if (hit instanceof SiteFill fill) {
            menu.getItems().add(item(I18n.text("Másolat"), () -> duplicate(fill)));
            menu.getItems().add(item(I18n.text("Támfal a feltöltés köré"), () -> ed.addRetainingWallsAroundFill(fill)));
        }
        if (hit instanceof SlabOpening o) {
            menu.getItems().add(item(I18n.text("Helyiség kijelölése"),() -> ed.select(ed.roomOf(o))));
            menu.getItems().add(item(I18n.text("Másolat"),() -> duplicate(o)));
        }
        if(hit instanceof FloorZone zone) {
            menu.getItems().add(item(I18n.text("Helyiség kijelölése"),() -> ed.select(ed.roomOf(zone))));
            menu.getItems().add(item(I18n.text("Másolat"),() -> duplicate(zone)));
        }
        if (hit instanceof Furniture f) {
            if (f.stair != null) menu.getItems().add(item(I18n.text("Mennyezetnyílás a lépcső fölé"),() ->
                    ed.openingForStair(f,f.stair.type == Stair.Type.SPIRAL ? SlabOpening.Shape.CIRCLE : SlabOpening.Shape.RECTANGLE)));
            menu.getItems().add(item(I18n.text("Forgatás 90°  (R)"), () -> {
                ed.checkpoint();
                f.angle = (f.angle + 90) % 360;
                ed.changed();
            }));
            menu.getItems().add(item(I18n.text("Másolat"), () -> duplicate(f)));
        }
        if (hit instanceof Wall w) {
            if(!w.isFence()) {
            menu.getItems().add(item(I18n.text("Ajtó ide"), () -> insertOpening(w, Opening.Type.DOOR, x, y)));
            menu.getItems().add(item(I18n.text("Ablak ide"), () -> insertOpening(w, Opening.Type.WINDOW, x, y)));
            menu.getItems().add(item(I18n.text("Erkélyajtó ide"), () -> insertOpening(w, Opening.Type.BALCONY_DOOR, x, y)));
            }
            menu.getItems().add(item(I18n.text("Fal kettévágása itt"), () -> ed.edit(() -> ProjectOps.splitWall(ed.project(),w,w.project(x,y)))));
            Room circle = ProjectOps.rotundaOf(ed.project(),w);
            if (circle != null) menu.getItems().add(item(I18n.text("Rotunda / sugár szerkesztése"),() -> ed.select(circle)));
        }
        if (hit instanceof LightSource l) menu.getItems().add(item(I18n.text("Másolat"), () -> {
            ed.checkpoint();
            LightSource c = new LightSource();
            c.name = l.name;
            c.x = l.x + 0.5;
            c.y = l.y + 0.5;
            c.height = l.height;
            c.power = l.power;
            c.color = l.color;
            c.radius = l.radius;
            ed.project().lights.add(c);
            ed.select(c);
            ed.changed();
        }));
        if (hit != null) {
            if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
            menu.getItems().add(item(I18n.text("Törlés  (Del)"), () -> ed.delete(hit)));
        } else {
            menu.getItems().add(item(I18n.text("Teljes terv megjelenítése"), this::fit));
        }
        menu.show(this, e.getScreenX(), e.getScreenY());
    }

    private static MenuItem item(String t, Runnable r) {
        MenuItem m = new MenuItem(t);
        m.setOnAction(a -> r.run());
        return m;
    }

    public void duplicate(Object o) {
        if(o instanceof FloorZone zone) {
            Room room=ed.roomOf(zone);
            ed.edit(() -> {
                FloorZone copy=zone.copy(); copy.outline.x+=copy.outline.width+0.1;
                room.floorZones.add(copy);
                hu.helmetsoft.homedesigner.geom.FloorLevels.validate(ed.project());
                ed.select(copy);
            });
            return;
        }
        if (o instanceof SlabOpening opening) {
            Room room = ed.roomOf(opening);
            SlabOpening copy = opening.copy(); copy.x += 0.3; copy.y += 0.3;
            ed.edit(() -> { room.slabOpenings.add(copy); ed.select(copy); });
            return;
        }
        if (o instanceof RoofElement || o instanceof Balcony || o instanceof RetainingWall || o instanceof SiteFill) {
            ed.duplicateExtension(o);
            return;
        }
        if (o instanceof Furniture f) {
            ed.checkpoint();
            Furniture c = new Furniture();
            c.name = f.name;
            c.modelPath = f.modelPath;
            c.x = f.x + 0.3;
            c.y = f.y + 0.3;
            c.elevation = f.elevation;
            c.angle = f.angle;
            c.width = f.width;
            c.depth = f.depth;
            c.height = f.height;
            c.upFix = f.upFix;
            c.mirror = f.mirror;
            c.override = f.override == null ? null : f.override.copy();
            c.stair = f.stair == null ? null : f.stair.copy();
            c.solar = f.solar == null ? null : f.solar.copy();
            c.cabinet = f.cabinet == null ? null : f.cabinet.copy();
            ed.project().furniture.add(c);
            ed.select(c);
            ed.changed();
        }
    }

    private void insertOpening(Wall w, Opening.Type t, double x, double y) {
        ed.edit(() -> ed.select(ProjectOps.addOpening(w,t,w.project(x,y))));
    }

    // ------------------------------------------------------------------ drag & drop of models

    private void dragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasString() && db.getString().startsWith(CatalogPanel.DND_PREFIX) || db.hasFiles() && db.getFiles().stream().anyMatch(f -> ModelLoader.isModelFile(f.toPath())))
            e.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
        e.consume();
    }

    private void dragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        double x = wx(e.getX()), y = wy(e.getY());
        boolean ok = false;
        if (db.hasString() && db.getString().startsWith(CatalogPanel.DND_PREFIX)) {
            ed.addFurniture(db.getString().substring(CatalogPanel.DND_PREFIX.length()), x, y);
            ok = true;
        } else if (db.hasFiles()) {
            for (File f : db.getFiles())
                if (ModelLoader.isModelFile(f.toPath())) {
                    ed.addFurniture(f.getAbsolutePath(), x, y);
                    x += 0.5;
                    ok = true;
                }
        }
        e.setDropCompleted(ok);
        e.consume();
        if (ok) ed.tool.set(Editor.Tool.SELECT);
    }

    // ------------------------------------------------------------------ hit testing

    private Object hit(double x, double y) {
        Project p = ed.project();
        double tol = 6 / scale;
        for (int i = p.rooms.size()-1; i >= 0; i--) {
            List<SlabOpening> openings = p.rooms.get(i).slabOpenings;
            for (int j = openings.size()-1; j >= 0; j--) {
                SlabOpening o = openings.get(j); double[][] boundary = SlabGeometry.boundary(o);
                for (int k = 0; k < boundary.length; k++) {
                    double[] a = boundary[k], b = boundary[(k+1)%boundary.length];
                    if (segDist(x,y,a[0],a[1],b[0],b[1]) < tol) return o;
                }
            }
        }
        for (int i = p.roofs.size()-1; i >= 0; i--) {
            Roof r = p.roofs.get(i);
            for (int j = r.elements.size()-1; j >= 0; j--) {
                RoofElement e = r.elements.get(j);
                if (x >= r.x+e.x && x <= r.x+e.x+e.width && y >= r.y+e.y && y <= r.y+e.y+e.depth) return e;
            }
        }
        for (int i = p.balconies.size()-1; i >= 0; i--)
            if (p.balconies.get(i).contains(x,y)) return p.balconies.get(i);
        for (int i = p.retainingWalls.size()-1; i >= 0; i--)
            if (p.retainingWalls.get(i).contains(x,y,tol/2)) return p.retainingWalls.get(i);
        for (int i = p.env.siteFills.size()-1; i >= 0; i--) {
            double[][] boundary = SlabGeometry.boundary(p.env.siteFills.get(i).outline);
            for (int k = 0; k < boundary.length; k++) {
                double[] a = boundary[k], b = boundary[(k+1)%boundary.length];
                if (segDist(x,y,a[0],a[1],b[0],b[1]) < tol) return p.env.siteFills.get(i);
            }
        }
        for (int i = p.lights.size() - 1; i >= 0; i--) {
            LightSource l = p.lights.get(i);
            if (Math.hypot(l.x - x, l.y - y) < 10 / scale) return l;
        }
        for (int i = p.furniture.size() - 1; i >= 0; i--) {
            Furniture f = p.furniture.get(i);
            double a = Math.toRadians(f.angle), c = Math.cos(a), s = Math.sin(a);
            double dx = x - f.x, dy = y - f.y;
            double lx = dx * c + dy * s, ly = -dx * s + dy * c;
            if (Math.abs(lx) <= f.width / 2 + tol / 2 && Math.abs(ly) <= f.depth / 2 + tol / 2) return f;
        }
        for (Wall w : p.walls)
            for (Opening o : w.openings) {
                double t = w.project(x, y);
                double d = w.distance(x, y);
                if (d < w.thickness / 2 + tol && Math.abs(t - o.pos) < o.width / 2) return o;
            }
        for (DimensionLine d : p.dimensions) {
            double[][] e = dimPoints(d);
            if (segDist(x, y, e[2][0], e[2][1], e[3][0], e[3][1]) < tol) return d;
        }
        for (Wall w : p.walls) if (w.distance(x, y) < w.thickness / 2 + tol / 2) return w;
        for (Roof r : p.roofs) {
            if (RoofOutline.distance(r,x,y) < tol) return r;
        }
        for (int i = p.rooms.size()-1; i >= 0; i--) {
            List<SlabOpening> openings = p.rooms.get(i).slabOpenings;
            for (int j = openings.size()-1; j >= 0; j--)
                if (SlabGeometry.contains(openings.get(j),x,y)) return openings.get(j);
        }
        for(int i=p.rooms.size()-1;i>=0;i--) for(FloorZone z:p.rooms.get(i).floorZones)
            if(SlabGeometry.contains(z.outline,x,y)) return z;
        for (int i = p.rooms.size() - 1; i >= 0; i--) if (p.rooms.get(i).contains(x, y)) return p.rooms.get(i);
        for (int i = p.env.siteFills.size()-1; i >= 0; i--)
            if (SlabGeometry.contains(p.env.siteFills.get(i).outline,x,y)) return p.env.siteFills.get(i);
        return null;
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
        return Math.hypot(px - ax - dx * t, py - ay - dy * t);
    }

    /** {start, end, offset start, offset end} */
    private static double[][] dimPoints(DimensionLine d) {
        double L = d.length();
        double nx = L < 1e-9 ? 0 : (d.y2 - d.y1) / L, ny = L < 1e-9 ? 0 : -(d.x2 - d.x1) / L;
        return new double[][]{{d.x1, d.y1}, {d.x2, d.y2}, {d.x1 + nx * d.offset, d.y1 + ny * d.offset}, {d.x2 + nx * d.offset, d.y2 + ny * d.offset}};
    }

    // ------------------------------------------------------------------ drawing

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setFill(BG);
        g.fillRect(0, 0, W, H);
        drawGrid(g, W, H);
        Project p = ed.project();
        Object sel = ed.selection();

        for (SiteFill fill : p.env.siteFills) drawSiteFill(g, fill, fill == sel);
        for (Room r : p.rooms) drawRoom(g, r, r == sel);
        for(Room r:p.rooms) for(FloorZone z:r.floorZones) drawFloorZone(g,r,z,z==sel);
        Map<Wall, WallGeometry.Corners> corners = WallGeometry.compute(p.walls.stream().filter(w -> !w.isFence()).toList());
        for(Wall fence:p.walls) if(fence.isFence()) corners.putAll(WallGeometry.compute(List.of(fence)));
        for (Wall w : p.walls) drawWall(g, w, corners.get(w), w == sel);
        for (Wall w : p.walls) for (Opening o : w.openings) drawOpening(g, w, o, o == sel);
        for (Room r : p.rooms) drawRoomLabel(g, r);
        for (Furniture f : p.furniture) drawFurniture(g, f, f == sel);
        for (Room r : p.rooms) for (SlabOpening o : r.slabOpenings) drawSlabOpening(g,o,o == sel);
        for (LightSource l : p.lights) drawLamp(g, l, l == sel);
        for (Roof r : p.roofs) drawRoof(g, r, r == sel);
        for (Balcony b : p.balconies) drawExtension(g,b.corners(),b.name,b.width,b.depth,b == sel,b.terrace ? Color.web("#9A7B4F") : Color.web("#598B81"));
        for (RetainingWall w : p.retainingWalls) drawRetainingWall(g, w, w == sel);
        for (Roof r : p.roofs) for (RoofElement e : r.elements)
            drawExtension(g,new double[][]{{r.x+e.x,r.y+e.y},{r.x+e.x+e.width,r.y+e.y},
                    {r.x+e.x+e.width,r.y+e.y+e.depth},{r.x+e.x,r.y+e.y+e.depth}},
                    I18n.display(e.type.label),e.width,e.depth,e == sel,e.type == RoofElement.Type.TERRACE ? Color.web("#598B81") : Color.web("#4596BA"));
        if (ed.showDimensions.get()) for (Wall w : p.walls) drawWallDimension(g, w, false);
        else if (sel instanceof Wall w) drawWallDimension(g, w, true);
        for (DimensionLine d : p.dimensions) drawDimension(g, d, d == sel);
        drawSelectionHandles(g, sel);
        drawToolPreview(g);
        drawScaleBar(g, W, H);
    }

    private void drawGrid(GraphicsContext g, double W, double H) {
        double step = scale >= 60 ? 0.1 : scale >= 15 ? 0.5 : 1;
        double x0 = Math.floor(wx(0) / step) * step, y0 = Math.floor(wy(0) / step) * step;
        g.setLineWidth(1);
        for (double x = x0; sx(x) < W; x += step) {
            boolean major = Math.abs(x - Math.round(x)) < 1e-6;
            g.setStroke(major ? GRID_MAJOR : GRID);
            double s = Math.round(sx(x)) + 0.5;
            g.strokeLine(s, 0, s, H);
        }
        for (double y = y0; sy(y) < H; y += step) {
            boolean major = Math.abs(y - Math.round(y)) < 1e-6;
            g.setStroke(major ? GRID_MAJOR : GRID);
            double s = Math.round(sy(y)) + 0.5;
            g.strokeLine(0, s, W, s);
        }
    }

    private void drawScaleBar(GraphicsContext g, double W, double H) {
        double m = scale >= 60 ? 1 : scale >= 20 ? 2 : 5;
        double len = m * scale;
        double x = 16, y = H - 18;
        g.setStroke(Color.web("#555"));
        g.setLineWidth(2);
        g.strokeLine(x, y, x + len, y);
        g.strokeLine(x, y - 4, x, y + 4);
        g.strokeLine(x + len, y - 4, x + len, y + 4);
        g.setFill(Color.web("#555"));
        g.setFont(Font.font(11));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText((int) m + " m", x + len / 2, y - 3);
    }

    private Image tintedTexture(SurfaceMaterial m) {
        if (m.albedo == null) return null;
        return tinted.computeIfAbsent(m.key, k -> {
            Texture t = m.albedo.limit(128);
            WritableImage wi = new WritableImage(t.w, t.h);
            int[] px = new int[t.argb.length];
            for (int i = 0; i < px.length; i++) {
                int c = t.argb[i];
                int r = (int) (((c >> 16) & 255) * m.color[0]), gg = (int) (((c >> 8) & 255) * m.color[1]), b = (int) ((c & 255) * m.color[2]);
                px[i] = 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, gg) << 8 | Math.min(255, b);
            }
            wi.getPixelWriter().setPixels(0, 0, t.w, t.h, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, t.w);
            return wi;
        });
    }

    private void drawRoom(GraphicsContext g, Room r, boolean selected) {
        int n = r.points.size();
        if (n < 3) return;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = sx(r.points.get(i).x);
            ys[i] = sy(r.points.get(i).y);
        }
        SurfaceMaterial m = MaterialLibrary.resolve(r.floor);
        Image img = tintedTexture(m);
        if (img != null) {
            double ts = m.tileSize * scale;
            g.setFill(new ImagePattern(img, offX, offY, ts, ts, false));
        } else g.setFill(FxUtil.color(m.color, 1));
        g.setGlobalAlpha(0.8);
        g.save();
        g.beginPath();
        g.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        for (double[][] contour : RoofOutline.contours(SlabGeometry.surface(r,false))) {
            g.moveTo(sx(contour[0][0]),sy(contour[0][1]));
            for (int i = 1; i < contour.length; i++) g.lineTo(sx(contour[i][0]),sy(contour[i][1]));
            g.closePath();
        }
        g.fill();
        if (selected) { g.setFill(Color.color(0.12,0.47,0.88,0.18)); g.fill(); }
        g.restore();
        g.setGlobalAlpha(1);
        if (selected) {
            g.setFill(Color.color(0.12, 0.47, 0.88, 0.18));
            g.setStroke(SEL);
            g.setLineWidth(2);
            g.setLineDashes(6, 4);
            g.strokePolygon(xs, ys, n);
            g.setLineDashes();
        }
    }

    private void drawRoomLabel(GraphicsContext g, Room r) {
        if (r.points.size() < 3) return;
        double[] c = r.centroid();
        double x = sx(c[0]), y = sy(c[1]);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        String a = String.format(I18n.locale(), "%.2f m²", r.area())
                +(r.elevation!=0 ? " · "+cm(r.elevation)+" cm" : "");
        g.setFont(Font.font(I18n.text("System"), FontWeight.BOLD, 13));
        g.setFill(Color.color(1, 1, 1, 0.75));
        double w = Math.max(r.name.length() * 7.5, a.length() * 6.5) + 12;
        g.fillRoundRect(x - w / 2, y - 18, w, 36, 8, 8);
        g.setFill(Color.web("#222"));
        g.fillText(r.name, x, y - 7);
        g.setFont(Font.font(I18n.text("System"), 11.5));
        g.setFill(Color.web("#444"));
        g.fillText(a, x, y + 8);
    }

    private void drawSlabOpening(GraphicsContext g, SlabOpening o, boolean selected) {
        double[][] points = SlabGeometry.boundary(o);
        g.save(); g.setStroke(selected ? SEL : Color.web("#B45309")); g.setLineWidth(selected ? 2.5 : 1.5);
        if (!o.cutFloor) g.setLineDashes(6,4);
        g.beginPath(); g.moveTo(sx(points[0][0]),sy(points[0][1]));
        for (int i = 1; i < points.length; i++) g.lineTo(sx(points[i][0]),sy(points[i][1]));
        g.closePath(); g.stroke(); g.setLineDashes();
        if (selected) label(g,sx(o.x),sy(o.y),o.name+" · "+cm(o.width)+" × "+cm(o.depth)+" cm");
        g.restore();
    }

    private void drawFloorZone(GraphicsContext g,Room room,FloorZone zone,boolean selected) {
        g.save();
        var material=MaterialLibrary.resolve(zone.finish);
        Image image=tintedTexture(material);
        g.setFill(image==null ? FxUtil.color(material.color,1)
                : new ImagePattern(image,offX,offY,material.tileSize*scale,material.tileSize*scale,false));
        g.beginPath();
        g.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        for(double[][] contour:RoofOutline.contours(hu.helmetsoft.homedesigner.geom.FloorLevels.surface(room,zone))) {
            g.moveTo(sx(contour[0][0]),sy(contour[0][1]));
            for(int i=1;i<contour.length;i++) g.lineTo(sx(contour[i][0]),sy(contour[i][1]));
            g.closePath();
        }
        g.fill(); g.setStroke(selected ? SEL : Color.web("#167A81")); g.setLineWidth(selected ? 2.5 : 1.5); g.stroke();
        label(g,sx(zone.outline.x),sy(zone.outline.y)-24,zone.name+" · "+cm(zone.offset)+" cm");
        g.restore();
    }

    private void drawWall(GraphicsContext g, Wall w, WallGeometry.Corners c, boolean selected) {
        if (c == null) return;
        double[][] poly = WallGeometry.outline(w,c);
        double[] xs = new double[poly.length], ys = new double[poly.length];
        for (int i = 0; i < poly.length; i++) {
            xs[i] = sx(poly[i][0]);
            ys[i] = sy(poly[i][1]);
        }
        g.setFill(selected ? SEL : w.isFence() ? Color.web("#92754E"):WALL);
        g.fillPolygon(xs, ys, poly.length);
        g.setStroke(selected ? SEL.darker() : Color.web("#22252A"));
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, poly.length);
        if(w.isFence()) {
            int n=Math.max(1,(int)Math.ceil(w.length()/w.fencePostSpacing));
            double size=Math.max(4,w.thickness*scale);
            for(int i=0;i<=n;i++) {
                double[] p=w.point(w.length()*i/n,0);
                g.fillRect(sx(p[0])-size/2,sy(p[1])-size/2,size,size);
                g.strokeRect(sx(p[0])-size/2,sy(p[1])-size/2,size,size);
            }
        }
    }

    private void drawOpening(GraphicsContext g, Wall w, Opening o, boolean selected) {
        if (w.curved()) {
            double lo = Math.max(0.01,o.pos-o.width/2), hi = Math.min(w.length()-0.01,o.pos+o.width/2);
            int n = Math.max(2,(int)Math.ceil((hi-lo)/w.length()*w.segments()));
            double[] xs = new double[2*(n+1)], ys = new double[xs.length];
            double offset = w.thickness/2+0.005;
            for (int i = 0; i <= n; i++) {
                double[] a = w.point(lo+(hi-lo)*i/n,offset), b = w.point(lo+(hi-lo)*i/n,-offset);
                xs[i] = sx(a[0]); ys[i] = sy(a[1]);
                xs[2*n+1-i] = sx(b[0]); ys[2*n+1-i] = sy(b[1]);
            }
            g.setFill(selected ? Color.web("#D6E8FF") : Color.WHITE);
            g.fillPolygon(xs,ys,xs.length);
            double[] a = w.point(lo,-Math.signum(w.arcAngle)*w.thickness/2), b = w.point(hi,-Math.signum(w.arcAngle)*w.thickness/2);
            Wall chord = new Wall(a[0],a[1],b[0],b[1],w.thickness,w.height);
            Opening proxy = Opening.create(o.type,chord.length()/2);
            proxy.width = chord.length(); proxy.height = o.height; proxy.flip = o.flip; proxy.mirror = o.mirror; proxy.top=o.top;
            drawOpening(g,chord,proxy,selected,false);
            double[] mid = w.point(o.pos,-offset-0.18);
            if (selected || ed.showDimensions.get() && scale > 25)
                label(g,sx(mid[0]),sy(mid[1]),cm(o.width)+I18n.text(" cm (ív)"));
            return;
        }
        drawOpening(g,w,o,selected,true);
    }

    private void drawOpening(GraphicsContext g, Wall w, Opening o, boolean selected, boolean dimension) {
        double dx = w.dirX(), dy = w.dirY(), nx = w.nx(), ny = w.ny();
        double t = w.thickness / 2 + 0.005;
        double a = o.pos - o.width / 2, b = o.pos + o.width / 2;
        double[] p0 = {w.x1 + dx * a, w.y1 + dy * a}, p1 = {w.x1 + dx * b, w.y1 + dy * b};
        double[] xs = {sx(p0[0] + nx * t), sx(p1[0] + nx * t), sx(p1[0] - nx * t), sx(p0[0] - nx * t)};
        double[] ys = {sy(p0[1] + ny * t), sy(p1[1] + ny * t), sy(p1[1] - ny * t), sy(p0[1] - ny * t)};
        g.setFill(selected ? Color.web("#D6E8FF") : Color.WHITE);
        g.fillPolygon(xs, ys, 4);
        Color col = selected ? SEL : Color.web("#30343A");
        g.setStroke(col);
        g.setLineWidth(1.2);
        // jambs
        g.strokeLine(xs[0], ys[0], xs[3], ys[3]);
        g.strokeLine(xs[1], ys[1], xs[2], ys[2]);
        switch (o.type) {
            case WINDOW -> {
                g.strokeLine(xs[0], ys[0], xs[1], ys[1]);
                g.strokeLine(xs[3], ys[3], xs[2], ys[2]);
                g.setLineWidth(2);
                g.strokeLine(sx(p0[0]), sy(p0[1]), sx(p1[0]), sy(p1[1]));
            }
            case PASSAGE -> {
                g.setLineDashes(4, 4);
                g.strokeLine(xs[0], ys[0], xs[1], ys[1]);
                g.strokeLine(xs[3], ys[3], xs[2], ys[2]);
                g.setLineDashes();
            }
            default -> {
                int side = o.flip ? -1 : 1;
                boolean balcony = o.type == Opening.Type.BALCONY_DOOR;
                if (balcony) {
                    g.setLineWidth(2);
                    g.strokeLine(sx(p0[0]), sy(p0[1]), sx(p1[0]), sy(p1[1]));
                }
                double leaf = balcony ? o.width / 2 : o.width;
                double[][] hinges = balcony ? new double[][]{{a, 1}, {b, -1}} : new double[][]{{o.mirror ? b : a, o.mirror ? -1 : 1}};
                for (double[] h : hinges) {
                    double hu = h[0], dir = h[1];
                    double hx = w.x1 + dx * hu + nx * t * side, hy = w.y1 + dy * hu + ny * t * side;
                    double ex = hx + nx * side * leaf, ey = hy + ny * side * leaf;
                    g.setLineWidth(2);
                    g.strokeLine(sx(hx), sy(hy), sx(ex), sy(ey));
                    // swing arc from leaf tip to closed position
                    double startAng = Math.toDegrees(Math.atan2(-(ny * side), nx * side));
                    double endAng = Math.toDegrees(Math.atan2(-(dy * dir), dx * dir));
                    double sweep = endAng - startAng;
                    while (sweep > 180) sweep -= 360;
                    while (sweep < -180) sweep += 360;
                    double r = leaf * scale;
                    g.setLineWidth(0.8);
                    g.setLineDashes(3, 3);
                    g.strokeArc(sx(hx) - r, sy(hy) - r, 2 * r, 2 * r, startAng, sweep, ArcType.OPEN);
                    g.setLineDashes();
                }
            }
        }
        if (dimension && (selected || ed.showDimensions.get() && scale > 25)) {
            g.setFont(Font.font(10));
            g.setFill(selected ? SEL : Color.web("#555"));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            double mx = (p0[0] + p1[0]) / 2 - nx * (t + 0.18), my = (p0[1] + p1[1]) / 2 - ny * (t + 0.18);
            String s = cm(o.width) + (o.type == Opening.Type.WINDOW ? " / " + cm(o.height) : "")
                    +(o.top!=Opening.Top.RECTANGLE ? " · "+I18n.display(o.top.label) : "");
            g.fillText(s, sx(mx), sy(my));
        }
    }

    private void drawFurniture(GraphicsContext g, Furniture f, boolean selected) {
        Image img = null;
        if (f.modelPath != null || f.stair != null || f.cabinet != null || f.solar != null) {
            Texture t = ModelLibrary.placedTop(f);
            if (t != null) img = FxUtil.image(t);
        }
        g.save();
        g.translate(sx(f.x), sy(f.y));
        g.rotate(f.angle);
        double w = f.width * scale, d = f.depth * scale;
        if (img != null) {
            if (f.mirror) g.scale(-1, 1);
            g.drawImage(img, -w / 2, -d / 2, w, d);
            if (f.mirror) g.scale(-1, 1);
        } else {
            g.setFill(Color.color(0.85, 0.82, 0.76, 0.85));
            g.fillRoundRect(-w / 2, -d / 2, w, d, 6, 6);
            g.setStroke(Color.web("#8A8170"));
            g.setLineWidth(1);
            g.strokeRoundRect(-w / 2, -d / 2, w, d, 6, 6);
            String err = f.modelPath != null ? ModelLibrary.error(f.modelPath) : null;
            g.setFill(err != null ? Color.web("#B91C1C") : Color.web("#555"));
            g.setFont(Font.font(10));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(err != null ? I18n.text("Hiba!") : I18n.text("betöltés…"), 0, 0);
        }
        if (selected) {
            g.setStroke(SEL);
            g.setLineWidth(1.5);
            g.setLineDashes(5, 3);
            g.strokeRect(-w / 2, -d / 2, w, d);
            g.setLineDashes();
        }
        if (f.stair != null) {
            g.setStroke(selected ? SEL : Color.web("#333333")); g.setLineWidth(1.5);
            double r = Math.min(w,d)*0.28;
            if (f.stair.type == Stair.Type.STRAIGHT) {
                g.strokeLine(0,-r,0,r); g.strokeLine(0,r,-5,r-8); g.strokeLine(0,r,5,r-8);
            } else {
                double sign = f.stair.clockwise ? 1 : -1;
                g.strokeArc(-r,-r,2*r,2*r,90,-sign*240,javafx.scene.shape.ArcType.OPEN);
            }
        }
        g.restore();
    }

    private void drawLamp(GraphicsContext g, LightSource l, boolean selected) {
        double x = sx(l.x), y = sy(l.y);
        Color c = FxUtil.parse(l.color, Color.web("#FFE2B8"));
        g.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.35));
        g.fillOval(x - 14, y - 14, 28, 28);
        g.setFill(Color.web("#FACC15"));
        g.fillOval(x - 6, y - 6, 12, 12);
        g.setStroke(selected ? SEL : Color.web("#7C5E00"));
        g.setLineWidth(selected ? 2.5 : 1.2);
        g.strokeOval(x - 6, y - 6, 12, 12);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.strokeLine(x + Math.cos(a) * 8, y + Math.sin(a) * 8, x + Math.cos(a) * 12, y + Math.sin(a) * 12);
        }
    }

    private void drawExtension(GraphicsContext g, double[][] c, String name, double w, double d, boolean selected, Color color) {
        double[] xs = new double[4], ys = new double[4];
        for (int i = 0; i < 4; i++) { xs[i] = sx(c[i][0]); ys[i] = sy(c[i][1]); }
        g.setFill(Color.color(color.getRed(),color.getGreen(),color.getBlue(),0.23));
        g.fillPolygon(xs,ys,4);
        g.setStroke(selected ? SEL : color);
        g.setLineWidth(selected ? 2.5 : 1.5);
        g.strokePolygon(xs,ys,4);
        g.setFont(Font.font(10));
        g.setFill(selected ? SEL : Color.web("#24516B"));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(name,(xs[0]+xs[2])/2,(ys[0]+ys[2])/2,Math.max(30,w*scale));
        if (selected || ed.showDimensions.get()) {
            dimLine(g,c[0][0],c[0][1],c[1][0],c[1][1],w,color,selected);
            dimLine(g,c[1][0],c[1][1],c[2][0],c[2][1],d,color,selected);
        }
    }

    private void drawSiteFill(GraphicsContext g, SiteFill fill, boolean selected) {
        double[][] b = SlabGeometry.boundary(fill.outline);
        double[] xs = new double[b.length], ys = new double[b.length];
        for (int i = 0; i < b.length; i++) { xs[i] = sx(b[i][0]); ys[i] = sy(b[i][1]); }
        Color c = Color.web("#5E8C3A");
        g.setFill(Color.color(c.getRed(),c.getGreen(),c.getBlue(),0.16));
        g.fillPolygon(xs,ys,b.length);
        g.setStroke(selected ? SEL : c);
        g.setLineWidth(selected ? 2.5 : 1.5);
        if (!fill.retaining) g.setLineDashes(8,5);
        g.strokePolygon(xs,ys,b.length);
        g.setLineDashes();
        g.setFont(Font.font(10));
        g.setFill(selected ? SEL : Color.web("#3D5E25"));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(fill.name+"  ▲ "+String.format(I18n.locale(),"%+.2f m",fill.elevation),sx(fill.outline.x),sy(fill.outline.y),Math.max(40,fill.outline.width*scale));
    }

    private void drawRetainingWall(GraphicsContext g, RetainingWall w, boolean selected) {
        double[][] c = w.corners();
        double[] xs = new double[4], ys = new double[4];
        for (int i = 0; i < 4; i++) { xs[i] = sx(c[i][0]); ys[i] = sy(c[i][1]); }
        Color color = Color.web("#7A7068");
        g.setFill(Color.color(color.getRed(),color.getGreen(),color.getBlue(),0.65));
        g.fillPolygon(xs,ys,4);
        g.setStroke(selected ? SEL : Color.web("#4A433D"));
        g.setLineWidth(selected ? 2.5 : 1.2);
        g.strokePolygon(xs,ys,4);
        if (selected || ed.showDimensions.get()) dimLine(g,w.x1,w.y1,w.x2,w.y2,w.length(),color,selected);
    }

    private void drawRoof(GraphicsContext g, Roof r, boolean selected) {
        g.setStroke(selected ? SEL : Color.color(0.7, 0.2, 0.15, 0.55));
        g.setLineWidth(selected ? 2 : 1.2);
        g.setLineDashes(8, 5);
        double o = r.overhang;
        strokeRoofOutline(g,RoofOutline.boundary(r,true));
        g.setLineDashes(2, 4);
        strokeRoofOutline(g,RoofOutline.boundary(r,false));
        g.setLineDashes();
        if (r.radial()) {
            double cx = r.x+r.w/2, cy = r.y+r.d/2;
            g.strokeOval(sx(cx)-3,sy(cy)-3,6,6);
            if (selected) label(g,sx(cx),sy(cy)-16,I18n.display(r.type.label)+" · R "+meters(r.w/2));
            return;
        }
        if (r.type == Roof.Type.GABLE || r.type == Roof.Type.HIP) {
            g.setLineWidth(1.5);
            if (r.ridgeAlongX) {
                double y = r.y + r.d / 2, inset = r.type == Roof.Type.HIP ? r.d / 2 : -o;
                roofLine(g,r,r.x+inset,y,r.x+r.w-inset,y);
            } else {
                double x = r.x + r.w / 2, inset = r.type == Roof.Type.HIP ? r.w / 2 : -o;
                roofLine(g,r,x,r.y+inset,x,r.y+r.d-inset);
            }
        }

    }

    private void strokeRoofOutline(GraphicsContext g, double[][] outline) {
        double[] xs = new double[outline.length], ys = new double[outline.length];
        for (int i = 0; i < outline.length; i++) { xs[i] = sx(outline[i][0]); ys[i] = sy(outline[i][1]); }
        g.strokePolygon(xs,ys,outline.length);
    }

    private void roofLine(GraphicsContext g, Roof r, double ax, double ay, double bx, double by) {
        TreeSet<Double> cuts = new TreeSet<>();
        cuts.add(0.0); cuts.add(1.0);
        double dx = bx-ax, dy = by-ay;
        double[][] outline = RoofOutline.boundary(r,true);
        var outlinePath = RoofOutline.path(outline);
        for (int i = 0; i < outline.length; i++) {
            double[] a = outline[i], b = outline[(i+1)%outline.length];
            double ex = b[0]-a[0], ey = b[1]-a[1], det = dx*ey-dy*ex;
            if (Math.abs(det) < 1e-10) continue;
            double t = ((a[0]-ax)*ey-(a[1]-ay)*ex)/det;
            double u = ((a[0]-ax)*dy-(a[1]-ay)*dx)/det;
            if (t > 0 && t < 1 && u >= 0 && u <= 1) cuts.add(t);
        }
        Double last = null;
        for (double t : cuts) {
            if (last != null && outlinePath.contains(ax+dx*(last+t)/2,ay+dy*(last+t)/2))
                g.strokeLine(sx(ax+dx*last),sy(ay+dy*last),sx(ax+dx*t),sy(ay+dy*t));
            last = t;
        }
    }

    private void drawWallDimension(GraphicsContext g, Wall w, boolean strong) {
        double L = w.length();
        if (L < 0.05) return;
        double off = w.thickness / 2 + 14 / scale;
        // place the dimension on the side facing outside when the finish says so, else left
        int side = ProjectOps.isExterior(w.right) ? -1 : 1;
        if (w.curved()) {
            g.setStroke(strong ? SEL : Color.web("#6B7280"));
            g.setLineWidth(strong ? 1.2 : 0.8);
            double[] last = null;
            for (int i = 0; i <= w.segments(); i++) {
                double[] q = w.point(L*i/w.segments(),side*off);
                if (last != null) g.strokeLine(sx(last[0]),sy(last[1]),sx(q[0]),sy(q[1]));
                last = q;
            }
            double[] mid = w.point(L/2,side*(off+12/scale));
            String text = meters(L)+I18n.text(" ív")+(strong || ed.selection() == w ? " · R "+meters(w.radius()) : "");
            double angle = Math.toDegrees(Math.atan2(w.tangentY(L/2),w.tangentX(L/2)));
            if (angle > 90 || angle < -90) angle += 180;
            g.save();
            g.translate(sx(mid[0]),sy(mid[1]));
            g.rotate(angle);
            g.setFont(Font.font(I18n.text("System"),strong ? FontWeight.BOLD : FontWeight.NORMAL,11));
            g.setFill(Color.color(0.965,0.96,0.95,0.94));
            g.fillRoundRect(-text.length()*3.2-4,-9,text.length()*6.4+8,18,5,5);
            g.setFill(strong ? SEL : Color.web("#6B7280"));
            g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
            g.fillText(text,0,0);
            g.restore();
            return;
        }
        double nx = w.nx() * side, ny = w.ny() * side;
        double ax = w.x1 + nx * off, ay = w.y1 + ny * off, bx = w.x2 + nx * off, by = w.y2 + ny * off;
        dimLine(g, ax, ay, bx, by, L, strong ? SEL : Color.web("#6B7280"), strong);
    }

    private void drawDimension(GraphicsContext g, DimensionLine d, boolean selected) {
        double[][] e = dimPoints(d);
        g.setStroke(selected ? SEL : DIM);
        g.setLineWidth(0.8);
        if (Math.abs(d.offset) > 1e-6) {
            g.strokeLine(sx(e[0][0]), sy(e[0][1]), sx(e[2][0]), sy(e[2][1]));
            g.strokeLine(sx(e[1][0]), sy(e[1][1]), sx(e[3][0]), sy(e[3][1]));
        }
        dimLine(g, e[2][0], e[2][1], e[3][0], e[3][1], d.length(), selected ? SEL : DIM, true);
    }

    private void dimLine(GraphicsContext g, double ax, double ay, double bx, double by, double len, Color c, boolean strong) {
        double x1 = sx(ax), y1 = sy(ay), x2 = sx(bx), y2 = sy(by);
        double L = Math.hypot(x2 - x1, y2 - y1);
        if (L < 12) return;
        double ux = (x2 - x1) / L, uy = (y2 - y1) / L;
        g.setStroke(c);
        g.setLineWidth(strong ? 1.2 : 0.8);
        g.strokeLine(x1, y1, x2, y2);
        // architectural ticks
        double tk = 5;
        g.setLineWidth(strong ? 1.8 : 1.2);
        g.strokeLine(x1 - (ux - uy) * tk, y1 - (uy + ux) * tk, x1 + (ux - uy) * tk, y1 + (uy + ux) * tk);
        g.strokeLine(x2 - (ux - uy) * tk, y2 - (uy + ux) * tk, x2 + (ux - uy) * tk, y2 + (uy + ux) * tk);
        String s = meters(len);
        double ang = Math.toDegrees(Math.atan2(uy, ux));
        if (ang > 90.01 || ang < -89.99) ang += 180;
        g.save();
        g.translate((x1 + x2) / 2, (y1 + y2) / 2);
        g.rotate(ang);
        g.setFont(Font.font(I18n.text("System"), strong ? FontWeight.BOLD : FontWeight.NORMAL, strong ? 12 : 10.5));
        double tw = s.length() * (strong ? 7 : 6) + 6;
        if (tw < L - 8) {
            g.setFill(Color.color(0.965, 0.96, 0.95, 0.9));
            g.fillRect(-tw / 2, -8, tw, 15);
            g.setFill(c);
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(s, 0, 0);
        }
        g.restore();
    }

    static String meters(double m) {
        return String.format(I18n.locale(), "%.2f m", m);
    }

    static String cm(double m) {
        return Math.round(m * 100) + "";
    }

    private void drawSelectionHandles(GraphicsContext g, Object sel) {
        if (sel == null) return;
        if (outline(sel)!=null) {
            SlabOpening o=outline(sel);
            double[][] h = openingHandles(o);
            if (o.shape == SlabOpening.Shape.POLYGON) {
                for (double[] point : SlabGeometry.boundary(o)) handle(g,sx(point[0]),sy(point[1]));
            } else for (int i = 0; i < 4; i++) handle(g,h[i][0],h[i][1]);
            double[] mid = o.point(0.5,1);
            g.setStroke(SEL); g.setLineWidth(1); g.strokeLine(sx(mid[0]),sy(mid[1]),h[4][0],h[4][1]);
            handle(g,h[4][0],h[4][1]);
        } else if (sel instanceof Wall w) {
            if (w.rotundaId != null) return;
            handle(g, sx(w.x1), sy(w.y1));
            handle(g, sx(w.x2), sy(w.y2));
            double[] mid = w.point(w.length()/2,0);
            handle(g,sx(mid[0]),sy(mid[1]));
        } else if (sel instanceof Room r) {
            if (r.rotundaId != null) {
                handle(g,sx(r.circleX+r.circleRadius),sy(r.circleY));
                label(g,sx(r.circleX),sy(r.circleY)-32,"R "+meters(r.circleRadius));
            } else for (Pt q : r.points) handle(g, sx(q.x), sy(q.y));
        } else if (sel instanceof Furniture f) {
            double[][] h = furnitureHandles(f);
            for (int i = 0; f.solar == null && i < 4; i++) handle(g, h[i][0], h[i][1]);
            double[][] c = furnitureHandles(f);
            double mx = (c[2][0] + c[3][0]) / 2, my = (c[2][1] + c[3][1]) / 2;
            g.setStroke(SEL);
            g.setLineWidth(1);
            g.strokeLine(mx, my, h[4][0], h[4][1]);
            g.setFill(Color.WHITE);
            g.fillOval(h[4][0] - 7, h[4][1] - 7, 14, 14);
            g.strokeOval(h[4][0] - 7, h[4][1] - 7, 14, 14);
            g.setFont(Font.font(11));
            g.setFill(SEL);
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText("↻", h[4][0], h[4][1]);
            // size label
            String s = cm(f.width) + " × " + cm(f.depth) + " × " + cm(f.height) + " cm";
            g.setFont(Font.font(I18n.text("System"), FontWeight.BOLD, 11));
            double tx = sx(f.x), ty = Math.min(Math.min(h[0][1], h[1][1]), Math.min(h[2][1], h[3][1])) - 12;
            double tw = s.length() * 6.4 + 10;
            g.setFill(Color.color(1, 1, 1, 0.9));
            g.fillRoundRect(tx - tw / 2, ty - 9, tw, 18, 6, 6);
            g.setFill(SEL);
            g.fillText(s, tx, ty);
        } else if (sel instanceof RetainingWall w) {
            handle(g, sx(w.x1), sy(w.y1));
            handle(g, sx(w.x2), sy(w.y2));
        } else if (sel instanceof DimensionLine d) {
            double[][] e = dimPoints(d);
            handle(g, sx(e[2][0]), sy(e[2][1]));
            handle(g, sx(e[3][0]), sy(e[3][1]));
        } else if (sel instanceof Roof r) {
            handle(g, sx(r.x), sy(r.y));
            handle(g, sx(r.x + r.w), sy(r.y));
            handle(g, sx(r.x + r.w), sy(r.y + r.d));
            handle(g, sx(r.x), sy(r.y + r.d));
        }
    }

    private void handle(GraphicsContext g, double x, double y) {
        g.setFill(Color.WHITE);
        g.fillRect(x - HANDLE, y - HANDLE, HANDLE * 2, HANDLE * 2);
        g.setStroke(SEL);
        g.setLineWidth(1.5);
        g.strokeRect(x - HANDLE, y - HANDLE, HANDLE * 2, HANDLE * 2);
    }

    private void drawToolPreview(GraphicsContext g) {
        Editor.Tool tool = ed.tool.get();
        Project p = ed.project();
        g.setLineCap(StrokeLineCap.ROUND);
        if (tool == Editor.Tool.ROTUNDA && !pts.isEmpty()) {
            Pt c = pts.get(0);
            double radius = Math.hypot(curX-c.x,curY-c.y);
            if (!typed.isEmpty()) {
                try { radius = Double.parseDouble(typed.replace(',','.')); }
                catch (NumberFormatException ex) { label(g,sx(c.x),sy(c.y)-20,I18n.text("Érvénytelen sugár")); return; }
            }
            if (!Double.isFinite(radius) || radius <= 0) return;
            g.setFill(Color.color(0.12,0.47,0.88,0.1));
            g.fillOval(sx(c.x-radius),sy(c.y-radius),2*radius*scale,2*radius*scale);
            g.setStroke(SEL);
            g.setLineWidth(Math.max(2,p.wallThickness*scale));
            g.strokeOval(sx(c.x-radius),sy(c.y-radius),2*radius*scale,2*radius*scale);
            g.setLineWidth(1);
            g.strokeLine(sx(c.x),sy(c.y),sx(c.x+radius),sy(c.y));
            label(g,sx(c.x),sy(c.y)-20,"R "+meters(radius)+" · Ø "+meters(2*radius));
        }
        if (tool == Editor.Tool.ARC_WALL && !pts.isEmpty()) {
            g.setStroke(SEL);
            g.setLineWidth(Math.max(2,p.wallThickness*scale));
            if (pts.size() == 1) {
                Pt a = pts.get(0);
                g.strokeLine(sx(a.x),sy(a.y),sx(curX),sy(curY));
                label(g,sx(curX),sy(curY)-20,I18n.text("2. pont: az ív köztes pontja"));
            } else {
                try {
                    Wall arc = Wall.through(pts.get(0),pts.get(1),new Pt(curX,curY),p.wallThickness,p.wallHeight);
                    double[] last = arc.point(0,0);
                    for (int i = 1; i <= arc.segments(); i++) {
                        double[] q = arc.point(arc.length()*i/arc.segments(),0);
                        g.strokeLine(sx(last[0]),sy(last[1]),sx(q[0]),sy(q[1]));
                        last = q;
                    }
                    double[] mid = arc.point(arc.length()/2,0);
                    label(g,sx(mid[0]),sy(mid[1])-20,"R "+meters(arc.radius())+I18n.text(" · ív ")+meters(arc.length()));
                } catch (IllegalArgumentException ex) {
                    label(g,sx(curX),sy(curY)-20,ex.getMessage());
                }
            }
            for (Pt q : pts) handle(g,sx(q.x),sy(q.y));
        }
        if (tool == Editor.Tool.SLAB_CIRCLE && !pts.isEmpty()) {
            Pt c = pts.getFirst();
            double radius = Math.hypot(curX-c.x,curY-c.y);
            if (!typed.isEmpty()) {
                try { radius = Double.parseDouble(typed.replace(',','.')); }
                catch (NumberFormatException ex) { ed.status.set(I18n.text("Érvénytelen sugár: ")+typed); }
            }
            g.setStroke(SEL); g.setLineWidth(2);
            g.strokeOval(sx(c.x-radius),sy(c.y-radius),2*radius*scale,2*radius*scale);
            label(g,sx(c.x),sy(c.y)-20,"R "+meters(radius)+" · Ø "+meters(2*radius));
        }
        if ((tool == Editor.Tool.WALL || tool == Editor.Tool.FLOOR || tool == Editor.Tool.SLAB_POLYGON) && !pts.isEmpty()) {
            g.setStroke(tool == Editor.Tool.WALL ? Color.color(0.12, 0.47, 0.88, 0.6) : Color.color(0.1, 0.6, 0.3, 0.8));
            g.setLineWidth(tool == Editor.Tool.WALL ? Math.max(2, p.wallThickness * scale) : 2);
            Pt last = pts.get(pts.size() - 1);
            double tx = curX, ty = curY;
            if (!typed.isEmpty()) {
                try {
                    double len = Double.parseDouble(typed.replace(',', '.'));
                    double dx = curX - last.x, dy = curY - last.y, l = Math.hypot(dx, dy);
                    if (l > 1e-6) {
                        tx = last.x + dx / l * len;
                        ty = last.y + dy / l * len;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (tool == Editor.Tool.FLOOR || tool == Editor.Tool.SLAB_POLYGON)
                for (int i = 1; i < pts.size(); i++) g.strokeLine(sx(pts.get(i - 1).x), sy(pts.get(i - 1).y), sx(pts.get(i).x), sy(pts.get(i).y));
            g.strokeLine(sx(last.x), sy(last.y), sx(tx), sy(ty));
            double L = Math.hypot(tx - last.x, ty - last.y);
            label(g, sx((last.x + tx) / 2), sy((last.y + ty) / 2) - 16, typed.isEmpty() ? meters(L) : typed + " m ⏎");
            for (Pt q : pts) {
                g.setFill(Color.WHITE);
                g.fillOval(sx(q.x) - 4, sy(q.y) - 4, 8, 8);
            }
        }
        if (tool == Editor.Tool.DIMENSION && !pts.isEmpty()) {
            Pt a = pts.get(0);
            dimLine(g, a.x, a.y, curX, curY, Math.hypot(curX - a.x, curY - a.y), DIM, true);
        }
        if (tool == Editor.Tool.RETAINING_WALL && !pts.isEmpty()) {
            Pt a = pts.get(0);
            g.setStroke(Color.color(0.48, 0.44, 0.41, 0.8));
            g.setLineWidth(Math.max(2, 0.25 * scale));
            g.strokeLine(sx(a.x), sy(a.y), sx(curX), sy(curY));
            label(g, sx((a.x + curX) / 2), sy((a.y + curY) / 2) - 16, meters(Math.hypot(curX - a.x, curY - a.y)));
        }
        if ((tool == Editor.Tool.TERRACE || tool == Editor.Tool.FILL) && dragStart != null) {
            double x0 = Math.min(dragStart.x, curX), x1 = Math.max(dragStart.x, curX);
            double y0 = Math.min(dragStart.y, curY), y1 = Math.max(dragStart.y, curY);
            Color c = tool == Editor.Tool.FILL ? Color.web("#5E8C3A") : Color.web("#9A7B4F");
            g.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.18));
            g.setStroke(c);
            g.setLineWidth(2);
            if (tool == Editor.Tool.FILL && fillEllipse) {
                g.fillOval(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
                g.strokeOval(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
            } else {
                g.fillRect(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
                g.strokeRect(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
            }
            label(g, sx((x0 + x1) / 2), sy(y0) - 16, meters(x1 - x0));
            label(g, sx(x1) + 34, sy((y0 + y1) / 2), meters(y1 - y0));
        }
        if ((tool == Editor.Tool.ROOM || tool == Editor.Tool.SLAB_RECT || tool == Editor.Tool.SLAB_ELLIPSE || tool==Editor.Tool.FLOOR_LEVEL) && dragStart != null) {
            double x0 = Math.min(dragStart.x, curX), x1 = Math.max(dragStart.x, curX);
            double y0 = Math.min(dragStart.y, curY), y1 = Math.max(dragStart.y, curY);
            g.setFill(Color.color(0.12, 0.47, 0.88, 0.12));
            g.fillRect(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
            g.setStroke(Color.color(0.12, 0.47, 0.88, 0.7));
            g.setLineWidth(tool == Editor.Tool.ROOM ? Math.max(2,p.wallThickness*scale) : 2);
            if (tool == Editor.Tool.SLAB_ELLIPSE) g.strokeOval(sx(x0),sy(y0),(x1-x0)*scale,(y1-y0)*scale);
            else g.strokeRect(sx(x0), sy(y0), (x1 - x0) * scale, (y1 - y0) * scale);
            label(g, sx((x0 + x1) / 2), sy(y0) - 16, meters(x1 - x0));
            label(g, sx(x1) + 34, sy((y0 + y1) / 2), meters(y1 - y0));
            label(g, sx((x0 + x1) / 2), sy((y0 + y1) / 2), String.format(I18n.locale(), "%.2f m²", (x1 - x0) * (y1 - y0)));
        }
        if (tool == Editor.Tool.DOOR || tool == Editor.Tool.WINDOW) {
            Wall w = ProjectOps.nearestWall(p, curX, curY, 0.25 + 8 / scale);
            if (w != null) {
                Opening o = Opening.create(tool == Editor.Tool.DOOR ? Opening.Type.DOOR : Opening.Type.WINDOW, 0);
                o.pos = clampPos(w, o, w.project(curX, curY));
                g.setGlobalAlpha(0.6);
                drawOpening(g, w, o, true);
                g.setGlobalAlpha(1);
            }
        }
        if (tool != Editor.Tool.SELECT && tool != Editor.Tool.DOOR && tool != Editor.Tool.WINDOW && tool != Editor.Tool.LAMP) {
            double x = sx(curX), y = sy(curY);
            g.setStroke(snappedPoint ? Color.web("#16A34A") : SEL);
            g.setLineWidth(1.5);
            if (snappedPoint) g.strokeRect(x - 6, y - 6, 12, 12);
            g.strokeLine(x - 10, y, x + 10, y);
            g.strokeLine(x, y - 10, x, y + 10);
        }
    }

    private void label(GraphicsContext g, double x, double y, String s) {
        g.setFont(Font.font(I18n.text("System"), FontWeight.BOLD, 12));
        double w = s.length() * 7.2 + 10;
        g.setFill(Color.color(0.12, 0.47, 0.88, 0.92));
        g.fillRoundRect(x - w / 2, y - 10, w, 20, 8, 8);
        g.setFill(Color.WHITE);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(s, x, y);
    }

    /** Clears cached tinted floor textures (after material edits). */
    public void clearCaches() {
        tinted.clear();
    }
}
