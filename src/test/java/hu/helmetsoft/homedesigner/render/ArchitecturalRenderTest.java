package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.RoofGeometry;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitecturalRenderTest {
    private static Project project(RoofElement.Type type) {
        Project p = new Project();
        p.env.showGround = false;
        Roof r = new Roof(); r.w = 10; r.d = 8;
        RoofElement e = new RoofElement(); e.type = type;
        e.x = 3; e.y = 5; e.width = 1.8; e.depth = 1.5;
        r.elements.add(e); p.roofs.add(r);
        return p;
    }

    @Test void skylightIsGlassFromBothInsideAndOutsideNotOpaqueRoof() {
        Project p = project(RoofElement.Type.SKYLIGHT);
        Roof r = p.roofs.get(0);
        RoofElement e = r.elements.get(0);
        float x = (float)(e.x+e.width/2), z = (float)(e.y+e.depth/2);
        float h = (float)new RoofGeometry(r).heightAt(x,z);
        RenderScene scene = RenderScene.build(p,false,true);
        for (int direction : new int[]{-1,1}) {
            RenderScene.Hit hit = new RenderScene.Hit();
            assertTrue(scene.intersect(x,h-direction*2,z,0,direction,0,0.001f,10,hit));
            assertTrue(scene.mats[scene.tm[hit.tri]].glass);
        }
        assertEquals(0,RenderScene.build(p,false,false).triangleCount());
    }

    @Test void terraceIsOpenFromAboveDownToItsRecessedFloor() {
        Project p = project(RoofElement.Type.TERRACE);
        Roof r = p.roofs.get(0);
        RoofElement e = r.elements.get(0);
        RoofGeometry g = new RoofGeometry(r);
        double expectedFloor = Math.min(g.heightAt(e.x,e.y),g.heightAt(e.x+e.width,e.y+e.depth))-e.floorDrop;
        RenderScene scene = RenderScene.build(p,false,true);
        RenderScene.Hit hit = new RenderScene.Hit();
        assertTrue(scene.intersect((float)(e.x+e.width/2),20,(float)(e.y+e.depth/2),0,-1,0,0.001f,30,hit));
        assertEquals(expectedFloor,20-hit.t,1e-5);
        assertFalse(scene.mats[scene.tm[hit.tri]].glass);
    }

    @Test void dormerHasAnUnobstructedGlazedFrontForEveryCover() {
        for (RoofElement.Cover cover : RoofElement.Cover.values()) {
            Project p = project(RoofElement.Type.DORMER);
            Roof r = p.roofs.get(0); RoofElement e = r.elements.get(0); e.cover = cover;
            RoofGeometry g = new RoofGeometry(r);
            double front = g.heightAt(e.x,e.y+e.depth);
            double back = g.heightAt(e.x,e.y);
            float eyeY = (float)((front+g.thickness+0.12+Math.max(front,back)+g.thickness+e.height-0.12)/2);
            RenderScene scene = RenderScene.build(p,false,true);
            RenderScene.Hit hit = new RenderScene.Hit();
            assertTrue(scene.intersect((float)(e.x+e.width/2),eyeY,(float)(e.y+e.depth+3),0,0,-1,0.001f,10,hit));
            assertTrue(scene.mats[scene.tm[hit.tri]].glass,cover.toString());
        }
    }

    @Test void facadeBalconyRemainsInPhotoSceneWithRoofDisabled() {
        Project p = new Project(); p.env.showGround = false;
        Balcony b = new Balcony(); b.elevation = 3.2; p.balconies.add(b);
        RenderScene s = RenderScene.build(p,false,false);
        RenderScene.Hit hit = new RenderScene.Hit();
        assertTrue(s.intersect(1,10,0.7f,0,-1,0,0.001f,20,hit));
        assertEquals(b.elevation,10-hit.t,1e-5);
    }
}
