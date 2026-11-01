package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StairRenderTest {
    private static Furniture stair(Stair.Type type) {
        Furniture f = new Furniture(); f.stair = new Stair(); f.stair.type = type;
        f.width = type == Stair.Type.SPIRAL ? 2 : 1.1; f.depth = type == Stair.Type.SPIRAL ? 2 : 4; f.height = 2.8;
        return f;
    }

    @Test void everyStraightTreadAndBothSpiralDirectionsRenderAtTheExactSpecifiedWalkingLevel() {
        for (Stair.Type type : Stair.Type.values()) for (boolean clockwise : new boolean[]{true,false}) {
            Project p = new Project(); p.env.showGround = false;
            Furniture f = stair(type); f.elevation = 0.4; f.x = 2; f.y = -1; f.angle = 37;
            f.stair.clockwise = clockwise; p.furniture.add(f);
            RenderScene scene = RenderScene.build(p,false,false);
            assertTrue(scene.triangleCount() > 100);
            for (int i = 0; i < f.stair.steps; i++) {
                double x, z;
                if (type == Stair.Type.STRAIGHT) { x = 0; z = -f.depth/2+(i+0.5)*f.depth/f.stair.steps; }
                else {
                    double a = -Math.PI/2+(clockwise ? 1 : -1)*Math.toRadians(f.stair.turn)*(i+0.5)/f.stair.steps;
                    x = 0.6*Math.cos(a); z = 0.6*Math.sin(a);
                }
                double a = Math.toRadians(f.angle), wx = f.x+x*Math.cos(a)-z*Math.sin(a), wz = f.y+x*Math.sin(a)+z*Math.cos(a);
                RenderScene.Hit hit = new RenderScene.Hit();
                assertTrue(scene.intersect((float)wx,20,(float)wz,0,-1,0,0.001f,30,hit));
                assertEquals(f.elevation+(i+1)*f.height/f.stair.steps,20-hit.t,0.0001,type+" step "+i);
            }
        }
    }

    @Test void openStairDoesNotBecomeASolidBoundingBoxInThePhotoRender() {
        Project p = new Project(); p.env.showGround = false;
        Furniture f = stair(Stair.Type.STRAIGHT); f.stair.railing = false; p.furniture.add(f);
        RenderScene scene = RenderScene.build(p,false,false);
        RenderScene.Hit hit = new RenderScene.Hit();
        assertFalse(scene.intersect(0,0.04f,-4,0,0,1,0.001f,10,hit));
        assertTrue(scene.intersect(0,0.15f,-4,0,0,1,0.001f,10,hit));
    }
}
