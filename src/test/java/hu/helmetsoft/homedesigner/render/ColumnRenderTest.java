package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.io.*;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ColumnRenderTest {
    @TempDir Path temporary;

    @Test void horizontalTimberBeamIsClosedAndVisibleFromBelowAtCeilingHeightAfterPlanRotation() throws Exception {
        Path pack=temporary.resolve(HomeModels.FOLDER); HomeModels.write(pack);
        Path file=pack.resolve("BEAMS").resolve("Fagerenda_vizszintes.obj");
        ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
        Project p=new Project(); p.env.showGround=false;
        Furniture beam=new Furniture(); beam.modelPath=file.toString();
        beam.width=6; beam.depth=0.2; beam.height=0.3; beam.elevation=2.4; beam.angle=90;
        beam.override=new Finish("oak_wood",null); p.furniture.add(beam);
        RenderScene scene=RenderScene.build(p,false,false); RenderScene.Hit hit=new RenderScene.Hit();
        for(float z:new float[]{-2.9f,0,2.9f}) {
            assertTrue(scene.intersect(0,1,z,0,1,0,0.001f,4,hit));
            assertEquals(2.4,1+hit.t,1e-5);
            assertSame(MaterialLibrary.resolve(beam.override),scene.mats[scene.tm[hit.tri]]);
            assertTrue(scene.intersect(0,4,z,0,-1,0,0.001f,4,hit));
            assertEquals(2.7,4-hit.t,1e-5);
        }
        assertFalse(scene.intersect(0.2f,1,0,0,1,0,0.001f,4,hit));
    }

    @Test void importedColumnsHaveClosedCapsDistinctProfilesAndChosenMaterialsAtActualWorldHeight() throws Exception {
        Path pack=temporary.resolve(HomeModels.FOLDER);
        HomeModels.write(pack);
        for(String name:new String[]{"Oszlop_negyzetes","Oszlop_kor"}) {
            Path file=pack.resolve("COLUMNS").resolve(name+".obj");
            ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
            for(String material:new String[]{"oak_wood","stone","marble"}) {
                Project p=new Project(); p.env.showGround=false;
                p.ensureLevels(); p.activeLevel().baseHeight=3;
                Furniture column=new Furniture();
                column.modelPath=file.toString();
                column.width=column.depth=0.36; column.height=3; column.elevation=1.2;
                column.override=new Finish(material,null);
                p.furniture.add(column);
                RenderScene scene=RenderScene.build(p,false,false);
                RenderScene.Hit hit=new RenderScene.Hit();
                assertTrue(scene.intersect(0.03f,9,0.02f,0,-1,0,0.001f,10,hit));
                assertEquals(7.2,9-hit.t,1e-5,"Cap must reach terrace datum + column height + storey base");
                assertSame(MaterialLibrary.resolve(column.override),scene.mats[scene.tm[hit.tri]]);
                assertTrue(scene.intersect(0.03f,3,0.02f,0,1,0,0.001f,3,hit));
                assertEquals(4.2,3+hit.t,1e-5,"Bottom must be closed");
                boolean corner=scene.intersect(0.13f,5.5f,0.13f,1,0,0,0.001f,1,hit);
                assertEquals(name.equals("Oszlop_negyzetes"),corner,"Square and circular shafts need different silhouettes");
            }
        }
    }
}
