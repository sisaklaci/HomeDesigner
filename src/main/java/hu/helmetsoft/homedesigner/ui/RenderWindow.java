package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.Environment;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.model.Project;
import hu.helmetsoft.homedesigner.render.PathTracer;
import hu.helmetsoft.homedesigner.render.RenderCamera;
import hu.helmetsoft.homedesigner.render.RenderScene;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** "Fotó render" window: progressive path-traced image of the current 3D view. */
final class RenderWindow {
    private record Res(String label, int width) {
        @Override
        public String toString() {
            return I18n.display(label);
        }
    }

    private record Quality(String label, int passes) {
        @Override
        public String toString() {
            return I18n.display(label);
        }
    }

    private final Stage stage = new Stage();
    private final Editor ed;
    private final View3D view;
    private final ImageView iv = new ImageView();
    private final Label status = new Label(I18n.text("Nyomd meg a Render gombot!"));
    private final ProgressBar progress = new ProgressBar(0);
    private final ComboBox<Res> res = new ComboBox<>();
    private final ComboBox<Quality> quality = new ComboBox<>();
    private final CheckBox denoise = new CheckBox(I18n.text("Zajszűrés"));
    private final Slider exposure = new Slider(0.2, 4, 1);
    private final Button start = new Button(I18n.text("▶ Render"));
    private final Button stop = new Button(I18n.text("■ Leállítás"));
    private final Button save = new Button(I18n.text("💾 Mentés képként..."));
    private PathTracer tracer;
    private Environment env;
    private WritableImage image;
    private int maxPasses;
    private long t0;
    private final AtomicBoolean uiPending = new AtomicBoolean();
    private final AtomicLong lastUi = new AtomicLong();
    private volatile boolean cancelled;
    private String statusKey = "Nyomd meg a Render gombot!";
    private Object[] statusArguments = {};
    private double completedSeconds = -1;
    private final Label sizeLabel = new Label();
    private final Label qualityLabel = new Label();
    private final Label brightnessLabel = new Label();

    RenderWindow(Window owner, Editor ed, View3D view) {
        this.ed = ed;
        this.view = view;
        stage.initOwner(owner);
        stage.setTitle(I18n.text("Fotó render – HomeDesigner"));

        res.getItems().addAll(new Res("Kicsi (960 px)", 960), new Res("Közepes (1280 px)", 1280), new Res("Full HD (1920 px)", 1920), new Res("Nagy (2560 px)", 2560), new Res("4K (3840 px)", 3840));
        res.getSelectionModel().select(1);
        quality.getItems().addAll(new Quality("Gyors vázlat", 24), new Quality("Jó", 96), new Quality("Kiváló", 320), new Quality("Mestermű (lassú)", 1200), new Quality("Folyamatos (amíg le nem állítod)", 1_000_000));
        quality.getSelectionModel().select(1);
        denoise.setSelected(true);
        denoise.setOnAction(e -> refreshImage());
        exposure.setPrefWidth(140);
        exposure.valueProperty().addListener((o, a, b) -> {
            if (env != null) {
                env.exposure = b.doubleValue();
                refreshImage();
            }
        });
        start.getStyleClass().add("accent");
        start.setDefaultButton(true);
        start.setTooltip(new Tooltip(I18n.text("Új render a 3D nézet jelenlegi kameraállásából")));
        start.setOnAction(e -> render());
        stop.setOnAction(e -> {
            stopRender();
            Platform.runLater(this::refreshImage);
        });
        stop.setDisable(true);
        save.setDisable(true);
        save.setOnAction(e -> saveImage());

        sizeLabel.setText(I18n.text("Méret"));
        qualityLabel.setText(I18n.text("Minőség"));
        brightnessLabel.setText(I18n.text("Fényesség"));
        javafx.scene.layout.FlowPane top = new javafx.scene.layout.FlowPane(10, 6, sizeLabel, res, qualityLabel, quality, denoise, brightnessLabel, exposure, start, stop, save);
        top.getChildren().forEach(n -> ((javafx.scene.layout.Region) n).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8, 10, 8, 10));
        top.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        StackPane center = new StackPane(iv);
        center.setStyle("-fx-background-color: #2B2D31;");
        center.setMinSize(200, 150);
        iv.fitWidthProperty().bind(center.widthProperty().subtract(16));
        iv.fitHeightProperty().bind(center.heightProperty().subtract(16));

