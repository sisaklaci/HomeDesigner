package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.geom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinModelsTest {
    @TempDir Path directory;

    @Test void allOriginalModelsExportAndImportWithMaterialsNormalsAndNaturalMetricSizes() throws Exception {
        BuiltinModels.write(directory);
        assertEquals(15,BuiltinModels.NAMES.size());
        for (String name : BuiltinModels.NAMES) {
            ModelData original = BuiltinModels.create(name);
            Path obj = directory.resolve(name+".obj");
            assertTrue(Files.size(obj) > 1000);
            ModelData loaded = ModelLoader.load(obj,null);
            assertEquals(original.triangleCount(),loaded.triangleCount(),name);
            assertEquals(original.parts.size(),loaded.parts.size(),name);
            assertEquals(original.maxX-original.minX,loaded.maxX-loaded.minX,1e-5,name);
            assertEquals(original.maxY-original.minY,loaded.maxY-loaded.minY,1e-5,name);
            assertEquals(original.maxZ-original.minZ,loaded.maxZ-loaded.minZ,1e-5,name);
            assertEquals(0,loaded.minY,1e-6);
            assertTrue(loaded.maxY < 2.1 && loaded.maxY > 0.1,name);
            for (int pi = 0; pi < loaded.parts.size(); pi++) {
                var part = loaded.parts.get(pi);
                assertArrayEquals(original.parts.get(pi).material.color,part.material.color,1e-6);
                assertEquals(original.parts.get(pi).material.metal,part.material.metal);
                assertEquals(original.parts.get(pi).material.roughness,part.material.roughness,1e-6);
                assertEquals(original.parts.get(pi).material.albedo != null,part.material.albedo != null);
                for (float v : part.mesh.pos) assertTrue(Float.isFinite(v),name);
                for (int i = 0; i < part.mesh.nrm.length; i += 3)
                    assertEquals(1,Math.sqrt(Math.pow(part.mesh.nrm[i],2)+Math.pow(part.mesh.nrm[i+1],2)+Math.pow(part.mesh.nrm[i+2],2)),1e-5,name);
            }
            assertNotNull(FurnitureTransform.topView(loaded,0,100));
        }
    }

    @Test void showerHasTransparentGlassAndBasinsAndBathsHaveActualInteriorSurfaces() throws Exception {
        BuiltinModels.write(directory);
        ModelData shower = ModelLoader.load(directory.resolve("Zuhanykabin_90.obj"),null);
        assertTrue(shower.parts.stream().anyMatch(p -> p.material.glass && p.material.opacity < 0.5));
        for (String name : new String[]{"Mosdotal_pultra_50","Kad_szabadonallo_170","Kad_egyenes_170"}) {
            ModelData model = BuiltinModels.create(name);
            boolean inner = false, outer = false;
            for (ModelData.Part p : model.parts) for (int i = 0; i < p.mesh.vertexCount(); i++) {
                double x = p.mesh.pos[i*3], z = p.mesh.pos[i*3+2];
                double dot = x*p.mesh.nrm[i*3]+z*p.mesh.nrm[i*3+2];
                inner |= dot < -0.05; outer |= dot > 0.05;
            }
            assertTrue(inner && outer,name+" needs inner and outer shell normals");
        }
    }
}
