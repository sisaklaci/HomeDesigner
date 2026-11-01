package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import com.google.gson.Gson;
import hu.helmetsoft.homedesigner.geom.FurnitureTransform;
import hu.helmetsoft.homedesigner.geom.GeoPart;
import hu.helmetsoft.homedesigner.geom.Mesh;
import hu.helmetsoft.homedesigner.geom.StructureBuilder;
import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ModelLoader;
import hu.helmetsoft.homedesigner.io.WalkVideo;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;

import java.io.File;
import java.util.*;

/** Realtime 3D view: orbit or walk-through camera, picking and furniture dragging. */
public final class View3D extends StackPane {
    private final Editor ed;
    private final Group root = new Group();
    private final Group world = new Group();
    private final Group structure = new Group(), furniture = new Group(), overlay3d = new Group();
    private final SubScene sub;
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final DirectionalLight sun = new DirectionalLight(), fill = new DirectionalLight();
    private final AmbientLight ambient = new AmbientLight();
    private final Canvas labels = new Canvas();

    public final CheckBox showRoof = new CheckBox(I18n.text("Tető")), showCeiling = new CheckBox(I18n.text("Mennyezet")), showDims = new CheckBox(I18n.text("Méretek"));
    private final ToggleButton walkBtn = new ToggleButton(I18n.text("🚶 Séta"));
    private final Button recordBtn = new Button(I18n.text("REC")), stopBtn = new Button(I18n.text("Stop"));
    private final Label recordStatus = new Label(I18n.text("MP4 • 15 kép/mp"));
    private final Button reset = new Button();
    private java.nio.file.Path lastSavedVideo;
    private boolean lastVideoFailed;
    private WalkVideo recording;
    private java.util.concurrent.CompletableFuture<java.nio.file.Path> recordingResult;
    private long recordingStart, nextFrame;
    private boolean finishingVideo;

    // camera state (world: Y up)
    private double tx = 5, ty = 1, tz = 4, dist = 16, yaw = 35, pitch = -38;
    private double ex = 3, ey = 1.6, ez = 3;
    private double walkFloor;
    private Project cameraProject;
    private final double fov = 55;
    private boolean walk;

    private String structureSig = "";
    private final Map<Object, List<MeshView>> ownerViews = new IdentityHashMap<>();
    private final Map<String, javafx.scene.shape.TriangleMesh> overrideMeshes = new HashMap<>();
    private final Set<KeyCode> keys = EnumSet.noneOf(KeyCode.class);
    private static final Set<KeyCode> MOVE_KEYS = EnumSet.of(KeyCode.W, KeyCode.A, KeyCode.S, KeyCode.D, KeyCode.Q, KeyCode.E,
            KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.PAGE_UP, KeyCode.PAGE_DOWN);
    private boolean hover;
    private final javafx.event.EventHandler<KeyEvent> keyDown = this::keyDown, keyUp = this::keyUp;

    private double pressX, pressY, lastX, lastY;
    private MouseButton pressButton;
    private final Label help = new Label();
    private static final String ORBIT_HELP = "Bal egér: forgatás • Jobb/középső: mozgatás • Görgő: nagyítás • Bútor húzható • Dupla katt: fókusz";
    private static final String WALK_HELP = "Séta: W/S/fel/le: előre-hátra • A/D: oldalazás • ←/→ vagy egérhúzás: fordulás balra/jobbra (mint FPS játékban) • Görgő: előre/hátra • Q/E: le/fel";
    private boolean dragged;
    private Furniture dragF;
    private double dragOffX, dragOffY;
    private String before;

