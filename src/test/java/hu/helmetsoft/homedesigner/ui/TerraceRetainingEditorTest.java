package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.geom.FloorLevels;
import hu.helmetsoft.homedesigner.geom.RetainingWallGeometry;
import hu.helmetsoft.homedesigner.geom.SiteGeometry;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.I18n;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TerraceRetainingEditorTest {
    @BeforeAll static void setUp() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS));
    }

    private static void fx(Runnable r) throws Exception {
        FutureTask<Void> task = new FutureTask<>(r, null);
        Platform.runLater(task);
        task.get(120, TimeUnit.SECONDS);
    }

    private static Project slope() {
        Project p = new Project();
        p.env.terrainSlope = 12; p.env.terrainAzimuth = 90;
        return p;
    }

    @Test void terraceToolCreatesWalkableTerraceAboveTheSlopeWithHistoryCopyDeleteAndPersistence() throws Exception {
        fx(() -> {
            Project p = slope();
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            new PropertiesPanel(ed, plan);
            assertNull(ed.addTerrace(0, 0, 0.1, 3), "Too small terraces are rejected");
            Balcony t = ed.addTerrace(1, 1, 5, 4);
            assertNotNull(t);
            assertTrue(t.terrace);
            assertSame(t, ed.selection());
            assertNotEquals(Balcony.Support.NONE, t.support);
            for (double[] q : new double[][]{{1, 1}, {5, 1}, {1, 4}, {5, 4}, {3, 2.5}})
                assertTrue(t.elevation >= SiteGeometry.height(p, q[0], q[1]) + 0.01 - 1e-9, "Slab clears the terrain everywhere");
            assertEquals(t.elevation, FloorLevels.floorAt(ed.project(), 3, 2.5), 1e-12);
            assertEquals(1, ed.project().balconies.size());

            ed.duplicateExtension(t);
            assertEquals(2, ed.project().balconies.size());
            assertTrue(ed.project().balconies.get(1).terrace);
            ed.delete(ed.project().balconies.get(1));
            assertEquals(1, ed.project().balconies.size());
            ed.undo();
            assertEquals(2, ed.project().balconies.size());
            ed.undo(); ed.undo();
            assertTrue(ed.project().balconies.isEmpty());
            ed.redo();
            assertEquals(1, ed.project().balconies.size());
            Project q = ProjectIO.fromJson(ProjectIO.toJson(ed.project()));
            assertTrue(q.balconies.get(0).terrace);
        });
    }

    @Test void retainingWallToolDefaultsAreValidAndOwnedByTheActiveStorey() throws Exception {
        fx(() -> {
            Project p = slope();
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            new PropertiesPanel(ed, plan);
            assertNull(ed.addRetainingWall(0, 0, 0.05, 0));
            RetainingWall w = ed.addRetainingWall(-3, 2, 3, 2);
            assertNotNull(w);
            RetainingWallGeometry.validate(w);
            assertSame(w, ed.selection());
            double base = ed.project().activeLevel().baseHeight;
            assertTrue(RetainingWallGeometry.topWorld(w, base) > RetainingWallGeometry.ground(p.env, -3, 2), "Crest is visible above the terrain");
            assertSame(ed.project().levels.get(0), ed.project().levelOf(w));

            ed.addLevel("Emelet", 3);
            RetainingWall upper = ed.addRetainingWall(-3, 6, 3, 6);
            assertNotNull(upper);
            assertEquals(3, ed.project().activeLevel().baseHeight, 1e-12);
            assertSame(ed.project().activeLevel(), ed.project().levelOf(upper));
            assertEquals(RetainingWallGeometry.topWorld(w, 0), RetainingWallGeometry.topWorld(upper, 3), 0.5, "Default crest follows the terrain, not the storey");
            assertEquals(1, ed.project().retainingWalls.size(), "Active storey list only has its own wall");

            ed.duplicateExtension(upper);
            assertEquals(2, ed.project().retainingWalls.size());
            RetainingWall copy = ed.project().retainingWalls.get(1);
            assertEquals(upper.x1 + 0.3, copy.x1, 1e-12);
            assertNotSame(upper.finish, copy.finish);
            ed.delete(copy);
            assertEquals(1, ed.project().retainingWalls.size());
            ed.undo();
            assertEquals(2, ed.project().retainingWalls.size());

            // selecting a wall on another storey switches to its storey
            RetainingWall groundWall = null;
            for (BuildingLevel l : ed.project().levels) if (l.baseHeight == 0) groundWall = l.retainingWalls.get(0);
            ed.select(groundWall);
            assertEquals(0, ed.project().activeLevel().baseHeight, 1e-12);

            Project q = ProjectIO.fromJson(ProjectIO.toJson(ed.project()));
            int total = 0;
            for (BuildingLevel l : q.levels) total += l.retainingWalls == null ? 0 : l.retainingWalls.size();
            assertEquals(3, total);
        });
    }

    @Test void globalFillToolAndOneClickPerimeterWallsAreOneUndoStep() throws Exception {
        fx(() -> {
            Project p = slope();
            Editor ed = new Editor(p);
            PlanView plan = new PlanView(ed);
            PropertiesPanel panel = new PropertiesPanel(ed, plan);
            assertNull(ed.addSiteFill(0, 0, 0.1, 0.1, false));
            SiteFill f = ed.addSiteFill(-2, -2, 2, 2, false);
            assertNotNull(f);
            assertEquals(1, ed.project().env.siteFills.size());
            assertFalse(f.retaining);
            assertEquals(Math.round((hu.helmetsoft.homedesigner.geom.TerrainGeometry.height(p.env, 0, 0) + 0.51) * 100) / 100.0, f.elevation, 1e-12);
            assertNull(ed.project().levelOf(f), "Fills are global");
            assertSame(f, ed.selection());
            assertTrue(hasButton(panel, I18n.text("Támfal a feltöltés köré")), "Fill properties offer the one-click wall action");

            SiteFill ellipse = ed.addSiteFill(5, 5, 9, 7, true);
            assertEquals(SlabOpening.Shape.ELLIPSE, ellipse.outline.shape);
            ed.duplicateExtension(ellipse);
            assertEquals(3, ed.project().env.siteFills.size());
            ed.delete(ed.project().env.siteFills.get(2));
            ed.delete(ellipse);
            assertEquals(1, ed.project().env.siteFills.size());

            ed.addLevel("Emelet", 3);
            SiteFill current = ed.project().env.siteFills.get(0);
            ed.select(current);
            String level = ed.project().activeLevelId;
            assertEquals(level, ed.project().activeLevelId, "Global fill selection does not switch storeys");
            List<RetainingWall> walls = ed.addRetainingWallsAroundFill(current);
            assertEquals(4, walls.size());
            assertTrue(current.retaining, "Edge becomes vertical in the same transaction");
            assertEquals(4, ed.project().retainingWalls.size());
            for (RetainingWall w : walls) {
                assertSame(ed.project().activeLevel(), ed.project().levelOf(w));
                assertTrue(w.topAbsolute);
                assertEquals(current.elevation, RetainingWallGeometry.topWorld(w, 3), 1e-12);
            }

            ed.undo();
            assertTrue(ed.project().retainingWalls.isEmpty(), "Single undo removes all generated walls");
            assertFalse(ed.project().env.siteFills.get(0).retaining, "…and restores the sloped edge");
            assertEquals(1, ed.project().env.siteFills.size());
            ed.redo();
            assertEquals(4, ed.project().retainingWalls.size());
            assertTrue(ed.project().env.siteFills.get(0).retaining);

            // walls are independent afterwards: moving the fill does not move them
            SiteFill again = ed.project().env.siteFills.get(0);
            double x1 = ed.project().retainingWalls.get(0).x1;
            ed.select(again);
            ed.edit(() -> again.outline.x += 2);
            assertEquals(x1, ed.project().retainingWalls.get(0).x1, 1e-12);
            ed.undo();
            assertTrue(ed.selection() instanceof SiteFill, "Fill selection survives undo on another storey");
            assertSame(ed.project().env.siteFills.get(0), ed.selection());

            SiteFill circle = ed.addSiteFill(10, 10, 14, 14, true);
            circle.outline.shape = SlabOpening.Shape.CIRCLE;
            assertEquals(24, ed.addRetainingWallsAroundFill(circle).size(), "Curved fills get a bounded number of walls");
        });
    }

    private static boolean hasButton(Node node, String text) {
        if (node instanceof Button b && text.equals(b.getText())) return true;
        if (node instanceof javafx.scene.control.ScrollPane s && s.getContent() != null) return hasButton(s.getContent(), text);
        if (node instanceof javafx.scene.control.TitledPane t && t.getContent() != null && hasButton(t.getContent(), text)) return true;
        if (node instanceof Parent parent) for (Node c : parent.getChildrenUnmodifiable()) if (hasButton(c, text)) return true;
        return false;
    }
}