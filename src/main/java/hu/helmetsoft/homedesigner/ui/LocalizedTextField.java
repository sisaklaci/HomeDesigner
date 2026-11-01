package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;
import javafx.scene.control.*;

/** Text-input actions belong to the selected application language, not the OS/toolkit locale. */
final class LocalizedTextField extends TextField {
    LocalizedTextField() { install(this); }
    LocalizedTextField(String text) { super(text); install(this); }

    static void install(TextInputControl field) {
        ContextMenu menu = new ContextMenu();
        MenuItem undo = action(I18n.text("Visszavonás"), field::undo);
        undo.disableProperty().bind(field.undoableProperty().not());
        MenuItem redo = action(I18n.text("Újra"), field::redo);
        redo.disableProperty().bind(field.redoableProperty().not());
        MenuItem cut = action(I18n.text("Kivágás"), field::cut);
        MenuItem copy = action(I18n.text("Másolás"), field::copy);
        MenuItem paste = action(I18n.text("Beillesztés"), field::paste);
        MenuItem delete = action(I18n.text("Törlés"), () -> field.replaceSelection(""));
        MenuItem select = action(I18n.text("Összes kijelölése"), field::selectAll);
        cut.disableProperty().bind(field.editableProperty().not().or(field.selectedTextProperty().isEmpty()));
        copy.disableProperty().bind(field.selectedTextProperty().isEmpty());
        paste.disableProperty().bind(field.editableProperty().not());
        delete.disableProperty().bind(cut.disableProperty());
        menu.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste, delete,
                new SeparatorMenuItem(), select);
        field.setContextMenu(menu);
    }

    private static MenuItem action(String text, Runnable run) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> run.run());
        return item;
    }
}
