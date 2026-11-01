package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Color editor with explicit resource-backed controls instead of the toolkit's untranslated popup. */
final class LocalizedColorPicker extends ColorPicker {
    LocalizedColorPicker() { this(Color.WHITE); }

    LocalizedColorPicker(Color color) {
        super(color);
        setStyle("-fx-color-label-visible: false;");
        setAccessibleText(I18n.text("Szín kiválasztása"));
    }

    @Override public void show() {
        Dialog<Color> dialog = new Dialog<>();
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle(I18n.text("Szín kiválasztása"));
        ButtonType accept = new ButtonType(I18n.text("Rendben"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(accept,
                new ButtonType(I18n.text("Mégse"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Color initial = getValue() == null ? Color.WHITE : getValue();
        Slider red = new Slider(0, 255, initial.getRed()*255);
        Slider green = new Slider(0, 255, initial.getGreen()*255);
        Slider blue = new Slider(0, 255, initial.getBlue()*255);
        TextField hex = new LocalizedTextField(FxUtil.hex(initial));
        Rectangle preview = new Rectangle(90, 40, initial);
        Label error = new Label();
        GridPane grid = new GridPane(10, 10);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label(I18n.text("Piros")), red);
        grid.addRow(1, new Label(I18n.text("Zöld")), green);
        grid.addRow(2, new Label(I18n.text("Kék")), blue);
        grid.addRow(3, new Label("RGB / HEX"), hex);
        grid.addRow(4, preview, error);
        Runnable changed = () -> {
            Color color = Color.rgb((int)Math.round(red.getValue()), (int)Math.round(green.getValue()), (int)Math.round(blue.getValue()));
            preview.setFill(color);
            hex.setText(FxUtil.hex(color));
            error.setText("");
        };
        for (Slider slider : new Slider[]{red, green, blue}) slider.valueProperty().addListener(o -> changed.run());
        Runnable applyHex = () -> {
            try {
                Color color = Color.web(hex.getText().trim());
                red.setValue(color.getRed()*255);
                green.setValue(color.getGreen()*255);
                blue.setValue(color.getBlue()*255);
                preview.setFill(color);
                error.setText("");
            } catch (IllegalArgumentException ex) { error.setText(I18n.text("Érvénytelen színkód")); }
        };
        hex.setOnAction(e -> applyHex.run());
        hex.focusedProperty().addListener((o, before, focused) -> { if (!focused) applyHex.run(); });
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().lookupButton(accept).addEventFilter(ActionEvent.ACTION, e -> {
            applyHex.run();
            if (!error.getText().isEmpty()) e.consume();
        });
        dialog.setResultConverter(button -> button == accept ? (Color)preview.getFill() : null);
        dialog.showAndWait().ifPresent(color -> {
            setValue(color);
            fireEvent(new ActionEvent());
        });
    }
}
