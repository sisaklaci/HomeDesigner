package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.edit.DemoProject;
import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/** Main application window. */
public final class MainWindow {
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);
    private final Stage stage;
    private final Editor ed;
    private final PlanView plan;
    private final View3D view;
    private CatalogPanel catalog;
    private PropertiesPanel props;
    private RenderWindow renderWindow;
    private final BorderPane root;
    private final SplitPane main;
    private final java.util.List<Runnable> toolbarSubscriptions = new java.util.ArrayList<>();
    private final Label planTitle = new Label(I18n.text("Alaprajz"));

    public MainWindow(Stage stage, Project p) {
        this.stage = stage;
        ed = new Editor(p);
        plan = new PlanView(ed);
        view = new View3D(ed);
        catalog = new CatalogPanel(ed, plan);
        props = new PropertiesPanel(ed, plan);

        StackPane planBox = titled(plan, planTitle);
        StackPane viewBox = new StackPane(view);
        SplitPane views = new SplitPane(planBox, viewBox);
        views.setOrientation(Orientation.HORIZONTAL);
        views.setDividerPositions(0.5);
        main = new SplitPane(catalog, views, props);
        main.setDividerPositions(0.15, 0.80);
        SplitPane.setResizableWithParent(catalog, false);
        SplitPane.setResizableWithParent(props, false);

        root = new BorderPane(main);
        root.setTop(topBar());
        root.setBottom(statusBar());

        Scene scene = new Scene(root, 1500, 900);
        scene.getStylesheets().add(MainWindow.class.getResource("/hu/helmetsoft/homedesigner/app.css").toExternalForm());
        stage.setScene(scene);
        stage.titleProperty().bind(ed.title);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> {
            if (!view.canCloseVideo() || !confirmDiscard()) e.consume();
            else Platform.exit();
        });
        ed.loadModels();
    }

    private VBox topBar() {
        HBox menuRow = new HBox(menu(), languageSelector());
        HBox.setHgrow(menuRow.getChildren().getFirst(), Priority.ALWAYS);
        return new VBox(menuRow, toolbar(), levelBar());
    }

    private HBox levelBar() {
        ComboBox<BuildingLevel> levels=new ComboBox<>();
        levels.setId("building-level-selector");
        levels.setPrefWidth(285);
        boolean[] updating={false};
        Runnable refresh=() -> {
            updating[0]=true;
            levels.getItems().setAll(ed.project().levels);
            levels.setValue(ed.project().activeLevel());
            updating[0]=false;
        };
        refresh.run();
        toolbarSubscriptions.add(ed.onProject(refresh));
        levels.valueProperty().addListener((o,a,b) -> {
            if(!updating[0] && b!=null) ed.switchLevel(b.id);
        });
        Button add=new Button(I18n.text("+ Szint"));
        add.setId("building-level-add");
        add.setOnAction(e -> levelDialog(true));
        Button edit=new Button(I18n.text("Szint szerkesztése"));
        edit.setId("building-level-edit");
        edit.setOnAction(e -> levelDialog(false));
        Button remove=new Button(I18n.text("Szint törlése"));
        remove.setId("building-level-delete");
        remove.setOnAction(e -> {
            try { ed.deleteLevel(); }
            catch(IllegalArgumentException ex) { Dialogs.info(I18n.text("Épületszintek"),ex.getMessage()); }
        });
        Label hint=new Label(I18n.text("A 2D nézet a kiválasztott szintet, a 3D az egész épületet mutatja."));
        HBox bar=new HBox(10,new Label(I18n.text("Épületszint:")),levels,add,edit,remove,hint);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6,12,6,12));
        bar.setStyle("-fx-background-color: #e4edf6;");
        return bar;
    }

    private void levelDialog(boolean add) {
        BuildingLevel level=ed.project().activeLevel();
        Dialog<ButtonType> dialog=new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(I18n.text(add ? "Új épületszint" : "Szint szerkesztése"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        TextField name=new TextField(add ? I18n.text(level.baseHeight<0 ? "Földszint" : "Emelet") : I18n.display(level.name));
        TextField height=new TextField(String.format(java.util.Locale.ROOT,"%.2f",add ? level.baseHeight+3 : level.baseHeight));
        name.setId("building-level-name"); height.setId("building-level-height");
        GridPane grid=new GridPane(); grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(15));
        grid.addRow(0,new Label(I18n.text("Szint neve")),name);
        grid.addRow(1,new Label(I18n.text("Alapszint (m, abszolút)")),height);
        Label error=new Label(); error.setStyle("-fx-text-fill: #b42318;"); grid.add(error,0,2,2,1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
            try {
                double base=Double.parseDouble(height.getText().trim().replace(',','.'));
                if(add) ed.addLevel(name.getText(),base); else ed.updateLevel(name.getText(),base);
            } catch(IllegalArgumentException ex) {
                error.setText(I18n.text("Adj nevet és −1000 és +1000 m közötti véges alapszintet!"));
                e.consume();
            }
        });
        dialog.showAndWait();
    }

    public void show() {
        stage.setMaximized(true);
        stage.show();
    }

    /** Developer aid (-Dhd.test=true): walks through a few selections and opens the render window. */
    public void debugScenario() {
        var p = ed.project();
        var steps = new java.util.ArrayList<Runnable>();
        String turn = System.getProperty("hd.turntest");
        if (turn != null) {
            var t = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(8));
            t.setOnFinished(e -> view.debugTurnShots(turn));
            t.play();
            return;
        }
        if (!p.walls.isEmpty()) steps.add(() -> ed.select(p.walls.get(0)));
        if (!p.walls.isEmpty() && !p.walls.get(0).openings.isEmpty()) steps.add(() -> ed.select(p.walls.get(0).openings.get(0)));
        if (!p.rooms.isEmpty()) steps.add(() -> ed.select(p.rooms.get(0)));
        if (!p.roofs.isEmpty()) steps.add(() -> ed.select(p.roofs.get(0)));
        if (!p.lights.isEmpty()) steps.add(() -> ed.select(p.lights.get(0)));
        if (!p.furniture.isEmpty()) steps.add(() -> ed.select(p.furniture.get(0)));
        steps.add(this::photo);
        steps.add(() -> renderWindow.hide());
        steps.add(() -> {
            plan.requestFocus();
            view.setWalkMode(true);
            plan.requestFocus();
            javafx.event.Event.fireEvent(plan, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.W, false, false, false, false));
            javafx.event.Event.fireEvent(plan, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.RIGHT, false, false, false, false));
        });
        steps.add(() -> {
            javafx.event.Event.fireEvent(plan, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", javafx.scene.input.KeyCode.W, false, false, false, false));
            javafx.event.Event.fireEvent(plan, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", javafx.scene.input.KeyCode.RIGHT, false, false, false, false));
            System.out.println("eye after walk: " + java.util.Arrays.toString(view.eye()) + " yaw " + view.yaw());
            view.debugDrag(javafx.scene.input.MouseButton.SECONDARY, 100);
            System.out.println("after right drag: " + java.util.Arrays.toString(view.eye()) + " yaw " + view.yaw());
            view.debugDrag(javafx.scene.input.MouseButton.PRIMARY, -100);
            System.out.println("after left drag: " + java.util.Arrays.toString(view.eye()) + " yaw " + view.yaw());
        });
        steps.add(this::photo);
        var tl = new javafx.animation.Timeline();
        for (int i = 0; i < steps.size(); i++) {
            Runnable r = steps.get(i);
            tl.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(6 + i * 3), e -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }));
        }
        tl.play();
    }

    private static StackPane titled(javafx.scene.Node n, Label l) {
        l.getStyleClass().add("view-title");
        l.setMouseTransparent(true);
        StackPane sp = new StackPane(n, l);
        StackPane.setAlignment(l, Pos.TOP_RIGHT);
        StackPane.setMargin(l, new Insets(8, 10, 0, 0));
        sp.setMinWidth(150);
        return sp;
    }

    // ------------------------------------------------------------------ menu

    private MenuBar menu() {
        Menu file = new Menu(I18n.text("Fájl"));
        file.getItems().addAll(
                item(I18n.text("Új terv"), "Shortcut+N", this::newProject),
                item(I18n.text("Mintaház megnyitása"), null, () -> {
                    if (confirmDiscard()) ed.setProject(DemoProject.create(), null);
                }),
                item(I18n.text("Megnyitás..."), "Shortcut+O", this::open),
                new SeparatorMenuItem(),
                item(I18n.text("Mentés"), "Shortcut+S", this::save),
                item(I18n.text("Mentés másként..."), "Shortcut+Shift+S", this::saveAs),
                new SeparatorMenuItem(),
                item(I18n.text("3D nézet mentése képként..."), null, () -> RenderWindow.saveSnapshot(stage, view.snapshot())),
                item(I18n.text("Fotó render..."), "F9", this::photo),
                new SeparatorMenuItem(),
                item(I18n.text("Kilépés"), null, () -> {
                    if (view.canCloseVideo() && confirmDiscard()) Platform.exit();
                }));
        MenuItem undo = item(I18n.text("Visszavonás"), "Shortcut+Z", ed::undo);
        undo.disableProperty().bind(ed.canUndo.not());
        MenuItem redo = item(I18n.text("Újra"), "Shortcut+Y", ed::redo);
        redo.disableProperty().bind(ed.canRedo.not());
        Menu edit = new Menu(I18n.text("Szerkesztés"));
        edit.getItems().addAll(undo, redo, new SeparatorMenuItem(),
                item(I18n.text("Másolat"), "Shortcut+D", () -> plan.duplicate(ed.selection())),
                item(I18n.text("Törlés"), null, () -> ed.delete(ed.selection())),
                new SeparatorMenuItem(),
                item(I18n.text("Tető hozzáadása a házra"), null, this::addRoof),
                item(I18n.text("Egyenes lépcső hozzáadása"), null, () -> addStair(Stair.Type.STRAIGHT)),
                item(I18n.text("Csigalépcső hozzáadása"), null, () -> addStair(Stair.Type.SPIRAL)),
                item(I18n.text("Egyedi szekrény szerkesztése"), null, () -> { double[] c=plan.center(); ed.addCabinet(c[0],c[1]); }),
                item(I18n.text("Tetősíkablak hozzáadása"), null, () -> addRoofElement(RoofElement.Type.SKYLIGHT)),
                item(I18n.text("Kiugró tetőablak hozzáadása"), null, () -> addRoofElement(RoofElement.Type.DORMER)),
                item(I18n.text("Süllyesztett tetőterasz hozzáadása"), null, () -> addRoofElement(RoofElement.Type.TERRACE)),
                item(I18n.text("Homlokzati erkély hozzáadása"), null, ed::addBalcony),
                new SeparatorMenuItem(),
                item(I18n.text("Terasz rajzolása"), null, () -> ed.tool.set(Editor.Tool.TERRACE)),
                item(I18n.text("Támfal rajzolása"), null, () -> ed.tool.set(Editor.Tool.RETAINING_WALL)),
                item(I18n.text("Földfeltöltés rajzolása"), null, () -> ed.tool.set(Editor.Tool.FILL)));
        Menu tools = new Menu(I18n.text("Eszközök"));
        for (Editor.Tool t : Editor.Tool.values()) {
            MenuItem mi = item(I18n.display(t.label), null, () -> ed.tool.set(t));
            tools.getItems().add(mi);
        }
        CheckMenuItem dims = new CheckMenuItem(I18n.text("Méretek mutatása az alaprajzon"));
        dims.selectedProperty().bindBidirectional(ed.showDimensions);
        CheckMenuItem dims3 = new CheckMenuItem(I18n.text("Méretek a 3D nézetben"));
        dims3.selectedProperty().bindBidirectional(view.showDims.selectedProperty());
        CheckMenuItem roof = new CheckMenuItem(I18n.text("Tető a 3D nézetben"));
        roof.selectedProperty().bindBidirectional(view.showRoof.selectedProperty());
        CheckMenuItem ceil = new CheckMenuItem(I18n.text("Mennyezet a 3D nézetben"));
        ceil.selectedProperty().bindBidirectional(view.showCeiling.selectedProperty());
        Menu vm = new Menu(I18n.text("Nézet"));
        vm.getItems().addAll(dims, dims3, roof, ceil, new SeparatorMenuItem(),
                item(I18n.text("Alaprajz illesztése"), "Shortcut+0", plan::fit),
                item(I18n.text("3D kamera alaphelyzet"), null, view::resetCamera));
        Menu help = new Menu(I18n.text("Súgó"));
        help.getItems().addAll(item(I18n.text("Gyors útmutató"), "F1", this::showHelp), item(I18n.text("Névjegy"), null, () -> Dialogs.info("HomeDesigner 1.0",
                I18n.text("Belsőépítészeti 3D tervező.\n\nAlaprajz, falak, burkolatok, tető, bútor import (OBJ, BLEND, MAX, FBX, 3DS, DAE, GLTF...)\nés fotórealisztikus (path tracing) renderelés.\n\n© helmetsoft.com\nSisak László +36 70 70 17 963"))));
        return new MenuBar(file, edit, tools, vm, help);
    }

    private static MenuItem item(String text, String accel, Runnable r) {
        MenuItem mi = new MenuItem(text);
        if (accel != null) mi.setAccelerator(KeyCombination.keyCombination(accel));
        mi.setOnAction(e -> r.run());
        return mi;
    }

    // ------------------------------------------------------------------ toolbar

    private ToolBar toolbar() {
        ToolBar tb = new ToolBar();
        ToggleGroup g = new ToggleGroup();
        String[] icons = {"↖", "▬", "▭", "⬠", "🚪", "🪟", "💡", "↔"};
        for (Editor.Tool t : Editor.Tool.values()) {
            if (t == Editor.Tool.ARC_WALL || t == Editor.Tool.ROTUNDA || t.name().startsWith("SLAB_")
                    || t == Editor.Tool.TERRACE || t == Editor.Tool.RETAINING_WALL || t == Editor.Tool.FILL) continue;
            ToggleButton b = new ToggleButton((t.ordinal() < icons.length ? icons[t.ordinal()] + "  " : "") + I18n.display(t.label));
            b.setToggleGroup(g);
            b.setUserData(t);
            b.setTooltip(new Tooltip(I18n.display(t.hint)));
            b.setFocusTraversable(false);
            b.setOnAction(e -> {
                ed.tool.set(t);
                b.setSelected(true);
            });
            tb.getItems().add(b);
        }
        MenuButton curves = new MenuButton(I18n.text("Ívelt fal / kör"));
        for (Editor.Tool t : new Editor.Tool[]{Editor.Tool.WALL,Editor.Tool.ARC_WALL,Editor.Tool.ROTUNDA})
            curves.getItems().add(item(I18n.display(t.label),null,() -> ed.tool.set(t)));
        curves.setTooltip(new Tooltip(I18n.text("Egyenes fal, hárompontos körív vagy teljes rotunda")));
        javafx.beans.InvalidationListener updateCurves = o -> curves.setText(ed.tool.get() == Editor.Tool.ARC_WALL || ed.tool.get() == Editor.Tool.ROTUNDA
                ? I18n.display(ed.tool.get().label) : I18n.text("Ívelt fal / kör"));
        ed.tool.addListener(updateCurves);
        toolbarSubscriptions.add(() -> ed.tool.removeListener(updateCurves));
        updateCurves.invalidated(ed.tool);
        tb.getItems().add(2,curves);
        Runnable sync = () -> g.getToggles().forEach(tg -> tg.setSelected(tg.getUserData() == ed.tool.get()));
        javafx.beans.InvalidationListener syncTools = o -> sync.run();
        ed.tool.addListener(syncTools);
        toolbarSubscriptions.add(() -> ed.tool.removeListener(syncTools));
        sync.run();
        MenuButton roof = new MenuButton(I18n.text("🏠  Tető / erkély"));
        roof.setTooltip(new Tooltip(I18n.text("Tető, tetőablakok, tetőterasz és homlokzati erkély")));
        roof.getItems().add(item(I18n.text("Új tető a falakra"),null,this::addRoof));
        for (RoofElement.Type type : RoofElement.Type.values())
            roof.getItems().add(item("+ " + I18n.display(type.label),null,() -> addRoofElement(type)));
        roof.getItems().add(item(I18n.text("+ Homlokzati erkély"),null,ed::addBalcony));
        MenuButton stairs = new MenuButton(I18n.text("Lépcső"));
        for (Stair.Type type : Stair.Type.values())
            stairs.getItems().add(item(I18n.display(type.label),null,() -> addStair(type)));
        stairs.setTooltip(new Tooltip(I18n.text("Méretezhető egyenes és csigalépcső, állítható fokszámmal és korláttal")));
        MenuButton slabs = new MenuButton(I18n.text("Födémnyílás"));
        for (Editor.Tool t : new Editor.Tool[]{Editor.Tool.SLAB_RECT,Editor.Tool.SLAB_CIRCLE,Editor.Tool.SLAB_ELLIPSE,Editor.Tool.SLAB_POLYGON})
            slabs.getItems().add(item(I18n.display(t.label),null,() -> ed.tool.set(t)));
        slabs.setTooltip(new Tooltip(I18n.text("Téglalap, kör, ovális vagy szabad sokszög; külön mennyezet- és padlókivágás")));
        MenuButton site = new MenuButton(I18n.text("⛰  Terep / terasz"));
        for (Editor.Tool t : new Editor.Tool[]{Editor.Tool.TERRACE,Editor.Tool.RETAINING_WALL,Editor.Tool.FILL}) {
            MenuItem mi = item(I18n.display(t.label),null,() -> ed.tool.set(t));
            site.getItems().add(mi);
        }
        site.setTooltip(new Tooltip(I18n.text("Terasz, támfal és földfeltöltés rajzolása a lejtős terepen")));
        Button undo = new Button("↶");
        undo.setTooltip(new Tooltip(I18n.text("Visszavonás (Ctrl+Z)")));
        undo.disableProperty().bind(ed.canUndo.not());
        undo.setOnAction(e -> ed.undo());
        Button redo = new Button("↷");
        redo.setTooltip(new Tooltip(I18n.text("Újra (Ctrl+Y)")));
        redo.disableProperty().bind(ed.canRedo.not());
        redo.setOnAction(e -> ed.redo());
        CheckBox dims = new CheckBox(I18n.text("Méretek"));
        dims.selectedProperty().bindBidirectional(ed.showDimensions);
        dims.setFocusTraversable(false);
        Button photo = new Button(I18n.text("📷  Fotó render"));
        photo.setStyle("-fx-background-color: #1F6FEB; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6;");
        photo.getStyleClass().add("accent");
        photo.setTooltip(new Tooltip(I18n.text("Fotórealisztikus kép készítése a 3D nézet kameraállásából (F9)")));
        photo.setOnAction(e -> photo());
        Button levels=new Button(I18n.text("Padlószint"));
        levels.setTooltip(new Tooltip(I18n.display(Editor.Tool.FLOOR_LEVEL.hint)));
        levels.setOnAction(e -> ed.tool.set(Editor.Tool.FLOOR_LEVEL));
        tb.getItems().addAll(new Separator(), stairs, levels, slabs, roof, site, new Separator(), undo, redo, new Separator(), dims, new Separator(), photo);
        return tb;
    }

    private HBox statusBar() {
        Label hint = new Label();
        hint.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> I18n.display(ed.tool.get().hint), ed.tool));
        hint.getStyleClass().add("text-muted");
        Label st = new Label();
        st.textProperty().bind(ed.status);
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox hb = new HBox(10, hint, spacer, st);
        hb.setPadding(new Insets(4, 10, 4, 10));
        hb.getStyleClass().add("status-bar");
        return hb;
    }

    private HBox languageSelector() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton hu = new ToggleButton("Magyar", flag(false));
        ToggleButton en = new ToggleButton("English", flag(true));
        hu.setToggleGroup(group);
        en.setToggleGroup(group);
        hu.setUserData("hu");
        en.setUserData("en");
        hu.setId("language-hu");
        en.setId("language-en");
        group.selectToggle(I18n.locale().getLanguage().equals("en") ? en : hu);
        for (ToggleButton button : new ToggleButton[]{hu, en}) {
            button.setStyle("-fx-font-weight: bold; -fx-padding: 7 12;");
            button.setAccessibleText(button == hu ? "Magyar" : "English");
            button.setOnAction(event -> {
                if (!button.isSelected()) { button.setSelected(true); return; }
                String language = button.getUserData().toString();
                if (I18n.locale().getLanguage().equals(language)) return;
                if (!view.canCloseVideo()) {
                    group.selectToggle(I18n.locale().getLanguage().equals("en") ? en : hu);
                    return;
                }
                try {
                    I18n.persistLanguage(language);
                    changeLanguage(language);
                } catch (java.util.prefs.BackingStoreException | SecurityException ex) {
                    group.selectToggle(I18n.locale().getLanguage().equals("en") ? en : hu);
                    Dialogs.error(I18n.text("A nyelvválasztás nem menthető"), ex.getMessage());
                }
            });
        }
        HBox box = new HBox(6, new Label(I18n.text("Nyelv / Language")), hu, en);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(4, 10, 4, 8));
        return box;
    }

    private void changeLanguage(String language) {
        CatalogPanel.State state = catalog.state();
        double scroll = props.getVvalue();
        double[] dividers = main.getDividerPositions();
        props.dispose();
        catalog.dispose();
        toolbarSubscriptions.forEach(Runnable::run);
        toolbarSubscriptions.clear();
        I18n.setLanguage(language);
        catalog = new CatalogPanel(ed, plan);
        catalog.restore(state);
        props = new PropertiesPanel(ed, plan);
        props.setVvalue(scroll);
        main.getItems().set(0, catalog);
        main.getItems().set(2, props);
        main.setDividerPositions(dividers);
        SplitPane.setResizableWithParent(catalog, false);
        SplitPane.setResizableWithParent(props, false);
        root.setTop(topBar());
        root.setBottom(statusBar());
        planTitle.setText(I18n.text("Alaprajz"));
        view.localize();
        plan.localize();
        if (renderWindow != null) renderWindow.localize();
        ed.refreshLanguage();
    }

    // Vector flags remain legible on Windows even when the system emoji font lacks flag glyphs.
    private static javafx.scene.Node flag(boolean british) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(30, 20);
        var g = canvas.getGraphicsContext2D();
        if (!british) {
            g.setFill(javafx.scene.paint.Color.web("#CE2939")); g.fillRect(0, 0, 30, 7);
            g.setFill(javafx.scene.paint.Color.WHITE); g.fillRect(0, 7, 30, 6);
            g.setFill(javafx.scene.paint.Color.web("#477050")); g.fillRect(0, 13, 30, 7);
        } else {
            g.setFill(javafx.scene.paint.Color.web("#012169")); g.fillRect(0, 0, 30, 20);
            g.setStroke(javafx.scene.paint.Color.WHITE); g.setLineWidth(5);
            g.strokeLine(0, 0, 30, 20); g.strokeLine(0, 20, 30, 0);
            g.setStroke(javafx.scene.paint.Color.web("#C8102E")); g.setLineWidth(2);
            g.strokeLine(0, 0, 30, 20); g.strokeLine(0, 20, 30, 0);
            g.setFill(javafx.scene.paint.Color.WHITE); g.fillRect(11, 0, 8, 20); g.fillRect(0, 6, 30, 8);
            g.setFill(javafx.scene.paint.Color.web("#C8102E")); g.fillRect(13, 0, 4, 20); g.fillRect(0, 8, 30, 4);
        }
        return canvas;
    }

    // ------------------------------------------------------------------ actions

    private void addStair(Stair.Type type) {
        double[] center = plan.center();
        ed.addStair(type,center[0],center[1]);
    }

    private void addRoof() {
        ed.addRoof();
        view.showRoof.setSelected(true);
    }

    private void addRoofElement(RoofElement.Type type) {
        Roof r = ed.selection() instanceof Roof selected ? selected
                : ed.selection() instanceof RoofElement e ? ed.roofOf(e)
                : ed.project().roofs.size() == 1 ? ed.project().roofs.get(0) : null;
        if (r == null) {
            Dialogs.info(I18n.text("Tetőelem"),ed.project().roofs.isEmpty() ? I18n.text("Előbb adj hozzá egy tetőt!") : I18n.text("Előbb jelöld ki a kívánt tetőt az alaprajzon vagy a 3D nézetben!"));
            return;
        }
        view.showRoof.setSelected(true);
        ed.addRoofElement(r,type);
    }

    private void photo() {
        if (renderWindow == null) renderWindow = new RenderWindow(stage, ed, view);
        renderWindow.show();
    }

    private boolean confirmDiscard() {
        if (!ed.isDirty()) return true;
        Boolean r = Dialogs.yesNoCancel(I18n.text("Nem mentett változások"), I18n.text("Szeretnéd menteni a terv módosításait?"));
        if (r == null) return false;
        if (r) return save();
        return true;
    }

    private void newProject() {
        if (!confirmDiscard()) return;
        Project p = new Project();
        ed.setProject(p, null);
        ed.tool.set(Editor.Tool.WALL);
    }

    private FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("HomeDesigner terv (*.") + ProjectIO.EXT + ")", "*." + ProjectIO.EXT));
        String last = PREFS.get("projectDir", null);
        if (last != null && new File(last).isDirectory()) fc.setInitialDirectory(new File(last));
        return fc;
    }

    private void open() {
        if (!confirmDiscard()) return;
        File f = chooser(I18n.text("Terv megnyitása")).showOpenDialog(stage);
        if (f == null) return;
        PREFS.put("projectDir", f.getParent());
        try {
            ed.setProject(ProjectIO.load(f.toPath()), f.toPath());
            ed.loadModels();
        } catch (Exception ex) {
            Dialogs.error(I18n.text("A terv nem nyitható meg"), ex.toString());
        }
    }

    private boolean save() {
        if (ed.file() == null) return saveAs();
        return write(ed.file());
    }

    private boolean saveAs() {
        FileChooser fc = chooser(I18n.text("Terv mentése"));
        fc.setInitialFileName(ed.file() != null ? ed.file().getFileName().toString() : "terv." + ProjectIO.EXT);
        File f = fc.showSaveDialog(stage);
        if (f == null) return false;
        if (!f.getName().toLowerCase().endsWith("." + ProjectIO.EXT)) f = new File(f.getPath() + "." + ProjectIO.EXT);
        PREFS.put("projectDir", f.getParent());
        return write(f.toPath());
    }

    private boolean write(Path f) {
        try {
            ProjectIO.save(ed.project(), f);
            ed.saved(f);
            ed.status.set(I18n.text("Elmentve: ") + f);
            return true;
        } catch (Exception ex) {
            Dialogs.error(I18n.text("Mentési hiba"), ex.toString());
            return false;
        }
    }

    private void showHelp() {
        Dialogs.guide(I18n.text("Gyors útmutató"), I18n.text("""
                1. Falak: válaszd a „Fal” eszközt, kattints a sarokpontokra. Számot gépelve + Enter pontos hosszt ad.
                   Az első pontra kattintva bezárod a kört – ekkor a helyiség is létrejön.
                2. Szoba: a „Szoba” eszközzel téglalapot húzva falakkal együtt hozhatsz létre helyiséget.
                   „Ívelt fal / kör”: 3 pontos ív (kezdőpont, köztes ívpont, végpont), vagy
                   rotunda (középpont, sugár; szám + Enter = pontos sugár), automatikus körpadlóval.
                   A fal középső fogantyúja hajlít, a rotunda sugárfogantyúja a teljes kört méretezi.
                   A jobb oldali panelen az ívszög, ívhossz és sugár is megadható.
                3. Ajtó / ablak: válaszd az eszközt, majd kattints egy falra.
                   Íves falban is elhelyezhető. A szélesség/távolság a középíven mérendő;
                   a téglalap sík kerete a belső ív húrjára kerül. A nyílás legfeljebb 120°-ot foglalhat el.
                   „Felső él”: téglalap, félkör, negyedkör bal vagy jobb oldali magas ponttal.
                   A magasság az ív csúcsa. Félkörnél R = szélesség/2, negyedkörnél R = szélesség;
                   az ív alatt legalább 15 cm egyenes oldalfal kell. Az íves keret követi az íves falat.
                4. Bútor: a bal oldali katalógusból húzd be, vagy „Importálás...” (OBJ, BLEND, MAX, FBX...).
                   182 saját modell kategóriákba rendezve: székek, asztalok, kanapék, fotelek,
                   szekrények, szaniterek, fürdőszobabútorok, konyhafelszerelés, mosogatók,
                   konyhai gépek és ötféle hűtőszekrény. A kategóriaszűrő és a kereső együtt működik.
                   Szórakoztatóelektronika: TV-k, hi-fi, hangfalak, hangprojektor, játékkonzol.
                   Számítógépek és tartozékok, könyvek/könyvsor/halom, radiátorok és konvektorok,
                   valamint asztali, fali, mennyezeti, álló és oszlopventilátor is elhelyezhető.
                   Mennyezeti ventilátor: Emelés = mennyezetmagasság mínusz a modell magassága.
                   A gépek statikus látványelemek, nincs animáció vagy gépészeti méretezés.
                   Új kategóriák: Ágyak, Lámpatestek, Előszobabútorok, Villamos berendezések.
                   Lámpatest mellé a felső „Lámpa” eszközzel adj tényleges fényforrást.
                   Villanyóraszekrény, inverter és Wi-Fi modul: statikus látványmodell, nem bekötési terv.
                   TV-antenna és parabolaantenna is választható. Napelemek: tetőre vagy talajra
                   szerelhető panel, 0–85° között állítható valódi dőlésszöggel és követő tartószerkezettel.
                   A panel szélessége és hossza külön állítható; tetőre az Emeléssel és Forgatással illeszd.
                   Kerti bútorok és felszerelések: asztalok, székek, pad, napozóágy, kanapé,
                   szalonnasütő, grill, kétféle hintaágy, napernyő, virágláda és piknikasztal.
                   Kerti növények: lombos/gyümölcs-/nyírfa, fenyő, oszlopos/gömbtuja, bokor,
                   virágzó cserje, sövény, rózsa, tulipánok, virágágyás, szőlőtőke és kordonos szőlő.
                   Bojlerek és klímák: gázbojler, villanybojler, külön beltéri és kültéri klíma.
                   Medence és felszerelései: 6×3 m belméretű felszíni medence, kapaszkodókorlát,
                   vízforgató homokszűrővel. A korlát külön elem, Emeléssel illeszthető a peremre.
                   Kerti házak és tárolók: kerti kisház és szerszámtároló, statikus zárt ajtóval.
                   A medence nem vágja ki automatikusan a talajt; a vízfelület nem animált.
                   Szobanövények: fikusz, monstera, pálma, anyósnyelv, pozsgás, kaktusz,
                   orchidea és muskátli cserepben. Minden növény méretezhető és forgatható.
                   A falra/pultra kerülő tárgyak „Emelés” értékét állítsd a szerelési magasságra.
                   „+ Egyedi szekrény”: méretek, rekeszek, polcok, fiókok, ajtók és nyitott ajtók.
                   A korpusz, frontok és fogantyúk anyaga külön állítható. Ez látványterv, nem szabásjegyzék.
                   Ajtók: kétoldali kilincs; „Ajtólap üvegbetét → Részleges üvegezés”.
                   A betét bal/alsó széle, szélessége és magassága centiméterben állítható.
                   Az ív alatt és a széleken legalább 8 cm tömör szegély maradjon.
                5. Kijelölés: kattints bármire – jobb oldalt szerkesztheted a méretet, burkolatot, színt.
                   Bútor sarkait húzva átméretezhető, a kék gombbal forgatható (Shift: arányos).
                   Helyiség → „Padlószint (abszolút)”: például −51 cm = három 17 cm-es fellépés.
                   „Padlószint” eszköz vagy „+ Padlószint-terület”: rajzolj belső téglalapot.
                   A területnél 300×300 cm és −34 cm: 3×3 m-es, két fellépéssel süllyesztett rész.
                   A terület alakja téglalap, kör vagy ovális; a szinteltérés a szobához viszonyított.
                   „Lépcső létrehozása”: oldal, fokszám, szélesség és belépés után külön lépcső készül.
                   A szintterületek maradjanak a szobán belül, ne fedjék egymást.
                   A mennyezet, falak, ajtók és meglévő bútorok nem mozdulnak a padlóval.
                   Új bútor a helyi padlóra kerül; meglévőnél „Helyi padlóra helyezés”.
                   A séta követi a padlószintet és az egyenes lépcsőfokokat, nincs ütközésvizsgálat.
                6. Tető / erkély: a lenyíló menüből tető, tetősíkablak, kiugró tetőablak,
                   süllyesztett tetőterasz vagy korlátos erkély adható hozzá.
                   Több épületnél előbb jelöld ki a kívánt épület egyik falát vagy helyiségét!
                   Az új tető követi a zárt külső falakat, az íveket is. Rotundára kúptető készül;
                   a jobb oldalon kupolatetőre is válthatsz. A kör alap sugara külön méretezhető.
                   Falváltoztatás után: „Falakhoz igazítás” az adott tetőn.
                   Kúpra/kupolára jelenleg nem helyezhető tetőablak vagy tetőterasz.
                   Tetőelemek: válaszd ki a tetőt, majd a típust. Az alaprajzon húzhatók,
                   a jobb oldalon méretezhetők. Az X/Y helyük a tető sarkától mért távolság.
                7. 3D: bal egér forgatás, jobb egér mozgatás, görgő nagyítás, „Séta” mód W/A/S/D.
                   „REC”: válassz MP4 fájlt, majd járd be a tervet. „Stop”: felvétel lezárása és mentése.
                   Hang nélküli, 15 kép/mp-es, legfeljebb 960×540-es valós idejű 3D videó,
                   nem fotórealisztikus képkockák renderelése. Nincs külön telepítendő videókódoló.
                   Az időszámláló jelzi a felvételt; kilépés előtt várd meg a „Mentve” jelzést.
                   A „Mennyezet” induláskor látható, séta után is megmarad a beállítása.
                   A helyiség jobb paneljén a „Van mennyezet” is legyen bekapcsolva.
                   A Projekt panel „Helyiségek és mennyezet” listájából a „Helyiség megnyitása”
                   gombbal is elérheted, alaprajzi kijelölés nélkül. Ha nincs helyiségfelület,
                   a jelzett Padló eszközzel rajzold körbe a területet, majd engedélyezd a mennyezetét.
                   A külön rajzolt padló nem kap automatikusan mennyezetet.
                8. „Fotó render”: fotórealisztikus kép a 3D nézet kameraállásából.
                9. „Lépcső” menü (a katalógus alján is): egyenes vagy csigalépcső.
                   Jobb oldalon szintmagasság, fokszám, járólapvastagság, méretek és korlát;
                   csigánál fordulatszög, belső sugár és forgásirány is. Az „Alsó járószint”
                   az induló padlómagasság; a szintmagasság nem tartalmazza a korlátot.
                   A lépcső jobb paneljén a „Födémnyílás a lépcső fölé” gombok készítenek
                   szerkeszthető mennyezetnyílást. A nyílás nem követi később a lépcsőt.
                10. „Födémnyílás”: téglalap/ovális húzással, kör középpont + sugár,
                   szabad sokszög pontonként (Enter/dupla kattintás = lezárás).
                   A helyiséget előbb jelöld ki, ha több felület fedi egymást.
                   A jobb panelen külön kapcsolható a mennyezet- és padlókivágás.
                   A nyílás helyiséghatáron túli része nem vág másik helyiséget.
                   A nyílás ellenőrzéséhez a 3D „Mennyezet” kapcsolója legyen bekapcsolva.
                   Nincs automatikus emeletépítés vagy statikai/kivitelezési méretezés.
                11. Nyelv: a Magyar / English zászlókkal azonnal válthatsz, újraindítás nélkül.
                   A nyelvet megjegyezzük. A terv, kijelölés, kamera, fájlnév és visszavonási előzmények
                   megmaradnak; semmit nem mentünk vagy dobunk el. Videófelvétel közben előbb nyomd meg
                   a Stop gombot, és várd meg a mentés befejezését.

                Billentyűk: Del törlés, R forgatás, nyilak mozgatás, Ctrl+Z / Ctrl+Y, Ctrl+D másolat, Esc kilépés az eszközből.""") + "\n\n" + I18n.text("guide.storeys")).showAndWait();
    }
}
