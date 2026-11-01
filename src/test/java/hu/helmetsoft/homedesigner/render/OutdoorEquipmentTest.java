package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.io.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class OutdoorEquipmentTest {
    @TempDir Path temporary;

    @Test void originalPumpWellsAndCaravanHaveDistinctMetricGeometryAndVisibleParts() throws Exception {
        Path pack=temporary.resolve(HomeModels.FOLDER); HomeModels.write(pack);
        var assets=HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.WATER
                || a.category()==HomeModels.Category.CARAVANS).toList();
        assertEquals(5,assets.size());
        for(var asset:assets) {
            ModelData m=HomeModels.create(asset);
            assertTrue(m.parts.size()>=3,asset.name());
            assertTrue(m.triangleCount()<12000,asset.name());
            assertEquals(0,m.minY,1e-5,asset.name());
            for(var part:m.parts) {
                var mesh=part.mesh;
                for(int i=0;i<mesh.idx.length;i+=3) {
                    int a=mesh.idx[i]*3,b=mesh.idx[i+1]*3,c=mesh.idx[i+2]*3;
                    double ux=mesh.pos[b]-mesh.pos[a],uy=mesh.pos[b+1]-mesh.pos[a+1],uz=mesh.pos[b+2]-mesh.pos[a+2];
                    double vx=mesh.pos[c]-mesh.pos[a],vy=mesh.pos[c+1]-mesh.pos[a+1],vz=mesh.pos[c+2]-mesh.pos[a+2];
                    double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                    assertTrue(nx*mesh.nrm[a]+ny*mesh.nrm[a+1]+nz*mesh.nrm[a+2]>=-1e-7,
                            asset.name()+" material="+part.material.key+" triangle="+i/3);
                }
            }
            if(asset.category()==HomeModels.Category.CARAVANS) {
                assertTrue(m.maxX-m.minX>6.5); assertTrue(m.maxZ-m.minZ>2.3);
                assertTrue(m.maxY>2.6);
            }
            String path=pack.resolve(asset.category().name()).resolve(asset.name()+".obj").toString();
            ModelLibrary.get(path,null).get(30,TimeUnit.SECONDS);
            if(asset.name().equals("Kut_kerekes")) {
                Project p=new Project(); p.env.showGround=false;
                Furniture f=new Furniture(); f.modelPath=path;
                f.width=m.maxX-m.minX; f.height=m.maxY; f.depth=m.maxZ-m.minZ; p.furniture.add(f);
                RenderScene scene=RenderScene.build(p,false,false); RenderScene.Hit hit=new RenderScene.Hit();
                assertTrue(scene.intersect(0.3f,1,0.1f,0,-1,0,0.001f,3,hit));
                assertTrue(1-hit.t<0.2,"The well needs an actual open bore, not a solid cylinder");
                assertTrue(scene.intersect(0.55f,1,0.1f,0,-1,0,0.001f,3,hit));
                assertEquals(0.84,1-hit.t,0.01,"The well rim must be visible");
            }
        }
    }
}
