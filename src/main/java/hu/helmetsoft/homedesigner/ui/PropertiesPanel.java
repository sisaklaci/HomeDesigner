package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.edit.RoofFitter;
import hu.helmetsoft.homedesigner.geom.FurnitureTransform;
import hu.helmetsoft.homedesigner.geom.RoofOutline;
import hu.helmetsoft.homedesigner.geom.SlabGeometry;
import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.material.MaterialLibrary.Category;
import hu.helmetsoft.homedesigner.model.*;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;

/** Right-hand panel: properties of the selected object, or project/environment settings. */
public final class PropertiesPanel extends ScrollPane {
    private final Editor ed;
    private final PlanView plan;
    private final VBox box = new VBox(10);
    private final List<Runnable> refreshers = new ArrayList<>();
    private Object shown = new Object();

    public PropertiesPanel(Editor ed, PlanView plan) {
        this.ed = ed;
        this.plan = plan;
        box.setPadding(new Insets(10));
        setContent(box);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setMinWidth(250);
        setPrefWidth(300);
        subscriptions.add(ed.onSelection(this::rebuild));
        subscriptions.add(ed.onProject(() -> {
            shown = new Object();
            rebuild();
        }));
        subscriptions.add(ed.onChange(() -> {
            Object s = ed.selection();
            if (s != shown || !stillExists(s)) rebuild();
            else refreshers.forEach(Runnable::run);
        }));
        rebuild();
    }

    private final java.util.List<Runnable> subscriptions = new java.util.ArrayList<>();

    void dispose() {
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
    }

    private boolean stillExists(Object s) {
        Project p = ed.project();
        if (s == null) return true;
        if (s instanceof Wall w) return p.walls.contains(w);
        if (s instanceof Room r) return p.rooms.contains(r);
        if (s instanceof FloorZone z) return ed.roomOf(z)!=null;
        if (s instanceof SlabOpening o) return ed.roomOf(o) != null;
        if (s instanceof Furniture f) return p.furniture.contains(f);
        if (s instanceof LightSource l) return p.lights.contains(l);
        if (s instanceof Roof r) return p.roofs.contains(r);
        if (s instanceof RoofElement e) return ed.roofOf(e) != null;
        if (s instanceof Balcony b) return p.balconies.contains(b);
        if (s instanceof RetainingWall w) return p.retainingWalls.contains(w);
        if (s instanceof SiteFill f) return p.env.siteFills.contains(f);
        if (s instanceof DimensionLine d) return p.dimensions.contains(d);
        if (s instanceof Opening o) return ed.wallOf(o) != null;
        return false;
    }

    private void rebuild() {
        Object s = ed.selection();
        if (s != null && !stillExists(s)) {
            ed.select(null);
            return;
        }
        shown = s;
        refreshers.clear();
        box.getChildren().clear();
        if (s instanceof Wall w) wall(w);
        else if (s instanceof Opening o) opening(o);
        else if (s instanceof Room r) room(r);
        else if (s instanceof FloorZone z) floorZone(z);
        else if (s instanceof SlabOpening o) slabOpening(o);
        else if (s instanceof Furniture f) furniture(f);
        else if (s instanceof LightSource l) lamp(l);
        else if (s instanceof Roof r) roof(r);
        else if (s instanceof RoofElement e) roofElement(e);
        else if (s instanceof Balcony b) { if (b.terrace) terrace(b); else balcony(b); }
        else if (s instanceof RetainingWall w) retainingWall(w);
        else if (s instanceof SiteFill f) siteFill(f);
        else if (s instanceof DimensionLine d) dimension(d);
        else project();
    }

    // ------------------------------------------------------------------ helpers

    private Form section(String title) {
        Label l = new Label(title);
        l.getStyleClass().add("title-4");
        Form f = new Form();
        box.getChildren().addAll(l, f.grid);
        return f;
    }

    private void heading(String title, String sub) {
        Label l = new Label(title);
        l.getStyleClass().add("title-3");
        box.getChildren().add(l);
        if (sub != null) {
            Label s = new Label(sub);
            s.getStyleClass().add("text-muted");
            s.setWrapText(true);
            box.getChildren().add(s);
        }
    }

    private void buttons(Button... bs) {
        FlowPane fp = new FlowPane(6, 6, bs);
        box.getChildren().add(fp);
    }

    private static Button button(String text, Runnable r) {
        Button b = new Button(text);
        b.setOnAction(e -> r.run());
        return b;
    }

    private void edit(Runnable r) {
        ed.edit(r);
    }

    static double parse(String s) {
        s = s.trim().replace(',', '.').replace(" ", "").replace("\u00a0", "");
        s = s.replaceAll("[^0-9.+\\-eE]", "");
        return Double.parseDouble(s);
    }

    static String fmt(double v, int dec) {
        String s = String.format(Locale.ROOT, "%." + dec + "f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        if (s.equals("-0")) s = "0";
        return I18n.locale().getLanguage().equals("hu") ? s.replace('.', ',') : s;
    }

    private final class Form {
        final GridPane grid = new GridPane();
        int row;

        Form() {
            grid.setHgap(8);
            grid.setVgap(6);
            ColumnConstraints c0 = new ColumnConstraints();
            c0.setMinWidth(90);
            c0.setHalignment(HPos.LEFT);
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHgrow(Priority.ALWAYS);
            c1.setFillWidth(true);
            grid.getColumnConstraints().addAll(c0, c1);
        }

        void add(String label, Node n) {
            if (label != null) grid.add(new Label(label), 0, row);
            if (label == null) GridPane.setColumnSpan(n, 2);
            grid.add(n, label == null ? 0 : 1, row++);
            if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        }

        /** Numeric field; value shown multiplied by factor (100 → cm). */
        TextField num(String label, DoubleSupplier get, DoubleConsumer set, String unit, double factor, double min, double max) {
            TextField tf = new LocalizedTextField();
            tf.setPrefColumnCount(6);
            int dec = factor >= 100 ? 1 : 2;
            Runnable show = () -> {
                if (!tf.isFocused()) tf.setText(fmt(get.getAsDouble() * factor, dec));
            };
            show.run();
            refreshers.add(show);
            Runnable apply = () -> {
                try {
                    double v = parse(tf.getText()) / factor;
                    if (!Double.isFinite(v)) throw new NumberFormatException();
                    v = Math.max(min, Math.min(max, v));
                    if (Math.abs(v - get.getAsDouble()) > 1e-9) {
                        double fv = v;
                        edit(() -> set.accept(fv));
                    }
                } catch (NumberFormatException ex) {
                    ed.status.set(I18n.text("Érvénytelen szám: ") + label);
                }
                tf.setText(fmt(get.getAsDouble() * factor, dec));
            };
            tf.setOnAction(e -> {
                apply.run();
                tf.selectAll();
            });
            tf.focusedProperty().addListener((o, a, b) -> {
                if (!b) apply.run();
            });
            Label u = new Label(unit);
            u.setMinWidth(24);
            HBox h = new HBox(6, tf, u);
            h.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(tf, Priority.ALWAYS);
            add(label, h);
            return tf;
        }

        TextField text(String label, Supplier<String> get, Consumer<String> set) {
            TextField tf = new LocalizedTextField(get.get());
            Runnable apply = () -> {
                if (!tf.getText().equals(get.get())) edit(() -> set.accept(tf.getText()));
            };
            tf.setOnAction(e -> apply.run());
            tf.focusedProperty().addListener((o, a, b) -> {
                if (!b) apply.run();
            });
            add(label, tf);
            return tf;
        }

        CheckBox check(String text, BooleanSupplier get, Consumer<Boolean> set) {
            CheckBox cb = new CheckBox(text);
            cb.setSelected(get.getAsBoolean());
            cb.setOnAction(e -> edit(() -> set.accept(cb.isSelected())));
            refreshers.add(() -> cb.setSelected(get.getAsBoolean()));
            add(null, cb);
            return cb;
        }

        <T> ComboBox<T> combo(String label, T[] values, Supplier<T> get, Consumer<T> set) {
            ComboBox<T> c = new ComboBox<>();
            c.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(T value) { return I18n.display(value); }
                @Override public T fromString(String value) { return null; }
            });
            c.getItems().setAll(values);
            c.setValue(get.get());
            c.setOnAction(e -> {
                if (c.getValue() != null && c.getValue() != get.get()) edit(() -> set.accept(c.getValue()));
            });
            add(label, c);
            return c;
        }