        progress.setPrefWidth(220);
        HBox bottom = new HBox(10, progress, status);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(6, 10, 6, 10));

        BorderPane bp = new BorderPane(center, top, null, bottom, null);
        Scene sc = new Scene(bp, 1180, 760);
        stage.setScene(sc);
        stage.setOnHidden(e -> stopRender());
    }

    void hide() {
        stage.hide();
    }

    void localize() {
        stage.setTitle(I18n.text("Fotó render – HomeDesigner"));
        sizeLabel.setText(I18n.text("Méret"));
        qualityLabel.setText(I18n.text("Minőség"));
        brightnessLabel.setText(I18n.text("Fényesség"));
        denoise.setText(I18n.text("Zajszűrés"));
        start.setText(I18n.text("▶ Render"));
        start.setTooltip(new Tooltip(I18n.text("Új render a 3D nézet jelenlegi kameraállásából")));
        stop.setText(I18n.text("■ Leállítás"));
        save.setText(I18n.text("💾 Mentés képként..."));
        int resolution = res.getSelectionModel().getSelectedIndex();
        int selectedQuality = quality.getSelectionModel().getSelectedIndex();
        res.setItems(javafx.collections.FXCollections.observableArrayList(res.getItems()));
        quality.setItems(javafx.collections.FXCollections.observableArrayList(quality.getItems()));
        res.getSelectionModel().select(resolution);
        quality.getSelectionModel().select(selectedQuality);
        setStatus(statusKey, statusArguments);
    }

    private void setStatus(String key, Object... arguments) {
        statusKey = key;
        statusArguments = arguments.clone();
        Object[] displayArguments = arguments.clone();
        if (key.equals("Renderelés... %d minta/pixel, %.0f mp%s") && displayArguments.length == 4
                && displayArguments[3] instanceof Number remaining)
            displayArguments[3] = remaining.doubleValue() < 0 ? ""
                    : I18n.text(", hátravan kb. %.0f mp", remaining.doubleValue());
        status.setText(I18n.text(key, displayArguments));
    }

    void show() {
        stage.show();
        stage.toFront();
        // always render what the 3D view currently shows
        render();
    }

    private void stopRender() {
        cancelled = true;
        if (tracer != null && tracer.isRunning()) completedSeconds = (System.currentTimeMillis() - t0) / 1000.0;
        if (tracer != null) tracer.stop();
        start.setDisable(false);
        stop.setDisable(true);
    }

    private void render() {
        stopRender();
        cancelled = false;
        completedSeconds = -1;
        Project p = ProjectIO.fromJson(ProjectIO.toJson(ed.project()));
        env = p.env;
        env.exposure = exposure.getValue();
        int w = res.getValue().width;
        int h = Math.max(64, (int) Math.round(w / view.aspect()));
        double[] eye = view.eye();
        RenderCamera cam = RenderCamera.of(eye[0], eye[1], eye[2], view.yaw(), view.pitch(), view.fov());
        boolean ceilings = view.showCeiling.isSelected(), roofs = view.showRoof.isSelected();
        maxPasses = quality.getValue().passes;
        start.setDisable(true);
        stop.setDisable(false);
        save.setDisable(true);
        progress.setProgress(-1);
        setStatus("Modellek betöltése...");
        image = new WritableImage(w, h);
        iv.setImage(image);

        Thread th = new Thread(() -> {
            try {
                List<CompletableFuture<?>> fs = new ArrayList<>();
                for (Furniture f : p.furniture)
                    if (f.modelPath != null) fs.add(ModelLibrary.get(f.modelPath, null).exceptionally(ex -> null));
                CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).join();
                if (cancelled) return;
                Platform.runLater(() -> setStatus("Jelenet előkészítése..."));
                RenderScene scene = RenderScene.build(p, ceilings, roofs);
                if (cancelled) return;
                PathTracer pt = new PathTracer(scene, cam, env, w, h);
                Platform.runLater(() -> {
                    if (cancelled) return;
                    tracer = pt;
                    t0 = System.currentTimeMillis();
                    pt.start(maxPasses, this::onPass);
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    stopRender();
                    progress.setProgress(0);
                    setStatus("Hiba: %s", ex.toString());
                    Dialogs.error(I18n.text("Render hiba"), String.valueOf(ex));
                });
            }
        }, "render-prepare");
        th.setDaemon(true);
        th.start();
    }

    private void onPass() {
        PathTracer pt = tracer;
        if (pt == null) return;
        boolean finished = pt.passes() >= maxPasses;
        long now = System.currentTimeMillis();
        long interval = pt.passes() < 8 ? 250 : 1000;
        if (!finished && now - lastUi.get() < interval) return;
        if (!uiPending.compareAndSet(false, true)) return;
        lastUi.set(now);
        boolean dn = finished && denoise.isSelected();
        int[] px = pt.image(dn);
        Platform.runLater(() -> {
            uiPending.set(false);
            if (pt != tracer) return;
            image.getPixelWriter().setPixels(0, 0, pt.width, pt.height, PixelFormat.getIntArgbInstance(), px, 0, pt.width);
            double sec = (System.currentTimeMillis() - t0) / 1000.0;
            int n = pt.passes();
            save.setDisable(false);
            if (finished || !pt.isRunning()) {
                progress.setProgress(1);
                completedSeconds = sec;
                setStatus("Kész: %d minta/pixel, %.0f mp  (%d × %d)", n, sec, pt.width, pt.height);
                start.setDisable(false);
                stop.setDisable(true);
                if (!finished) refreshImage();
            } else {
                double frac = maxPasses >= 1_000_000 ? -1 : (double) n / maxPasses;
                progress.setProgress(frac);
                double eta = frac > 0.01 && frac < 1 ? sec / frac - sec : -1;
                setStatus("Renderelés... %d minta/pixel, %.0f mp%s", n, sec, eta);
            }
        });
    }

    /** Re-reads the image (exposure / denoise change or after stopping). */
    private void refreshImage() {
        PathTracer pt = tracer;
        if (pt == null || pt.passes() == 0) return;
        boolean dn = denoise.isSelected() && !pt.isRunning();
        int[] px = pt.image(dn);
        image.getPixelWriter().setPixels(0, 0, pt.width, pt.height, PixelFormat.getIntArgbInstance(), px, 0, pt.width);
        if (!pt.isRunning()) {
            double seconds = completedSeconds < 0 ? (System.currentTimeMillis() - t0) / 1000.0 : completedSeconds;
            setStatus(pt.passes() >= maxPasses ? "Kész: %d minta/pixel, %.0f mp  (%d × %d)"
                    : "Leállítva: %d minta/pixel, %.0f mp  (%d × %d)", pt.passes(), seconds, pt.width, pt.height);
        }
    }

    private void saveImage() {
        PathTracer pt = tracer;
        if (pt == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.text("Kép mentése"));
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.text("PNG kép"), "*.png"), new FileChooser.ExtensionFilter(I18n.text("JPEG kép"), "*.jpg"));
        fc.setInitialFileName("render.png");
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            int[] px = pt.image(denoise.isSelected());
            String name = f.getName().toLowerCase();
            boolean jpg = name.endsWith(".jpg") || name.endsWith(".jpeg");
            BufferedImage bi = new BufferedImage(pt.width, pt.height, jpg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, pt.width, pt.height, px, 0, pt.width);
            if (!jpg && !name.endsWith(".png")) f = new File(f.getPath() + ".png");
            ImageIO.write(bi, jpg ? "jpg" : "png", f);
            setStatus("Elmentve: %s", f.getAbsolutePath());
        } catch (Exception ex) {
            Dialogs.error(I18n.text("Mentési hiba"), ex.toString());
        }
    }

    static void saveSnapshot(Window owner, WritableImage img) {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.text("3D nézet mentése képként"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("PNG kép"), "*.png"));
        fc.setInitialFileName("nezet.png");
        File f = fc.showSaveDialog(owner);
        if (f == null) return;
        try {
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            int[] px = new int[w * h];
            img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            bi.setRGB(0, 0, w, h, px, 0, w);
            ImageIO.write(bi, "png", f);
        } catch (Exception ex) {
            Dialogs.error(I18n.text("Mentési hiba"), ex.toString());
        }
    }
}
