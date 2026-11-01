package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.FloorLevels;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BuildingLevelsEditorTest {
    @Test void selectorSwitchingEditingHistoryDeletionGuardsAndThreeDimensionalOwners() throws Exception {
        Path log=Path.of("target","building-levels-editor-test.log");
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Dhd.language=hu","-cp",cp,Probe.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(120,TimeUnit.SECONDS)) {
            process.destroyForcibly(); fail("Storey UI probe timed out: "+Files.readString(log));
        }
        assertEquals(0,process.exitValue(),Files.readString(log));
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CompletableFuture<Void> done=new CompletableFuture<>();
            Platform.startup(() -> {
                try { verify(); done.complete(null); }
                catch(Throwable ex) { done.completeExceptionally(ex); }
            });
            try { done.get(100,TimeUnit.SECONDS); }
            catch(Throwable ex) { ex.printStackTrace(); System.exit(1); }
            Platform.exit(); System.exit(0);
        }

        @SuppressWarnings("unchecked")
        private static void verify() throws Exception {
            Project p=new Project(); Room ground=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Ground");
            Stage stage=new Stage(); MainWindow window=new MainWindow(stage,p);
            Editor ed=field(window,"ed"); View3D view=field(window,"view");
            stage.show();
            try {
                String groundId=p.activeLevelId;
                ed.select(ground);
                ed.addLevel("Cellar",-3);
                String cellarId=p.activeLevelId;
                assertTrue(p.rooms.isEmpty()); assertNull(ed.selection());
                ed.addLevel("Upper",3);
                String upperId=p.activeLevelId;
                ComboBox<BuildingLevel> selector=(ComboBox<BuildingLevel>)stage.getScene().lookup("#building-level-selector");
                assertNotNull(selector); assertEquals(3,selector.getItems().size()); assertEquals(upperId,selector.getValue().id);
                ed.edit(() -> ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Upper"));
                assertEquals(4,p.walls.size());
                selector.setValue(p.levels.getFirst());
                assertEquals(groundId,p.activeLevelId); assertSame(ground,p.rooms.getFirst());
                ed.undo();
                assertEquals(upperId,ed.project().activeLevelId); assertTrue(ed.project().rooms.isEmpty());
                assertEquals(upperId,selector.getValue().id);
                ed.redo();
                assertEquals(1,ed.project().rooms.size());
                assertEquals(upperId,ed.project().activeLevelId,"Redo must retain the edited storey");
                ed.updateLevel("First floor",3.5);
                assertEquals(3.5,ed.project().activeLevel().baseHeight);
                ed.undo();
                assertEquals(upperId,ed.project().activeLevelId,"Undo storey elevation must stay on that storey");
                assertEquals(3,ed.project().activeLevel().baseHeight);
                ed.redo(); assertEquals(3.5,ed.project().activeLevel().baseHeight);
                assertThrows(IllegalArgumentException.class,ed::deleteLevel);
                ed.switchLevel(cellarId);
                ed.deleteLevel(); assertEquals(2,ed.project().levels.size());
                ed.undo(); assertEquals(cellarId,ed.project().activeLevelId); assertEquals(3,ed.project().levels.size());
                ed.redo(); assertEquals(2,ed.project().levels.size());
                ed.switchLevel(upperId);
                Furniture chair=new Furniture(); chair.x=2; chair.y=2;
                ed.edit(() -> ed.project().furniture.add(chair));
                ed.switchLevel(groundId); ed.select(chair);
                assertEquals(upperId,ed.project().activeLevelId);
                view.rebuild(true);
                assertNotNull(view.snapshot());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.copy(ed.project())));
                assertEquals(0,FloorLevels.walkHeight(ed.project(),1,1,0));
                assertEquals(3.5,FloorLevels.walkHeight(ed.project(),1,1,3.5));
                ed.switchLevel(groundId);
                assertEquals(1,ed.project().rooms.size()); assertTrue(ed.project().furniture.isEmpty());
            } finally { stage.close(); }
        }

        @SuppressWarnings("unchecked")
        private static <T> T field(Object owner,String name) throws Exception {
            var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return (T)field.get(owner);
        }
    }
}