        Slider slider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set, String fmt) {
            Slider s = new Slider(min, max, get.getAsDouble());
            Label v = new Label(String.format(I18n.locale(), fmt, get.getAsDouble()));
            v.setMinWidth(46);
            final String[] before = new String[1];
            s.valueChangingProperty().addListener((o, a, b) -> {
                if (b) before[0] = ed.snapshot();
                else {
                    ed.commit(before[0]);
                    before[0] = null;
                }
            });
            s.valueProperty().addListener((o, a, b) -> {
                v.setText(String.format(I18n.locale(), fmt, b.doubleValue()));
                if (Math.abs(b.doubleValue() - get.getAsDouble()) < 1e-9) return;
                if (before[0] == null) ed.checkpoint();
                set.accept(b.doubleValue());
                ed.changed();
            });
            HBox h = new HBox(6, s, v);
            h.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(s, Priority.ALWAYS);
            add(label, h);
            return s;
        }

        FinishEditor finish(String label, Category cat, Supplier<Finish> f) {
            FinishEditor fe = new FinishEditor(ed, cat, f);
            refreshers.add(fe::refresh);
            if (label != null) {
                Label l = new Label(label);
                GridPane.setColumnSpan(l, 2);
                grid.add(l, 0, row++);
            }
            add(null, fe);
            return fe;
        }

        void info(String label, Supplier<String> s) {
            Label l = new Label(s.get());
            l.setStyle("-fx-font-weight: bold;");
            refreshers.add(() -> l.setText(s.get()));
            add(label, l);
        }
    }

    // ------------------------------------------------------------------ editors

    private void wall(Wall w) {
        Room circle = ProjectOps.rotundaOf(ed.project(),w);
        heading(w.isFence() ? I18n.text("Kerítés") : w.curved() ? I18n.text("Ívelt fal") : I18n.text("Fal"),w.isFence()
                ? I18n.text("A kerítés típusa, magassága, oszlopköze és anyaga állítható. Kapuhelyhez rajzolj különálló szakaszokat.")
                :circle != null
                ? I18n.text("A rotunda négy összekapcsolt negyedívből áll. Mozgatás és sugármódosítás: a teljes kör együtt változik.")
                : I18n.text("A végpontok mozgathatók; a középső fogantyú húzásával hajlíthatod a falat. Jobb klikk: ajtó/ablak, kettévágás."));
        Form f = section(I18n.text("Méretek"));
        f.combo(I18n.text("Fal / kerítés típusa"),Wall.Fence.values(),() -> w.fence,v -> {
            boolean wasFence=w.isFence();
            w.fence=v;
            w.validate();
            if(!wasFence && w.isFence()) {
                w.height=1.5; w.thickness=0.12;
                String preset=switch(v) {
                    case PICKET,HORIZONTAL -> "oak_wood";
                    case METAL,MESH -> "steel";
                    default -> "stone";
                };
                w.left=new Finish(preset,null); w.right=w.left.copy();
            }
            shown=this;
        });
        f.num(w.curved() ? I18n.text("Ívhossz") : I18n.text("Hossz"), w::length, v -> setWallLength(w, v), "cm", 100, 0.05, 200);
        if (circle == null) {
            f.num(I18n.text("Ívszög (0 = egyenes)"),() -> w.arcAngle,v -> { ProjectOps.setArcAngle(w,v); shown = this; },"°",1,-350,350);
            if (w.curved()) f.num(I18n.text("Sugár"),w::radius,v -> setWallLength(w,w.length()*v/w.radius()),"cm",100,0.03,100);
        } else {
            f.num(I18n.text("Rotunda sugara"),() -> circle.circleRadius,
                    v -> ProjectOps.updateRotunda(ed.project(),circle,circle.circleX,circle.circleY,v),"cm",100,0.3,100);
            buttons(button(I18n.text("Rotunda / körpadló kijelölése"),() -> ed.select(circle)),
                    button(I18n.text("Felbontás önálló ívekre"),() -> edit(() -> {
                        ProjectOps.detachRotunda(ed.project(),circle.rotundaId);
                        shown = this;
                    })));
        }
        f.num(I18n.text("Vastagság"), () -> w.thickness, v -> w.thickness = v, "cm", 100, 0.02, 2);
        f.num(I18n.text("Magasság"), () -> w.height, v -> w.height = v, "cm", 100, 0.1, 20);
        if(w.isFence()) {
            f.check(I18n.text("Kerítés követi a terepet"),() -> w.fenceFollowTerrain,v -> w.fenceFollowTerrain=v);
            f.num(I18n.text("Kerítés emelése"),() -> w.fenceElevation,v -> w.fenceElevation=v,"cm",100,-100,100);
            f.num(I18n.text("Kerítés oszlopköze"),() -> w.fencePostSpacing,v -> w.fencePostSpacing=v,"cm",100,0.3,5);
            section(I18n.text("Kerítés anyaga")).finish(null,Category.OTHER,() -> w.left);
            buttons(button(I18n.text("Törlés"),() -> ed.delete(w)));
            return;
        }
        boolean lExt = ProjectOps.isExterior(w.left), rExt = ProjectOps.isExterior(w.right);
        Form b = section(I18n.text("Burkolatok"));
        b.finish(lExt ? I18n.text("Külső oldal") : rExt ? I18n.text("Belső oldal") : I18n.text("Egyik oldal"), Category.WALL, () -> w.left);
        b.finish(rExt ? I18n.text("Külső oldal") : lExt ? I18n.text("Belső oldal") : I18n.text("Másik oldal"), Category.WALL, () -> w.right);
        buttons(button(I18n.text("Oldalak cseréje"), () -> edit(() -> {
                    Finish t = w.left;
                    w.left = w.right;
                    w.right = t;
                    shown = this;
                })),
                button(I18n.text("+ Ajtó"), () -> addOpening(w, Opening.Type.DOOR)),
                button(I18n.text("+ Ablak"), () -> addOpening(w, Opening.Type.WINDOW)),
                button(I18n.text("+ Erkélyajtó"), () -> addOpening(w, Opening.Type.BALCONY_DOOR)));
        if (!w.openings.isEmpty()) {
            Form o = section(I18n.text("Nyílások"));
            for (Opening op : w.openings) {
                Hyperlink h = new Hyperlink(I18n.display(op.type.label) + "  " + PlanView.cm(op.width) + " × " + PlanView.cm(op.height) + " cm");
                h.setOnAction(e -> ed.select(op));
                o.add(null, h);
            }
        }
        buttons(button(I18n.text("Törlés"), () -> ed.delete(w)));
    }

    private void addOpening(Wall w, Opening.Type t) {
        edit(() -> ed.select(ProjectOps.addOpening(w,t,w.length()/2)));
    }

    private void setWallLength(Wall w, double len) {
        Room circle = ProjectOps.rotundaOf(ed.project(),w);
        if (circle != null) {
            ProjectOps.updateRotunda(ed.project(),circle,circle.circleX,circle.circleY,circle.circleRadius*len/w.length());
            return;
        }
        double ox = w.x2, oy = w.y2;
        double factor = len/w.length();
        double nx = w.x1 + (w.x2-w.x1)*factor, ny = w.y1 + (w.y2-w.y1)*factor;
        Project p = ed.project();
        for (Wall o : p.walls) {
            if (o == w) continue;
            if (Math.hypot(o.x1 - ox, o.y1 - oy) < 1e-3) { o.x1 = nx; o.y1 = ny; }
            if (Math.hypot(o.x2 - ox, o.y2 - oy) < 1e-3) { o.x2 = nx; o.y2 = ny; }
        }
        for (Room r : p.rooms)
            for (Pt pt : r.points)
                if (Math.hypot(pt.x - ox, pt.y - oy) < 1e-3) { pt.x = nx; pt.y = ny; }
        w.x2 = nx;
        w.y2 = ny;
    }

    private void opening(Opening o) {
        Wall w = ed.wallOf(o);
        heading(I18n.display(o.type.label),w != null && w.curved()
                ? I18n.text("A szélesség és a távolság a fal középívén mérendő. A sík keret a belső ív húrjára kerül; tényleges szélessége kisebb.")
                : I18n.text("Az alaprajzon a fal mentén húzható."));
        Form f = section(I18n.text("Beállítások"));
        f.combo(I18n.text("Típus"), Opening.Type.values(), () -> o.type, t -> {
            Opening d = Opening.create(t, o.pos);
            o.type = t;
            o.width = d.width;
            o.height = d.height;
            o.sill = d.sill;
            shown = this;
        });
        f.combo(I18n.text("Felső él"),Opening.Top.values(),() -> o.top,t -> o.top=t);
        f.info(I18n.text("Ív geometriája"),() -> o.top==Opening.Top.RECTANGLE ? I18n.text("Egyenes felső él")
                : "R "+fmt((o.top==Opening.Top.SEMICIRCLE ? o.width/2 : o.width)*100,1)+I18n.text(" cm; a magasság az ív csúcsa"));
        f.num(I18n.text("Szélesség"), () -> o.width, v -> o.width = v, "cm", 100, 0.2, 10);
        f.num(I18n.text("Magasság"), () -> o.height, v -> o.height = v, "cm", 100, 0.2, 10);
        f.num(I18n.text("Parapet"), () -> o.sill, v -> o.sill = v, "cm", 100, 0, 10);
        if (w != null) {
            f.num(I18n.text("Távolság a fal elejétől"), () -> o.pos - o.width / 2, v -> o.pos = Math.max(o.width / 2, Math.min(w.length() - o.width / 2, v + o.width / 2)), "cm", 100, 0, 200);
        }
        if (o.type != Opening.Type.PASSAGE) {
            f.check(I18n.text("Nyitás a másik oldalra"), () -> o.flip, v -> o.flip = v);
            f.check(I18n.text("Zsanér a másik oldalon (tükrözés)"), () -> o.mirror, v -> o.mirror = v);
            Form fr = section(I18n.text("Keret"));
            fr.finish(null, Category.OTHER, () -> o.frame);
        }
        if(o.type==Opening.Type.DOOR) {
            Form glass=section(I18n.text("Ajtólap üvegbetét"));
            glass.check(I18n.text("Részleges üvegezés"),() -> o.glazed,v -> { o.glazed=v; shown=this; });
            if(o.glazed) {
                glass.num(I18n.text("Bal szél"),() -> o.glassX*o.width,v -> o.glassX=v/o.width,"cm",100,0,10);
                glass.num(I18n.text("Alsó szél"),() -> o.glassY*o.height,v -> o.glassY=v/o.height,"cm",100,0,10);
                glass.num(I18n.text("Üveg szélessége"),() -> o.glassWidth*o.width,v -> o.glassWidth=v/o.width,"cm",100,0.01,10);
                glass.num(I18n.text("Üveg magassága"),() -> o.glassHeight*o.height,v -> o.glassHeight=v/o.height,"cm",100,0.01,10);
                glass.info(I18n.text("Méretezés"),() -> I18n.text("Az ajtó bal / alsó szélétől; legalább 8 cm tömör szegély. Átméretezéskor a betét arányosan változik."));
            }
        }
        buttons(button(I18n.text("Fal kijelölése"), () -> ed.select(w)), button(I18n.text("Törlés"), () -> ed.delete(o)));
    }

    private void room(Room r) {
        heading(r.rotundaId != null ? I18n.text("Rotunda") : I18n.text("Helyiség"),r.rotundaId != null
                ? I18n.text("A körpadló és a négy negyedív együtt mozgatható és méretezhető. Törléskor a körfalak is törlődnek.")
                : I18n.text("A sarokpontok az alaprajzon húzhatók."));
        Form f = section(I18n.text("Általános"));
        f.text(I18n.text("Név"), () -> r.name, v -> r.name = v);
        if (r.rotundaId != null) {
            f.num(I18n.text("Sugár"),() -> r.circleRadius,v -> ProjectOps.updateRotunda(ed.project(),r,r.circleX,r.circleY,v),"cm",100,0.3,100);
            f.num(I18n.text("Középpont X"),() -> r.circleX,v -> ProjectOps.updateRotunda(ed.project(),r,v,r.circleY,r.circleRadius),"cm",100,-1000,1000);
            f.num(I18n.text("Középpont Y"),() -> r.circleY,v -> ProjectOps.updateRotunda(ed.project(),r,r.circleX,v,r.circleRadius),"cm",100,-1000,1000);
            Form walls = section(I18n.text("Körfalak"));
            walls.num(I18n.text("Falvastagság"),() -> ed.project().walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).findFirst().orElseThrow().thickness,
                    v -> ed.project().walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).forEach(w -> w.thickness = v),"cm",100,0.02,2);
            walls.num(I18n.text("Falmagasság"),() -> ed.project().walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).findFirst().orElseThrow().height,
                    v -> ed.project().walls.stream().filter(w -> r.rotundaId.equals(w.rotundaId)).forEach(w -> w.height = v),"cm",100,0.1,20);
        }
        f.info(I18n.text("Alapterület"), () -> String.format(I18n.locale(), "%.2f m²", Math.abs(r.area())));
        Form fl = section(I18n.text("Padló burkolat"));
        fl.num(I18n.text("Padlószint (abszolút)"),() -> r.elevation,v -> { r.elevation=v; shown=this; },"cm",100,-10,10);
        fl.finish(null, Category.FLOOR, () -> r.floor);
        heading(I18n.text("Szintkülönbségek"),I18n.text("A padlószint a kiválasztott épületszint 0 cm síkjához képest értendő. A mennyezet, falak, ajtók és meglévő bútorok nem mozdulnak vele. Az új tárgyak a helyi padlószintre kerülnek."));
        buttons(button(I18n.text("+ Padlószint-terület"),() -> ed.tool.set(Editor.Tool.FLOOR_LEVEL)));
        for(FloorZone z:r.floorZones) buttons(button(z.name,() -> ed.select(z)));
        levelSteps(r,null);
        Form c = section(I18n.text("Mennyezet"));
        c.check(I18n.text("Van mennyezet"), () -> r.hasCeiling, v -> r.hasCeiling = v);
        c.num(I18n.text("Magasság (0 = falmagasság)"), () -> r.ceilingHeight, v -> r.ceilingHeight = v, "cm", 100, 0, 20);
        c.finish(null, Category.WALL, () -> r.ceiling);
        Form vault=section(I18n.text("Pinceboltozat"));
        vault.combo(I18n.text("Boltozat típusa"),Room.Vault.values(),() -> r.vault,v -> {
            r.vault=v;
            if(v!=Room.Vault.NONE) {
                r.hasCeiling=true;
                if(v==Room.Vault.ELLIPTICAL) r.vaultRise=0.4;
                else if(r.vaultRise<0.6) r.vaultRise=0.8;
            }
            shown=this;
        });
        if(r.vault!=Room.Vault.NONE) {
            vault.num(I18n.text("Boltozat emelkedése"),() -> r.vaultRise,v -> r.vaultRise=v,"cm",100,0.15,5);
            vault.check(I18n.text("Boltozat tengelye X irányú"),() -> r.vaultAlongX,v -> r.vaultAlongX=v);
            vault.check(I18n.text("Díszítő boltozatbordák"),() -> r.vaultRibs,v -> r.vaultRibs=v);
            Label hint=new Label(I18n.text("A mennyezet magassága a boltozat vállvonala; a csúcs az emelkedéssel magasabb. A födémnyílások a boltozatot is kivágják. A fölötte levő szintet a csúcs fölé helyezd."));
            hint.setWrapText(true);
            vault.add(null,hint);
        }
        section(I18n.text("Födémnyílások")).info(I18n.text("Nyílások száma"),() -> Integer.toString(r.slabOpenings.size()));
        buttons(button(I18n.text("+ Téglalapnyílás"),() -> ed.tool.set(Editor.Tool.SLAB_RECT)),
                button(I18n.text("+ Kör / ovális"),() -> ed.tool.set(Editor.Tool.SLAB_ELLIPSE)),
                button(I18n.text("+ Szabad sokszög"),() -> ed.tool.set(Editor.Tool.SLAB_POLYGON)));
        for (SlabOpening o : r.slabOpenings) buttons(button(o.name,() -> ed.select(o)));
        buttons(button(I18n.text("Falak burkolata egyszerre..."), () -> wallsOfRoom(r)), button(I18n.text("Törlés"), () -> ed.delete(r)));
    }

    private void slabOpening(SlabOpening o) {
        Room r = ed.roomOf(o);
        heading(I18n.text("Födémnyílás"),I18n.text("A „")+r.name+I18n.text("” helyiség felületéből vág. A helyiségen kívüli része nem hat más helyiségekre. A 3D nézetben a Mennyezet kapcsolóval látható; a talaj a padló alatt megmarad."));
        Form p = section(I18n.text("Alak és elhelyezés"));
        p.text(I18n.text("Név"),() -> o.name,v -> o.name = v);
        p.combo(I18n.text("Alak"),SlabOpening.Shape.values(),() -> o.shape,v -> {
            o.shape = v;
            if (v == SlabOpening.Shape.CIRCLE) o.depth = o.width;
            if (v == SlabOpening.Shape.POLYGON && o.points.isEmpty())
                o.points.addAll(List.of(new Pt(0,0),new Pt(1,0),new Pt(1,1),new Pt(0,1)));
            shown = this;
        });
        p.num(I18n.text("Középpont X"),() -> o.x,v -> o.x = v,"m",1,-1000,1000);
        p.num(I18n.text("Középpont Y"),() -> o.y,v -> o.y = v,"m",1,-1000,1000);
        p.num(o.shape == SlabOpening.Shape.CIRCLE ? I18n.text("Átmérő") : I18n.text("Szélesség"),() -> o.width,v -> {
            o.width = v; if (o.shape == SlabOpening.Shape.CIRCLE) o.depth = v;
        },"cm",100,0.05,200);
        if (o.shape != SlabOpening.Shape.CIRCLE) p.num(I18n.text("Hosszúság"),() -> o.depth,v -> o.depth = v,"cm",100,0.05,200);
        p.num(I18n.text("Forgatás"),() -> o.angle,v -> o.angle = v,"°",1,-3600,3600);
        Form cut = section(I18n.text("Kivágandó felületek"));
        cut.check(I18n.text("Mennyezet kivágása"),() -> o.cutCeiling,v -> {
            o.cutCeiling = v; if (v) r.hasCeiling = true;
        });
        cut.check(I18n.text("Padló kivágása"),() -> o.cutFloor,v -> o.cutFloor = v);
        cut.info(I18n.text("Mennyezet"),() -> r.hasCeiling ? I18n.text("Bekapcsolva a helyiségben") : I18n.text("Kikapcsolva a helyiségben"));
        cut.info(I18n.text("Helyiséggel átfedés"),() -> {
            var intersection = SlabGeometry.roomArea(r);
            intersection.intersect(RoofOutline.area(SlabGeometry.boundary(o)));
            return intersection.isEmpty() ? I18n.text("Nincs – itt nem vág felületet!") : I18n.text("Van");
        });
        if (o.shape == SlabOpening.Shape.POLYGON) {
            heading(I18n.text("Sarokpontok"),I18n.text("Az alaprajzon húzhatók. A szélesség/hosszúság a teljes sokszöget méretezi."));
            for (int i = 0; i < o.points.size(); i++) {
                final int index = i;
                Form v = section((i+1)+I18n.text(". pont"));
                v.num("X "+(i+1),() -> SlabGeometry.boundary(o)[index][0],
                        x -> SlabGeometry.moveVertex(o,index,x,SlabGeometry.boundary(o)[index][1]),"m",1,-1000,1000);
                v.num("Y "+(i+1),() -> SlabGeometry.boundary(o)[index][1],
                        y -> SlabGeometry.moveVertex(o,index,SlabGeometry.boundary(o)[index][0],y),"m",1,-1000,1000);
                buttons(button(I18n.text("Pont beszúrása utána"),() -> edit(() -> {
                    Pt a = o.points.get(index), b = o.points.get((index+1)%o.points.size());
                    o.points.add(index+1,new Pt((a.x+b.x)/2,(a.y+b.y)/2)); shown = this;
                })),button(I18n.text("Pont törlése"),() -> edit(() -> {
                    if (o.points.size() <= 3) throw new IllegalArgumentException(I18n.text("Legalább három sarokpont szükséges."));
                    o.points.remove(index); shown = this;
                })));
            }

        }
        buttons(button(I18n.text("Helyiség kijelölése"),() -> ed.select(r)),button(I18n.text("Másolat"),() -> plan.duplicate(o)),
                button(I18n.text("Törlés"),() -> ed.delete(o)));
    }

    private void floorZone(FloorZone zone) {
        Room room=ed.roomOf(zone); SlabOpening o=zone.outline;
        heading(I18n.text("Padlószint-terület"),I18n.text("A „")+room.name+I18n.text("” helyiség része. Negatív eltérés: süllyesztés, pozitív: emelés. A körvonala húzható, méretezhető és forgatható; a területek ne fedjék egymást."));
        Form f=section(I18n.text("Szint és burkolat"));
        f.text(I18n.text("Név"),() -> zone.name,v -> zone.name=v);
        f.num(I18n.text("Szinteltérés a szobához"),() -> zone.offset,v -> { zone.offset=v; shown=this; },"cm",100,-10,10);
        f.info(I18n.text("Abszolút padlószint"),() -> fmt((room.elevation+zone.offset)*100,1)+" cm");
        f.finish(null,Category.FLOOR,() -> zone.finish);
        Form p=section(I18n.text("Alak és elhelyezés"));
        p.combo(I18n.text("Alak"),new SlabOpening.Shape[]{SlabOpening.Shape.RECTANGLE,SlabOpening.Shape.CIRCLE,SlabOpening.Shape.ELLIPSE},
                () -> o.shape,v -> { o.shape=v; if(v==SlabOpening.Shape.CIRCLE) o.depth=o.width; shown=this; });
        p.num(I18n.text("Középpont X"),() -> o.x,v -> o.x=v,"m",1,-1000,1000);
        p.num(I18n.text("Középpont Y"),() -> o.y,v -> o.y=v,"m",1,-1000,1000);
        p.num(o.shape==SlabOpening.Shape.CIRCLE ? I18n.text("Átmérő") : I18n.text("Szélesség"),() -> o.width,
                v -> { o.width=v; if(o.shape==SlabOpening.Shape.CIRCLE) o.depth=v; },"cm",100,0.05,200);
        if(o.shape!=SlabOpening.Shape.CIRCLE) p.num(I18n.text("Hosszúság"),() -> o.depth,v -> o.depth=v,"cm",100,0.05,200);
        p.num(I18n.text("Forgatás"),() -> o.angle,v -> o.angle=v,"°",1,-3600,3600);
        if(o.shape==SlabOpening.Shape.RECTANGLE) levelSteps(room,zone);
        else heading(I18n.text("Lépcső"),I18n.text("Kör/ovális terület mellé a Lépcső eszközzel helyezhetsz külön lépcsőt."));
        buttons(button(I18n.text("Helyiség kijelölése"),() -> ed.select(room)),button(I18n.text("Másolat"),() -> plan.duplicate(zone)),
                button(I18n.text("Törlés"),() -> ed.delete(zone)));
    }

    private void levelSteps(Room room,FloorZone zone) {
        Form form=section(I18n.text("Lépcső a szintkülönbséghez"));
        Label hint=new Label(I18n.text("Külön mozgatható, tömör lépcső készül. A felső vége a kiválasztott él közepére kerül. Nem követi később a padlószint szerkesztését."));
        hint.setWrapText(true); form.add(null,hint);
        ComboBox<String> side=new ComboBox<>();
        double[][] points=zone==null ? room.points.stream().map(p -> new double[]{p.x,p.y}).toArray(double[][]::new)
                : SlabGeometry.boundary(zone.outline);
        for(int i=0;i<points.length;i++) side.getItems().add((i+1)+I18n.text(". él (")+fmt(points[i][0],2)+"; "+fmt(points[i][1],2)+")");
        side.getSelectionModel().selectFirst(); form.add(I18n.text("Oldal"),side);
        double difference=Math.abs(zone==null ? room.elevation : zone.offset);
        Spinner<Integer> steps=new Spinner<>(1,80,Math.max(1,Math.min(80,(int)Math.round(difference/0.17))));
        form.add(I18n.text("Fokok száma"),steps);
        TextField width=new LocalizedTextField("100"),going=new LocalizedTextField("28");
        form.add(I18n.text("Szélesség (cm)"),width); form.add(I18n.text("Belépés (cm)"),going);
        buttons(button(I18n.text("Lépcső létrehozása"),() -> ed.edit(() -> {
            Furniture stair=hu.helmetsoft.homedesigner.geom.FloorLevels.steps(ed.project(),room,zone,side.getSelectionModel().getSelectedIndex(),
                    steps.getValue(),parse(width.getText())/100,parse(going.getText())/100);
            ed.project().furniture.add(stair); ed.select(stair);
        })));
    }

    /** Applies one finish to the room-facing side of all walls around the room. */
    private void wallsOfRoom(Room r) {
        Dialog<Finish> d = new Dialog<>();
        d.setTitle(I18n.text("Falburkolat"));
        d.setHeaderText(I18n.text("A(z) „") + r.name + I18n.text("” helyiség összes falának belső oldala"));
        Finish tmp = new Finish("plaster", "#F3EFE7");
        Editor dummy = new Editor(new Project());
        d.getDialogPane().setContent(new FinishEditor(dummy, Category.WALL, () -> tmp));
        ButtonType accept = new ButtonType(I18n.text("Rendben"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(accept,
                new ButtonType(I18n.text("Mégse"), ButtonBar.ButtonData.CANCEL_CLOSE));
        d.getDialogPane().setPrefWidth(380);
        d.initOwner(getScene().getWindow());
        d.setResultConverter(b -> b == accept ? tmp : null);
        d.showAndWait().ifPresent(fin -> edit(() -> {
            for (Wall w : ed.project().walls) {
                double[] mid = w.point(w.length()/2,0);
                double mx = mid[0], my = mid[1], k = w.thickness / 2 + 0.05;
                boolean onEdge = true;
                for (double u : new double[]{0,w.length()/4,w.length()/2,w.length()*0.75,w.length()}) {
                    double[] q = w.point(u,0);
                    boolean near = false;
                    for (int i = 0; i < r.points.size(); i++) {
                        Pt a = r.points.get(i), b = r.points.get((i+1)%r.points.size());
                        Wall e = new Wall(a.x,a.y,b.x,b.y,0,0);
                        if (e.distance(q[0],q[1]) < w.thickness/2+0.02) { near = true; break; }
                    }
                    if (!near) { onEdge = false; break; }
                }
                if (!onEdge) continue;
                double nx = w.tangentY(w.length()/2), ny = -w.tangentX(w.length()/2);
                if (r.contains(mx + nx * k, my + ny * k)) w.left = fin.copy();
                else if (r.contains(mx - nx * k, my - ny * k)) w.right = fin.copy();
            }
        }));
    }

    private void furniture(Furniture fu) {
        if (fu.solar != null) { solar(fu); return; }
        if (fu.cabinet != null) { cabinet(fu); return; }
        if (fu.stair != null) { stair(fu); return; }
        ModelData md = fu.modelPath == null ? null : ModelLibrary.now(fu.modelPath);
        String err = fu.modelPath == null ? null : ModelLibrary.error(fu.modelPath);
        heading(I18n.text("Bútor / tárgy"), fu.modelPath == null ? null : fu.modelPath);
        if (err != null) {
            Label l = new Label(I18n.text("Betöltési hiba: ") + err);
            l.setWrapText(true);
            l.setTextFill(Color.web("#B91C1C"));
            box.getChildren().add(l);
        } else if (md == null) box.getChildren().add(new Label(I18n.text("Modell betöltése folyamatban...")));
        else if (md.note != null) {
            Label l = new Label(md.displayNote());
            l.setWrapText(true);
            l.getStyleClass().add("text-muted");
            box.getChildren().add(l);
        }
        Form g = section(I18n.text("Elhelyezés"));
        g.text(I18n.text("Név"), () -> fu.name, v -> fu.name = v);
        g.num("X", () -> fu.x, v -> fu.x = v, "m", 1, -1e4, 1e4);
        g.num("Y", () -> fu.y, v -> fu.y = v, "m", 1, -1e4, 1e4);
        g.num(I18n.text("Emelés"), () -> fu.elevation, v -> fu.elevation = v, "cm", 100, -50, 50);
        g.num(I18n.text("Forgatás"), () -> fu.angle, v -> fu.angle = ((v % 360) + 360) % 360, "°", 1, -3600, 3600);
        Form s = section(I18n.text("Méret"));
        CheckBox keep = new CheckBox(I18n.text("Arányok megtartása"));
        keep.setSelected(!hu.helmetsoft.homedesigner.io.HomeModels.isColumn(fu.modelPath)
                && !hu.helmetsoft.homedesigner.io.HomeModels.isBeam(fu.modelPath));
        s.num(I18n.text("Szélesség"), () -> fu.width, v -> resize(fu, keep.isSelected(), v / fu.width), "cm", 100, 0.01, 100);
        s.num(I18n.text("Mélység"), () -> fu.depth, v -> resize(fu, keep.isSelected(), Double.NaN, v / fu.depth), "cm", 100, 0.01, 100);
        s.num(I18n.text("Magasság"), () -> fu.height, v -> resize(fu, keep.isSelected(), Double.NaN, Double.NaN, v / fu.height), "cm", 100, 0.01, 100);
        s.add(null, keep);
        s.combo(I18n.text("Tájolás"), FurnitureTransform.UP_FIX_LABELS, () -> FurnitureTransform.UP_FIX_LABELS[Math.max(0, Math.min(5, fu.upFix))], v -> {
            int i = List.of(FurnitureTransform.UP_FIX_LABELS).indexOf(v);
            fu.upFix = Math.max(0, i);
            if (md != null) ProjectOps.applyModelSize(fu, md, fu.height);
            plan.clearCaches();
        });
        s.check(I18n.text("Tükrözés"), () -> fu.mirror, v -> fu.mirror = v);
        Button orig = button(I18n.text("Eredeti méret"), () -> {
            if (md != null) edit(() -> ProjectOps.applyModelSize(fu, md, -1));
        });
        orig.setDisable(md == null);
        s.add(null, orig);
        Form m = section(I18n.text("Anyag"));
        m.check(I18n.text("Egyedi anyag az eredeti helyett"), () -> fu.override != null, v -> {
            fu.override = v ? new Finish("fabric", null) : null;
            shown = this;
        });
        if (fu.override != null) m.finish(null, Category.OTHER, () -> fu.override);
        buttons(button(I18n.text("Forgatás 90°"), () -> edit(() -> fu.angle = (fu.angle + 90) % 360)),
                button(I18n.text("Helyi padlóra helyezés"),() -> edit(() ->
                        fu.elevation=hu.helmetsoft.homedesigner.geom.FloorLevels.floorAt(ed.project(),fu.x,fu.y))),
                button(I18n.text("Másolat"), () -> plan.duplicate(fu)),
                button(I18n.text("Törlés"), () -> ed.delete(fu)));
    }

    private void solar(Furniture f) {
        SolarPanel s=f.solar;
        heading(I18n.text("Napelem"),I18n.text("A dőlésszög a vízszinteshez képest értendő. A tartószerkezet automatikusan követi; a tetőre helyezéshez az emelést és a forgatást állítsd be."));
        Form p=section(I18n.text("Elhelyezés"));
        p.text(I18n.text("Név"),() -> f.name,v -> f.name=v);
        p.num("X",() -> f.x,v -> f.x=v,"m",1,-1000,1000);
        p.num("Y",() -> f.y,v -> f.y=v,"m",1,-1000,1000);
        p.num(I18n.text("Emelés"),() -> f.elevation,v -> f.elevation=v,"cm",100,-50,100);
        p.num(I18n.text("Forgatás"),() -> f.angle,v -> f.angle=v,"°",1,-3600,3600);
        Form g=section(s.type==SolarPanel.Type.ROOF ? I18n.text("Tetőre szerelhető napelem") : I18n.text("Talajra szerelhető napelem"));
        g.num(I18n.text("Dőlésszög"),() -> s.tilt,v -> {
            s.tilt=v; hu.helmetsoft.homedesigner.geom.SolarGeometry.updateSize(f); plan.clearCaches();
        },"°",1,0,85);
        g.num(I18n.text("Panel szélessége"),() -> s.width,v -> {
            s.width=v; hu.helmetsoft.homedesigner.geom.SolarGeometry.updateSize(f); plan.clearCaches();
        },"cm",100,0.3,5);
        g.num(I18n.text("Panel hossza"),() -> s.length,v -> {
            s.length=v; hu.helmetsoft.homedesigner.geom.SolarGeometry.updateSize(f); plan.clearCaches();
        },"cm",100,0.3,5);
        g.info(I18n.text("Magasság"),() -> fmt(f.height*100,1)+" cm");
        g.info(I18n.text("Vízszintes hossz"),() -> fmt(f.depth*100,1)+" cm");
        buttons(button(I18n.text("Másolat"),() -> plan.duplicate(f)),button(I18n.text("Törlés"),() -> ed.delete(f)));
    }

    private void cabinet(Furniture f) {
        Cabinet c=f.cabinet;
        heading(I18n.text("Egyedi szekrényszerkesztő"),I18n.text("Paraméteres látványterv. A méretek a korpuszra vonatkoznak, a nyitott ajtó és a fogantyú túlnyúlik rajtuk. Nem gyártási/szabásjegyzék."));
        Form p=section(I18n.text("Elhelyezés és méretek"));
        p.text(I18n.text("Név"),() -> f.name,v -> f.name=v);
        p.num("X",() -> f.x,v -> f.x=v,"m",1,-1000,1000);
        p.num("Y",() -> f.y,v -> f.y=v,"m",1,-1000,1000);
        p.num(I18n.text("Emelés"),() -> f.elevation,v -> f.elevation=v,"cm",100,0,10);
        p.num(I18n.text("Forgatás"),() -> f.angle,v -> f.angle=v,"°",1,-3600,3600);
        p.num(I18n.text("Szélesség"),() -> f.width,v -> f.width=v,"cm",100,0.2,8);
        p.num(I18n.text("Magasság"),() -> f.height,v -> f.height=v,"cm",100,0.3,4);
        p.num(I18n.text("Mélység"),() -> f.depth,v -> f.depth=v,"cm",100,0.15,1.5);
        Form g=section(I18n.text("Belső felosztás és frontok"));
        g.num(I18n.text("Oszlopok"),() -> c.columns,v -> c.columns=whole(v),"",1,1,10);
        g.num(I18n.text("Polcok / oszlop"),() -> c.shelves,v -> c.shelves=whole(v),"",1,0,12);
        g.num(I18n.text("Fiókok / oszlop"),() -> c.drawers,v -> c.drawers=whole(v),"",1,0,8);
        g.num(I18n.text("Lapvastagság"),() -> c.board,v -> c.board=v,"mm",1000,0.01,0.05);
        g.num(I18n.text("Lábazat"),() -> c.plinth,v -> c.plinth=v,"cm",100,0,0.3);
        g.check(I18n.text("Ajtók"),() -> c.doors,v -> { c.doors=v; shown=this; });
        if (c.doors) g.check(I18n.text("Ajtók nyitva (90°)"),() -> c.openDoors,v -> c.openDoors=v);
        section(I18n.text("Korpusz és polcok")).finish(null,Category.OTHER,() -> c.body);
        section(I18n.text("Ajtók és fiókfrontok")).finish(null,Category.OTHER,() -> c.fronts);
        section(I18n.text("Fogantyúk")).finish(null,Category.OTHER,() -> c.handles);
        buttons(button(I18n.text("Másolat"),() -> plan.duplicate(f)),button(I18n.text("Törlés"),() -> ed.delete(f)));
    }

    private static int whole(double v) {
        if (v != Math.rint(v)) throw new IllegalArgumentException(I18n.text("A darabszám egész szám legyen."));
        return (int)v;
    }

    private void stair(Furniture f) {
        Stair s = f.stair;
        heading(I18n.text("Lépcső"), I18n.text("Önálló, paraméteres modell. A szintmagasság az utolsó fok járószintje; a korlát ezen felül értendő. Födémnyílást külön kell kialakítani. Nem kivitelezési méretezés."));
        Form p = section(I18n.text("Elhelyezés"));
        p.text(I18n.text("Név"),() -> f.name,v -> f.name = v);
        p.num("X",() -> f.x,v -> f.x = v,"m",1,-1000,1000);
        p.num("Y",() -> f.y,v -> f.y = v,"m",1,-1000,1000);
        p.num(I18n.text("Alsó járószint"),() -> f.elevation,v -> f.elevation = v,"cm",100,-50,100);
        p.num(I18n.text("Forgatás"),() -> f.angle,v -> f.angle = v,"°",1,-3600,3600);
        Form g = section(I18n.text("Lépcsőszerkesztő"));
        g.combo(I18n.text("Típus"),Stair.Type.values(),() -> s.type,v -> {
            s.type = v;
            if (v == Stair.Type.SPIRAL) f.width = f.depth = Math.max(1.8,f.width);
            else { f.width = 1.1; f.depth = 4; }
            shown = this;
        });
        g.num(I18n.text("Szintmagasság"),() -> f.height,v -> f.height = v,"cm",100,0.05,10);
        g.num(I18n.text("Fokszám"),() -> s.steps,v -> {
            if (v != Math.rint(v)) throw new IllegalArgumentException(I18n.text("A fokszám egész szám legyen."));
            s.steps = (int)v;
        },"",1,s.type==Stair.Type.STRAIGHT ? 1 : 3,80);
        g.info(I18n.text("Fellépés"),() -> fmt(f.height/s.steps*100,1)+" cm");
        g.num(I18n.text("Fokvastagság"),() -> s.treadThickness,v -> s.treadThickness = v,"cm",100,0.02,0.2);
        if (s.type == Stair.Type.SPIRAL) {
            g.num(I18n.text("Külső átmérő"),() -> f.width,v -> f.width = f.depth = v,"cm",100,0.8,20);
            g.num(I18n.text("Belső sugár"),() -> s.innerRadius,v -> s.innerRadius = v,"cm",100,0.06,9);
            g.num(I18n.text("Teljes fordulat"),() -> s.turn,v -> s.turn = v,"°",1,90,720);
            g.check(I18n.text("Óramutató szerint felfelé"),() -> s.clockwise,v -> s.clockwise = v);
            g.info(I18n.text("Járószélesség"),() -> fmt((f.width/2-s.innerRadius)*100,1)+" cm");
            g.info(I18n.text("Szabad fejmagasság / kör"),() -> s.turn <= 360 ? I18n.text("Egy fordulatig") :
                    fmt((f.height*360/s.turn-s.treadThickness)*100,1)+" cm");
        } else {
            g.num(I18n.text("Szélesség"),() -> f.width,v -> f.width = v,"cm",100,0.5,20);
            g.num(I18n.text("Vízszintes hossz"),() -> f.depth,v -> f.depth = v,"cm",100,0.1,30);
            g.info(I18n.text("Belépés"),() -> fmt(f.depth/s.steps*100,1)+" cm");
            g.check(I18n.text("Tömör lépcsőtest"),() -> s.solid,v -> s.solid=v);
        }
        g.check(I18n.text("Korlát"),() -> s.railing,v -> { s.railing = v; shown = this; });
        if (s.railing) g.num(I18n.text("Korlátmagasság"),() -> s.railingHeight,v -> s.railingHeight = v,"cm",100,0.5,1.5);
        section(I18n.text("Lépcsőfok burkolata")).finish(null,Category.OTHER,() -> s.treads);
        section(I18n.text("Tartószerkezet és korlát")).finish(null,Category.OTHER,() -> s.frame);
        heading(I18n.text("Födémnyílás a lépcső fölé"),I18n.text("A gomb a teljes lépcső vetületét lefedő önálló mennyezetnyílást készít (nem követi később a lépcsőt). A padlókivágás külön kapcsolható. A fejmagasságot ellenőrizd a 3D nézetben!"));
        buttons(button(I18n.text("+ Téglalapnyílás"),() -> ed.openingForStair(f,SlabOpening.Shape.RECTANGLE)),
                button(I18n.text("+ Környílás"),() -> ed.openingForStair(f,SlabOpening.Shape.CIRCLE)),
                button(I18n.text("+ Ovális nyílás"),() -> ed.openingForStair(f,SlabOpening.Shape.ELLIPSE)));
        buttons(button(I18n.text("Forgatás 90°"),() -> edit(() -> f.angle = (f.angle+90)%360)),
                button(I18n.text("Másolat"),() -> plan.duplicate(f)),button(I18n.text("Törlés"),() -> ed.delete(f)));
    }

    private void resize(Furniture f, boolean keep, double... k) {
        double kx = k.length > 0 ? k[0] : Double.NaN, ky = k.length > 1 ? k[1] : Double.NaN, kz = k.length > 2 ? k[2] : Double.NaN;
        double any = !Double.isNaN(kx) ? kx : !Double.isNaN(ky) ? ky : kz;
        if (keep) {
            f.width *= any;
            f.depth *= any;
            f.height *= any;
        } else {
            if (!Double.isNaN(kx)) f.width *= kx;
            if (!Double.isNaN(ky)) f.depth *= ky;
            if (!Double.isNaN(kz)) f.height *= kz;
        }
    }

    private void lamp(LightSource l) {
        heading(I18n.text("Lámpa"), I18n.text("A fotó renderben valódi fényforrásként világít."));
        Form f = section(I18n.text("Beállítások"));
        f.text(I18n.text("Név"), () -> l.name, v -> l.name = v);
        f.num(I18n.text("Magasság"), () -> l.height, v -> l.height = v, "cm", 100, 0.05, 20);
        f.slider(I18n.text("Fényerő"), 0.1, 6, () -> l.power, v -> l.power = v, "%.1f");
        f.num(I18n.text("Izzó mérete"), () -> l.radius, v -> l.radius = v, "cm", 100, 0.01, 0.5);
        ColorPicker cp = new LocalizedColorPicker(FxUtil.parse(l.color, Color.WHITE));
        cp.setOnAction(e -> edit(() -> l.color = FxUtil.hex(cp.getValue())));
        f.add(I18n.text("Fény színe"), cp);
        HBox presets = new HBox(4);
        for (String[] p : new String[][]{{I18n.text("Meleg"), "#FFD6A0"}, {I18n.text("Természetes"), "#FFF1DD"}, {I18n.text("Hideg"), "#E6F0FF"}}) {
            Button b = new Button(p[0]);
            b.setOnAction(e -> edit(() -> {
                l.color = p[1];
                cp.setValue(Color.web(p[1]));
            }));
            presets.getChildren().add(b);
        }
        f.add(null, presets);
        buttons(button(I18n.text("Másolat"), () -> plan.duplicate(l)), button(I18n.text("Törlés"), () -> ed.delete(l)));
    }

    private void roof(Roof r) {
        heading(I18n.text("Tető"), I18n.text("A tető körvonala önálló: falváltoztatás után a „Falakhoz igazítás” frissíti az adott épülethez. A sarkok húzással méretezhetők."));
        Form f = section(I18n.text("Forma"));
        f.combo(I18n.text("Típus"), Roof.Type.values(), () -> r.type, v -> { r.type = v; shown = this; });
        f.info(I18n.text("Alap"),() -> r.circular ? I18n.text("Kör") : r.footprint.isEmpty() ? I18n.text("Téglalap") : I18n.text("Falakat követő körvonal"));
        f.check(I18n.text("Kör alap"),() -> r.circular,v -> {
            r.circular = v;
            r.footprint.clear();
            if (v) r.d = r.w;
            else if (r.radial()) r.type = Roof.Type.GABLE;
            shown = this;
        });
        if (r.type == Roof.Type.DOME)
            f.num(I18n.text("Kupola emelkedése"),() -> r.domeRise,v -> r.domeRise = v,"cm",100,0.05,100);
        else if (r.type != Roof.Type.FLAT)
            f.num(I18n.text("Hajlásszög"), () -> r.pitch, v -> r.pitch = v, "°", 1, 0, 75);
        f.num(I18n.text("Eresz magasság"), () -> r.baseHeight, v -> r.baseHeight = v, "cm", 100, 0, 50);
        f.num(I18n.text("Túlnyúlás"), () -> r.overhang, v -> r.overhang = v, "cm", 100, 0, 3);
        f.num(I18n.text("Vastagság"), () -> r.thickness, v -> r.thickness = v, "cm", 100, 0.02, 1);
        f.num("X", () -> r.x, v -> r.x = v, "m", 1, -1000, 1000);
        f.num("Y", () -> r.y, v -> r.y = v, "m", 1, -1000, 1000);
        if (r.circular) {
            f.num(I18n.text("Sugár"),() -> r.w/2,v -> {
                double cx = r.x+r.w/2, cy = r.y+r.d/2;
                r.resize(2*v,2*v); r.x = cx-v; r.y = cy-v;
            },"cm",100,0.25,100);
        } else {
            f.num(I18n.text("Szélesség"), () -> r.w, v -> r.w = v, "cm", 100, 0.5, 200);
            f.num(I18n.text("Mélység"), () -> r.d, v -> r.d = v, "cm", 100, 0.5, 200);
        }
        if (!r.radial()) f.check(I18n.text("Gerinc a hosszabb (X) irányban"), () -> r.ridgeAlongX, v -> r.ridgeAlongX = v);
        f.check(I18n.text("Oromfal / fal feletti lezárás"), () -> r.gableWalls, v -> r.gableWalls = v);
        Form m = section(I18n.text("Fedés"));
        m.finish(null, Category.ROOF, () -> r.finish);
        Form g = section(I18n.text("Oromfal burkolat"));
        g.finish(null, Category.WALL, () -> r.gable);
        section(r.radial() ? I18n.text("Kúpra/kupolára tetőelem jelenleg nem helyezhető") : I18n.text("Ablakok és tetőteraszok"));
        if (!r.radial()) for (RoofElement.Type type : RoofElement.Type.values())
                buttons(button("+ " + I18n.display(type.label), () -> ed.addRoofElement(r,type)));
        for (int i = 0; i < r.elements.size(); i++) {
            RoofElement e = r.elements.get(i);
            buttons(button((i+1) + ". " + I18n.display(e.type.label), () -> ed.select(e)));
        }
        buttons(button(I18n.text("Falakhoz igazítás"), () -> edit(() -> {
            RoofFitter.refit(ed.project(),r);
            shown = this;
        })), button(I18n.text("Törlés"), () -> ed.delete(r)));
    }

    private void roofElement(RoofElement e) {
        Roof r = ed.roofOf(e);
        heading(I18n.display(e.type.label), I18n.text("Húzd az alaprajzon. A méretek vízszintes vetületek; az X/Y a tető bal felső sarkától mérendő. Egyetlen tetősíkon kell maradnia."));
        Form f = section(I18n.text("Elhelyezés és méret"));
        f.num(I18n.text("X a tetőn"), () -> e.x, v -> e.x = v, "cm", 100, 0.05, 200);
        f.num(I18n.text("Y a tetőn"), () -> e.y, v -> e.y = v, "cm", 100, 0.05, 200);
        f.num(I18n.text("Szélesség"), () -> e.width, v -> e.width = v, "cm", 100, 0.3, 20);
        f.num(I18n.text("Mélység"), () -> e.depth, v -> e.depth = v, "cm", 100, 0.3, 20);
        if (e.type == RoofElement.Type.DORMER) {
            f.combo(I18n.text("Saját tető"), RoofElement.Cover.values(), () -> e.cover, v -> e.cover = v);
            f.num(I18n.text("Kiemelés a tető fölé"), () -> e.height, v -> e.height = v, "cm", 100, 0.3, 4);
        }
        if (e.type == RoofElement.Type.TERRACE) {
            f.num(I18n.text("Süllyesztés"), () -> e.floorDrop, v -> e.floorDrop = v, "cm", 100, 0.2, 3);
            f.num(I18n.text("Korlátmagasság"), () -> e.railingHeight, v -> e.railingHeight = v, "cm", 100, 0.9, 2);
            f.combo(I18n.text("Korlát"), Balcony.Railing.values(), () -> e.railing, v -> e.railing = v);
            section(I18n.text("Járófelület")).finish(null, Category.FLOOR, () -> e.floor);
        }
        section(I18n.text("Keret / korlát")).finish(null, Category.OTHER, () -> e.frame);
        section(I18n.text("Káva / oldalfal")).finish(null, Category.WALL, () -> e.wall);
        buttons(button(I18n.text("Szülő tető"), () -> ed.select(r)), button(I18n.text("Másolat"), () -> plan.duplicate(e)),
                button(I18n.text("Törlés"), () -> ed.delete(e)));
    }

    private void balcony(Balcony b) {
        heading(I18n.text("Homlokzati erkély"), I18n.text("Az alaprajzon húzható. Az X/Y a hátsó bal sarok; 0° esetén a homlokzat az X iránnyal párhuzamos. Erkélyajtót a fal helyi menüjéből adhatsz hozzá."));
        Form f = section(I18n.text("Elhelyezés"));
        f.text(I18n.text("Név"), () -> b.name, v -> b.name = v);
        f.num("X", () -> b.x, v -> b.x = v, "m", 1, -1000, 1000);
        f.num("Y", () -> b.y, v -> b.y = v, "m", 1, -1000, 1000);
        f.num(I18n.text("Forgatás"), () -> b.angle, v -> b.angle = v, "°", 1, -360, 360);
        f.num(I18n.text("Padlószint"), () -> b.elevation, v -> b.elevation = v, "cm", 100, 0, 100);
        f.num(I18n.text("Szélesség"), () -> b.width, v -> b.width = v, "cm", 100, 0.5, 50);
        f.num(I18n.text("Mélység"), () -> b.depth, v -> b.depth = v, "cm", 100, 0.5, 20);
        f.num(I18n.text("Lemezvastagság"), () -> b.thickness, v -> b.thickness = v, "cm", 100, 0.05, 1);
        Form rail = section(I18n.text("Korlát"));
        rail.combo(I18n.text("Kialakítás"), Balcony.Railing.values(), () -> b.railing, v -> b.railing = v);
        rail.num(I18n.text("Magasság"), () -> b.railingHeight, v -> b.railingHeight = v, "cm", 100, 0.9, 2);
        rail.check(I18n.text("Elöl"), () -> b.frontRail, v -> b.frontRail = v);
        rail.check(I18n.text("Bal oldalt"), () -> b.leftRail, v -> b.leftRail = v);
        rail.check(I18n.text("Jobb oldalt"), () -> b.rightRail, v -> b.rightRail = v);
        rail.check(I18n.text("Hátul (homlokzatnál)"), () -> b.backRail, v -> b.backRail = v);
        section(I18n.text("Járófelület")).finish(null, Category.FLOOR, () -> b.floor);
        section(I18n.text("Korlát anyaga")).finish(null, Category.OTHER, () -> b.railFinish);
        supportSection(b);
        buttons(button(I18n.text("Másolat"), () -> plan.duplicate(b)), button(I18n.text("Törlés"), () -> ed.delete(b)));
    }

    private void supportSection(Balcony b) {
        Form s = section(I18n.text("Alátámasztás"));
        s.combo(I18n.text("Kialakítás"), Balcony.Support.values(), () -> b.support, v -> { b.support = v; shown = this; });
        if (b.support != Balcony.Support.NONE)
            s.num(I18n.text("Pillér / lábazat mérete"), () -> b.supportSize, v -> b.supportSize = v, "cm", 100, 0.1, 2);
        Label hint = new Label(I18n.text("Az alátámasztás a lemez aljától a tényleges (lejtős vagy feltöltött) terepig ér."));
        hint.setWrapText(true);
        s.add(null, hint);
    }

    private void terrace(Balcony b) {
        heading(I18n.text("Terasz"), I18n.text("Vízszintes, járható teraszlemez a szint padló- vagy terepszintjén. Nem tetőterasz: önállóan áll, a lejtős terepig alátámasztható. Az alaprajzon húzható."));
        Form f = section(I18n.text("Elhelyezés"));
        f.text(I18n.text("Név"), () -> b.name, v -> b.name = v);
        f.num("X", () -> b.x, v -> b.x = v, "m", 1, -1000, 1000);
        f.num("Y", () -> b.y, v -> b.y = v, "m", 1, -1000, 1000);
        f.num(I18n.text("Forgatás"), () -> b.angle, v -> b.angle = v, "°", 1, -360, 360);
        f.num(I18n.text("Járószint (a szinthez képest)"), () -> b.elevation, v -> b.elevation = v, "cm", 100, -20, 30);
        f.num(I18n.text("Szélesség"), () -> b.width, v -> b.width = v, "cm", 100, 0.3, 50);
        f.num(I18n.text("Mélység"), () -> b.depth, v -> b.depth = v, "cm", 100, 0.3, 50);
        f.num(I18n.text("Lemezvastagság"), () -> b.thickness, v -> b.thickness = v, "cm", 100, 0.05, 1);
        f.info(I18n.text("Terep a középpontban"), () -> String.format(I18n.locale(), "%+.2f m",
                hu.helmetsoft.homedesigner.geom.SiteGeometry.height(ed.project(), b.point(0.5,0.5)[0], b.point(0.5,0.5)[1]) + 0.01 - ed.project().activeLevel().baseHeight));
        section(I18n.text("Járófelület")).finish(null, Category.FLOOR, () -> b.floor);
        supportSection(b);
        Form rail = section(I18n.text("Korlát"));
        rail.combo(I18n.text("Kialakítás"), Balcony.Railing.values(), () -> b.railing, v -> b.railing = v);
        rail.check(I18n.text("Elöl"), () -> b.frontRail, v -> b.frontRail = v);
        rail.check(I18n.text("Bal oldalt"), () -> b.leftRail, v -> b.leftRail = v);
        rail.check(I18n.text("Jobb oldalt"), () -> b.rightRail, v -> b.rightRail = v);
        rail.check(I18n.text("Hátul (homlokzatnál)"), () -> b.backRail, v -> b.backRail = v);
        buttons(button(I18n.text("Másolat"), () -> plan.duplicate(b)), button(I18n.text("Törlés"), () -> ed.delete(b)));
    }

    private void retainingWall(RetainingWall w) {
        heading(I18n.text("Támfal"), I18n.text("Önálló tömör fal: a teteje vízszintes, az alja a terepet követi vagy rögzített szinten van. A végpontjai az alaprajzon húzhatók."));
        Form f = section(I18n.text("Elhelyezés"));
        f.text(I18n.text("Név"), () -> w.name, v -> w.name = v);
        f.num("X1", () -> w.x1, v -> w.x1 = v, "m", 1, -1000, 1000);
        f.num("Y1", () -> w.y1, v -> w.y1 = v, "m", 1, -1000, 1000);
        f.num("X2", () -> w.x2, v -> w.x2 = v, "m", 1, -1000, 1000);
        f.num("Y2", () -> w.y2, v -> w.y2 = v, "m", 1, -1000, 1000);
        f.num(I18n.text("Hossz"), w::length, v -> {
            double l = w.length();
            if (l < 1e-6) return;
            w.x2 = w.x1 + (w.x2 - w.x1) * v / l;
            w.y2 = w.y1 + (w.y2 - w.y1) * v / l;
        }, "cm", 100, 0.1, 1000);
        f.num(I18n.text("Forgatás"), w::angle, v -> {
            double l = w.length(), a = Math.toRadians(v);
            w.x2 = w.x1 + Math.cos(a) * l;
            w.y2 = w.y1 + Math.sin(a) * l;
        }, "°", 1, -360, 360);
        f.num(I18n.text("Vastagság"), () -> w.thickness, v -> {
            w.thickness = v; w.footingWidth = Math.max(w.footingWidth, v);
        }, "cm", 100, 0.05, 2);
        f.combo(I18n.text("Fal helyzete a vonalhoz"), RetainingWall.Side.values(), () -> w.side, v -> w.side = v);
        double base = ed.project().activeLevel().baseHeight;
        Form h = section(I18n.text("Magasság"));
        h.check(I18n.text("Abszolút (világ) koronaszint"), () -> w.topAbsolute, v -> {
            w.top += v ? base : -base;
            w.bottom += v ? base : -base;
            w.topAbsolute = v; shown = this;
        });
        h.num(w.topAbsolute ? I18n.text("Koronaszint (abszolút)") : I18n.text("Koronaszint (a szinthez képest)"), () -> w.top, v -> w.top = v, "cm", 100, -100, 100);
        h.check(I18n.text("Az alja a terepet követi"), () -> w.followTerrain, v -> { w.followTerrain = v; shown = this; });
        if (w.followTerrain) h.num(I18n.text("Befogás a terep alá"), () -> w.embedment, v -> w.embedment = v, "cm", 100, 0, 5);
        else h.num(w.topAbsolute ? I18n.text("Alsó szint (abszolút)") : I18n.text("Alsó szint (a szinthez képest)"), () -> w.bottom, v -> w.bottom = v, "cm", 100, -100, 100);
        h.info(I18n.text("Látható magasság"), () -> {
            double[] m = w.point(w.length()/2, 0);
            double top = hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.topWorld(w, base);
            return String.format(I18n.locale(), "%.2f m", top - hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.ground(ed.project().env, m[0], m[1]));
        });
        Form ft = section(I18n.text("Alaptest"));
        ft.check(I18n.text("Sávalap a fal alatt"), () -> w.footing, v -> { w.footing = v; shown = this; });
        if (w.footing) {
            ft.num(I18n.text("Alap szélessége"), () -> w.footingWidth, v -> w.footingWidth = v, "cm", 100, 0.1, 5);
            ft.num(I18n.text("Alap magassága"), () -> w.footingHeight, v -> w.footingHeight = v, "cm", 100, 0.05, 2);
        }
        section(I18n.text("Burkolat")).finish(null, Category.WALL, () -> w.finish);
        buttons(button(I18n.text("Másolat"), () -> plan.duplicate(w)), button(I18n.text("Törlés"), () -> ed.delete(w)));
    }

    private void siteFill(SiteFill fill) {
        SlabOpening o = fill.outline;
        heading(I18n.text("Földfeltöltés"), I18n.text("Globális tereprendezés: minden szinten látható és szerkeszthető. A teteje vízszintes abszolút világszint, nem függ az aktív szinttől. Az épület belsejét nem tölti fel."));
        Form p = section(I18n.text("Alak és elhelyezés"));
        p.text(I18n.text("Név"), () -> fill.name, v -> fill.name = v);
        SlabOpening.Shape[] shapes = {SlabOpening.Shape.RECTANGLE, SlabOpening.Shape.CIRCLE, SlabOpening.Shape.ELLIPSE};
        p.combo(I18n.text("Alak"), o.shape == SlabOpening.Shape.POLYGON ? SlabOpening.Shape.values() : shapes, () -> o.shape, v -> {
            o.shape = v;
            if (v == SlabOpening.Shape.CIRCLE) o.depth = o.width;
            if (v == SlabOpening.Shape.POLYGON && o.points.isEmpty())
                o.points.addAll(List.of(new Pt(0,0),new Pt(1,0),new Pt(1,1),new Pt(0,1)));
            shown = this;
        });
        p.num(I18n.text("Középpont X"), () -> o.x, v -> o.x = v, "m", 1, -1000, 1000);
        p.num(I18n.text("Középpont Y"), () -> o.y, v -> o.y = v, "m", 1, -1000, 1000);
        p.num(o.shape == SlabOpening.Shape.CIRCLE ? I18n.text("Átmérő") : I18n.text("Szélesség"), () -> o.width, v -> {
            o.width = v; if (o.shape == SlabOpening.Shape.CIRCLE) o.depth = v;
        }, "cm", 100, 0.3, 500);
        if (o.shape != SlabOpening.Shape.CIRCLE) p.num(I18n.text("Hosszúság"), () -> o.depth, v -> o.depth = v, "cm", 100, 0.3, 500);
        p.num(I18n.text("Forgatás"), () -> o.angle, v -> o.angle = v, "°", 1, -3600, 3600);
        Form h = section(I18n.text("Szint és perem"));
        h.num(I18n.text("Abszolút felső szint"), () -> fill.elevation, v -> fill.elevation = v, "cm", 100, -100, 100);
        h.info(I18n.text("Eredeti terepszint"), () -> String.format(I18n.locale(), "%+.2f m",
                hu.helmetsoft.homedesigner.geom.TerrainGeometry.height(ed.project().env, o.x, o.y)));
        h.info(I18n.text("Aktív szint alapja"), () -> String.format(I18n.locale(), "%+.2f m", ed.project().activeLevel().baseHeight));
        h.check(I18n.text("Függőleges perem (támfallal megtartott)"), () -> fill.retaining, v -> { fill.retaining = v; shown = this; });
        if (!fill.retaining) h.num(I18n.text("Rézsű szélessége"), () -> fill.slopeWidth, v -> fill.slopeWidth = v, "cm", 100, 0.1, 30);
        section(I18n.text("Felső burkolat")).finish(null, Category.OTHER, () -> fill.finish);
        Label hint = new Label(I18n.text("A „Támfal a feltöltés köré” függőleges peremre állítja a feltöltést, és a körvonal külső oldalán önálló, szerkeszthető támfalakat hoz létre az aktív szinten (koronájuk a feltöltés szintje, aljuk a terepet követi). Egy lépésben visszavonható; később nem követi a feltöltés módosítását."));
        hint.setWrapText(true);
        box.getChildren().add(hint);
        buttons(button(I18n.text("Támfal a feltöltés köré"), () -> ed.addRetainingWallsAroundFill(fill)),
                button(I18n.text("Másolat"), () -> plan.duplicate(fill)), button(I18n.text("Törlés"), () -> ed.delete(fill)));
    }

    private void dimension(DimensionLine d) {
        heading(I18n.text("Méretvonal"), I18n.text("A végpontjai húzhatók, a vonal elhúzásával eltolható."));
        Form f = section(I18n.text("Méret"));
        f.num(I18n.text("Hossz"), d::length, v -> {
            double l = d.length();
            if (l < 1e-6) return;
            d.x2 = d.x1 + (d.x2 - d.x1) * v / l;
            d.y2 = d.y1 + (d.y2 - d.y1) * v / l;
        }, "cm", 100, 0.01, 1000);
        f.num(I18n.text("Eltolás"), () -> d.offset, v -> d.offset = v, "cm", 100, -50, 50);
        buttons(button(I18n.text("Törlés"), () -> ed.delete(d)));
    }

    private void project() {
        Project p = ed.project();
        heading(I18n.text("Projekt"), I18n.text("Kattints egy elemre a tulajdonságai szerkesztéséhez."));
        Form s = section(I18n.text("Áttekintés"));
        s.info(I18n.text("Falak"), () -> String.valueOf(p.walls.size()));
        s.info(I18n.text("Helyiségek"), () -> p.rooms.size() + I18n.text(" db, ") + String.format(I18n.locale(), "%.1f m²", p.rooms.stream().mapToDouble(r -> Math.abs(r.area())).sum()));
        s.info(I18n.text("Bútorok"), () -> String.valueOf(p.furniture.size()));
        s.info(I18n.text("Tetőelemek"), () -> String.valueOf(p.roofs.stream().mapToInt(r -> r.elements.size()).sum()));
        s.info(I18n.text("Erkélyek"), () -> String.valueOf(p.balconies.stream().filter(b -> !b.terrace).count()));
        s.info(I18n.text("Teraszok"), () -> String.valueOf(p.balconies.stream().filter(b -> b.terrace).count()));
        s.info(I18n.text("Támfalak"), () -> String.valueOf(p.retainingWalls.size()));
        s.info(I18n.text("Földfeltöltések"), () -> String.valueOf(p.env.siteFills.size()));
        Form rooms = section(I18n.text("Helyiségek és mennyezet"));
        if (p.rooms.isEmpty()) {
            Label missing = new Label(I18n.text("Nincs helyiségfelület a tervben. A falak önmagukban nem adnak padlót vagy mennyezetet. Rajzold körbe a belső területet a Padló (sokszög) eszközzel, majd a helyiségben kapcsold be a Van mennyezet beállítást."));
            missing.setWrapText(true);
            rooms.add(null, missing);
            rooms.add(null, button(I18n.text("Padló / helyiség rajzolása"), () -> ed.tool.set(Editor.Tool.FLOOR)));
        } else {
            List<Room> choices = List.copyOf(p.rooms);
            ComboBox<String> picker = new ComboBox<>();
            picker.setId("project-room-picker");
            for (int i=0;i<choices.size();i++) {
                Room r=choices.get(i);
                picker.getItems().add((i+1)+". "+r.name+" ("+fmt(r.area(),1)+" m²)");
            }
            picker.getSelectionModel().selectFirst();
            rooms.add(null, picker);
            rooms.add(null, button(I18n.text("Helyiség megnyitása"), () -> {
                int index=picker.getSelectionModel().getSelectedIndex();
                if(index>=0) {
                    ed.tool.set(Editor.Tool.SELECT);
                    ed.select(choices.get(index));
                    setVvalue(0);
                }
            }));
            Label hint = new Label(I18n.text("Itt válaszd ki a helyiséget, majd nyisd meg. A Mennyezet részben találod a Van mennyezet kapcsolót."));
            hint.setWrapText(true);
            rooms.add(null, hint);
        }
        Form d = section(I18n.text("Új falak alapértékei"));
        d.num(I18n.text("Falmagasság"), () -> p.wallHeight, v -> p.wallHeight = v, "cm", 100, 0.5, 20);
        d.num(I18n.text("Falvastagság"), () -> p.wallThickness, v -> p.wallThickness = v, "cm", 100, 0.02, 2);
        Environment env = p.env;
        Form site=section(I18n.text("Domboldal és alapozás"));
        site.num(I18n.text("Terep dőlésszöge"),() -> env.terrainSlope,v -> env.terrainSlope=v,"°",1,0,60);
        site.num(I18n.text("Emelkedés iránya"),() -> env.terrainAzimuth,v -> env.terrainAzimuth=v,"°",1,0,360);
        site.num(I18n.text("Terep referenciaszintje"),() -> env.terrainHeight,v -> env.terrainHeight=v,"cm",100,-100,100);
        site.num(I18n.text("Terep referencia X"),() -> env.terrainOriginX,v -> env.terrainOriginX=v,"m",1,-1000,1000);
        site.num(I18n.text("Terep referencia Y"),() -> env.terrainOriginY,v -> env.terrainOriginY=v,"m",1,-1000,1000);
        site.combo(I18n.text("Alapozás"),Environment.Foundation.values(),() -> env.foundation,v -> { env.foundation=v; shown=this; });
        if(env.foundation!=Environment.Foundation.NONE) {
            site.num(I18n.text("Alapmélység a terep alatt"),() -> env.foundationDepth,v -> env.foundationDepth=v,"cm",100,0.1,20);
            site.finish(I18n.text("Alap burkolata"),Category.OTHER,() -> env.foundationFinish);
        }
        Label siteHint=new Label(I18n.text("A terep lejt, az épületszintek vízszintesek maradnak. 0° irány az alaprajzi észak. Az alaptest a helyiségpadlók körvonalát követi; pince módban a talaj a helyiségek területén kivágódik."));
        siteHint.setWrapText(true);
        site.add(null,siteHint);
        Form earth=section(I18n.text("Tereprendezés"));
        earth.check(I18n.text("Automatikus rézsű az épület körül"),() -> env.autoEmbankment,v -> { env.autoEmbankment=v; shown=this; });
        if(env.autoEmbankment) {
            earth.num(I18n.text("Rézsű szélessége"),() -> env.embankmentWidth,v -> env.embankmentWidth=v,"cm",100,0.1,30);
            earth.num(I18n.text("Rézsű teteje a padlóhoz képest"),() -> env.embankmentTopOffset,v -> env.embankmentTopOffset=v,"cm",100,-5,0);
            earth.finish(I18n.text("Rézsű burkolata"),Category.OTHER,() -> env.embankmentFinish);
        }
        earth.add(null,button(I18n.text("Földfeltöltés rajzolása"),() -> ed.tool.set(Editor.Tool.FILL)));
        earth.add(null,button(I18n.text("Támfal rajzolása"),() -> ed.tool.set(Editor.Tool.RETAINING_WALL)));
        earth.add(null,button(I18n.text("Terasz rajzolása"),() -> ed.tool.set(Editor.Tool.TERRACE)));
        Form e = section(I18n.text("Napfény és környezet"));
        e.slider(I18n.text("Nap iránya"), 0, 360, () -> env.sunAzimuth, v -> env.sunAzimuth = v, "%.0f°");
        e.slider(I18n.text("Nap magassága"), 2, 90, () -> env.sunElevation, v -> env.sunElevation = v, "%.0f°");
        e.slider(I18n.text("Napfény"), 0, 3, () -> env.sunIntensity, v -> env.sunIntensity = v, "%.1f");
        e.slider(I18n.text("Égbolt fény"), 0, 3, () -> env.skyIntensity, v -> env.skyIntensity = v, "%.1f");
        e.slider(I18n.text("Expozíció"), 0.2, 4, () -> env.exposure, v -> env.exposure = v, "%.1f");
        e.check(I18n.text("Talaj megjelenítése"), () -> env.showGround, v -> env.showGround = v);
        e.finish(I18n.text("Talaj"), Category.OTHER, () -> env.ground);
        Label h = new Label(env.hdri == null ? I18n.text("nincs (beépített égbolt)") : new File(env.hdri).getName());
        h.setWrapText(true);
        Button pick = button(I18n.text("HDR égbolt..."), () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle(I18n.text("HDR környezeti kép (.hdr)"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("Radiance HDR"), "*.hdr"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null) edit(() -> {
                env.hdri = f.getAbsolutePath();
                shown = this;
            });
        });
        Button clear = button(I18n.text("Alap"), () -> edit(() -> {
            env.hdri = null;
            shown = this;
        }));
        HBox hb = new HBox(6, pick, clear);
        e.add(I18n.text("Égbolt kép"), h);
        e.add(null, hb);
    }
}
