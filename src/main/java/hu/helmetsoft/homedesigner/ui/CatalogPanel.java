package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.AppPaths;
import hu.helmetsoft.homedesigner.io.BuiltinModels;
import hu.helmetsoft.homedesigner.io.HomeModels;
import hu.helmetsoft.homedesigner.model.Stair;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ModelLoader;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/** Furniture catalog: model files from the built-in and user folders. */
public final class CatalogPanel extends VBox {
    public static final String DND_PREFIX = "hd-model:";
    private static final Preferences PREFS = Preferences.userNodeForPackage(CatalogPanel.class);

    private final Editor ed;
    private final PlanView plan;
    private final Path bundled;
    private final ListView<Path> list = new ListView<>();
    private final TextField filter = new LocalizedTextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final List<Path> all = new ArrayList<>();
    private final Set<String> previewsRequested = new HashSet<>();

    private static final Set<ListView<Path>> observedLists = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Runnable modelLoadedListener =
            () -> Platform.runLater(() -> List.copyOf(observedLists).forEach(ListView::refresh));
    private static boolean observingModels;

    private static void observe(ListView<Path> list) {
        observedLists.add(list);
        if (!observingModels) {
            observingModels = true;
            ModelLibrary.onLoaded(modelLoadedListener);
        }
    }

    void dispose() { observedLists.remove(list); }

    record State(String query, String categoryKey, Path selected) {}

    State state() {
        String selected = category.getValue();
        String key = "Minden modell";
        if (I18n.text("Fürdőszoba").equals(selected)) key = "Fürdőszoba";
        else for (var value : HomeModels.Category.values())
            if (I18n.display(value.label).equals(selected)) key = value.label;
        return new State(filter.getText(), key, list.getSelectionModel().getSelectedItem());
    }

    void restore(State state) {
        filter.setText(state.query());
        category.setValue(I18n.display(state.categoryKey()));
        list.getSelectionModel().select(state.selected());
        if (state.selected() != null) list.scrollTo(state.selected());
    }

    public CatalogPanel(Editor ed, PlanView plan) {
        this(ed,plan,AppPaths.objectsDir());
    }

