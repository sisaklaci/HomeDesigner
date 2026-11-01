package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.io.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StairGeometryTest {
    public static Furniture stair(Stair.Type type) {
        Furniture f = new Furniture();
        f.stair = new Stair(); f.stair.type = type; f.height = 2.8; f.width = type == Stair.Type.SPIRAL ? 2 : 1.1;
        f.depth = type == Stair.Type.SPIRAL ? 2 : 4;
        return f;
    }

    @Test void bothStairTypesProduceFiniteMetricMeshesAndCacheOnlyUntilShapeChanges() {
        for (Stair.Type type : Stair.Type.values()) {
            Furniture f = stair(type);
            ModelData model = ModelLibrary.placed(f);
            assertTrue(model.triangleCount() > 100);
            assertSame(model,ModelLibrary.placed(f));
            assertNotNull(ModelLibrary.placedTop(f));
            assertEquals(f.width,model.maxX-model.minX,1e-6);
            assertEquals(StairGeometry.totalHeight(f),model.maxY,1e-6);
            for (ModelData.Part p : model.parts) {
                for (float v : p.mesh.pos) assertTrue(Float.isFinite(v));
                for (float v : p.mesh.uv) assertTrue(Float.isFinite(v));
                for (int i = 0; i < p.mesh.nrm.length; i += 3)
                    assertEquals(1,Math.sqrt(Math.pow(p.mesh.nrm[i],2)+Math.pow(p.mesh.nrm[i+1],2)+Math.pow(p.mesh.nrm[i+2],2)),1e-6);
            }
            f.x = 3; f.y = 5; f.angle = 90;
            assertSame(model,ModelLibrary.placed(f));
            f.stair.steps = 18;
            ModelData changed = ModelLibrary.placed(f);
            assertNotSame(model,changed);
            f.stair.treads.color = "#DDAA66";
            assertNotSame(changed,ModelLibrary.placed(f));
            f.stair.railing = false;
            assertEquals(f.height,ModelLibrary.placed(f).maxY,1e-6);
        }
    }

    @Test void staircaseTransformPreservesFloorToFloorRiseAndRailingHeightWhenRotatedAndElevated() {
        Furniture f = stair(Stair.Type.SPIRAL);
        f.elevation = 1.2; f.x = 3; f.y = -4; f.angle = 90;
        ModelData model = ModelLibrary.placed(f);
        double[] m = FurnitureTransform.matrix(model,f);
        assertEquals(f.elevation,m[7],1e-6);
        assertEquals(f.elevation+StairGeometry.totalHeight(f),m[5]*model.maxY+m[7],1e-6);
        assertEquals(f.x,m[3],1e-6); assertEquals(f.y,m[11],1e-6);
        assertEquals(-1,m[2],1e-6); assertEquals(1,m[8],1e-6);
    }

    @Test void saveCopyAndLegacyProjectsPreserveStairParametersAndRejectBadData() {
        Project p = new Project();
        Furniture f = stair(Stair.Type.SPIRAL); f.stair.turn = 540; f.stair.steps = 24;
        p.furniture.add(f);
        Project loaded = ProjectIO.fromJson(ProjectIO.toJson(p));
        assertEquals(540,loaded.furniture.getFirst().stair.turn);
        assertEquals(24,loaded.furniture.getFirst().stair.steps);
        Stair copy = f.stair.copy(); copy.treads.color = "#000000"; copy.steps = 30;
        assertEquals(24,f.stair.steps); assertNull(f.stair.treads.color);
        assertNotNull(ProjectIO.fromJson("{\"furniture\":[{\"name\":\"Régi bútor\"}]}"));
        f.stair.steps = 0;
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson(ProjectIO.toJson(p)));
    }

    @Test void invalidAndInconsistentConstructionSettingsAreExplicitlyRejected() {
        Furniture f = stair(Stair.Type.SPIRAL);
        f.depth = 3; assertThrows(IllegalArgumentException.class,() -> StairGeometry.validate(f)); f.depth = 2;
        f.stair.innerRadius = 0.9; assertThrows(IllegalArgumentException.class,() -> StairGeometry.validate(f)); f.stair.innerRadius = 0.12;
        f.stair.steps = 3; assertThrows(IllegalArgumentException.class,() -> StairGeometry.validate(f)); f.stair.steps = 16;
        f.stair.treadThickness = 0.3; assertThrows(IllegalArgumentException.class,() -> StairGeometry.validate(f)); f.stair.treadThickness = 0.05;
        f.height = Double.NaN; assertThrows(IllegalArgumentException.class,() -> StairGeometry.validate(f));
    }
}
