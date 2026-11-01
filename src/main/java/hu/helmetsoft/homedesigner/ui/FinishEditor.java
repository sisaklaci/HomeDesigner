package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.MaterialLibrary.Category;
import hu.helmetsoft.homedesigner.material.MaterialLibrary.Preset;
import hu.helmetsoft.homedesigner.model.Finish;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.function.Supplier;

/** Editor for a surface covering: preset with thumbnail, tint color, pattern size, custom image. */
final class FinishEditor extends VBox {
    private static final java.util.Map<String,javafx.scene.image.Image> THUMBNAILS = new java.util.HashMap<>();
    private final Editor ed;
    private final Supplier<Finish> target;
    private final ComboBox<Preset> combo = new ComboBox<>();
    private final ColorPicker color = new LocalizedColorPicker();
    private final Slider scale = new Slider(0.25, 4, 1);
    private final Label scaleLbl = new Label();
    private final Button image = new Button(I18n.text("Kép..."));
    private boolean updating;

    FinishEditor(Editor ed, Category category, Supplier<Finish> target) {
        this.ed = ed;
        this.target = target;
        setSpacing(4);
        combo.getItems().setAll(MaterialLibrary.presets(category));
        combo.setVisibleRowCount(12);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setCellFactory(v -> new PresetCell());
        combo.setButtonCell(new PresetCell());
        HBox.setHgrow(combo, Priority.ALWAYS);
        color.getStyleClass().add("button");
        color.setPrefWidth(52);
        color.setMinWidth(52);
        color.setTooltip(new Tooltip(I18n.text("Szín / árnyalat")));
        Button reset = new Button("⟲");
        reset.setTooltip(new Tooltip(I18n.text("Eredeti szín")));
        HBox row1 = new HBox(4, combo, color, reset);
        row1.setAlignment(Pos.CENTER_LEFT);

        scale.setTooltip(new Tooltip(I18n.text("Minta mérete (csempe, deszka, tégla nagysága)")));
        HBox.setHgrow(scale, Priority.ALWAYS);
        scaleLbl.setMinWidth(38);
        image.setTooltip(new Tooltip(I18n.text("Saját textúra kép (fotó) használata")));
        HBox row2 = new HBox(4, new Label(I18n.text("Minta")), scale, scaleLbl, image);
        row2.setAlignment(Pos.CENTER_LEFT);
        for (var n : row2.getChildren()) if (n != scale) ((Region) n).setMinWidth(Region.USE_PREF_SIZE);
        reset.setMinWidth(Region.USE_PREF_SIZE);
        combo.setMinWidth(80);
        combo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(combo, Priority.ALWAYS);
        getChildren().addAll(row1, row2);

        combo.valueProperty().addListener((o, a, b) -> {
            if (updating || b == null) return;
            Finish f = target.get();
            if (b.id.equals(f.preset)) return;
            ed.checkpoint();
            f.preset = b.id;
            f.color = null;
            if ("custom".equals(b.id) && f.texture == null) {
                pickImage(f);
            }
            done();
        });
        color.setOnAction(e -> {
            if (updating) return;
            ed.checkpoint();
            target.get().color = FxUtil.hex(color.getValue());
            done();
        });
        reset.setOnAction(e -> {
            ed.checkpoint();
            target.get().color = null;
            done();
        });
        scale.valueChangingProperty().addListener((o, a, b) -> {
            if (b && !updating) ed.checkpoint();
            if (!b) done();
        });
        scale.valueProperty().addListener((o, a, b) -> {
            scaleLbl.setText(String.format(I18n.locale(), "%.0f%%", b.doubleValue() * 100));
            if (updating) return;
            if (!scale.isValueChanging()) ed.checkpoint();
            target.get().scale = Math.round(b.doubleValue() * 20) / 20.0;
            if (!scale.isValueChanging()) done();
        });
        image.setOnAction(e -> {
            Finish f = target.get();
            ed.checkpoint();
            if (pickImage(f)) {
                f.preset = "custom";
                done();
            }
        });
        refresh();
    }

    private boolean pickImage(Finish f) {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.text("Textúra kép kiválasztása"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("Képek"), "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif", "*.tga"));
        File file = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return false;
        f.texture = file.getAbsolutePath();
        return true;
    }

    private void done() {
        refresh();
        ed.changed();
    }

    void refresh() {
        updating = true;
        Finish f = target.get();
        Preset p = MaterialLibrary.preset(f.preset);
        combo.setValue(p);
        String c = f.color != null ? f.color : p.defColor;
        color.setValue(FxUtil.parse(c, Color.WHITE));
        color.setDisable(!p.tintable && !"custom".equals(p.id));
        scale.setValue(f.scale > 0 ? f.scale : 1);
        scaleLbl.setText(String.format(I18n.locale(), "%.0f%%", scale.getValue() * 100));
        updating = false;
    }

    static Node thumb(Preset p, String tint, double size) {
        StackPane sp = new StackPane();
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        var t = MaterialLibrary.texture(p.id);
        Color c = FxUtil.parse(tint != null ? tint : p.defColor, Color.WHITE);
        if (t != null) {
            ImageView iv = new ImageView(THUMBNAILS.computeIfAbsent(p.id,k -> FxUtil.image(t.limit(64))));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setSmooth(true);
            sp.getChildren().add(iv);
            if (p.tintable) {
                Rectangle r = new Rectangle(size, size, c);
                r.setBlendMode(BlendMode.MULTIPLY);
                sp.getChildren().add(r);
            }
        } else {
            sp.getChildren().add(new Rectangle(size, size, c));
        }
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(6);
        clip.setArcHeight(6);
        sp.setClip(clip);
        return sp;
    }

    private static final class PresetCell extends ListCell<Preset> {
        @Override
        protected void updateItem(Preset p, boolean empty) {
            super.updateItem(p, empty);
            if (empty || p == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(I18n.display(p.name));
                setGraphic(thumb(p, null, 22));
            }
        }
    }
}
