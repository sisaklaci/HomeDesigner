package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.CabinetGeometry;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.io.*;
import hu.helmetsoft.homedesigner.render.RenderScene;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CabinetGeometryTest {
    private static Furniture cabinet() {
        Furniture f=new Furniture(); f.cabinet=new Cabinet(); f.width=1.2; f.depth=0.55; f.height=2;
        return f;
    }
    @Test void carcassAndRealShelvesAreParametricRatherThanScaledSolidBoxes() {
        Furniture f=cabinet(); f.cabinet.doors=false;
        Project p=new Project(); p.env.showGround=false; p.furniture.add(f);
        RenderScene scene=RenderScene.build(p,false,false);
        RenderScene.Hit hit=new RenderScene.Hit();
        assertFalse(scene.intersect(-0.3f,0.4f,0.5f,0,0,-1,0.001f,0.5f,hit));
        assertTrue(scene.intersect(-0.3f,0.4f,0.5f,0,0,-1,0.001f,2,hit));
        assertEquals(-f.depth/2+f.cabinet.board/2,0.5-hit.t,1e-5);
        assertTrue(scene.intersect(-0.3f,0.7f,0,0,-1,0,0.001f,1,hit));
        assertEquals(f.cabinet.plinth+f.cabinet.board+(f.height-f.cabinet.plinth-2*f.cabinet.board)/4+f.cabinet.board,0.7-hit.t,1e-5);
        ModelData model=ModelLibrary.placed(f); f.cabinet.shelves=4;
        assertNotSame(model,ModelLibrary.placed(f)); assertNotNull(ModelLibrary.placedTop(f));
    }
    @Test void frontsDoorsAndDrawersRegenerateAndBoundsStayAtCarcassSize() {
        Furniture f=cabinet(); var closed=CabinetGeometry.build(f);
        f.cabinet.openDoors=true; var open=CabinetGeometry.build(f);
        assertEquals(closed.maxZ,open.maxZ,1e-6);
        assertTrue(open.parts.stream().anyMatch(p -> {
            for(int i=2;i<p.mesh.pos.length;i+=3) if(p.mesh.pos[i]>open.maxZ+0.2) return true;
            return false;
        }));
        f.cabinet.drawers=3; var drawers=CabinetGeometry.build(f);
        assertTrue(drawers.triangleCount()>open.triangleCount());
        assertEquals(f.width,drawers.maxX-drawers.minX,1e-6);
    }
    @Test void cabinetPersistsAndDeepCopiesAndRejectsInvalidCombinations() {
        Furniture f=cabinet(); Project p=new Project(); p.furniture.add(f);
        f.cabinet.drawers=2; f.cabinet.fronts.color="#556677";
        assertEquals(ProjectIO.toJson(p),ProjectIO.toJson(ProjectIO.copy(p)));
        Cabinet c=f.cabinet.copy(); c.fronts.color="#FFFFFF"; assertEquals("#556677",f.cabinet.fronts.color);
        f.cabinet.columns=10; assertThrows(IllegalArgumentException.class,() -> CabinetGeometry.validate(f));
        f.cabinet.columns=2; f.width=Double.NaN; assertThrows(IllegalArgumentException.class,() -> CabinetGeometry.validate(f));
    }
}
