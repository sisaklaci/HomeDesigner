package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class LiveLocalizationTest {
    @Test void flagsSwitchImmediatelyWithoutReplacingEditorViewsOrHistory() throws Exception {
        Path log = Path.of("target", "localization-live-test.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dhd.language=hu", "-cp", classpath, Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Live-localization probe timed out: " + Files.readString(log));
        }
        assertEquals(0, process.exitValue(), Files.readString(log));
    }

    /** Separate toolkit process so this test never interferes with other JavaFX test classes. */
    public static final class Probe {
        public static void main(String[] args) throws Exception {
            var result = new CompletableFuture<Void>();
            Platform.startup(() -> {
                try { verify(); result.complete(null); }
                catch (Throwable ex) { result.completeExceptionally(ex); }
            });
            try { result.get(75, TimeUnit.SECONDS); }
            catch (Throwable ex) { ex.printStackTrace(); System.exit(1); }
            Platform.exit();
            System.exit(0);
        }

        private static void verify() throws Exception {
            Preferences preferences = Preferences.userNodeForPackage(I18n.class);
            String savedLanguage = preferences.get("language", null);
            Stage stage = new Stage();
            try {
                I18n.setLanguage("hu");
                Project project = new Project();
                Room room = ProjectOps.addRoomWithWalls(project, ProjectOps.rect(0, 0, 6, 4), "Saját / Custom room");
                MainWindow window = new MainWindow(stage, project);
                Editor editor = field(window, "ed");
                PlanView plan = field(window, "plan");
                View3D view = field(window, "view");
                var scene = stage.getScene();
                CatalogPanel catalog = field(window, "catalog");
                ComboBox<String> category = field(catalog, "category");
                category.setValue(I18n.text("Napelemek"));
                ((TextField)field(catalog, "filter")).setText("napelem");
                RenderWindow render = new RenderWindow(stage, editor, view);
                setField(window, "renderWindow", render);
                ((ComboBox<?>)field(render, "res")).getSelectionModel().select(2);
                ((ComboBox<?>)field(render, "quality")).getSelectionModel().select(3);
                Path filename = Path.of("localization-custom-project.hdp");
                editor.setProject(project, filename);
                editor.select(room);
                editor.edit(() -> room.ceilingHeight = 3.1);
                view.setWalkMode(true);
                view.showRoof.setSelected(false);
                view.showCeiling.setSelected(false);
                double[] eye = view.eye();
                double yaw = view.yaw(), pitch = view.pitch();
                String snapshot = editor.snapshot();
                Object selection = editor.selection();
                int listeners = ((List<?>)field(editor, "changeListeners")).size();
                int projectListeners = ((List<?>)field(editor, "projectListeners")).size();
                int selectionListeners = ((List<?>)field(editor, "selectionListeners")).size();
                setDouble(plan, "scale", 73);
                setDouble(plan, "offX", 123);
                setDouble(plan, "offY", 245);
                for (String language : List.of("en", "hu", "en", "hu", "en")) {
                    ((ToggleButton)stage.getScene().lookup("#language-" + language)).fire();
                    assertEquals(language, I18n.locale().getLanguage());
                    assertSame(editor, field(window, "ed"));
                    assertSame(scene, stage.getScene());
                    assertSame(plan, field(window, "plan"));
                    assertSame(view, field(window, "view"));
                    assertEquals(snapshot, editor.snapshot());
                    assertSame(selection, editor.selection());
                    assertEquals(filename, editor.file());
                    assertTrue(editor.isDirty());
                    assertTrue(editor.canUndo.get());
                    assertArrayEquals(eye, view.eye(), 1e-12);
                    assertEquals(yaw, view.yaw());
                    assertEquals(pitch, view.pitch());
                    assertTrue(view.isWalk());
                    assertFalse(view.showRoof.isSelected());
                    assertFalse(view.showCeiling.isSelected());
                    assertEquals(73, (double)field(plan, "scale"));
                    assertEquals(123, (double)field(plan, "offX"));
                    assertEquals(245, (double)field(plan, "offY"));
                    assertEquals(listeners, ((List<?>)field(editor, "changeListeners")).size());
                    assertEquals(projectListeners, ((List<?>)field(editor, "projectListeners")).size());
                    assertEquals(selectionListeners, ((List<?>)field(editor, "selectionListeners")).size());
                    PropertiesPanel props = field(window, "props");
                    assertTrue(props.getContent().lookupAll(".label").stream().anyMatch(n -> n instanceof Label label
                            && label.getText().equals(language.equals("en") ? "Name" : "Név")));
                    CatalogPanel currentCatalog = field(window, "catalog");
                    assertEquals(I18n.text("Napelemek"), ((ComboBox<?>)field(currentCatalog, "category")).getValue());
                    assertEquals("napelem", ((TextField)field(currentCatalog, "filter")).getText());
                    assertSame(render, field(window, "renderWindow"));
                    assertEquals(2, ((ComboBox<?>)field(render, "res")).getSelectionModel().getSelectedIndex());
                    assertEquals(3, ((ComboBox<?>)field(render, "quality")).getSelectionModel().getSelectedIndex());
                    assertEquals(I18n.text("Zajszűrés"), ((CheckBox)field(render, "denoise")).getText());
                }
                assertEquals("Roof", view.showRoof.getText());
                assertEquals("en", preferences.get("language", null));
                assertEquals("Saját / Custom room", room.name);
                editor.undo();
                assertNotEquals(snapshot, editor.snapshot());
                editor.redo();
                assertEquals(snapshot, editor.snapshot());
                assertFalse(Files.exists(filename), "Language changes must not save a project automatically");
            } finally {
                stage.hide();
                if (savedLanguage == null) preferences.remove("language");
                else preferences.put("language", savedLanguage);
                preferences.flush();
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T field(Object instance, String name) throws Exception {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T)field.get(instance);
        }

        private static void setDouble(Object instance, String name, double value) throws Exception {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setDouble(instance, value);
        }

        private static void setField(Object instance, String name, Object value) throws Exception {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, value);
        }
    }
}
