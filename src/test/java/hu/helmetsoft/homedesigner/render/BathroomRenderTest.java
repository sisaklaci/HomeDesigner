package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BathroomRenderTest {
    @TempDir Path directory;

    private RenderScene scene(String name) throws Exception {
        Path file = directory.resolve(name+".obj");
        BuiltinModels.writeObj(BuiltinModels.create(name),file);
        ModelData model = ModelLibrary.get(file.toString(),null).get(30,TimeUnit.SECONDS);
        Furniture f = new Furniture(); f.modelPath = file.toString(); ProjectOps.applyModelSize(f,model,-1);
        Project p = new Project(); p.env.showGround = false; p.furniture.add(f);
        return RenderScene.build(p,false,false);
    }

    @Test void exportedBathAndBasinRenderTheirOpenInteriorInsteadOfAnOpaqueTop() throws Exception {
        for (String name : new String[]{"Mosdotal_pultra_50","Kad_szabadonallo_170","Kad_egyenes_170"}) {
            ModelData original = BuiltinModels.create(name);
            double cx = (original.minX+original.maxX)/2, cz = (original.minZ+original.maxZ)/2;
            RenderScene s = scene(name);
            RenderScene.Hit middle = new RenderScene.Hit(), rim = new RenderScene.Hit();
            assertTrue(s.intersect((float)(0.08-cx),3,(float)-cz,0,-1,0,0.001f,5,middle));
            double rimX = name.startsWith("Mosdo") ? 0.235 : 0.34;
            assertTrue(s.intersect((float)(rimX-cx),3,(float)-cz,0,-1,0,0.001f,5,rim));
            assertTrue(3-middle.t < (name.startsWith("Mosdo") ? 0.08 : 0.2),name);
            assertTrue(middle.t-rim.t > 0.05,name);
        }
    }

    @Test void showerGlassRemainsTransmissiveInThePhotoRendererAfterObjImport() throws Exception {
        RenderScene s = scene("Zuhanykabin_90");
        RenderScene.Hit hit = new RenderScene.Hit();
        assertTrue(s.intersect(-0.15f,1.4f,2,0,0,-1,0.001f,5,hit));
        assertTrue(s.mats[s.tm[hit.tri]].glass);
        assertEquals(0.22,s.mats[s.tm[hit.tri]].opacity,1e-6);
    }
}
