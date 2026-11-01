package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.edit.RoofFitter;
import hu.helmetsoft.homedesigner.geom.StructureBuilder;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureEditorTest {
    @TempDir Path temporary;
    @BeforeAll static void toolkit() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(30,TimeUnit.SECONDS));
    }

    private static void fx(Runnable r) throws Exception {
        FutureTask<Void> task = new FutureTask<>(r,null);
        Platform.runLater(task);
        task.get(120,TimeUnit.SECONDS);
    }

    @Test void catalogColumnsAllowIndependentHeightAndDirectMaterialEditingWithHistory() throws Exception {
        Path pack=temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER);
        hu.helmetsoft.homedesigner.io.HomeModels.write(pack);
        Path file=pack.resolve("COLUMNS").resolve("Oszlop_kor.obj");
        hu.helmetsoft.homedesigner.io.ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
        fx(() -> {
            Editor ed=new Editor(new Project()); PlanView plan=new PlanView(ed);
            PropertiesPanel props=new PropertiesPanel(ed,plan);
            Stage stage=new Stage(); stage.setScene(new Scene(new HBox(plan,props),1050,850)); stage.show();
            try {
                Furniture column=ed.addFurniture(file.toString(),1,2);
                ed.changedNow();
                assertEquals(2.4,column.height,1e-5);
                assertNotNull(column.override);
                var keep=props.lookupAll(".check-box").stream()
                        .filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().equals("Arányok megtartása"))
                        .map(n -> (javafx.scene.control.CheckBox)n).findFirst().orElseThrow();
                assertFalse(keep.isSelected());
                double width=column.width;
                TextField height=field(props,"Magasság");
                height.setText("300"); height.fireEvent(new ActionEvent());
                assertEquals(3,column.height,1e-8);
                assertEquals(width,column.width,1e-8);
                FinishEditor finish=(FinishEditor)props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getValue() instanceof hu.helmetsoft.homedesigner.material.MaterialLibrary.Preset)
                        .findFirst().orElseThrow().getParent().getParent();
                for(String preset:List.of("stone","marble","oak_wood")) {
                    ComboBox<?> combo=(ComboBox<?>)finish.lookup(".combo-box");
                    combo.getSelectionModel().select(combo.getItems().stream()
                            .filter(v -> ((hu.helmetsoft.homedesigner.material.MaterialLibrary.Preset)v).id.equals(preset))
                            .mapToInt(v -> combo.getItems().indexOf(v)).findFirst().orElseThrow());
                    assertEquals(preset,column.override.preset);
                }
                ed.undo();
                assertEquals("marble",((Furniture)ed.selection()).override.preset);
                ed.redo();
                Furniture saved=ProjectIO.fromJson(ed.snapshot()).furniture.getFirst();
                assertEquals("oak_wood",saved.override.preset);
                assertEquals(3,saved.height,1e-8);
                assertEquals(width,saved.width,1e-8);
            } finally { props.dispose(); stage.close(); }
        });
    }

    @Test void horizontalTimberBeamsStartUnderTheCeilingAndResizeWithoutChangingTheirCrossSection() throws Exception {
        Path pack=temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER);
        hu.helmetsoft.homedesigner.io.HomeModels.write(pack);
        Path file=pack.resolve("BEAMS").resolve("Fagerenda_vizszintes.obj");
        hu.helmetsoft.homedesigner.io.ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
        fx(() -> {
            Project p=new Project();
            Room room=ProjectOps.addRoom(p,ProjectOps.rect(-4,-4,8,8),"Room"); room.ceilingHeight=3;
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            Stage stage=new Stage(); stage.setScene(new Scene(new HBox(plan,props),1050,850)); stage.show();
            try {
                Furniture beam=ed.addFurniture(file.toString(),0,0); ed.changedNow();
                assertEquals(4,beam.width,1e-6); assertEquals(0.2,beam.depth,1e-6); assertEquals(0.2,beam.height,1e-6);
                assertEquals(2.8,beam.elevation,1e-6); assertEquals("oak_wood",beam.override.preset);
                TextField length=field(props,"Szélesség"); length.setText("600"); length.fireEvent(new ActionEvent());
                assertEquals(6,beam.width,1e-6); assertEquals(0.2,beam.height,1e-6); assertEquals(0.2,beam.depth,1e-6);
                TextField angle=field(props,"Forgatás"); angle.setText("90"); angle.fireEvent(new ActionEvent());
                assertEquals(90,beam.angle,1e-6);
                ed.undo(); assertEquals(0,((Furniture)ed.selection()).angle,1e-6);
                ed.redo();
                Furniture saved=ProjectIO.fromJson(ed.snapshot()).furniture.getFirst();
                assertEquals(90,saved.angle,1e-6); assertEquals(6,saved.width,1e-6); assertEquals(2.8,saved.elevation,1e-6);
            } finally { props.dispose(); stage.close(); }
        });
    }

    @Test void wallsCanBecomeDifferentFencesWithUndoSplitAndConversionGuards() throws Exception {
        fx(() -> {
            Project p=new Project(); Wall wall=new Wall(0,0,6,0,0.15,2.7); p.walls.add(wall);
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            Stage stage=new Stage(); stage.setScene(new Scene(new HBox(plan,props),1050,850)); stage.show();
            try {
                ed.select(wall);
                for(Wall.Fence type:Wall.Fence.values()) if(type!=Wall.Fence.NONE) {
                    ComboBox<?> types=props.lookupAll(".combo-box").stream()
                            .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Wall.Fence.PICKET))
                            .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                    types.getSelectionModel().select(type.ordinal()); types.fireEvent(new ActionEvent()); ed.changedNow();
                    assertEquals(type,((Wall)ed.selection()).fence,ed.status.get());
                    assertNotNull(field(props,"Kerítés oszlopköze"));
                }
                TextField spacing=field(props,"Kerítés oszlopköze"); spacing.setText("150"); spacing.fireEvent(new ActionEvent());
                assertEquals(1.5,((Wall)ed.selection()).fencePostSpacing,1e-8);
                ed.undo(); assertEquals(2,((Wall)ed.selection()).fencePostSpacing,1e-8);
                ed.redo();
                Wall fence=(Wall)ed.selection();
                ed.edit(() -> ProjectOps.splitWall(ed.project(),fence,3));
                assertEquals(2,ed.project().walls.size());
                assertTrue(ed.project().walls.stream().allMatch(w -> w.fence==Wall.Fence.SOLID));
                Project loaded=ProjectIO.fromJson(ed.snapshot());
                assertEquals(1.5,loaded.walls.getLast().fencePostSpacing);
                Wall withDoor=new Wall(0,4,5,4,0.15,2.7); ProjectOps.addOpening(withDoor,Opening.Type.DOOR,2);
                ed.edit(() -> ed.project().walls.add(withDoor));
                ed.select(withDoor);
                ComboBox<?> types=props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Wall.Fence.PICKET))
                        .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                Platform.runLater(() -> {
                    var dialogs=javafx.stage.Window.getWindows().stream()
                            .filter(w -> w!=stage && w.isShowing()).toList();
                    assertEquals(1,dialogs.size(),"Rejected conversion must display an error dialog");
                    dialogs.forEach(javafx.stage.Window::hide);
                });
                types.getSelectionModel().select(Wall.Fence.PICKET.ordinal());
                types.fireEvent(new ActionEvent());
                assertFalse(ed.project().walls.getLast().isFence(),"Invalid conversion must restore the original wall");
                assertEquals(1,ed.project().walls.getLast().openings.size());
                assertFalse(ed.status.get().isBlank(),"Rejected conversion must explain the error");
            } finally { props.dispose(); stage.close(); }
        });
    }

    @Test void originalOutdoorEquipmentAndFenceStylesRenderInTheEditor() throws Exception {
        Path pack=temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER);
        hu.helmetsoft.homedesigner.io.HomeModels.write(pack);
        var assets=hu.helmetsoft.homedesigner.io.HomeModels.ASSETS.stream().filter(a ->
                a.category()==hu.helmetsoft.homedesigner.io.HomeModels.Category.WATER
                        || a.category()==hu.helmetsoft.homedesigner.io.HomeModels.Category.CARAVANS).toList();
        for(var asset:assets) hu.helmetsoft.homedesigner.io.ModelLibrary.get(
                pack.resolve(asset.category().name()).resolve(asset.name()+".obj").toString(),null).get(30,TimeUnit.SECONDS);
        fx(() -> {
            Project p=new Project(); Editor ed=new Editor(p);
            int n=0;
            for(Wall.Fence type:Wall.Fence.values()) if(type!=Wall.Fence.NONE) {
                Wall w=new Wall(-9+n*3.6,-5,-5.6+n*3.6,-5,0.12,1.5);
                w.fence=type; w.fenceFollowTerrain=false;
                w.left=new Finish(type==Wall.Fence.PICKET || type==Wall.Fence.HORIZONTAL ? "oak_wood":
                        type==Wall.Fence.SOLID ? "stone":"steel",null);
                p.walls.add(w); n++;
            }
            double[][] positions={{-6,2.5},{-6,-1.5},{-3,-1.5},{-3,2.5},{3,0}};
            for(int i=0;i<assets.size();i++) {
                var asset=assets.get(i);
                Furniture furniture=ed.addFurniture(pack.resolve(asset.category().name()).resolve(asset.name()+".obj").toString(),
                        positions[i][0],positions[i][1]);
                if(asset.category()==hu.helmetsoft.homedesigner.io.HomeModels.Category.CARAVANS) {
                    assertTrue(furniture.width>6.5,"Caravan must retain its full metric length including the hitch");
                    assertEquals(2.765,furniture.height,0.001);
                }
            }
            PlanView plan=new PlanView(ed); View3D view=new View3D(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(250); view.setPrefWidth(1000);
            HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1650,950)); stage.show();
            try {
                ed.select(p.walls.getFirst()); ed.changedNow(); root.applyCss(); root.layout();
                view.showDims.setSelected(false); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"outdoor-equipment-and-fences.png"));
                    saveImage(view.snapshot(),Path.of(dir,"outdoor-equipment-3d.png"));
                } catch(java.io.IOException ex) { throw new RuntimeException(ex); }
            } finally { props.dispose(); stage.close(); }
        });
    }

    @Test void hillsideAndVaultControlsEditPersistAndUndoRealGeometrySettings() throws Exception {
        fx(() -> {
            Project p=new Project();
            Room room=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Pince");
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed);
            PropertiesPanel props=new PropertiesPanel(ed,plan);
            Stage stage=new Stage(); stage.setScene(new Scene(new HBox(plan,props),1050,850)); stage.show();
            try {
                TextField slope=field(props,"Terep dőlésszöge");
                slope.setText("18"); slope.fireEvent(new ActionEvent());
                assertEquals(18,p.env.terrainSlope,1e-9);
                ComboBox<?> foundation=props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Environment.Foundation.EMBEDDED))
                        .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                foundation.getSelectionModel().select(2);
                assertEquals(Environment.Foundation.EMBEDDED,p.env.foundation);
                ed.select(room);
                ComboBox<?> vault=props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Room.Vault.POINTED))
                        .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                vault.getSelectionModel().select(2);
                vault.fireEvent(new ActionEvent());
                assertEquals(Room.Vault.POINTED,room.vault,ed.status.get());
                ed.changedNow();
                TextField rise=field(props,"Boltozat emelkedése");
                rise.setText("115"); rise.fireEvent(new ActionEvent());
                assertEquals(1.15,room.vaultRise,1e-9);
                ed.undo();
                assertEquals(0.8,((Room)ed.selection()).vaultRise,1e-9);
                ed.redo();
                assertEquals(1.15,((Room)ed.selection()).vaultRise,1e-9);
                Project saved=ProjectIO.fromJson(ed.snapshot());
                assertEquals(18,saved.env.terrainSlope);
                assertEquals(Environment.Foundation.EMBEDDED,saved.env.foundation);
                assertEquals(Room.Vault.POINTED,saved.rooms.getFirst().vault);
            } finally { props.dispose(); stage.close(); }
        });
    }

    @Test void multiStoreyHillsideSceneKeepsLevelsSeparateAndRendersVaults() throws Exception {
        fx(() -> {
            Project p=new Project();
            p.env.terrainSlope=12; p.env.terrainAzimuth=90; p.env.foundation=Environment.Foundation.BLOCK;
            Editor ed=new Editor(p); ed.updateLevel("Pince",-3);
            p.wallHeight=2.1;
            Room cellar=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(-8,-2,6,4),"Boltozatos pince");
            cellar.vault=Room.Vault.POINTED; cellar.vaultRise=0.7;
            ed.addLevel("Földszint",0); p.wallHeight=2.7;
            ProjectOps.addRoomWithWalls(p,ProjectOps.rect(1,-2,7,4),"Nappali").hasCeiling=false;
            ed.addLevel("Emelet",3);
            Room upper=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(1,-2,4,4),"Emeleti háló"); upper.hasCeiling=false;
            assertEquals(3,p.levels.size()); assertEquals(List.of(upper),p.rooms);
            PlanView plan=new PlanView(ed); View3D view=new View3D(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(340); view.setPrefWidth(850);
            HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1550,900)); stage.show();
            try {
                ed.changedNow(); view.showCeiling.setSelected(true);
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                var options=new StructureBuilder.Options(); options.ceilings=true;
                var parts=StructureBuilder.build(p,options);
                assertTrue(parts.stream().anyMatch(part -> part.owner==cellar));
                assertTrue(parts.stream().anyMatch(part -> part.owner==upper));
                ed.select(cellar);
                assertSame(cellar,p.rooms.getFirst()); assertEquals(-3,p.activeLevel().baseHeight);
                assertNotNull(field(props,"Boltozat emelkedése"));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    root.applyCss(); root.layout(); view.rebuild(true);
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"hillside-storeys-and-vault.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { props.dispose(); stage.close(); }
        });
    }

    private static Project example() {
        Project p = new Project();
        ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,12,10),"Tetőtér");
        p.roofs.add(ProjectOps.fitRoof(p));
        return p;
    }

    @Test void addingEditingDuplicatingUndoingAndDeleting() throws Exception {
        fx(() -> {
            Project p = example();
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage();
            stage.setScene(new Scene(new HBox(plan,props),950,700));
            stage.show();
            try {
                Roof r = p.roofs.get(0);
                ed.addRoofElement(r,RoofElement.Type.SKYLIGHT);
                RoofElement e = (RoofElement)ed.selection();
                TextField width = field(props,"Szélesség");
                width.setText("100");
                width.fireEvent(new ActionEvent());
                assertEquals(1,e.width,1e-9);
                ed.undo();
                assertInstanceOf(RoofElement.class,ed.selection());
                assertEquals(0.9,((RoofElement)ed.selection()).width,1e-9);
                ed.redo();
                assertEquals(1,((RoofElement)ed.selection()).width,1e-9);
                plan.duplicate(ed.selection());
                assertEquals(2,ed.project().roofs.get(0).elements.size());
                ed.delete(ed.selection());
                assertEquals(1,ed.project().roofs.get(0).elements.size());
                ed.undo();
                assertEquals(2,ed.project().roofs.get(0).elements.size());
                ed.addRoofElement(ed.project().roofs.get(0),RoofElement.Type.DORMER);
                assertNotNull(field(props,"Kiemelés a tető fölé"));
                ed.addRoofElement(ed.project().roofs.get(0),RoofElement.Type.TERRACE);
                assertNotNull(field(props,"Süllyesztés"));
                ed.addBalcony();
                Balcony b = (Balcony)ed.selection();
                TextField angle = field(props,"Forgatás");
                angle.setText("45"); angle.fireEvent(new ActionEvent());
                assertEquals(45,b.angle,1e-9);
                TextField w = field(props,"Szélesség");
                w.setText("400"); w.fireEvent(new ActionEvent());
                assertEquals(4,b.width,1e-9);
                ed.undo(); ed.redo();
                assertInstanceOf(Balcony.class,ed.selection());
                plan.duplicate(ed.selection());
                assertEquals(2,ed.project().balconies.size());
                ed.delete(ed.selection());
                assertEquals(1,ed.project().balconies.size());
                ed.undo();
                assertEquals(2,ed.project().balconies.size());
                // Deleting a roof deletes its owned openings, but not independent facade balconies.
                ed.delete(ed.project().roofs.get(0));
                assertTrue(ed.project().roofs.isEmpty());
                assertEquals(2,ed.project().balconies.size());
                ed.undo();
                assertEquals(4,ed.project().roofs.get(0).elements.size());
                assertFalse(StructureBuilder.build(ed.project(),new StructureBuilder.Options()).isEmpty());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
            } finally { stage.close(); }
        });
    }

    @Test void planAndThreeDimensionalSceneDisplayEveryElement() throws Exception {
        fx(() -> {
            Editor ed = new Editor(example());
            Roof r = ed.project().roofs.get(0);
            for (RoofElement.Type type : RoofElement.Type.values()) ed.addRoofElement(r,type);
            ed.addBalcony();
            PlanView plan = new PlanView(ed);
            View3D view = new View3D(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            HBox root = new HBox(plan,view,props);
            HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            plan.setPrefWidth(500); view.setPrefWidth(650);
            Stage stage = new Stage();
            stage.setScene(new Scene(root,1450,850));
            stage.show();
            try {
                plan.fit(); view.resetCamera();
                root.applyCss(); root.layout();
                for (RoofElement e : r.elements) {
                    ed.select(e);
                    view.rebuild(true);
                    assertNotNull(field(props,"Szélesség"));
                }

                ed.select(r);
                view.rebuild(true);
                String dir = System.getProperty("hd.testSnapshots");
                if (dir != null) {
                    try {
                        Files.createDirectories(Path.of(dir));
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"roof-and-balcony.png"));
                        ed.select(r.elements.get(1));
                        root.applyCss(); root.layout();
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"dormer-properties.png"));
                        ed.select(ed.project().balconies.get(0));
                        root.applyCss(); root.layout();
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"balcony-properties.png"));
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }
                view.showRoof.setSelected(false);
                view.rebuild(true);
                assertNotNull(view.snapshot());
            } finally { stage.close(); }
        });
    }

    @Test void rejectedRoofEditsRestoreProjectSelectionAndUndoHistory() throws Exception {
        fx(() -> {
            Project p = example();
            Roof r = p.roofs.get(0);
            RoofElement e = new RoofElement();
            ProjectOps.placeRoofElement(r,e); r.elements.add(e);
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage();
            stage.setScene(new Scene(props,320,800));
            stage.show();
            try {
                ed.select(e);
                String before = ed.snapshot();
                var closeDialog = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                closeDialog.setOnFinished(event -> {
                    for (javafx.stage.Window w : java.util.List.copyOf(javafx.stage.Window.getWindows()))
                        if (w != stage) w.hide();
                });
                closeDialog.play();
                ed.edit(() -> e.x = 500);
                assertEquals(before,ed.snapshot());
                assertInstanceOf(RoofElement.class,ed.selection());
                assertFalse(ed.canUndo.get());
                assertFalse(ed.status.get().isBlank());
                assertNotNull(field(props,"Szélesség"));
            } finally { stage.close(); }
        });
    }

    @Test void threeClickArcDrawingBendingAndPlacingWindowUseActualMouseEvents() throws Exception {
        fx(() -> {
            Editor ed = new Editor(new Project());
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage();
            stage.setScene(new Scene(new HBox(plan,props),1000,650));
            stage.show();
            plan.fit();
            try {
                ed.tool.set(Editor.Tool.ROTUNDA);
                click(plan,5,5); key(plan,KeyCode.ESCAPE);
                assertFalse(ed.isDirty()); assertFalse(ed.canUndo.get());
                ed.tool.set(Editor.Tool.ARC_WALL);
                click(plan,3,0); click(plan,3,3); click(plan,0,3);
                assertEquals(1,ed.project().walls.size());
                Wall w = ed.project().walls.get(0);
                assertEquals(180,w.arcAngle,1e-8);
                assertNotNull(field(props,"Ívhossz")); assertNotNull(field(props,"Sugár"));
                ed.undo(); assertTrue(ed.project().walls.isEmpty());
                ed.redo(); w = ed.project().walls.get(0);
                ed.tool.set(Editor.Tool.WINDOW);
                double[] at = w.point(w.length()/2,0);
                click(plan,at[0],at[1]);
                assertEquals(1,w.openings.size());
                assertInstanceOf(Opening.class,ed.selection());
                assertEquals(w.length()/2,w.openings.get(0).pos,0.04);
                ed.undo(); assertTrue(ed.project().walls.get(0).openings.isEmpty());
                ed.redo();
                w = ed.project().walls.get(0);
                ed.select(w);
                TextField radius = field(props,"Sugár");
                radius.setText("300"); radius.fireEvent(new ActionEvent());
                assertEquals(3,w.radius(),1e-8);
                assertEquals(180,w.arcAngle,1e-8);
                ed.undo(); assertEquals(Math.sqrt(4.5),ed.project().walls.get(0).radius(),1e-8);
                ed.tool.set(Editor.Tool.ARC_WALL);
                click(plan,6,0); click(plan,7,0); click(plan,8,0);
                assertEquals(1,ed.project().walls.size());
                assertTrue(ed.status.get().contains("egyenesre"));
                key(plan,KeyCode.ESCAPE);
                click(plan,6,0); click(plan,7,1); key(plan,KeyCode.ESCAPE);
                assertEquals(1,ed.project().walls.size());
            } finally { stage.close(); }
        });
    }

    @Test void rotundaNumericRadiusPropertiesDragUndoRedoAndDeletionStayLinked() throws Exception {
        fx(() -> {
            Editor ed = new Editor(new Project());
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage();
            stage.setScene(new Scene(new HBox(plan,props),1000,650));
            stage.show(); plan.fit();
            try {
                ed.tool.set(Editor.Tool.ROTUNDA);
                click(plan,4,4);
                for (String digit : new String[]{"2",",","5"})
                    plan.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED,digit,"",KeyCode.UNDEFINED,false,false,false,false));
                key(plan,KeyCode.ENTER);
                Room r = ed.project().rooms.get(0);
                assertEquals(2.5,r.circleRadius,1e-9); assertEquals(4,ed.project().walls.size());
                TextField radius = field(props,"Sugár");
                radius.setText("300"); radius.fireEvent(new ActionEvent());
                assertEquals(3,r.circleRadius,1e-9);
                for (Wall w : ed.project().walls) assertEquals(3,w.radius(),1e-8);
                ed.undo(); ed.redo();
                r = ed.project().rooms.get(0);
                ed.tool.set(Editor.Tool.SELECT);
                click(plan,4,4);
                key(plan,KeyCode.RIGHT);
                assertEquals(4.01,r.circleX,1e-9);
                assertEquals(7.01,ed.project().walls.get(0).x1,1e-9);
                mouse(plan,MouseEvent.MOUSE_PRESSED,7.01,4,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,8.01,4,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,8.01,4,MouseButton.PRIMARY);
                assertEquals(4,r.circleRadius,0.011);
                for (Wall w : ed.project().walls) assertEquals(r.circleRadius,w.radius(),1e-8);
                ed.undo(); assertEquals(3,ed.project().rooms.get(0).circleRadius,1e-9);
                ed.redo();
                ed.delete(ed.project().rooms.get(0));
                assertTrue(ed.project().walls.isEmpty()); assertTrue(ed.project().rooms.isEmpty());
                ed.undo();
                assertEquals(4,ed.project().walls.size()); assertEquals(1,ed.project().rooms.size());
                ed.delete(ed.project().walls.get(0));
                assertEquals(3,ed.project().walls.size()); assertNull(ed.project().rooms.get(0).rotundaId);
                ed.undo(); ProjectOps.validateCurves(ed.project());
            } finally { stage.close(); }
        });
    }

    @Test void arcMidpointHandleBendsStraightWallAndCurvesRenderInBothViews() throws Exception {
        fx(() -> {
            Project p = new Project();
            Wall straight = new Wall(0,0,4,0,0.2,2.7); p.walls.add(straight);
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            Stage stage = new Stage();
            stage.setScene(new Scene(plan,600,500));
            stage.show();
            // Switch to an empty project for a known plan transform, then insert the test wall.
            ed.setProject(new Project(),null); plan.fit();
            ed.project().walls.add(straight); ed.select(straight);
            try {
                mouse(plan,MouseEvent.MOUSE_PRESSED,2,0,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,2,2,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,2,2,MouseButton.PRIMARY);
                assertEquals(-180,straight.arcAngle,1e-8);
                ed.undo(); assertFalse(ed.project().walls.get(0).curved());
                ed.redo(); assertTrue(ed.project().walls.get(0).curved());
                ProjectOps.addRotunda(ed.project(),8,4,3);
                Room r = ed.project().rooms.get(0);
                Wall w = ed.project().walls.get(1);
                ProjectOps.addOpening(w,Opening.Type.WINDOW,w.length()/2);
                ProjectOps.addOpening(ed.project().walls.get(2),Opening.Type.DOOR,2);
                View3D view = new View3D(ed);
                PropertiesPanel props = new PropertiesPanel(ed,plan);
                HBox root = new HBox(plan,view,props);
                plan.setPrefWidth(520); view.setPrefWidth(650);
                HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
                stage.setScene(new Scene(root,1450,850));
                ed.select(r);
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                assertNotNull(field(props,"Sugár"));
                String dir = System.getProperty("hd.testSnapshots");
                if (dir != null) {
                    try {
                        Files.createDirectories(Path.of(dir));
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"curved-walls-and-rotunda.png"));
                        ed.select(ed.project().walls.get(0));
                        root.applyCss(); root.layout();
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"arc-wall-properties.png"));
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }
            } finally { stage.close(); }
        });
    }

    @Test void rejectedCurveResizeKeepsSavedModelSelectionAndUndoHistory() throws Exception {
        fx(() -> {
            Project p = new Project();
            Room room = ProjectOps.addRotunda(p,0,0,3);
            ProjectOps.addOpening(p.walls.get(0),Opening.Type.WINDOW,2);
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage(); stage.setScene(new Scene(props,320,800)); stage.show();
            ed.select(room);
            try {
                String before = ed.snapshot();
                var closeDialog = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                closeDialog.setOnFinished(event -> {
                    for (javafx.stage.Window window : java.util.List.copyOf(javafx.stage.Window.getWindows()))
                        if (window != stage) window.hide();
                });
                closeDialog.play();
                ed.edit(() -> ProjectOps.updateRotunda(ed.project(),room,0,0,0.3));
                assertEquals(before,ed.snapshot());
                assertInstanceOf(Room.class,ed.selection());
                assertFalse(ed.canUndo.get());
                assertFalse(ed.status.get().isBlank());
                assertNotNull(field(props,"Sugár"));
            } finally { stage.close(); }
        });
    }

    @Test void independentBuildingRoofsFitCurvesSwitchToDomeAndUndoShapeEdits() throws Exception {
        fx(() -> {
            Project p = new Project();
            Room first = ProjectOps.addRotunda(p,0,0,3);
            ProjectOps.addRoomWithWalls(p,ProjectOps.rect(10,0,5,5),"Második épület");
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage();
            plan.setPrefWidth(750); HBox.setHgrow(plan,Priority.ALWAYS);
            stage.setScene(new Scene(new HBox(plan,props),1100,800)); stage.show();
            try {
                ed.select(first); ed.addRoof();
                Roof cone = p.roofs.getFirst();
                assertTrue(cone.circular); assertEquals(Roof.Type.CONE,cone.type);
                assertNotNull(field(props,"Sugár"));
                ComboBox<?> combo = roofTypes(props);
                combo.getSelectionModel().select(combo.getItems().indexOf(Roof.Type.DOME));
                ed.changedNow();
                assertEquals(Roof.Type.DOME,cone.type); assertNotNull(field(props,"Kupola emelkedése"));
                TextField rise = field(props,"Kupola emelkedése");
                rise.setText("250"); rise.fireEvent(new ActionEvent());
                assertEquals(2.5,cone.domeRise,1e-9);
                ed.undo(); ed.redo();
                cone = ed.project().roofs.getFirst();
                assertEquals(2.5,cone.domeRise,1e-9);
                TextField radius = field(props,"Sugár");
                radius.setText("400"); radius.fireEvent(new ActionEvent());
                assertEquals(8,cone.w,1e-9); assertEquals(cone.w,cone.d,1e-9);
                assertEquals(0,cone.x+cone.w/2,0.001);
                ProjectOps.updateRotunda(ed.project(),ed.project().rooms.getFirst(),0,0,3.5);
                ed.changedNow();
                props.applyCss(); props.layout();
                Button refit = props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Falakhoz igazítás"))
                        .map(n -> (Button)n).findFirst().orElseThrow();
                refit.fire();
                assertEquals(7.15,cone.w,0.002);
                ed.select(ed.project().rooms.get(1)); ed.addRoof();
                assertEquals(2,ed.project().roofs.size());
                Roof rectangle = ed.project().roofs.get(1);
                assertFalse(rectangle.circular); assertTrue(rectangle.x > 9);
                assertTrue(cone.x < 0); assertEquals(Roof.Type.DOME,cone.type);
                ed.undo(); assertEquals(1,ed.project().roofs.size());
                ed.redo(); assertEquals(2,ed.project().roofs.size());
                Roof selected = ed.project().roofs.getFirst();
                ed.select(selected); plan.fit();
                click(plan,selected.x+selected.w+selected.overhang,selected.y+selected.d/2);
                assertSame(selected,ed.selection());
                double x = selected.x+selected.w, y = selected.y+selected.d;
                mouse(plan,MouseEvent.MOUSE_PRESSED,x,y,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,x+1,y+1,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,x+1,y+1,MouseButton.PRIMARY);
                assertEquals(selected.w,selected.d,1e-9);
                assertEquals(8.15,selected.w,0.06);
                assertDoesNotThrow(() -> hu.helmetsoft.homedesigner.geom.RoofGeometry.validate(ed.project()));
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
            } finally { stage.close(); }
        });
    }

    @Test void roofsWithRoundAndMixedOutlinesDisplayInPlanAndThreeDimensionalScene() throws Exception {
        fx(() -> {
            Project p = new Project();
            Room a = ProjectOps.addRotunda(p,0,0,2.5);
            Room b = ProjectOps.addRotunda(p,8,0,2.5);
            Roof cone = RoofFitter.fit(p,a), dome = RoofFitter.fit(p,b);
            dome.type = Roof.Type.DOME; dome.domeRise = 2;
            p.roofs.add(cone); p.roofs.add(dome);
            p.walls.add(new Wall(-3,8,3,8,0.2,2.7));
            Wall arc = new Wall(3,8,3,14,0.2,2.7); arc.arcAngle = 180; p.walls.add(arc);
            p.walls.add(new Wall(3,14,-3,14,0.2,2.7)); p.walls.add(new Wall(-3,14,-3,8,0.2,2.7));
            Roof curved = RoofFitter.fit(p,arc); p.roofs.add(curved);
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed); View3D view = new View3D(ed);
            PropertiesPanel props = new PropertiesPanel(ed,plan);
            HBox root = new HBox(plan,view,props);
            plan.setPrefWidth(520); view.setPrefWidth(650);
            HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage = new Stage(); stage.setScene(new Scene(root,1500,900)); stage.show();
            try {
                ed.select(dome); root.applyCss(); root.layout();
                plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot()); assertNotNull(field(props,"Kupola emelkedése"));
                assertFalse(curved.footprint.isEmpty());
                String dir = System.getProperty("hd.testSnapshots");
                if (dir != null) {
                    try {
                        Files.createDirectories(Path.of(dir));
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"curved-roofs.png"));
                        ed.select(curved); root.applyCss(); root.layout();
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"wall-following-roof.png"));
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }
            } finally { stage.close(); }
        });
    }

    @Test void incompatibleRoofTypeChangeKeepsOriginalFootprintAndOpenings() throws Exception {
        fx(() -> {
            Project p = example();
            Roof r = p.roofs.getFirst();
            RoofElement e = new RoofElement(); ProjectOps.placeRoofElement(r,e); r.elements.add(e);
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            Stage stage = new Stage(); stage.setScene(new Scene(props,340,850)); stage.show(); ed.select(r);
            try {
                String before = ed.snapshot();
                var closeDialog = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                closeDialog.setOnFinished(event -> {
                    for (javafx.stage.Window window : java.util.List.copyOf(javafx.stage.Window.getWindows()))
                        if (window != stage) window.hide();
                });
                closeDialog.play();
                ComboBox<?> types = roofTypes(props);
                types.getSelectionModel().select(types.getItems().indexOf(Roof.Type.CONE));
                assertEquals(before,ed.snapshot()); assertFalse(ed.canUndo.get());
                assertInstanceOf(Roof.class,ed.selection());
                assertEquals(1,ed.project().roofs.getFirst().elements.size());
                assertFalse(ed.status.get().isBlank());
            } finally { stage.close(); }
        });
    }

    @Test void parametricStairsEditDuplicateUndoAndKeepCircularFootprintDuringDrag() throws Exception {
        fx(() -> {
            Editor ed = new Editor(new Project()); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            plan.setPrefWidth(750); HBox.setHgrow(plan,Priority.ALWAYS);
            Stage stage = new Stage(); stage.setScene(new Scene(new HBox(plan,props),1100,850)); stage.show();
            try {
                Furniture f = ed.addStair(Stair.Type.STRAIGHT,0,0);
                assertNotNull(field(props,"Vízszintes hossz"));
                TextField height = field(props,"Szintmagasság");
                height.setText("300"); height.fireEvent(new ActionEvent());
                TextField steps = field(props,"Fokszám");
                steps.setText("18"); steps.fireEvent(new ActionEvent());
                ed.undo(); ed.redo();
                f = ed.project().furniture.getFirst();
                assertEquals(3,f.height,1e-9); assertEquals(18,f.stair.steps);
                props.applyCss(); props.layout();
                ComboBox<?> types = props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Stair.Type.SPIRAL))
                        .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                types.getSelectionModel().select(types.getItems().indexOf(Stair.Type.SPIRAL));
                ed.changedNow();
                assertEquals(Stair.Type.SPIRAL,f.stair.type); assertEquals(f.width,f.depth,1e-9);
                TextField turn = field(props,"Teljes fordulat"); assertNotNull(turn);
                turn.setText("540"); turn.fireEvent(new ActionEvent());
                plan.duplicate(f);
                assertEquals(2,ed.project().furniture.size());
                Furniture duplicate = (Furniture)ed.selection();
                assertNotSame(f.stair,duplicate.stair);
                assertNotSame(f.stair.treads,duplicate.stair.treads);
                duplicate.stair.treads.color = "#EEBB88";
                assertNull(f.stair.treads.color);
                ed.delete(duplicate); ed.undo(); ed.redo(); ed.undo();
                ed.select(ed.project().furniture.getFirst()); f = (Furniture)ed.selection();
                plan.fit();
                double x = f.x+f.width/2, y = f.y+f.depth/2;
                mouse(plan,MouseEvent.MOUSE_PRESSED,x,y,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,x+0.4,y+0.15,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,x+0.4,y+0.15,MouseButton.PRIMARY);
                assertEquals(f.width,f.depth,1e-9); assertTrue(f.width > 2);
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
                assertNotNull(hu.helmetsoft.homedesigner.io.ModelLibrary.placedTop(f));
                String before = ed.snapshot();
                var closeDialog = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                closeDialog.setOnFinished(e -> {
                    for (javafx.stage.Window w : java.util.List.copyOf(javafx.stage.Window.getWindows())) if (w != stage) w.hide();
                });
                closeDialog.play();
                TextField badSteps = field(props,"Fokszám");
                badSteps.setText("3"); badSteps.fireEvent(new ActionEvent());
                assertEquals(before,ed.snapshot());
                assertFalse(ed.status.get().isBlank());
            } finally { stage.close(); }
        });
    }

    @Test void originalBathroomModelsAndBothStairsDisplayInTheEditor() throws Exception {
        hu.helmetsoft.homedesigner.io.BuiltinModels.write(temporary);
        Project p = new Project(); p.env.showGround = true;
        ProjectOps.addRoomWithWalls(p,ProjectOps.rect(-5,-3,5,5),"Fürdőszoba");
        p.walls.clear();
        String[] names = {"Mosdoszekreny_80","WC_tartalyos","Kad_szabadonallo_170","Zuhanykabin_90","Furdoszobaszekreny_40"};
        double[][] positions = {{-4.1,-2},{-2.6,-2},{-4.1,0.2},{-1.2,0.5},{-1.2,-2}};
        for (int i = 0; i < names.length; i++) {
            Path path = temporary.resolve(names[i]+".obj");
            var model = hu.helmetsoft.homedesigner.io.ModelLibrary.get(path.toString(),null).get(30,TimeUnit.SECONDS);
            Furniture f = new Furniture(); f.name = names[i]; f.modelPath = path.toString(); f.x = positions[i][0]; f.y = positions[i][1];
            ProjectOps.applyModelSize(f,model,-1); p.furniture.add(f);
        }
        fx(() -> {
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); View3D view = new View3D(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            Furniture straight = ed.addStair(Stair.Type.STRAIGHT,1.5,-0.5); straight.angle = 90;
            Furniture spiral = ed.addStair(Stair.Type.SPIRAL,4.5,-0.5);
            HBox root = new HBox(plan,view,props);
            plan.setPrefWidth(450); view.setPrefWidth(700); HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage = new Stage(); stage.setScene(new Scene(root,1500,900)); stage.show();
            try {
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot()); assertNotNull(field(props,"Külső átmérő"));
                assertSame(spiral,ed.selection());
                String dir = System.getProperty("hd.testSnapshots");
                if (dir != null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"bathroom-and-stairs.png"));
                    ed.select(ed.project().furniture.get(5)); root.applyCss(); root.layout();
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"straight-stair-properties.png"));
                    ed.project().furniture.removeIf(f -> f.stair != null);
                    ed.select(ed.project().furniture.get(3)); ed.changedNow(); view.resetCamera(); view.showDims.setSelected(false);
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"bathroom-closeup.png"));
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            } finally { stage.close(); }
        });
    }

    @Test void bathroomCatalogFiltersAccentsAddsNaturalSizeAndOffersParametricStairs() throws Exception {
        Path bath = temporary.resolve(hu.helmetsoft.homedesigner.io.BuiltinModels.FOLDER);
        hu.helmetsoft.homedesigner.io.BuiltinModels.write(bath);
        Path path = bath.resolve("Zuhanykabin_90.obj");
        hu.helmetsoft.homedesigner.io.ModelLibrary.get(path.toString(),null).get(30,TimeUnit.SECONDS);
        fx(() -> {
            Editor ed = new Editor(new Project()); PlanView plan = new PlanView(ed);
            CatalogPanel catalog = new CatalogPanel(ed,plan,temporary);
            Stage stage = new Stage(); stage.setScene(new Scene(new HBox(catalog,plan),900,800)); stage.show();
            try {
                catalog.applyCss(); catalog.layout();
                ComboBox<?> categories = (ComboBox<?>)catalog.lookup(".combo-box");
                categories.getSelectionModel().select(categories.getItems().indexOf("Fürdőszoba"));
                ListView<?> list = catalog.getChildren().stream().filter(n -> n instanceof ListView<?>)
                        .map(n -> (ListView<?>)n).findFirst().orElseThrow();
                assertEquals(15,list.getItems().size());
                TextField filter = (TextField)catalog.lookup(".text-field");
                filter.setText("fürdő"); assertEquals(1,list.getItems().size());
                filter.setText("ZUHANY"); assertEquals(2,list.getItems().size());
                filter.setText("zuhanykabin"); assertEquals(1,list.getItems().size());
                list.getSelectionModel().selectFirst();
                list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,false,false,false,false));
                assertEquals(1,ed.project().furniture.size());
                Furniture shower = ed.project().furniture.getFirst();
                assertEquals(0.9,shower.width,0.002); assertEquals(2.015,shower.height,0.002);
                assertEquals(path.toAbsolutePath().toString(),shower.modelPath);
                MenuButton stairs = catalog.lookupAll(".menu-button").stream().filter(n -> n instanceof MenuButton m && m.getText().contains("Lépcső"))
                        .map(n -> (MenuButton)n).findFirst().orElseThrow();
                stairs.getItems().get(1).fire();
                assertEquals(Stair.Type.SPIRAL,((Furniture)ed.selection()).stair.type);
                var material = hu.helmetsoft.homedesigner.io.ModelLibrary.placed(shower).parts.stream()
                        .filter(part -> part.material.glass).findFirst().orElseThrow().material;
                var alphaMap = FxUtil.material(material).getDiffuseMap();
                assertNotNull(alphaMap);
                assertEquals(material.opacity,alphaMap.getPixelReader().getColor(0,0).getOpacity(),0.005);
                ed.undo(); assertEquals(1,ed.project().furniture.size());
            } finally { stage.close(); }
        });
    }

    private static ComboBox<?> roofTypes(PropertiesPanel props) {
        props.applyCss(); props.layout();
        return props.lookupAll(".combo-box").stream().filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Roof.Type.DOME))
                .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
    }

    private static void click(PlanView plan, double x, double y) {
        mouse(plan,MouseEvent.MOUSE_PRESSED,x,y,MouseButton.PRIMARY);
        mouse(plan,MouseEvent.MOUSE_RELEASED,x,y,MouseButton.PRIMARY);
    }

    @Test void allOpeningShapesCanBeDrawnAndUnfinishedDrawingCanBeCancelled() throws Exception {
        fx(() -> {
            Project p = new Project(); Room r = ProjectOps.addRoom(p,ProjectOps.rect(0,0,12,10),"Nappali"); r.hasCeiling = false;
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            plan.setPrefWidth(700); HBox.setHgrow(plan,Priority.ALWAYS);
            Stage stage = new Stage(); stage.setScene(new Scene(new HBox(plan,props),1050,800)); stage.show();
            try {
                plan.fit(); ed.select(r);
                ed.tool.set(Editor.Tool.SLAB_RECT);
                mouse(plan,MouseEvent.MOUSE_PRESSED,1,1,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,3,4,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,3,4,MouseButton.PRIMARY);
                assertEquals(1,r.slabOpenings.size()); assertTrue(r.hasCeiling);
                SlabOpening rectangle = r.slabOpenings.getFirst();
                assertEquals(2,rectangle.width,1e-9); assertEquals(3,rectangle.depth,1e-9);
                assertTrue(rectangle.cutCeiling); assertFalse(rectangle.cutFloor); assertSame(rectangle,ed.selection());
                assertEquals(Editor.Tool.SELECT,ed.tool.get());
                ed.tool.set(Editor.Tool.SLAB_CIRCLE);
                mouse(plan,MouseEvent.MOUSE_PRESSED,5,3,MouseButton.PRIMARY);
                for (char c : "1,2".toCharArray()) plan.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED,String.valueOf(c),"",KeyCode.UNDEFINED,false,false,false,false));
                key(plan,KeyCode.ENTER);
                assertEquals(2,r.slabOpenings.size());
                assertEquals(2.4,r.slabOpenings.get(1).width,1e-9);
                assertEquals(r.slabOpenings.get(1).width,r.slabOpenings.get(1).depth,1e-9);
                ed.tool.set(Editor.Tool.SLAB_ELLIPSE);
                mouse(plan,MouseEvent.MOUSE_PRESSED,7,1,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,10,3,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,10,3,MouseButton.PRIMARY);
                assertEquals(SlabOpening.Shape.ELLIPSE,r.slabOpenings.getLast().shape);
                ed.tool.set(Editor.Tool.SLAB_POLYGON);
                for (double[] point : new double[][]{{2,6},{5,6},{5,7},{3,7},{3,9},{2,9}})
                    mouse(plan,MouseEvent.MOUSE_PRESSED,point[0],point[1],MouseButton.PRIMARY);
                key(plan,KeyCode.ENTER);
                assertEquals(4,r.slabOpenings.size()); assertEquals(6,r.slabOpenings.getLast().points.size());
                assertNotNull(field(props,"X 1"));
                ed.tool.set(Editor.Tool.SLAB_POLYGON);
                for (double[] point : new double[][]{{7,6},{9,6},{9,8}})
                    mouse(plan,MouseEvent.MOUSE_PRESSED,point[0],point[1],MouseButton.PRIMARY);
                key(plan,KeyCode.ESCAPE); assertEquals(4,r.slabOpenings.size());
                ed.tool.set(Editor.Tool.SLAB_RECT);
                mouse(plan,MouseEvent.MOUSE_PRESSED,7,6,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,9,8,MouseButton.PRIMARY);
                key(plan,KeyCode.ESCAPE);
                mouse(plan,MouseEvent.MOUSE_RELEASED,9,8,MouseButton.PRIMARY);
                assertEquals(4,r.slabOpenings.size());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
            } finally { stage.close(); }
        });
    }

    @Test void openingPropertiesMoveResizeRotateDuplicateUndoAndDelete() throws Exception {
        fx(() -> {
            Project p = new Project(); Room r = ProjectOps.addRoom(p,ProjectOps.rect(0,0,10,10),"Szoba");
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            HBox.setHgrow(plan,Priority.ALWAYS); plan.setPrefWidth(700);
            Stage stage = new Stage(); stage.setScene(new Scene(new HBox(plan,props),1100,850)); stage.show();
            try {
                plan.fit();
                SlabOpening o = new SlabOpening(); o.x = 3; o.y = 3; o.width = o.depth = 2; o.shape = SlabOpening.Shape.CIRCLE;
                assertTrue(ed.addSlabOpening(r,o));
                TextField diameter = field(props,"Átmérő"); diameter.setText("240"); diameter.fireEvent(new ActionEvent());
                assertEquals(2.4,o.width,1e-9); assertEquals(o.width,o.depth,1e-9);
                var floor = props.lookupAll(".check-box").stream().map(n -> (javafx.scene.control.CheckBox)n)
                        .filter(c -> c.getText().equals("Padló kivágása")).findFirst().orElseThrow();
                floor.fire(); assertTrue(o.cutFloor);
                TextField angle = field(props,"Forgatás"); angle.setText("30"); angle.fireEvent(new ActionEvent()); assertEquals(30,o.angle,1e-9);
                // Move with the keyboard, then restore selected child identity through undo/redo.
                key(plan,KeyCode.RIGHT); assertEquals(3.01,o.x,1e-9);
                ed.undo(); ed.redo(); o = (SlabOpening)ed.selection();
                assertEquals(3.01,o.x,1e-9); assertEquals(1,ed.project().rooms.getFirst().slabOpenings.size());
                o.angle = 0; ed.changedNow();
                double cornerX = o.x+o.width/2, cornerY = o.y+o.depth/2;
                mouse(plan,MouseEvent.MOUSE_PRESSED,cornerX,cornerY,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,cornerX+0.4,cornerY+0.1,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,cornerX+0.4,cornerY+0.1,MouseButton.PRIMARY);
                assertEquals(o.width,o.depth,1e-9); assertTrue(o.width > 2.7);
                plan.duplicate(o); assertEquals(2,ed.project().rooms.getFirst().slabOpenings.size());
                assertNotSame(o,ed.selection()); ed.delete(ed.selection()); ed.undo(); ed.redo();
                assertEquals(1,ed.project().rooms.getFirst().slabOpenings.size());
                ed.select(ed.project().rooms.getFirst().slabOpenings.getFirst());
                ed.delete(ed.project().rooms.getFirst()); assertTrue(ed.project().rooms.isEmpty());
                ed.undo(); assertEquals(1,ed.project().rooms.getFirst().slabOpenings.size());
            } finally { stage.close(); }
        });
    }

    @Test void polygonVerticesCanBeDraggedInsertedAndRemovedAndMoveWithTheirRoom() throws Exception {
        fx(() -> {
            Project p = new Project(); Room r = ProjectOps.addRoom(p,ProjectOps.rect(0,0,10,10),"Szoba");
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan);
            HBox.setHgrow(plan,Priority.ALWAYS); plan.setPrefWidth(700);
            Stage stage = new Stage(); stage.setScene(new Scene(new HBox(plan,props),1100,850)); stage.show();
            try {
                plan.fit(); SlabOpening o = hu.helmetsoft.homedesigner.geom.SlabGeometry.polygon(ProjectOps.rect(2,2,3,3));
                ed.addSlabOpening(r,o);
                mouse(plan,MouseEvent.MOUSE_PRESSED,2,2,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,1.5,1.5,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,1.5,1.5,MouseButton.PRIMARY);
                assertEquals(1.5,hu.helmetsoft.homedesigner.geom.SlabGeometry.boundary(o)[0][0],1e-9);
                props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Pont beszúrása utána"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                ed.changedNow(); assertEquals(5,o.points.size());
                props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Pont törlése"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                ed.changedNow(); assertEquals(4,o.points.size());
                double x = o.x, y = o.y; ed.select(r); key(plan,KeyCode.RIGHT); key(plan,KeyCode.DOWN);
                assertEquals(x+0.01,o.x,1e-9); assertEquals(y+0.01,o.y,1e-9);
                ed.undo(); ed.redo();
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
            } finally { stage.close(); }
        });
    }

    @Test void stairButtonsCreateFittingIndependentHolesAndLiveThreeDimensionalScene() throws Exception {
        fx(() -> {
            Project p = new Project(); Room r = ProjectOps.addRoom(p,ProjectOps.rect(-4,-4,12,10),"Lépcsőház");
            r.ceilingHeight = 2.8; p.env.showGround = true;
            Editor ed = new Editor(p); PlanView plan = new PlanView(ed); PropertiesPanel props = new PropertiesPanel(ed,plan); View3D view = new View3D(ed);
            Furniture straight = ed.addStair(Stair.Type.STRAIGHT,-1,0); straight.angle = 20;
            ed.openingForStair(straight,SlabOpening.Shape.RECTANGLE);
            SlabOpening rect = r.slabOpenings.getFirst(); assertEquals(straight.angle,rect.angle,1e-9);
            assertTrue(rect.width > straight.width); assertTrue(rect.depth > straight.depth);
            Furniture spiral = ed.addStair(Stair.Type.SPIRAL,4,0);
            HBox root = new HBox(plan,view,props); plan.setPrefWidth(420); view.setPrefWidth(700);
            HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage = new Stage(); stage.setScene(new Scene(root,1500,900)); stage.show();
            try {
                props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("+ Környílás"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                SlabOpening circle = (SlabOpening)ed.selection();
                assertEquals(SlabOpening.Shape.CIRCLE,circle.shape); assertEquals(spiral.width+0.2,circle.width,1e-9);
                assertFalse(circle.cutFloor); assertTrue(circle.cutCeiling); assertTrue(r.hasCeiling);
                double originalX = circle.x; ed.edit(() -> spiral.x += 0.4); assertEquals(originalX,circle.x,1e-9);
                root.applyCss(); root.layout(); plan.fit(); view.showCeiling.setSelected(true); view.showDims.setSelected(false); view.resetCamera();
                view.rebuild(false); assertNotNull(view.snapshot());
                String dir = System.getProperty("hd.testSnapshots");
                if (dir != null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"ceiling-openings-and-stairs.png"));
                    ed.edit(() -> { circle.cutFloor = true; rect.cutFloor = true; });
                    ed.changedNow();
                    view.showCeiling.setSelected(false); view.rebuild(false); root.applyCss(); root.layout();
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"floor-openings-and-stairs.png"));
                } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
            } finally { stage.close(); }
        });
    }

    @Test void roomCanBeSelectedByFloorClickOrProjectPickerAndCeilingCanBeEnabled() throws Exception {
        fx(() -> {
            Project p=new Project();
            Room first=ProjectOps.addRoom(p,ProjectOps.rect(0,0,4,4),"Első"); first.hasCeiling=false;
            Room second=ProjectOps.addRoom(p,ProjectOps.rect(5,0,4,4),"Második"); second.hasCeiling=false;
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            HBox root=new HBox(plan,props); HBox.setHgrow(plan,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1100,800)); stage.show();
            try {
                root.applyCss(); root.layout(); plan.fit();
                click(plan,2,2); assertSame(first,ed.selection());
                click(plan,7,2); assertSame(second,ed.selection());
                ed.select(null);
                ComboBox<?> picker=(ComboBox<?>)props.lookup("#project-room-picker");
                assertNotNull(picker); assertEquals(2,picker.getItems().size());
                picker.getSelectionModel().select(1);
                ed.tool.set(Editor.Tool.WALL);
                props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Helyiség megnyitása"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                assertSame(second,ed.selection()); assertEquals(Editor.Tool.SELECT,ed.tool.get());
                var ceiling=props.lookupAll(".check-box").stream()
                        .filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().equals("Van mennyezet"))
                        .map(n -> (javafx.scene.control.CheckBox)n).findFirst().orElseThrow();
                ceiling.fire(); assertTrue(second.hasCeiling); assertFalse(first.hasCeiling);
                ed.undo(); assertFalse(ed.project().rooms.get(1).hasCeiling);
                ed.redo(); assertTrue(ed.project().rooms.get(1).hasCeiling);
                assertTrue(ProjectIO.copy(ed.project()).rooms.get(1).hasCeiling);
                Editor empty=new Editor(new Project()); PropertiesPanel emptyProps=new PropertiesPanel(empty,new PlanView(empty));
                root.getChildren().setAll(plan,emptyProps); root.applyCss(); root.layout();
                Button draw=emptyProps.lookupAll(".button").stream()
                        .filter(n -> n instanceof Button b && b.getText().equals("Padló / helyiség rajzolása"))
                        .map(n -> (Button)n).findFirst().orElseThrow();
                draw.fire(); assertEquals(Editor.Tool.FLOOR,empty.tool.get()); assertTrue(empty.project().rooms.isEmpty());
            } finally { stage.close(); }
        });
    }

    @Test void ceilingSurvivesWalkExitAndCameraResetAndGuideReallyScrolls() throws Exception {
        fx(() -> {
            Project p=new Project(); Room room=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,6),"Mennyezet");
            Editor ed=new Editor(p); View3D view=new View3D(ed);
            Stage stage=new Stage(); stage.setScene(new Scene(view,850,650)); stage.show();
            try {
                assertTrue(view.showCeiling.isSelected()); assertTrue(room.hasCeiling);
                view.setWalkMode(true); view.setWalkMode(false);
                assertTrue(view.showCeiling.isSelected());
                view.setWalkMode(true); view.resetCamera();
                assertTrue(view.showCeiling.isSelected()); assertFalse(view.isWalk());
                view.showCeiling.setSelected(false); view.resetCamera(); assertFalse(view.showCeiling.isSelected());
                view.setWalkMode(true); view.showCeiling.setSelected(false); view.setWalkMode(false);
                assertFalse(view.showCeiling.isSelected());
                var guide=Dialogs.guide("Útmutató","Hosszú súgószöveg, görgetéssel.\n".repeat(200));
                guide.show();
                try {
                    var scroll=(javafx.scene.control.ScrollPane)guide.getDialogPane().getContent();
                    guide.getDialogPane().applyCss(); guide.getDialogPane().layout();
                    assertTrue(scroll.getContent().getBoundsInLocal().getHeight()>scroll.getViewportBounds().getHeight()*3);
                    scroll.setVvalue(1); assertEquals(1,scroll.getVvalue(),1e-9);
                    assertTrue(guide.isResizable()); assertTrue(guide.getHeight()<800);
                } finally { guide.close(); }
            } finally { stage.close(); }
        });
    }

    @Test void customCabinetEditorChangesStructureDuplicatesAndPersists() throws Exception {
        fx(() -> {
            Editor ed=new Editor(new Project()); PlanView plan=new PlanView(ed); PropertiesPanel props=new PropertiesPanel(ed,plan); View3D view=new View3D(ed);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(400); view.setPrefWidth(650);
            HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1400,850)); stage.show();
            try {
                Furniture f=ed.addCabinet(0,0);
                TextField width=field(props,"Szélesség"); width.setText("180"); width.fireEvent(new ActionEvent());
                TextField cols=field(props,"Oszlopok"); cols.setText("3"); cols.fireEvent(new ActionEvent());
                TextField drawers=field(props,"Fiókok / oszlop"); drawers.setText("2"); drawers.fireEvent(new ActionEvent());
                assertEquals(1.8,f.width,1e-9); assertEquals(3,f.cabinet.columns); assertEquals(2,f.cabinet.drawers);
                ed.undo(); ed.redo(); f=(Furniture)ed.selection(); assertNotNull(f.cabinet);
                props.lookupAll(".check-box").stream().filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().startsWith("Ajtók nyitva"))
                        .map(n -> (javafx.scene.control.CheckBox)n).findFirst().orElseThrow().fire();
                assertTrue(f.cabinet.openDoors);
                plan.duplicate(f); Furniture copy=(Furniture)ed.selection();
                assertNotSame(f.cabinet,copy.cabinet); assertNotSame(f.cabinet.body,copy.cabinet.body);
                ed.delete(copy); ed.undo(); ed.redo(); ed.select(ed.project().furniture.getFirst());
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try { Files.createDirectories(Path.of(dir)); saveImage(stage.getScene().snapshot(null),Path.of(dir,"cabinet-editor.png")); }
                catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    @Test void materialThumbnailsUseSmallImagesWithoutReducingTheRenderTexture() throws Exception {
        fx(() -> {
            var preset=hu.helmetsoft.homedesigner.material.MaterialLibrary.preset("oak_wood");
            var texture=hu.helmetsoft.homedesigner.material.MaterialLibrary.texture(preset.id);
            assertTrue(texture.w>64);
            var first=(StackPane)FinishEditor.thumb(preset,null,22);
            var second=(StackPane)FinishEditor.thumb(preset,"#FF0000",22);
            var image=((javafx.scene.image.ImageView)first.getChildren().getFirst()).getImage();
            assertTrue(image.getWidth()<=64); assertTrue(image.getHeight()<=64);
            assertSame(image,((javafx.scene.image.ImageView)second.getChildren().getFirst()).getImage());
            assertTrue(texture.w>64);
        });
    }

    @Test void electronicsBooksHeatingAndFansCanBePlacedFilteredAndDisplayed() throws Exception {
        var categories=List.of(hu.helmetsoft.homedesigner.io.HomeModels.Category.ELECTRONICS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.COMPUTERS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.BOOKS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.HEATING,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.FANS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.BEDS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.LAMPS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.HALL,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.ELECTRICAL,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.SOLAR,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.MECHANICAL,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.POOL,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN_BUILDINGS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN_PLANTS,
                hu.helmetsoft.homedesigner.io.HomeModels.Category.INDOOR_PLANTS);
        Path pack=temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER);
        hu.helmetsoft.homedesigner.io.HomeModels.write(pack);
        java.util.Map<hu.helmetsoft.homedesigner.io.HomeModels.Category,List<Furniture>> groups=new java.util.LinkedHashMap<>();
        for(var category:categories) {
            List<Furniture> furniture=new java.util.ArrayList<>();
            for(var asset:hu.helmetsoft.homedesigner.io.HomeModels.ASSETS) if(asset.category()==category) {
                Path file=pack.resolve(category.name()).resolve(asset.name()+".obj");
                var model=hu.helmetsoft.homedesigner.io.ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
                Furniture f=new Furniture(); f.name=asset.name(); f.modelPath=file.toString();
                ProjectOps.applyModelSize(f,model,-1);
                int i=furniture.size();
                double spacing=category==hu.helmetsoft.homedesigner.io.HomeModels.Category.POOL ? 8
                        : category==hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN_BUILDINGS ? 4
                        : category==hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN_PLANTS ? 5
                        : category==hu.helmetsoft.homedesigner.io.HomeModels.Category.GARDEN ? 3 : 1.8;
                f.x=(i%3)*spacing; f.y=(i/3)*spacing;
                furniture.add(f);
            }
            groups.put(category,furniture);
        }
        fx(() -> {
            Editor ed=new Editor(new Project()); PlanView plan=new PlanView(ed); View3D view=new View3D(ed);
            CatalogPanel catalog=new CatalogPanel(ed,plan,temporary);
            HBox root=new HBox(catalog,plan,view); plan.setPrefWidth(300); view.setPrefWidth(1000);
            HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1550,900)); stage.show();
            try {
                root.applyCss(); root.layout();
                ComboBox<?> picker=(ComboBox<?>)catalog.lookup(".combo-box");
                ListView<?> list=catalog.getChildren().stream().filter(n -> n instanceof ListView<?>)
                        .map(n -> (ListView<?>)n).findFirst().orElseThrow();
                TextField filter=(TextField)catalog.lookup(".text-field");
                for(var entry:groups.entrySet()) {
                    picker.getSelectionModel().select(picker.getItems().indexOf(entry.getKey().label));
                    assertEquals(entry.getValue().size(),list.getItems().size());
                    ed.project().furniture.clear(); ed.project().furniture.addAll(entry.getValue()); ed.changedNow();
                    plan.fit(); view.resetCamera(); view.showDims.setSelected(false); view.rebuild(true);
                    for(Furniture f:entry.getValue()) {
                        assertNotNull(hu.helmetsoft.homedesigner.io.ModelLibrary.placed(f));
                        assertNotNull(hu.helmetsoft.homedesigner.io.ModelLibrary.placedTop(f));
                    }
                    assertNotNull(view.snapshot());
                    assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.copy(ed.project())));
                    String dir=System.getProperty("hd.testSnapshots");
                    if(dir!=null) try {
                        Files.createDirectories(Path.of(dir));
                        saveImage(stage.getScene().snapshot(null),Path.of(dir,"catalog-"+entry.getKey().name()+".png"));
                    } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }
                picker.getSelectionModel().select(picker.getItems().indexOf("Radiátorok és konvektorok"));
                filter.setText("ALUMÍNIUM"); assertEquals(1,list.getItems().size());
                list.getSelectionModel().selectFirst();
                int count=ed.project().furniture.size();
                list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,false,false,false,false));
                assertEquals(count+1,ed.project().furniture.size());
                assertTrue(((Furniture)ed.selection()).name.toLowerCase().contains("aluminium"));
                ed.undo(); assertEquals(count,ed.project().furniture.size());
            } finally { stage.close(); }
        });
    }

    @Test void floorLevelsCanBeDrawnEditedMovedUndoneAndConnectedWithTwoSteps() throws Exception {
        fx(() -> {
            Project p=new Project();
            Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,8,6),"Nappali");
            Room lower=ProjectOps.addRoom(p,ProjectOps.rect(8,0,4,6),"Alsó szoba"); lower.elevation=-0.51;
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); PropertiesPanel props=new PropertiesPanel(ed,plan); View3D view=new View3D(ed);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(430); view.setPrefWidth(700);
            HBox.setHgrow(view,Priority.ALWAYS); Stage stage=new Stage(); stage.setScene(new Scene(root,1550,900)); stage.show();
            try {
                root.applyCss(); root.layout(); plan.fit();
                ed.select(r);
                TextField elevation=field(props,"Padlószint (abszolút)");
                elevation.setText("-17"); elevation.fireEvent(new ActionEvent());
                assertEquals(-0.17,r.elevation,1e-9);
                ed.tool.set(Editor.Tool.FLOOR_LEVEL);
                mouse(plan,MouseEvent.MOUSE_PRESSED,2,1.5,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,5,4.5,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,5,4.5,MouseButton.PRIMARY);
                assertEquals(1,r.floorZones.size()); FloorZone zone=r.floorZones.getFirst();
                assertSame(zone,ed.selection()); assertEquals(3,zone.outline.width,1e-9); assertEquals(3,zone.outline.depth,1e-9);
                assertEquals(-0.34,zone.offset,1e-9);
                assertNotNull(field(props,"Szinteltérés a szobához"));
                TextField offset=field(props,"Szinteltérés a szobához");
                offset.setText("-51"); offset.fireEvent(new ActionEvent());
                assertEquals(-0.51,zone.offset,1e-9); ed.undo(); ed.redo();
                zone=ed.project().rooms.getFirst().floorZones.getFirst();
                assertSame(zone,ed.selection()); assertEquals(-0.51,zone.offset,1e-9);
                offset=field(props,"Szinteltérés a szobához"); offset.setText("-34"); offset.fireEvent(new ActionEvent());
                ed.changedNow();
                mouse(plan,MouseEvent.MOUSE_PRESSED,3.5,3,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,4,3,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,4,3,MouseButton.PRIMARY);
                assertEquals(4,zone.outline.x,1e-9);
                mouse(plan,MouseEvent.MOUSE_PRESSED,5.5,4.5,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_DRAGGED,5.7,4.7,MouseButton.PRIMARY);
                mouse(plan,MouseEvent.MOUSE_RELEASED,5.7,4.7,MouseButton.PRIMARY);
                assertEquals(3.2,zone.outline.width,0.02); assertEquals(3.2,zone.outline.depth,0.02);
                props.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("Lépcső létrehozása"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                Furniture steps=(Furniture)ed.selection();
                assertEquals(2,steps.stair.steps); assertTrue(steps.stair.solid);
                assertEquals(-0.51,steps.elevation,1e-9); assertEquals(0.34,steps.height,1e-9);
                assertNotNull(field(props,"Fokszám"));
                ed.changedNow(); view.resetCamera(); view.showCeiling.setSelected(false); view.showDims.setSelected(false); view.rebuild(true);
                assertNotNull(view.snapshot());
                ed.select(zone); ed.delete(zone); assertTrue(ed.project().rooms.getFirst().floorZones.isEmpty());
                ed.undo(); assertEquals(1,ed.project().rooms.getFirst().floorZones.size());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.copy(ed.project())));
                ed.select(ed.project().rooms.getFirst().floorZones.getFirst());
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir)); root.applyCss(); root.layout();
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"floor-levels-and-steps.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    @Test void walkthroughStartsAtTheLocalFloorAndScrollFollowsLevelChanges() throws Exception {
        fx(() -> {
            Project p=new Project(); Room r=ProjectOps.addRoom(p,ProjectOps.rect(-5,-5,10,10),"Szoba"); r.elevation=-0.51;
            FloorZone zone=new FloorZone(); zone.outline.x=zone.outline.y=0; zone.outline.width=zone.outline.depth=1; r.floorZones.add(zone);
            Editor ed=new Editor(p); View3D view=new View3D(ed); Stage stage=new Stage();
            stage.setScene(new Scene(view,800,600)); stage.show();
            try {
                view.setWalkMode(true);
                assertEquals(0.75,view.eye()[1],1e-6);
                view.getChildren().stream().filter(n -> n instanceof javafx.scene.SubScene).findFirst().orElseThrow()
                        .fireEvent(new ScrollEvent(ScrollEvent.SCROLL,400,300,400,300,false,false,false,false,
                        false,false,0,200,0,200,ScrollEvent.HorizontalTextScrollUnits.NONE,0,
                        ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));
                assertEquals(1.09,view.eye()[1],1e-6);
                Furniture f=ed.addCabinet(0,0);
                assertEquals(-0.85,f.elevation,1e-9);
            } finally { stage.close(); }
        });
    }

    @Test void walkthroughRecordingTracksCameraAndStopsIntoPlayableMp4() throws Exception {
        var stage=new java.util.concurrent.atomic.AtomicReference<Stage>();
        var view=new java.util.concurrent.atomic.AtomicReference<View3D>();
        var saved=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Path>>();
        Path path=temporary.resolve("walk-ui.mp4");
        try {
            fx(() -> {
                View3D v=new View3D(new Editor(example())); view.set(v);
                Stage s=new Stage(); stage.set(s); s.setScene(new Scene(v,800,600)); s.show();
                v.applyCss(); v.layout(); v.showDims.setSelected(false);
                try { v.startRecording(path); } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                assertTrue(v.isWalk());
                assertTrue(((Button)v.lookup("#walk-record")).isDisabled());
                assertFalse(((Button)v.lookup("#walk-stop")).isDisabled());
            });
            Thread.sleep(600);
            fx(() -> { view.get().debugDrag(MouseButton.PRIMARY,180); stage.get().setWidth(1000); });
            Thread.sleep(1000);
            fx(() -> {
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.get().getScene().snapshot(null),Path.of(dir,"walk-recording.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                ((Button)view.get().lookup("#walk-stop")).fire();
                saved.set(view.get().stopRecording());
            });
            assertEquals(path,saved.get().get(60,TimeUnit.SECONDS));
            fx(() -> {
                assertFalse(((Button)view.get().lookup("#walk-record")).isDisabled());
                assertTrue(((Button)view.get().lookup("#walk-stop")).isDisabled());
                assertTrue(((Label)view.get().lookup("#walk-record-status")).getText().startsWith("Mentve:"));
            });
            try(var channel=org.jcodec.common.io.NIOUtils.readableChannel(path.toFile())) {
                var grab=org.jcodec.api.awt.AWTFrameGrab.createAWTFrameGrab(channel);
                var meta=grab.getVideoTrack().getMeta();
                assertTrue(meta.getTotalFrames()>=20);
                var first=grab.getFrame(); java.awt.image.BufferedImage last=first,frame;
                while((frame=grab.getFrame())!=null) last=frame;
                assertEquals(first.getWidth(),last.getWidth());
                assertTrue(first.getWidth()<=960 && first.getHeight()<=540);
                long difference=0;
                for(int y=0;y<first.getHeight();y+=5) for(int x=0;x<first.getWidth();x+=5)
                    difference+=Math.abs((first.getRGB(x,y)&255)-(last.getRGB(x,y)&255));
                assertTrue(difference>10000,"Recording must follow camera motion, not repeat a stuck snapshot");
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) {
                    assertTrue(javax.imageio.ImageIO.write(first,"png",Path.of(dir,"video-first.png").toFile()));
                    assertTrue(javax.imageio.ImageIO.write(last,"png",Path.of(dir,"video-last.png").toFile()));
                }
            }
        } finally {
            fx(() -> { if(stage.get()!=null) stage.get().close(); });
        }
    }

    @Test void partialDoorGlazingCanBeEditedUndoneAndRendered() throws Exception {
        fx(() -> {
            Project p=new Project(); Wall w=new Wall(0,0,4,0,0.2,3); p.walls.add(w);
            Opening door=Opening.create(Opening.Type.DOOR,2); w.openings.add(door);
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); View3D view=new View3D(ed);
            PropertiesPanel props=new PropertiesPanel(ed,plan);
            HBox root=new HBox(plan,view,props); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1350,850)); stage.show();
            try {
                ed.select(door); root.applyCss(); root.layout();
                props.lookupAll(".check-box").stream()
                        .filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().equals("Részleges üvegezés"))
                        .map(n -> (javafx.scene.control.CheckBox)n).findFirst().orElseThrow().fire();
                assertTrue(door.glazed);
                ed.changedNow();
                TextField width=field(props,"Üveg szélessége"); width.setText("30"); width.fireEvent(new ActionEvent());
                assertEquals(0.3,door.glassWidth*door.width,1e-9);
                ed.undo(); ed.redo(); Opening restored=(Opening)ed.selection();
                assertTrue(restored.glazed); assertEquals(0.3,restored.glassWidth*restored.width,1e-9);
                restored.flip=true; ed.changedNow();
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.copy(ed.project())));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try { Files.createDirectories(Path.of(dir)); saveImage(stage.getScene().snapshot(null),Path.of(dir,"door-glazing.png")); }
                catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    @Test void solarPanelsFromTheCatalogEditTiltRegenerateSupportsAndUndoIndependently() throws Exception {
        fx(() -> {
            Editor ed=new Editor(new Project()); PlanView plan=new PlanView(ed);
            PropertiesPanel props=new PropertiesPanel(ed,plan); View3D view=new View3D(ed);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(400); view.setPrefWidth(700);
            HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1450,850)); stage.show();
            try {
                for(var type:SolarPanel.Type.values()) {
                    String name=type==SolarPanel.Type.ROOF ? "Napelem_tetore.obj" : "Napelem_talajra.obj";
                    Furniture f=ed.addFurniture(temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER)
                            .resolve("SOLAR").resolve(name).toString(),type.ordinal()*3,0);
                    assertNotNull(f.solar); assertEquals(type,f.solar.type); assertNull(f.modelPath);
                    double before=f.height;
                    TextField tilt=field(props,"Dőlésszög"); tilt.setText("60"); tilt.fireEvent(new ActionEvent());
                    assertEquals(60,f.solar.tilt); assertTrue(f.height>before);
                    ed.undo(); ed.changedNow();
                    Furniture restored=(Furniture)ed.selection(); assertEquals(30,restored.solar.tilt);
                    ed.redo(); ed.changedNow(); restored=(Furniture)ed.selection();
                    assertEquals(60,restored.solar.tilt);
                    field(props,"Panel hossza").setText("240"); field(props,"Panel hossza").fireEvent(new ActionEvent());
                    assertEquals(2.4,restored.solar.length,1e-9);
                    plan.duplicate(restored); Furniture copy=(Furniture)ed.selection();
                    assertNotSame(restored.solar,copy.solar);
                    assertEquals(restored.height,copy.height,1e-9);
                    ed.delete(copy); ed.changedNow();
                }
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.copy(ed.project())));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"solar-editor.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    @Test void newGardenAssetsLoadAtMetricSizeAndAppearOnTheSlopingSite() throws Exception {
        var models=temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER);
        hu.helmetsoft.homedesigner.io.HomeModels.write(models);
        for(String name:List.of("Cseresznyefa","Kortefa","Datolyaszilvafa","Diofa","Meggyfa","Almafa","Naspolyafa","Szolo_lugas"))
            hu.helmetsoft.homedesigner.io.ModelLibrary.get(models.resolve("GARDEN_PLANTS").resolve(name+".obj").toString(),null).get(30,TimeUnit.SECONDS);
        for(String name:List.of("Palapa","Filagoria"))
            hu.helmetsoft.homedesigner.io.ModelLibrary.get(models.resolve("GARDEN_BUILDINGS").resolve(name+".obj").toString(),null).get(30,TimeUnit.SECONDS);
        hu.helmetsoft.homedesigner.io.ModelLibrary.get(models.resolve("GARDEN").resolve("Sorgarnitura_6_szemelyes.obj").toString(),null).get(30,TimeUnit.SECONDS);
        fx(() -> {
            Project project=new Project(); project.env.terrainSlope=8; project.env.terrainAzimuth=90;
            Editor ed=new Editor(project); PlanView plan=new PlanView(ed); View3D view=new View3D(ed);
            HBox root=new HBox(plan,view); plan.setPrefWidth(360); view.setPrefWidth(1000); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1400,850)); stage.show();
            try {
                String[] trees={"Cseresznyefa","Kortefa","Datolyaszilvafa","Diofa","Meggyfa","Almafa","Naspolyafa"};
                for(int i=0;i<trees.length;i++) {
                    Furniture f=ed.addFurniture(models.resolve("GARDEN_PLANTS").resolve(trees[i]+".obj").toString(),i*4,0);
                    assertNotNull(f); assertTrue(f.height>1.5);
                    assertEquals(Math.tan(Math.toRadians(8))*i*4,f.elevation,1e-7);
                }
                ed.changedNow(); root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"fruit-orchard.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                Furniture walnut=ed.project().furniture.get(3);
                for(Furniture f:List.copyOf(ed.project().furniture)) if(f!=walnut) ed.delete(f);
                ed.select(null); ed.changedNow(); view.showDims.setSelected(false); view.resetCamera(); view.rebuild(true);
                if(dir!=null) try {
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"walnut-detail.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                for(Furniture f:List.copyOf(ed.project().furniture)) ed.delete(f);
                ed.addFurniture(models.resolve("GARDEN_BUILDINGS").resolve("Palapa.obj").toString(),0,0);
                ed.addFurniture(models.resolve("GARDEN_BUILDINGS").resolve("Filagoria.obj").toString(),6,0);
                ed.addFurniture(models.resolve("GARDEN").resolve("Sorgarnitura_6_szemelyes.obj").toString(),3,5);
                ed.addFurniture(models.resolve("GARDEN_PLANTS").resolve("Szolo_lugas.obj").toString(),10,5);
                ed.changedNow(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertEquals(4,ed.project().furniture.size()); assertNotNull(view.snapshot());
                if(dir!=null) try {
                    saveImage(stage.getScene().snapshot(null),Path.of(dir,"garden-pavilions.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    @Test void startupArtworkStaysVisibleForFiveSecondsThenHandsOffOnce() throws Exception {
        var ready=new java.util.concurrent.CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var splash=new java.util.concurrent.atomic.AtomicReference<Stage>();
        long start=System.nanoTime();
        fx(() -> splash.set(StartupSplash.show(() -> { calls.incrementAndGet(); ready.countDown(); })));
        try {
            Thread.sleep(500);
            fx(() -> {
                assertTrue(splash.get().isShowing());
                assertTrue(splash.get().getScene().getRoot().lookupAll(".label").stream()
                        .anyMatch(n -> n instanceof Label l && l.getText().equals("created by helmetsoft.com")));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try {
                    Files.createDirectories(Path.of(dir));
                    saveImage(splash.get().getScene().snapshot(null),Path.of(dir,"startup.png"));
                } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            assertTrue(System.nanoTime()-start>=4_800_000_000L);
            assertEquals(5,StartupSplash.DURATION.toSeconds());
            assertEquals(1,calls.get());
            fx(() -> assertFalse(splash.get().isShowing()));
        } finally { fx(() -> splash.get().close()); }
    }

    @Test void groupedCatalogContainsNewFurnitureKitchenAndFridgesAndCabinetAction() throws Exception {
        hu.helmetsoft.homedesigner.io.HomeModels.write(temporary.resolve(hu.helmetsoft.homedesigner.io.HomeModels.FOLDER));
        fx(() -> {
            Editor ed=new Editor(new Project()); PlanView plan=new PlanView(ed); CatalogPanel catalog=new CatalogPanel(ed,plan,temporary);
            Stage stage=new Stage(); stage.setScene(new Scene(new HBox(catalog,plan),1000,800)); stage.show();
            try {
                catalog.applyCss(); catalog.layout();
                ComboBox<?> categories=(ComboBox<?>)catalog.lookup(".combo-box");
                ListView<?> list=catalog.getChildren().stream().filter(n -> n instanceof ListView<?>).map(n -> (ListView<?>)n).findFirst().orElseThrow();
                for(var category:hu.helmetsoft.homedesigner.io.HomeModels.Category.values()) {
                    long count=hu.helmetsoft.homedesigner.io.HomeModels.ASSETS.stream().filter(a -> a.category()==category).count();
                    if(count==0) continue;
                    categories.getSelectionModel().select(categories.getItems().indexOf(category.label));
                    assertEquals(count,list.getItems().size(),category.label);
                }
                categories.getSelectionModel().select(categories.getItems().indexOf("Hűtőszekrények"));
                TextField filter=(TextField)catalog.lookup(".text-field"); filter.setText("HŰTŐ"); assertEquals(5,list.getItems().size());
                filter.setText("side"); assertEquals(1,list.getItems().size());
                catalog.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals("+ Egyedi szekrény"))
                        .map(n -> (Button)n).findFirst().orElseThrow().fire();
                assertNotNull(((Furniture)ed.selection()).cabinet);
            } finally { stage.close(); }
        });
    }

    @Test void archedJoineryCanBeEditedAndDisplayedAndUndoKeepsProfile() throws Exception {
        fx(() -> {
            Project p=new Project(); p.env.showGround=true;
            for(int i=0;i<3;i++) {
                Wall wall=new Wall(i*3,0,i*3+2.8,0,0.2,3);
                Opening o=Opening.create(i==0 ? Opening.Type.DOOR : i==1 ? Opening.Type.WINDOW : Opening.Type.PASSAGE,1.4);
                o.width=1.2; o.sill=i==1 ? 0.55 : 0; o.height=i==1 ? 2.2 : 2.6;
                o.top=Opening.Top.values()[i+1]; wall.openings.add(o); p.walls.add(wall);
            }
            Wall curved=new Wall(0,3,3,3,0.2,3); curved.arcAngle=45;
            Opening arch=Opening.create(Opening.Type.WINDOW,curved.length()/2); arch.top=Opening.Top.SEMICIRCLE;
            curved.openings.add(arch); p.walls.add(curved);
            Editor ed=new Editor(p); PlanView plan=new PlanView(ed); View3D view=new View3D(ed); PropertiesPanel props=new PropertiesPanel(ed,plan);
            HBox root=new HBox(plan,view,props); plan.setPrefWidth(400); view.setPrefWidth(700);
            HBox.setHgrow(plan,Priority.ALWAYS); HBox.setHgrow(view,Priority.ALWAYS);
            Stage stage=new Stage(); stage.setScene(new Scene(root,1500,850)); stage.show();
            try {
                ed.select(p.walls.getFirst().openings.getFirst()); props.applyCss(); props.layout();
                ComboBox<?> tops=props.lookupAll(".combo-box").stream()
                        .filter(n -> n instanceof ComboBox<?> c && c.getItems().contains(Opening.Top.QUARTER_LEFT))
                        .map(n -> (ComboBox<?>)n).findFirst().orElseThrow();
                tops.getSelectionModel().select(tops.getItems().indexOf(Opening.Top.QUARTER_LEFT));
                ed.undo(); ed.redo(); assertEquals(Opening.Top.QUARTER_LEFT,((Opening)ed.selection()).top);
                root.applyCss(); root.layout(); plan.fit(); view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot()); assertEquals(ed.snapshot(),ProjectIO.toJson(ProjectIO.fromJson(ed.snapshot())));
                String dir=System.getProperty("hd.testSnapshots");
                if(dir!=null) try { Files.createDirectories(Path.of(dir)); saveImage(stage.getScene().snapshot(null),Path.of(dir,"arched-joinery.png")); }
                catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            } finally { stage.close(); }
        });
    }

    private static void mouse(PlanView plan, javafx.event.EventType<MouseEvent> type, double x, double y, MouseButton button) {
        double sx = plan.sx(x), sy = plan.sy(y);
        plan.fireEvent(new MouseEvent(type,sx,sy,sx,sy,button,1,false,false,false,false,
                type != MouseEvent.MOUSE_RELEASED,false,false,false,false,false,new PickResult(plan,sx,sy)));
    }

    private static void key(PlanView plan, KeyCode code) {
        plan.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,false,false,false));
    }

    private static TextField field(PropertiesPanel props, String label) {
        return findField(props.getContent(),label);
    }

    private static TextField findField(Node node, String label) {
        if (node instanceof GridPane grid) {
            for (Node child : grid.getChildren())
                if (child instanceof Label l && label.equals(l.getText())) {
                    int row = GridPane.getRowIndex(child);
                    for (Node peer : grid.getChildren())
                        if (Integer.valueOf(row).equals(GridPane.getRowIndex(peer)) && peer instanceof HBox h)
                            for (Node n : h.getChildren()) if (n instanceof TextField f) return f;
                }
        }
        if (node instanceof javafx.scene.Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                TextField f = findField(child,label);
                if (f != null) return f;
            }
        return null;
    }

    private static void saveImage(javafx.scene.image.Image img, Path file) throws java.io.IOException {
        int w = (int)img.getWidth(), h = (int)img.getHeight();
        var bi = new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var reader = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x,y,reader.getArgb(x,y));
        javax.imageio.ImageIO.write(bi,"png",file.toFile());
    }
}
