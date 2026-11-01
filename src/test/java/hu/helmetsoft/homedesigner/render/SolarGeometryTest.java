package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.SolarGeometry;
import hu.helmetsoft.homedesigner.geom.FurnitureTransform;
import hu.helmetsoft.homedesigner.io.HomeModels;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.model.Project;
import hu.helmetsoft.homedesigner.model.SolarPanel;
import hu.helmetsoft.homedesigner.render.RenderScene;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SolarGeometryTest {
    @Test void bothMountsRetainExactTiltAndGroundedSupportsAtAllSupportedAngles() {
        for(var type:SolarPanel.Type.values()) for(double tilt:new double[]{0,15,30,45,60,85}) {
            Furniture f=new Furniture(); f.solar=new SolarPanel(); f.solar.type=type; f.solar.tilt=tilt;
            f.elevation=2; SolarGeometry.updateSize(f); SolarGeometry.validate(f);
            var model=ModelLibrary.placed(f);
            assertEquals(0,model.minY,1e-6);
            assertEquals(SolarGeometry.height(f.solar),f.height,1e-6);
            assertTrue(model.triangleCount()<5000);
            double[] matrix=FurnitureTransform.matrix(model,f);
            assertEquals(1,matrix[0],1e-6); assertEquals(1,matrix[5],1e-6); assertEquals(1,matrix[10],1e-6);
            var cells=model.parts.stream().filter(p -> p.material.key.equals("Solar_cells")).findFirst().orElseThrow().mesh;
            double angle=Math.toRadians(tilt);
            boolean found=false;
            for(int i=0;i<cells.nrm.length;i+=3) if(cells.nrm[i+1]>0.01 && cells.nrm[i+2]<=0) {
                assertEquals(Math.cos(angle),cells.nrm[i+1],1e-6);
                assertEquals(-Math.sin(angle),cells.nrm[i+2],1e-6);
                found=true;
            }
            assertTrue(found);
            for(var part:model.parts) for(int i=0;i<part.mesh.idx.length;i+=3) {
                var m=part.mesh; int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                assertTrue((uy*vz-uz*vy)*m.nrm[a]+(uz*vx-ux*vz)*m.nrm[a+1]+(ux*vy-uy*vx)*m.nrm[a+2]>-1e-8);
            }
        }
    }

    @Test void editsInvalidateModelAndPlanPreviewAndPersistThroughProjectCopies() {
        Project project=new Project(); Furniture f=new Furniture(); f.solar=new SolarPanel();
        SolarGeometry.updateSize(f); project.furniture.add(f);
        var before=ModelLibrary.placed(f); var top=ModelLibrary.placedTop(f);
        f.solar.tilt=60; SolarGeometry.updateSize(f);
        assertNotSame(before,ModelLibrary.placed(f)); assertNotSame(top,ModelLibrary.placedTop(f));
        f.solar.width=2; f.solar.length=3; SolarGeometry.updateSize(f);
        assertEquals(2,f.width,1e-6);
        Project copy=ProjectIO.copy(project);
        assertEquals(ProjectIO.toJson(project),ProjectIO.toJson(copy));
        assertNotSame(f.solar,copy.furniture.getFirst().solar);
        SolarPanel independent=f.solar.copy(); independent.tilt=10;
        assertEquals(60,f.solar.tilt);
        f.solar.tilt=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> SolarGeometry.validate(f));
        f.solar.tilt=86;
        assertThrows(IllegalArgumentException.class,() -> SolarGeometry.validate(f));
        f.solar.tilt=30; f.solar.length=0.1;
        assertThrows(IllegalArgumentException.class,() -> SolarGeometry.validate(f));
    }

    @Test void catalogIdentifiesOnlyBundledSolarModelsAndPhotoSceneUsesTheGeneratedPanel() {
        Path root=Path.of(HomeModels.FOLDER,"SOLAR");
        assertEquals(SolarPanel.Type.ROOF,SolarGeometry.fromPath(root.resolve("Napelem_tetore.obj").toString()));
        assertEquals(SolarPanel.Type.GROUND,SolarGeometry.fromPath(root.resolve("Napelem_talajra.obj").toString()));
        assertNull(SolarGeometry.fromPath("Napelem_tetore.obj"));
        Project project=new Project(); project.env.showGround=false;
        Furniture f=new Furniture(); f.solar=new SolarPanel(); f.solar.tilt=0;
        SolarGeometry.updateSize(f); project.furniture.add(f);
        RenderScene scene=RenderScene.build(project,false,false);
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(0.1f,5,0.1f,0,-1,0,0.001f,10,hit));
        assertEquals(0.119,5-hit.t,0.005);
    }
}