    public View3D(Editor ed) {
        this.ed = ed;
        world.getTransforms().add(new Rotate(180, Rotate.X_AXIS));
        world.getChildren().addAll(structure, furniture, overlay3d);
        ambient.setColor(Color.gray(0.45));
        fill.setColor(Color.gray(0.28));
        root.getChildren().addAll(world, ambient, sun, fill, camera);
        sub = new SubScene(root, 400, 300, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.web("#BFD6EE"));
        sub.setCamera(camera);
        camera.setNearClip(0.05);
        camera.setFarClip(3000);
        camera.setFieldOfView(fov);
        sub.widthProperty().bind(widthProperty());
        sub.heightProperty().bind(heightProperty());
        labels.widthProperty().bind(widthProperty());
        labels.heightProperty().bind(heightProperty());
        labels.setMouseTransparent(true);
        setMinSize(100, 100);

        showRoof.setSelected(true);
        showCeiling.setSelected(true);
        showDims.setSelected(true);
        for (CheckBox c : List.of(showRoof, showCeiling, showDims)) {
            c.setFocusTraversable(false);
            c.selectedProperty().addListener(o -> rebuild(true));
        }
        walkBtn.setFocusTraversable(false);
        walkBtn.setTooltip(new Tooltip(I18n.text("Séta mód: W/A/S/D vagy nyilak a mozgáshoz, egérhúzással (bal vagy jobb gomb) fordulás")));
        walkBtn.selectedProperty().addListener((o, a, b) -> {
            setWalk(b);
            help.setText(I18n.text(b ? WALK_HELP : ORBIT_HELP));
        });
        reset.setText(I18n.text("⟲ Nézet"));
        reset.setFocusTraversable(false);
        reset.setTooltip(new Tooltip(I18n.text("Kamera alaphelyzetbe (az egész ház látszik)")));
        reset.setOnAction(e -> {
            walkBtn.setSelected(false);
            resetCamera();
        });
        HBox cameraBar = new HBox(10, walkBtn, reset, showRoof, showCeiling, showDims);
        cameraBar.getChildren().forEach(n -> ((Region) n).setMinWidth(Region.USE_PREF_SIZE));
        cameraBar.setAlignment(Pos.CENTER_LEFT);
        recordBtn.setId("walk-record");
        stopBtn.setId("walk-stop");
        recordStatus.setId("walk-record-status");
        recordBtn.setStyle("-fx-text-fill: #b71919; -fx-font-weight: bold;");
        recordBtn.setFocusTraversable(false);
        stopBtn.setFocusTraversable(false);
        stopBtn.setDisable(true);
        recordBtn.setTooltip(new Tooltip(I18n.text("Sétafelvétel MP4 videóba, hang és kezelőpanelek nélkül")));
        recordBtn.setOnAction(e -> chooseRecording());
        stopBtn.setOnAction(e -> stopRecording());
        HBox videoBar = new HBox(10, recordBtn, stopBtn, recordStatus);
        videoBar.setAlignment(Pos.CENTER_LEFT);
        VBox bar = new VBox(5, cameraBar, videoBar);
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.setMaxHeight(Region.USE_PREF_SIZE);
        bar.setMaxWidth(Region.USE_PREF_SIZE);
        bar.setStyle("-fx-background-color: rgba(255,255,255,0.82); -fx-background-radius: 8;");
        StackPane.setAlignment(bar, Pos.TOP_LEFT);
        StackPane.setMargin(bar, new Insets(8));
        help.setText(I18n.text(ORBIT_HELP));
        help.setStyle("-fx-background-color: rgba(0,0,0,0.45); -fx-text-fill: white; -fx-padding: 3 8; -fx-background-radius: 6; -fx-font-size: 11;");
        help.setMouseTransparent(true);
        StackPane.setAlignment(help, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(help, new Insets(8));
        getChildren().addAll(sub, labels, bar, help);

        ed.onChange(() -> rebuild(false));
        ed.onSelection(() -> rebuild(false));
        ed.onProject(() -> {
            structureSig = "";
            if(cameraProject!=ed.project()) resetCamera();
        });
        widthProperty().addListener(o -> drawLabels());
        heightProperty().addListener(o -> drawLabels());

        setFocusTraversable(true);
        sub.setOnMousePressed(this::pressed);
        sub.setOnMouseDragged(this::dragged);
        sub.setOnMouseReleased(this::released);
        sub.setOnScroll(this::scroll);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) ed.delete(ed.selection());
        });
        // Movement keys are caught at scene level so walking works no matter which panel has the focus
        // (except while typing into a field or using a list/combo box).
        sceneProperty().addListener((o, oldSc, sc) -> {
            if (oldSc != null) {
                oldSc.removeEventFilter(KeyEvent.KEY_PRESSED, keyDown);
                oldSc.removeEventFilter(KeyEvent.KEY_RELEASED, keyUp);
            }
            if (sc != null) {
                sc.addEventFilter(KeyEvent.KEY_PRESSED, keyDown);
                sc.addEventFilter(KeyEvent.KEY_RELEASED, keyUp);
                sc.windowProperty().addListener((o2, a, w) -> {
                    if (w != null) w.focusedProperty().addListener((o3, x, f) -> {
                        if (!f) keys.clear();
                    });
                });
            }
        });
        setOnMouseEntered(e -> hover = true);
        setOnMouseExited(e -> hover = false);
        setOnDragOver(this::dragOver);
        setOnDragDropped(this::dragDropped);
        new AnimationTimer() {
            long last;

            @Override
            public void handle(long now) {
                double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
                last = now;
                if (!keys.isEmpty()) moveKeys(dt);
                if (recording!=null && !finishingVideo) {
                    long elapsed=now-recordingStart;
                    recordStatus.setText(String.format(I18n.locale(), I18n.text("REC %02d:%02d"),elapsed/1_000_000_000L/60,elapsed/1_000_000_000L%60));
                    if(now>=nextFrame && recording.canAccept()) {
                        try { recording.offer(toAwt(snapshot()),Math.max(0,elapsed)); }
                        catch(RuntimeException ex) {
                            finishingVideo=true;
                            recording.abort(new java.io.IOException(I18n.text("A videó képrögzítése megszakadt"),ex));
                        }
                        nextFrame=now+1_000_000_000L/WalkVideo.FPS;
                    }
                }
            }
        }.start();
        resetCamera();
        rebuild(true);
    }

    void localize() {
        showRoof.setText(I18n.text("Tető"));
        showCeiling.setText(I18n.text("Mennyezet"));
        showDims.setText(I18n.text("Méretek"));
        walkBtn.setText(I18n.text("🚶 Séta"));
        walkBtn.setTooltip(new Tooltip(I18n.text("Séta mód: W/A/S/D vagy nyilak a mozgáshoz, egérhúzással (bal vagy jobb gomb) fordulás")));
        reset.setText(I18n.text("⟲ Nézet"));
        reset.setTooltip(new Tooltip(I18n.text("Kamera alaphelyzetbe (az egész ház látszik)")));
        recordBtn.setText(I18n.text("REC"));
        stopBtn.setText(I18n.text("Stop"));
        recordBtn.setTooltip(new Tooltip(I18n.text("Sétafelvétel MP4 videóba, hang és kezelőpanelek nélkül")));
        recordStatus.setText(lastVideoFailed ? I18n.text("Sikertelen mentés")
                : lastSavedVideo != null ? I18n.text("Mentve: ") + lastSavedVideo.getFileName() : I18n.text("MP4 • 15 kép/mp"));
        help.setText(I18n.text(walk ? WALK_HELP : ORBIT_HELP));
        drawLabels();
    }

    // ------------------------------------------------------------------ camera

    public void resetCamera() {
        cameraProject=ed.project();
        Project all=ed.project().worldProject();
        double[] b = all.wallBounds();
        double furnitureHeight = 0;
        if (b == null && (!all.furniture.isEmpty() || !all.rooms.isEmpty())) {
            b = new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(Room room:all.rooms) for(Pt point:room.points) {
                b[0]=Math.min(b[0],point.x); b[1]=Math.min(b[1],point.y);
                b[2]=Math.max(b[2],point.x); b[3]=Math.max(b[3],point.y);
            }
            for (Furniture f : all.furniture) {
                double a = Math.toRadians(f.angle), c = Math.abs(Math.cos(a)), s = Math.abs(Math.sin(a));
                double dx = (c*f.width+s*f.depth)/2, dz = (s*f.width+c*f.depth)/2;
                b[0] = Math.min(b[0],f.x-dx); b[1] = Math.min(b[1],f.y-dz);
                b[2] = Math.max(b[2],f.x+dx); b[3] = Math.max(b[3],f.y+dz);
                furnitureHeight = Math.max(furnitureHeight,f.elevation+hu.helmetsoft.homedesigner.geom.StairGeometry.totalHeight(f));
            }
            for(BuildingLevel level:ed.project().levels)
                furnitureHeight=Math.max(furnitureHeight,level.baseHeight+ed.project().wallHeight);
        }
        if (b == null || !Double.isFinite(b[0])) b = new double[]{0, 0, 8, 6};
        tx = (b[0] + b[2]) / 2;
        tz = (b[1] + b[3]) / 2;
        ty = Math.max(0.8,furnitureHeight/2);
        double ext = Math.max(furnitureHeight,Math.max(b[2] - b[0], b[3] - b[1]));
        dist = Math.max(6, ext * 1.45 + 3);
        yaw = 30;
        pitch = -40;
        walk = false;
        if (walkBtn.isSelected()) walkBtn.setSelected(false);
        updateCamera();
    }

    private void setWalk(boolean w) {
        requestFocus();
        if (w == walk) return;
        walk = w;
        if (w) {
            Room r = ed.selection() instanceof Room rr ? rr : ed.project().rooms.isEmpty() ? null : ed.project().rooms.get(0);
            if (r != null) {
                double[] c = r.centroid();
                ex = c[0];
                ez = c[1];
            } else {
                ex = tx;
                ez = tz;
            }
            walkFloor=hu.helmetsoft.homedesigner.geom.FloorLevels.walkHeight(ed.project(),ex,ez);
            ey = walkFloor+1.6;
            pitch = -8;
            showCeiling.setSelected(true);
        } else {
            double[] f = fwd();
            tx = ex + f[0] * 4;
            ty = Math.max(0.5, ey + f[1] * 4);
            tz = ez + f[2] * 4;
            dist = 9;
            pitch = -35;
        }
        updateCamera();
    }

    private double[] fwd() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch), cp = Math.cos(p);
        return new double[]{cp * Math.sin(y), Math.sin(p), -cp * Math.cos(y)};
    }

    /** Eye position in world coordinates. */
    public double[] eye() {
        if (walk) return new double[]{ex, ey, ez};
        double[] f = fwd();
        return new double[]{tx - f[0] * dist, ty - f[1] * dist, tz - f[2] * dist};
    }

    public double yaw() {
        return yaw;
    }

    public double pitch() {
        return pitch;
    }

    public double fov() {
        return fov;
    }

    public double aspect() {
        return getWidth() > 0 && getHeight() > 0 ? getWidth() / getHeight() : 1.6;
    }

    private double[][] basis() {
        double[] f = fwd();
        double rx = -f[2], rz = f[0], rl = Math.hypot(rx, rz);
        if (rl < 1e-9) {
            rx = Math.cos(Math.toRadians(yaw));
            rz = Math.sin(Math.toRadians(yaw));
            rl = 1;
        }
        double[] r = {rx / rl, 0, rz / rl};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        return new double[][]{r, u, f};
    }

    private void updateCamera() {
        pitch = Math.max(-89, Math.min(89, pitch));
        double[] e = eye();
        double[][] b = basis();
        double[] r = b[0], u = b[1], f = b[2];
        // world -> fx: (x, -y, -z); camera axes X = right, Y = down, Z = forward
        Affine a = new Affine(
                r[0], -u[0], f[0], e[0],
                -r[1], u[1], -f[1], -e[1],
                -r[2], u[2], -f[2], -e[2]);
        camera.getTransforms().setAll(a);
        drawLabels();
    }

    /** True if the 3D view should receive movement keys (not while typing into a field or using a list). */
    private boolean wantsKeys(KeyEvent e) {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) return false;
        if (getScene() == null) return false;
        Node f = getScene().getFocusOwner();
        if (f instanceof TextInputControl || f instanceof ComboBoxBase<?> || f instanceof ListView<?> || f instanceof Slider
                || f instanceof Spinner<?> || f instanceof ChoiceBox<?>) return false;
        // arrows nudge the selection in the plan view; leave them there unless the mouse is over the 3D view
        if (f instanceof PlanView && !hover && e.getCode().isArrowKey()) return false;
        return walk || hover || isFocused() || f == null;
    }

    private void keyDown(KeyEvent e) {
        if (e.getCode() == KeyCode.SHIFT) {
            keys.add(KeyCode.SHIFT);
            return;
        }
        if (!MOVE_KEYS.contains(e.getCode()) || !wantsKeys(e)) return;
        keys.add(e.getCode());
        e.consume();
    }

    private void keyUp(KeyEvent e) {
        if (keys.remove(e.getCode()) && e.getCode() != KeyCode.SHIFT) e.consume();
    }

    private void moveKeys(double dt) {
        if (keys.size() == 1 && keys.contains(KeyCode.SHIFT)) return;
        double sp = (keys.contains(KeyCode.SHIFT) ? 6 : 2.5) * dt;
        double y = Math.toRadians(yaw);
        double fx = Math.sin(y), fz = -Math.cos(y), rx = Math.cos(y), rz = Math.sin(y);
        double mx = 0, mz = 0, my = 0;
        if (keys.contains(KeyCode.W) || keys.contains(KeyCode.UP)) { mx += fx; mz += fz; }
        if (keys.contains(KeyCode.S) || keys.contains(KeyCode.DOWN)) { mx -= fx; mz -= fz; }
        if (keys.contains(KeyCode.D)) { mx += rx; mz += rz; }
        if (keys.contains(KeyCode.A)) { mx -= rx; mz -= rz; }
        if (keys.contains(KeyCode.E) || keys.contains(KeyCode.PAGE_UP)) my += 1;
        if (keys.contains(KeyCode.Q) || keys.contains(KeyCode.PAGE_DOWN)) my -= 1;
        // FPS style: ← turns the view to the left, → to the right
        if (keys.contains(KeyCode.LEFT)) yaw -= 70 * dt;
        if (keys.contains(KeyCode.RIGHT)) yaw += 70 * dt;
        if (walk) {
            moveWalk(mx*sp,mz*sp,my*sp);
        } else {
            double k = Math.max(1, dist / 8);
            tx += mx * sp * k;
            tz += mz * sp * k;
            ty = Math.max(0, ty + my * sp * k);
        }
        updateCamera();
    }

    private void moveWalk(double dx,double dz,double dy) {
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(dx,dz)/0.05));
        double oldFloor=walkFloor;
        for(int i=0;i<steps;i++) {
            ex+=dx/steps; ez+=dz/steps;
            walkFloor=hu.helmetsoft.homedesigner.geom.FloorLevels.walkHeight(ed.project(),ex,ez,walkFloor);
        }
        ey=Math.max(walkFloor+0.15,ey+walkFloor-oldFloor+dy);
    }

    // ------------------------------------------------------------------ rays

    /** World-space ray direction through a screen point. */
    private double[] ray(double mx, double my) {
        double W = getWidth(), H = getHeight();
        double t = Math.tan(Math.toRadians(fov) / 2);
        double sx = (2 * mx / W - 1) * t * W / H, sy = (1 - 2 * my / H) * t;
        double[][] b = basis();
        double[] d = new double[3];
        for (int i = 0; i < 3; i++) d[i] = b[2][i] + b[0][i] * sx + b[1][i] * sy;
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d[0] /= l;
        d[1] /= l;
        d[2] /= l;
        return d;
    }

    /** Intersection with the horizontal plane y=h as plan coordinates, or null. */
    private double[] planeHit(double mx, double my, double h) {
        double[] e = eye(), d = ray(mx, my);
        if (Math.abs(d[1]) < 1e-6) return null;
        double t = (h - e[1]) / d[1];
        if (t <= 0 || t > 500) return null;
        return new double[]{e[0] + d[0] * t, e[2] + d[2] * t};
    }

    private double[] project(double x, double y, double z) {
        double[] e = eye();
        double[][] b = basis();
        double vx = x - e[0], vy = y - e[1], vz = z - e[2];
        double zf = vx * b[2][0] + vy * b[2][1] + vz * b[2][2];
        if (zf < 0.1) return null;
        double W = getWidth(), H = getHeight();
        double t = Math.tan(Math.toRadians(fov) / 2);
        double px = (vx * b[0][0] + vy * b[0][1] + vz * b[0][2]) / (zf * t * W / H);
        double py = (vx * b[1][0] + vy * b[1][1] + vz * b[1][2]) / (zf * t);
        return new double[]{W / 2 + px * W / 2, H / 2 - py * H / 2};
    }

    // ------------------------------------------------------------------ mouse

    private Object pickOwner(MouseEvent e) {
        Node n = e.getPickResult().getIntersectedNode();
        while (n != null && n != world) {
            if (n.getUserData() != null) return n.getUserData();
            n = n.getParent();
        }
        return null;
    }

    private void pressed(MouseEvent e) {
        requestFocus();
        pressX = lastX = e.getX();
        pressY = lastY = e.getY();
        pressButton = e.getButton();
        dragged = false;
        dragF = null;
        if (e.getButton() == MouseButton.PRIMARY && !walk) {
            Object o = pickOwner(e);
            if (o instanceof Furniture f) {
                ed.select(f);
                double[] h = planeHit(e.getX(), e.getY(), f.elevation+ed.project().baseOf(f));
                if (h != null) {
                    dragF = f;
                    dragOffX = f.x - h[0];
                    dragOffY = f.y - h[1];
                    before = ed.snapshot();
                }
            }
        }
    }

    private void dragged(MouseEvent e) {
        double dx = e.getX() - lastX, dy = e.getY() - lastY;
        if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 3) dragged = true;
        lastX = e.getX();
        lastY = e.getY();
        if (!dragged) return;
        // getButton() is unreliable for drag events, so use the button recorded on press
        MouseButton btn = pressButton;
        if (btn == null || btn == MouseButton.NONE)
            btn = e.isPrimaryButtonDown() ? MouseButton.PRIMARY : e.isSecondaryButtonDown() ? MouseButton.SECONDARY : MouseButton.MIDDLE;
        if (dragF != null && btn == MouseButton.PRIMARY) {
            if (ed.selection() != dragF) ed.select(dragF);
            double[] h = planeHit(e.getX(), e.getY(), dragF.elevation+ed.project().baseOf(dragF));
            if (h != null) {
                dragF.x = h[0] + dragOffX;
                dragF.y = h[1] + dragOffY;
                ed.changedNow();
            }
            return;
        }
        // in walk mode both left and right drag turn the head in place, FPS mouse-look style
        if (walk && (btn == MouseButton.PRIMARY || btn == MouseButton.SECONDARY)) {
            yaw += dx * 0.25;
            pitch -= dy * 0.25;
            updateCamera();
        } else if (btn == MouseButton.PRIMARY) {
            yaw += dx * 0.35;
            pitch -= dy * 0.3;
            updateCamera();
        } else {
            double[][] b = basis();
            if (walk) {
                moveWalk(-b[0][0]*dx*0.01,-b[0][2]*dx*0.01,dy*0.01);
            } else {
                double k = dist * 0.0018;
                tx -= (b[0][0] * dx - b[1][0] * dy) * k;
                ty = Math.max(-2, ty + b[1][1] * dy * k);
                tz -= (b[0][2] * dx - b[1][2] * dy) * k;
            }
            updateCamera();
        }
    }

    private void released(MouseEvent e) {
        if (dragF != null && dragged) {
            ed.commit(before);
            ed.changed();
        } else if (!dragged && e.getButton() == MouseButton.PRIMARY) {
            Object o = pickOwner(e);
            ed.select(o);
            if (e.getClickCount() == 2 && o != null) focusOn(e);
        }
        dragF = null;
        before = null;
    }

    private void focusOn(MouseEvent e) {
        var r = e.getPickResult();
        if (r.getIntersectedNode() == null) return;
        javafx.geometry.Point3D p = r.getIntersectedNode().localToScene(r.getIntersectedPoint());
        javafx.geometry.Point3D w = world.sceneToLocal(p);
        if (walk) return;
        tx = w.getX();
        ty = w.getY();
        tz = w.getZ();
        dist = Math.max(2.5, dist * 0.6);
        updateCamera();
    }

    private void scroll(ScrollEvent e) {
        if (walk) {
            double[] f = fwd();
            double s = e.getDeltaY() * 0.01;
            moveWalk(f[0]*s,f[2]*s,0);
        } else {
            dist = Math.max(1, Math.min(400, dist * Math.pow(0.9985, e.getDeltaY())));
        }
        updateCamera();
    }

    private void dragOver(DragEvent e) {
        Dragboard db = e.getDragboard();
        if (db.hasString() && db.getString().startsWith(CatalogPanel.DND_PREFIX) || db.hasFiles() && db.getFiles().stream().anyMatch(f -> ModelLoader.isModelFile(f.toPath())))
            e.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
        e.consume();
    }

    private void dragDropped(DragEvent e) {
        Dragboard db = e.getDragboard();
        double[] h = planeHit(e.getX(), e.getY(), ed.project().activeLevel().baseHeight);
        var pick=e.getPickResult();
        if(pick!=null && pick.getIntersectedNode() instanceof MeshView mesh
                && (mesh.getUserData() instanceof Room || mesh.getUserData() instanceof FloorZone)) {
            var point=world.sceneToLocal(mesh.localToScene(pick.getIntersectedPoint()));
            h=new double[]{point.getX(),point.getZ()};
        }
        if (h == null) h = new double[]{tx, tz};
        boolean ok = false;
        if (db.hasString() && db.getString().startsWith(CatalogPanel.DND_PREFIX)) {
            ed.addFurniture(db.getString().substring(CatalogPanel.DND_PREFIX.length()), h[0], h[1]);
            ok = true;
        } else if (db.hasFiles()) {
            for (File f : db.getFiles())
                if (ModelLoader.isModelFile(f.toPath())) {
                    ed.addFurniture(f.getAbsolutePath(), h[0], h[1]);
                    h[0] += 0.5;
                    ok = true;
                }
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    // ------------------------------------------------------------------ scene

    private static final Gson GSON = new Gson();

    /** Rebuilds the scene; the static structure only when it actually changed. */
    public void rebuild(boolean force) {
        Project p = ed.project();
        Environment env = p.env;
        Object[] levels=p.levels.stream().map(l -> new Object[]{l.id,l.baseHeight,l.walls,l.rooms,l.roofs,l.balconies,l.retainingWalls,l.lights}).toArray();
        String sig = GSON.toJson(new Object[]{levels, env, showRoof.isSelected(), showCeiling.isSelected()});
        if (force || !sig.equals(structureSig)) {
            structureSig = sig;
            buildStructure(p);
        }
        updateLights(env);
        buildFurniture(p.worldProject());
        highlight();
        drawLabels();
    }

    private void buildStructure(Project p) {
        StructureBuilder.Options o = new StructureBuilder.Options();
        o.ceilings = showCeiling.isSelected();
        o.roofs = showRoof.isSelected();
        o.ground = true;
        o.lamps = true;
        List<GeoPart> parts = StructureBuilder.build(p, o);
        List<Node> nodes = new ArrayList<>();
        ownerViews.clear();
        for (GeoPart g : parts) {
            MeshView mv = new MeshView(FxUtil.mesh(g.mesh, null));
            mv.setMaterial(FxUtil.material(g.material));
            mv.setCullFace(CullFace.NONE);
            Object owner = g.owner instanceof Project || g.owner instanceof Environment ? null : g.owner;
            mv.setUserData(owner);
            if (owner == null) mv.setUserData(null);
            if (owner != null) ownerViews.computeIfAbsent(owner, k -> new ArrayList<>()).add(mv);
            nodes.add(mv);
        }
        structure.getChildren().setAll(nodes);
    }

    private void updateLights(Environment env) {
        double az = Math.toRadians(env.sunAzimuth), el = Math.toRadians(Math.max(2, env.sunElevation));
        // direction toward the sun in world coords (azimuth 0 = north = -z, clockwise)
        double sx = Math.sin(az) * Math.cos(el), sy = Math.sin(el), sz = -Math.cos(az) * Math.cos(el);
        // light travels from the sun: -s, converted to fx coords (x, -y, -z)
        sun.setDirection(new javafx.geometry.Point3D(-sx, sy, sz));
        double i = Math.min(1.2, 0.75 * env.sunIntensity + 0.1);
        sun.setColor(Color.color(Math.min(1, i), Math.min(1, i * 0.96), Math.min(1, i * 0.9)));
        fill.setDirection(new javafx.geometry.Point3D(sx * 0.6, 0.5, -sz * 0.6));
        ambient.setColor(Color.gray(Math.min(0.8, 0.38 + 0.12 * env.skyIntensity)));
    }

    private void buildFurniture(Project p) {
        List<Node> nodes = new ArrayList<>();
        for (Furniture f : p.furniture) {
            ModelData md = ModelLibrary.placed(f);
            if (md == null) {
                Box b = new Box(f.width, f.height, f.depth);
                PhongMaterial m = new PhongMaterial(Color.color(0.8, 0.78, 0.72, 0.6));
                b.setMaterial(m);
                b.getTransforms().addAll(new javafx.scene.transform.Translate(f.x, f.elevation + f.height / 2, f.y), new Rotate(-f.angle, Rotate.Y_AXIS));
                b.setUserData(p.sourceOf(f));
                nodes.add(b);
                continue;
            }
            double[] m = FurnitureTransform.matrix(md, f);
            Group g = new Group();
            g.setUserData(p.sourceOf(f));
            g.getTransforms().add(new Affine(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11]));
            SurfaceMaterial ov = f.override != null ? MaterialLibrary.resolve(f.override) : null;
            int idx = 0;
            for (ModelData.Part part : md.parts) {
                Mesh mesh = part.viewMesh();
                MeshView mv;
                if (ov != null) {
                    String key = System.identityHashCode(mesh) + "|" + ov.tileSize;
                    mv = new MeshView(overrideMeshes.computeIfAbsent(key, k -> {
                        float[] uv = FurnitureTransform.boxUv(mesh);
                        float s = (float) (1 / Math.max(1e-3, ov.tileSize));
                        for (int i = 0; i < uv.length; i++) uv[i] *= s;
                        return FxUtil.mesh(mesh, uv);
                    }));
                    mv.setMaterial(FxUtil.material(ov));
                } else {
                    mv = new MeshView(FxUtil.mesh(mesh));
                    mv.setMaterial(FxUtil.material(part.material));
                }
                mv.setCullFace(CullFace.NONE);
                g.getChildren().add(mv);
                idx++;
            }
            nodes.add(g);
        }
        furniture.getChildren().setAll(nodes);
    }

    private final Map<MeshView, javafx.scene.paint.Material> highlighted = new IdentityHashMap<>();

    private void highlight() {
        for (var e : highlighted.entrySet()) e.getKey().setMaterial(e.getValue());
        highlighted.clear();
        overlay3d.getChildren().clear();
        Object sel = ed.selection();
        if (sel == null) return;
        if (sel instanceof SlabOpening opening) {
            Room room = ed.roomOf(opening);
            if (room == null) return;
            double[][] boundary = hu.helmetsoft.homedesigner.geom.SlabGeometry.boundary(opening);
            List<Double> heights = new ArrayList<>();
            double base=ed.project().activeLevel().baseHeight;
            if (opening.cutFloor) heights.add(base+room.elevation+0.015);
            if (opening.cutCeiling && room.hasCeiling && showCeiling.isSelected())
                heights.add(base+(room.ceilingHeight > 0 ? room.ceilingHeight : ed.project().maxWallHeight())-0.015);
            PhongMaterial material = new PhongMaterial(Color.web("#1F78E0"));
            for (double h : heights) for (int i = 0; i < boundary.length; i++) {
                double[] a = boundary[i], b = boundary[(i+1)%boundary.length];
                Box edge = new Box(Math.hypot(b[0]-a[0],b[1]-a[1]),0.015,0.015);
                edge.setMaterial(material); edge.setMouseTransparent(true);
                edge.getTransforms().addAll(new javafx.scene.transform.Translate((a[0]+b[0])/2,h,(a[1]+b[1])/2),
                        new Rotate(-Math.toDegrees(Math.atan2(b[1]-a[1],b[0]-a[0])),Rotate.Y_AXIS));
                overlay3d.getChildren().add(edge);
            }
            return;
        }
        if (sel instanceof Furniture f) {
            double totalHeight = hu.helmetsoft.homedesigner.geom.StairGeometry.totalHeight(f);
            Box b = new Box(f.width + 0.04, totalHeight + 0.04, f.depth + 0.04);
            b.setDrawMode(DrawMode.LINE);
            b.setMaterial(new PhongMaterial(Color.web("#1F78E0")));
            b.getTransforms().addAll(new javafx.scene.transform.Translate(f.x, ed.project().baseOf(f)+f.elevation + totalHeight / 2, f.y), new Rotate(-f.angle, Rotate.Y_AXIS));
            b.setMouseTransparent(true);
            overlay3d.getChildren().add(b);
            return;
        }
        Object owner = sel instanceof Opening o ? ed.wallOf(o) : sel;
        List<MeshView> views = ownerViews.get(owner);
        if (views == null) return;
        for (MeshView mv : views) {
            javafx.scene.paint.Material old = mv.getMaterial();
            highlighted.put(mv, old);
            PhongMaterial hm = new PhongMaterial();
            if (old instanceof PhongMaterial pm) {
                hm.setDiffuseMap(pm.getDiffuseMap());
                Color c = pm.getDiffuseColor();
                hm.setDiffuseColor(c.interpolate(Color.web("#5AA2FF"), 0.45));
            } else hm.setDiffuseColor(Color.web("#5AA2FF"));
            mv.setMaterial(hm);
        }
    }

    // ------------------------------------------------------------------ dimension labels

    private void drawLabels() {
        GraphicsContext g = labels.getGraphicsContext2D();
        g.clearRect(0, 0, labels.getWidth(), labels.getHeight());
        if (!showDims.isSelected() || getWidth() < 10) return;
        Project p = ed.project();
        double base=p.activeLevel().baseHeight;
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        // under a visible roof wall/room labels would float over the tiles
        boolean covered = showRoof.isSelected() && !p.roofs.isEmpty() && !walk;
        for (Wall w : p.walls) {
            if (covered && !w.isFence() || w.length() < 0.3) continue;
            java.util.function.DoubleBinaryOperator top=(x,z) ->
                    w.isFence() ? (w.fenceFollowTerrain ? hu.helmetsoft.homedesigner.geom.SiteGeometry.height(p,x,z)+0.01:base)
                            +w.fenceElevation+w.height+0.03:base+w.height+0.03;
            double[] a = project(w.x1, top.applyAsDouble(w.x1,w.y1), w.y1), b = project(w.x2, top.applyAsDouble(w.x2,w.y2), w.y2);
            if (a == null || b == null) continue;
            if (Math.hypot(a[0] - b[0], a[1] - b[1]) < 40) continue;
            g.setStroke(Color.color(0.9, 0.35, 0.1, 0.9));
            g.setLineWidth(1.2);
            if (w.curved()) {
                double[] last = a;
                for (int i = 1; i <= w.segments(); i++) {
                    double[] q = w.point(w.length()*i/w.segments(),0), next = project(q[0],top.applyAsDouble(q[0],q[1]),q[1]);
                    if (last != null && next != null) g.strokeLine(last[0],last[1],next[0],next[1]);
                    last = next;
                }
            } else g.strokeLine(a[0], a[1], b[0], b[1]);
            g.strokeOval(a[0] - 2, a[1] - 2, 4, 4);
            g.strokeOval(b[0] - 2, b[1] - 2, 4, 4);
            double[] mid = w.point(w.length()/2,0), label = project(mid[0],top.applyAsDouble(mid[0],mid[1]),mid[1]);
            if (label != null) tag(g,label[0],label[1],PlanView.meters(w.length())+(w.curved() ? I18n.text(" (ív)") : ""),Color.web("#C2410C"));
        }
        if (ed.selection() instanceof Furniture f) {
            double[] c = project(f.x, base+f.elevation + hu.helmetsoft.homedesigner.geom.StairGeometry.totalHeight(f) + 0.12, f.y);
            if (c != null) tag(g, c[0], c[1], PlanView.cm(f.width) + " × " + PlanView.cm(f.depth) + " × " + PlanView.cm(f.height)
                    + (f.stair != null ? I18n.text(" cm (szintmagasság)") : " cm"), Color.web("#1F78E0"));
        }
        if (ed.selection() instanceof SlabOpening opening) {
            Room room = ed.roomOf(opening);
            if (room != null) {
                double h = opening.cutCeiling && room.hasCeiling && showCeiling.isSelected()
                        ? (room.ceilingHeight > 0 ? room.ceilingHeight : p.maxWallHeight())+0.1 : room.elevation+0.1;
                double[] c = project(opening.x,base+h,opening.y);
                if (c != null) tag(g,c[0],c[1],opening.name+" · "+PlanView.cm(opening.width)+" × "+PlanView.cm(opening.depth)+" cm",Color.web("#1F78E0"));
            }
        }
        if (ed.selection() instanceof Balcony b) {
            double[] mid = b.point(b.width/2,b.depth/2);
            double[] c = project(mid[0],base+b.elevation+b.railingHeight+0.12,mid[1]);
            if (c != null) tag(g,c[0],c[1],PlanView.cm(b.width)+" × "+PlanView.cm(b.depth)+" cm",Color.web("#1F78E0"));
        }
        if(ed.selection() instanceof FloorZone zone) {
            Room room=ed.roomOf(zone);
            double[] c=project(zone.outline.x,base+room.elevation+zone.offset+0.1,zone.outline.y);
            if(c!=null) tag(g,c[0],c[1],zone.name+" · "+PlanView.cm(zone.offset)+" cm",Color.web("#1F78E0"));
        }
        if (showRoof.isSelected() && ed.selection() instanceof RoofElement e) {
            Roof r = ed.roofOf(e);
            if (r != null) {
                double x = r.x+e.x+e.width/2, z = r.y+e.y+e.depth/2;
                var roofGeometry = new hu.helmetsoft.homedesigner.geom.RoofGeometry(r);
                double h = roofGeometry.heightAt(x,z)+roofGeometry.thickness+0.12;
                if (e.type == RoofElement.Type.DORMER) h += e.height+Math.max(e.width,e.depth)*0.3;
                double[] c = project(x,base+h,z);
                if (c != null) tag(g,c[0],c[1],PlanView.cm(e.width)+" × "+PlanView.cm(e.depth)+I18n.text(" cm (alaprajz)"),Color.web("#1F78E0"));
            }
        }
        for (Room r : p.rooms) {
            if (covered || r.points.size() < 3) continue;
            double[] c = r.centroid();
            double[] s = project(c[0], base+r.elevation+0.05, c[1]);
            if (s != null) tag(g, s[0], s[1], String.format(I18n.locale(), "%s  %.1f m²", r.name, r.area()), Color.web("#374151"));
        }
    }

    private static void tag(GraphicsContext g, double x, double y, String s, Color c) {
        g.setFont(Font.font(I18n.text("System"), FontWeight.BOLD, 11));
        double w = s.length() * 6.6 + 10;
        g.setFill(Color.color(1, 1, 1, 0.85));
        g.fillRoundRect(x - w / 2, y - 9, w, 18, 8, 8);
        g.setFill(c);
        g.fillText(s, x, y);
    }

    public WritableImage snapshot() {
        return sub.snapshot(null, null);
    }

    public boolean isWalk() {
        return walk;
    }

    private void chooseRecording() {
        javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();
        chooser.setTitle(I18n.text("Sétafelvétel mentése MP4 videóként"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(I18n.text("MP4 videó"),"*.mp4"));
        chooser.setInitialFileName("seta.mp4");
        File file=chooser.showSaveDialog(getScene().getWindow());
        if(file==null) return;
        java.nio.file.Path path=file.toPath();
        if(!file.getName().toLowerCase(Locale.ROOT).endsWith(".mp4"))
            path=path.resolveSibling(file.getName()+".mp4");
        if(java.nio.file.Files.exists(path)) {
            Boolean overwrite=Dialogs.yesNoCancel(I18n.text("Meglévő videó felülírása"),path.toString());
            if(!Boolean.TRUE.equals(overwrite)) return;
        }
        try { startRecording(path); }
        catch(java.io.IOException ex) { Dialogs.error(I18n.text("A videófelvétel nem indítható"),ex.toString()); }
    }

    void startRecording(java.nio.file.Path path) throws java.io.IOException {
        if(recording!=null) throw new IllegalStateException(I18n.text("Már folyamatban van egy videófelvétel."));
        double scale=Math.min(1,Math.min(960/sub.getWidth(),540/sub.getHeight()));
        int w=Math.max(2,((int)(sub.getWidth()*scale)/2)*2);
        int h=Math.max(2,((int)(sub.getHeight()*scale)/2)*2);
        recording=new WalkVideo(path,w,h);
        lastSavedVideo=null;
        lastVideoFailed=false;
        recordingResult=new java.util.concurrent.CompletableFuture<>();
        var completed=recordingResult;
        recordingStart=System.nanoTime(); nextFrame=recordingStart;
        finishingVideo=false;
        recordBtn.setDisable(true); stopBtn.setDisable(false);
        recordStatus.setText(I18n.text("REC 00:00"));
        recording.completion().whenComplete((saved,error) -> javafx.application.Platform.runLater(() -> {
            recording=null; finishingVideo=false;
            recordBtn.setDisable(false); stopBtn.setDisable(true);
            if(error!=null) {
                lastVideoFailed=true;
                recordStatus.setText(I18n.text("Sikertelen mentés"));
                Dialogs.error(I18n.text("A videó nem menthető"),error.toString());
                completed.completeExceptionally(error);
            } else {
                lastSavedVideo=saved;
                recordStatus.setText(I18n.text("Mentve: ")+saved.getFileName());
                recordStatus.setTooltip(new Tooltip(saved.toString()));
                completed.complete(saved);
            }
        }));
        walkBtn.setSelected(true);
        try { recording.offer(toAwt(snapshot()),0); }
        catch(RuntimeException ex) {
            finishingVideo=true;
            recording.abort(new java.io.IOException(I18n.text("Az első videókép nem rögzíthető"),ex));
        }
        requestFocus();
    }

    java.util.concurrent.CompletableFuture<java.nio.file.Path> stopRecording() {
        if(recording==null) throw new IllegalStateException(I18n.text("Nincs aktív videófelvétel."));
        if(!finishingVideo) {
            finishingVideo=true;
            stopBtn.setDisable(true); recordStatus.setText(I18n.text("MP4 mentése..."));
            recording.stop(System.nanoTime()-recordingStart);
        }
        requestFocus();
        return recordingResult;
    }

    public boolean canCloseVideo() {
        if(recording==null) return true;
        Dialogs.error(I18n.text("A videófelvétel még folyamatban van"),
                I18n.text("Kilépés előtt nyomd meg a 3D nézet Stop gombját, majd várd meg a „Mentve” jelzést."));
        return false;
    }

    private static java.awt.image.BufferedImage toAwt(javafx.scene.image.Image img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] pixels=((java.awt.image.DataBufferInt)bi.getRaster().getDataBuffer()).getData();
        img.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        return bi;
    }

    /** Test hook: saves snapshots of the 3D view while turning. */
    void debugTurnShots(String base) {
        Room r = ed.project().rooms.isEmpty() ? null : ed.project().rooms.get(0);
        walkBtn.setSelected(true);
        if (r != null) {
            double[] c = r.centroid();
            ex = c[0];
            ez = c[1];
        }
        ey = 1.6;
        pitch = -5;
        yaw = 20;
        updateCamera();
        shot(base + "_1_start.png");
        double y0 = yaw;
        keys.add(KeyCode.LEFT);
        moveKeys(0.3);
        keys.clear();
        System.out.println("LEFT arrow: yaw " + y0 + " -> " + yaw);
        shot(base + "_2_afterLEFTkey.png");
        y0 = yaw;
        debugDrag(MouseButton.PRIMARY, -120);
        System.out.println("left mouse drag to the left: yaw " + y0 + " -> " + yaw + " eye " + java.util.Arrays.toString(eye()));
        shot(base + "_3_afterDragLeft.png");
    }

    private void shot(String f) {
        try {
            javax.imageio.ImageIO.write(toAwt(sub.snapshot(null, null)), "png", new java.io.File(f));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setWalkMode(boolean w) {
        walkBtn.setSelected(w);
    }

    /** Test hook: simulates a horizontal mouse drag (drag events carry no button, like on some systems). */
    void debugDrag(MouseButton b, double dx) {
        double x = getWidth() / 2, y = getHeight() / 2;
        boolean p = b == MouseButton.PRIMARY, s = b == MouseButton.SECONDARY;
        pressed(new MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y, b, 1, false, false, false, false, p, false, s, false, false, false, null));
        for (int i = 1; i <= 10; i++)
            dragged(new MouseEvent(MouseEvent.MOUSE_DRAGGED, x + dx * i / 10, y, x, y, MouseButton.NONE, 1, false, false, false, false, p, false, s, false, false, false, null));
        released(new MouseEvent(MouseEvent.MOUSE_RELEASED, x + dx, y, x, y, b, 1, false, false, false, false, false, false, false, false, false, false, null));
    }
}
