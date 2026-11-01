package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;

import java.util.Optional;

final class Dialogs {
    private Dialogs() {
    }

    static void error(String header, String text) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.getButtonTypes().setAll(new ButtonType(I18n.text("Bezárás"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        a.setTitle(I18n.text("Hiba"));
        a.setHeaderText(header);
        TextArea ta = new TextArea(text);
        LocalizedTextField.install(ta);
        ta.setWrapText(true);
        ta.setEditable(false);
        ta.setPrefRowCount(6);
        a.getDialogPane().setContent(ta);
        a.show();
    }

    static void info(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.getButtonTypes().setAll(new ButtonType(I18n.text("Rendben"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE));
        a.setTitle(I18n.text("HomeDesigner"));
        a.setHeaderText(header);
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(560);
        a.getDialogPane().setContent(l);
        a.showAndWait();
    }

    static Alert guide(String title, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.getButtonTypes().setAll(new ButtonType(I18n.text("Bezárás"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        a.setTitle(I18n.text("HomeDesigner"));
        a.setHeaderText(title);
        Label content = new Label(text);
        content.setWrapText(true);
        content.setPadding(new javafx.geometry.Insets(12));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(680);
        scroll.setPrefViewportHeight(440);
        a.getDialogPane().setContent(scroll);
        a.setResizable(true);
        return a;
    }

    /** Returns true for yes, false for no, null for cancel. */
    static Boolean yesNoCancel(String header, String text) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, new ButtonType(I18n.text("Igen"), javafx.scene.control.ButtonBar.ButtonData.YES),
                new ButtonType(I18n.text("Nem"), javafx.scene.control.ButtonBar.ButtonData.NO),
                new ButtonType(I18n.text("Mégse"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        a.setTitle(I18n.text("HomeDesigner"));
        a.setHeaderText(header);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty()) return null;
        return switch (r.get().getButtonData()) {
            case YES -> true;
            case NO -> false;
            default -> null;
        };
    }
}