    CatalogPanel(Editor ed, PlanView plan, Path bundled) {
        this.ed = ed;
        this.plan = plan;
        this.bundled = bundled;
        setSpacing(6);
        setPadding(new Insets(8));
        setMinWidth(170);
        setPrefWidth(230);

        Label title = new Label(I18n.text("Bútorok, tárgyak"));
        title.getStyleClass().add("title-4");
        filter.setPromptText(I18n.text("Keresés..."));
        filter.textProperty().addListener(o -> applyFilter());
        category.getItems().addAll(I18n.text("Minden modell"),I18n.text("Fürdőszoba"));
        Arrays.stream(HomeModels.Category.values()).map(c -> I18n.display(c.label))
                .sorted(I18n.collator()).forEach(category.getItems()::add);
        category.getSelectionModel().selectFirst();
        category.setMaxWidth(Double.MAX_VALUE);
        category.valueProperty().addListener(o -> applyFilter());

        list.setCellFactory(v -> new ModelCell());
        observe(list);
        list.setPlaceholder(new Label(I18n.text("Nincs modell.\nImportálj OBJ / BLEND / MAX / FBX fájlt!")));
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) addSelected();
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) addSelected();
        });

        Button imp = new Button(I18n.text("Importálás..."));
        imp.setMaxWidth(Double.MAX_VALUE);
        imp.setTooltip(new Tooltip(I18n.text("3D modell fájl betöltése (OBJ, BLEND, MAX, FBX, 3DS, DAE, GLTF...)")));
        imp.setOnAction(e -> importFiles());
        Button folder = new Button(I18n.text("Mappa..."));
        folder.setMaxWidth(Double.MAX_VALUE);
        folder.setTooltip(new Tooltip(I18n.text("Egy teljes modellkönyvtár hozzáadása a katalógushoz")));
        folder.setOnAction(e -> addFolder());
        HBox.setHgrow(imp, Priority.ALWAYS);
        HBox.setHgrow(folder, Priority.ALWAYS);
        HBox buttons = new HBox(6, imp, folder);

        Label hint = new Label(I18n.text("Dupla kattintás vagy húzd az alaprajzra / 3D nézetbe!"));
        hint.setWrapText(true);
        hint.getStyleClass().add("text-muted");
        hint.setStyle("-fx-font-size: 11;");
        MenuButton stairs = new MenuButton(I18n.text("+ Lépcső"));
        stairs.setMaxWidth(Double.MAX_VALUE);
        for (Stair.Type type : Stair.Type.values()) {
            MenuItem add = new MenuItem(I18n.display(type.label));
            add.setOnAction(e -> {
                double[] center = plan.center();
                ed.addStair(type,center[0],center[1]);
            });
            stairs.getItems().add(add);
        }
        Button cabinet=new Button(I18n.text("+ Egyedi szekrény"));
        cabinet.setMaxWidth(Double.MAX_VALUE);
        cabinet.setOnAction(e -> { double[] center=plan.center(); ed.addCabinet(center[0],center[1]); });
        getChildren().addAll(title, category, filter, list, cabinet, stairs, buttons, hint);
        refresh();
    }

    private List<Path> folders() {
        List<Path> res = new ArrayList<>();
        if (bundled != null) res.add(bundled);
        for (String s : PREFS.get("folders", "").split("\\|"))
            if (!s.isBlank()) res.add(Path.of(s));
        return res;
    }

    public void refresh() {
        all.clear();
        Set<String> seen = new HashSet<>();
        for (String s : PREFS.get("files", "").split("\\|"))
            if (!s.isBlank() && Files.isRegularFile(Path.of(s)) && seen.add(s)) all.add(Path.of(s));
        for (Path d : folders()) {
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> st = Files.walk(d, 3)) {
                st.filter(Files::isRegularFile).filter(ModelLoader::isModelFile)
                        .filter(p -> seen.add(p.toAbsolutePath().toString()))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                        .forEach(all::add);
            } catch (IOException ex) {
                ed.status.set(I18n.text("A modellmappa nem olvasható: ")+d+" – "+ex.getMessage());
            }
        }
        all.sort(displayOrder(I18n.locale()));
        applyFilter();
    }

    private void applyFilter() {
        String f = searchText(filter.getText() == null ? "" : filter.getText());
        boolean bathOnly = I18n.text("Fürdőszoba").equals(category.getValue());
        list.getItems().setAll(all.stream().filter(p -> I18n.text("Minden modell").equals(category.getValue())
                        || bathOnly && bathroom(p) || I18n.display(HomeModels.category(p).label).equals(category.getValue()))
                .filter(p -> matchesSearch(p, f, I18n.locale())).toList());
    }

    static boolean matchesSearch(Path path, String query, Locale locale) {
        String normalized = searchText(query);
        return normalized.isEmpty() || searchText(displayName(path, locale)).contains(normalized)
                || searchText(path.getFileName().toString()).contains(normalized);
    }

    static String displayName(Path path) {
        return displayName(path, I18n.locale());
    }

    static String displayName(Path path, Locale locale) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        if (HomeModels.category(path) == HomeModels.Category.IMPORTED) return base.replace('_', ' ');
        var bundle = I18n.bundle(locale);
        String key = "asset." + base;
        return bundle.containsKey(key) ? bundle.getString(key) : base.replace('_', ' ');
    }

    static Comparator<Path> displayOrder(Locale locale) {
        java.text.Collator collator = java.text.Collator.getInstance(locale);
        return Comparator.comparing((Path p) -> displayName(p, locale), collator)
                .thenComparing(Path::toString);
    }

    private static String searchText(String text) {
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT).replace('_',' ').replace('-',' ').trim();
    }

    private static boolean bathroom(Path p) {
        return p.getParent() != null && BuiltinModels.FOLDER.equals(p.getParent().getFileName().toString());
    }

    private void addSelected() {
        Path p = list.getSelectionModel().getSelectedItem();
        if (p == null) return;
        double[] c = plan.center();
        ed.addFurniture(p.toAbsolutePath().toString(), c[0], c[1]);
    }

    private void importFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.text("3D modell importálása"));
        List<String> ext = new ArrayList<>();
        for (String e : ModelLoader.EXTENSIONS) ext.add("*." + e);
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.text("3D modellek"), ext),
                new FileChooser.ExtensionFilter(I18n.text("Wavefront OBJ"), "*.obj"),
                new FileChooser.ExtensionFilter(I18n.text("Blender"), "*.blend"),
                new FileChooser.ExtensionFilter("3ds Max", "*.max", "*.3ds"),
                new FileChooser.ExtensionFilter(I18n.text("Minden fájl"), "*.*"));
        String last = PREFS.get("lastDir", null);
        if (last != null && new File(last).isDirectory()) fc.setInitialDirectory(new File(last));
        List<File> files = fc.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        PREFS.put("lastDir", files.get(0).getParent());
        LinkedHashSet<String> stored = new LinkedHashSet<>(Arrays.asList(PREFS.get("files", "").split("\\|")));
        stored.remove("");
        double[] c = plan.center();
        for (File f : files) {
            stored.add(f.getAbsolutePath());
            ed.addFurniture(f.getAbsolutePath(), c[0], c[1]);
            c[0] += 0.6;
        }
        String joined = String.join("|", stored);
        if (joined.length() > 7000) joined = joined.substring(joined.length() - 7000);
        PREFS.put("files", joined);
        refresh();
    }

    private void addFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(I18n.text("Modellkönyvtár hozzáadása"));
        File d = dc.showDialog(getScene().getWindow());
        if (d == null) return;
        LinkedHashSet<String> f = new LinkedHashSet<>(Arrays.asList(PREFS.get("folders", "").split("\\|")));
        f.remove("");
        f.add(d.getAbsolutePath());
        PREFS.put("folders", String.join("|", f));
        refresh();
    }

    private final class ModelCell extends ListCell<Path> {
        private final ImageView icon = new ImageView();
        private Path shown;

        ModelCell() {
            icon.setFitWidth(40);
            icon.setFitHeight(40);
            icon.setPreserveRatio(true);
            setOnDragDetected(e -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.COPY);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(DND_PREFIX + getItem().toAbsolutePath());
                db.setContent(cc);
                if (icon.getImage() != null) db.setDragView(icon.getImage());
                e.consume();
            });
            ContextMenu cm = new ContextMenu();
            MenuItem add = new MenuItem(I18n.text("Hozzáadás a tervhez"));
            add.setOnAction(e -> addSelected());
            MenuItem prev = new MenuItem(I18n.text("Előnézet betöltése"));
            prev.setOnAction(e -> loadPreview(getItem()));
            cm.getItems().addAll(add, prev);
            setContextMenu(cm);
        }

        @Override
        protected void updateItem(Path p, boolean empty) {
            super.updateItem(p, empty);
            shown = p;
            if (empty || p == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            String n = p.getFileName().toString();
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 ? n.substring(dot + 1).toUpperCase() : "";
            setText(null);
            setTooltip(new Tooltip(p.toString()));
            String key = p.toAbsolutePath().toString();
            var md = ModelLibrary.now(key);
            if (md == null && HomeModels.category(p)!=HomeModels.Category.IMPORTED && ModelLibrary.error(key) == null && previewsRequested.add(key)) {
                ModelLibrary.get(key,null).whenComplete((model,ex) -> Platform.runLater(() -> {
                    if (ex != null) ed.status.set(I18n.text("Előnézeti hiba: ")+p.getFileName()+" – "+ModelLibrary.error(key));
                    list.refresh();
                }));
            }
            var t = md != null ? ModelLibrary.topView(key, 0) : null;
            icon.setImage(t == null ? null : FxUtil.image(t));
            Label glyph = new Label("3D");
            glyph.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #9CA3AF;");
            StackPane pic = new StackPane(icon.getImage() != null ? icon : glyph);
            pic.setMinSize(40, 40);
            pic.setPrefSize(40, 40);
            pic.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 6;");
            Label name = new Label(displayName(p));
            name.setStyle("-fx-font-weight: bold;");
            Label badge = new Label(ext);
            badge.setStyle("-fx-font-size: 10; -fx-text-fill: #6B7280;");
            VBox txt = new VBox(1, name, badge);
            txt.setAlignment(Pos.CENTER_LEFT);
            HBox row = new HBox(8, pic, txt);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }

        private void loadPreview(Path p) {
            if (p == null) return;
            ModelLibrary.get(p.toAbsolutePath().toString(), null).whenComplete((m, ex) -> Platform.runLater(() -> {
                if (shown == p) updateItem(p, false);
                list.refresh();
            }));
        }
    }
}
