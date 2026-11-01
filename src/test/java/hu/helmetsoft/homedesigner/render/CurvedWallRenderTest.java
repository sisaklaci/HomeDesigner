package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurvedWallRenderTest {
    private static Wall wall(Project p, double angle) {
        p.env.showGround = false;
        Wall w = new Wall(3,0,0,3,0.2,2.7);
        w.arcAngle = angle;
        p.walls.add(w);
        return w;
    }

    private static RenderScene.Hit ray(RenderScene scene, Wall w, double u, double h, int direction, boolean expected) {
        double[] point = w.point(u,0);
        double nx = w.tangentY(u), nz = -w.tangentX(u);
        RenderScene.Hit hit = new RenderScene.Hit();
        boolean found = scene.intersect((float)(point[0]-direction*nx),(float)h,(float)(point[1]-direction*nz),
                (float)(direction*nx),0,(float)(direction*nz),0.001f,2,hit);
        assertEquals(expected,found,"angle "+w.arcAngle+" u "+u+" height "+h+" direction "+direction);
        return hit;
    }

    @Test void windowsAndBalconyDoorsAreGlassFromInsideAndOutsideForBothCurveDirections() {
        for (double angle : new double[]{90,-90,270,-270})
            for (Opening.Type type : new Opening.Type[]{Opening.Type.WINDOW,Opening.Type.BALCONY_DOOR}) {
                Project p = new Project(); Wall w = wall(p,angle);
                Opening o = ProjectOps.addOpening(w,type,w.length()/2);
                o.width = 0.9; // One pane, so the centre ray does not hit a mullion.
                ProjectOps.validateCurves(p);
                RenderScene scene = RenderScene.build(p,false,false);
                for (int direction : new int[]{-1,1}) {
                    RenderScene.Hit hit = ray(scene,w,o.pos,o.sill+o.height*0.55,direction,true);
                    assertTrue(scene.mats[scene.tm[hit.tri]].glass);
                    hit = ray(scene,w,o.pos+o.width/2+0.1,o.sill+o.height/2,direction,true);
                    assertFalse(scene.mats[scene.tm[hit.tri]].glass);
                    assertEquals(0.9,hit.t,0.002);
                }
            }
    }

    @Test void passagesAndOpenDoorsHaveTrueHolesAndWallRemainsAboveAndBesideThem() {
        for (double angle : new double[]{90,-90})
            for (Opening.Type type : new Opening.Type[]{Opening.Type.PASSAGE,Opening.Type.DOOR}) {
                Project p = new Project(); Wall w = wall(p,angle);
                Opening o = ProjectOps.addOpening(w,type,w.length()/2);
                RenderScene scene = RenderScene.build(p,false,false);
                for (int direction : new int[]{-1,1}) {
                    ray(scene,w,o.pos,1,direction,false);
                    ray(scene,w,o.pos,2.6,direction,true);
                    ray(scene,w,o.pos+o.width/2+0.12,1,direction,true);
                }
            }
    }

    @Test void rotundaWallIsContinuousAroundEveryJoinAndFloorAndCeilingRender() {
        Project p = new Project(); p.env.showGround = false;
        Room r = ProjectOps.addRotunda(p,0,0,3);
        RenderScene scene = RenderScene.build(p,true,false);
        for (int i = 0; i < 360; i++) {
            double angle = Math.toRadians(i);
            RenderScene.Hit hit = new RenderScene.Hit();
            assertTrue(scene.intersect(0,1,0,(float)Math.cos(angle),0,(float)Math.sin(angle),0.001f,5,hit));
            int first = scene.nd[0], count = scene.nd[1];
            scene.nd[0] = 0; scene.nd[1] = scene.triangleCount();
            RenderScene.Hit brute = new RenderScene.Hit();
            assertTrue(scene.intersect(0,1,0,(float)Math.cos(angle),0,(float)Math.sin(angle),0.001f,5,brute));
            scene.nd[0] = first; scene.nd[1] = count;
            assertEquals(brute.t,hit.t,1e-5,"BVH must match unaccelerated ray at angle "+i);
            assertEquals(3-p.wallThickness/2,hit.t,0.002,"angle "+i);
            int vertex = hit.tri*9;
            double bary = 1-hit.u-hit.v;
            for (int axis = 0; axis < 3; axis++) {
                double expected = axis == 0 ? hit.t*Math.cos(angle) : axis == 1 ? 1 : hit.t*Math.sin(angle);
                double actual = bary*scene.tv[vertex+axis]+hit.u*scene.tv[vertex+3+axis]+hit.v*scene.tv[vertex+6+axis];
                assertEquals(expected,actual,1e-5);
            }
        }
        RenderScene.Hit hit = new RenderScene.Hit();
        assertTrue(scene.intersect(0,1,0,0,-1,0,0.001f,5,hit)); assertEquals(0.998,hit.t,1e-5);
        assertTrue(scene.intersect(0,1,0,0,1,0,0.001f,5,hit)); assertEquals(p.wallHeight-1,hit.t,1e-5);
        assertEquals(Math.PI*9,r.area(),1e-9);
    }
}
