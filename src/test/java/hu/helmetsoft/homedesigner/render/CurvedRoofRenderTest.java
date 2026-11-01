package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.*;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurvedRoofRenderTest {
    private static Project roofOnly(Roof roof) {
        Project p = new Project(); p.env.showGround = false; p.roofs.add(roof);
        return p;
    }

    private static Roof round(Roof.Type type) {
        Project p = new Project();
        Room room = ProjectOps.addRotunda(p,1,2,3);
        Roof roof = RoofFitter.fit(p,room);
        roof.type = type;
        return roof;
    }

    private static RenderScene.Hit vertical(RenderScene s, double x, double z, boolean above, boolean expected) {
        RenderScene.Hit hit = new RenderScene.Hit();
        assertEquals(expected,s.intersect((float)x,above ? 30 : 1,(float)z,0,above ? -1 : 1,0,0.001f,50,hit),
                "x "+x+" z "+z+" above "+above);
        return hit;
    }

    @Test void coneAndDomeMatchAnalyticHeightFromBothSidesAndHaveNoBoundingRectangleCorners() {
        for (Roof.Type type : new Roof.Type[]{Roof.Type.CONE,Roof.Type.DOME}) {
            Roof roof = round(type);
            RoofGeometry geometry = new RoofGeometry(roof);
            RenderScene scene = RenderScene.build(roofOnly(roof),false,true);
            double radius = roof.w/2+roof.overhang, cx = roof.x+roof.w/2, cz = roof.y+roof.d/2;
            for (int angle = 0; angle < 360; angle += 3) for (double fraction : new double[]{0,0.25,0.7,0.97}) {
                double a = Math.toRadians(angle), x = cx+radius*fraction*Math.cos(a), z = cz+radius*fraction*Math.sin(a);
                double h = geometry.heightAt(x,z);
                var top = vertical(scene,x,z,true,true);
                var bottom = vertical(scene,x,z,false,true);
                assertEquals(h+geometry.thickness,30-top.t,0.004,type+" top at "+fraction);
                assertEquals(h,1+bottom.t,0.004,type+" underside at "+fraction);
            }
            vertical(scene,cx+radius*0.9,cz+radius*0.9,true,false);
            vertical(scene,cx-radius*0.9,cz-radius*0.9,true,false);
            assertEquals(0,RenderScene.build(roofOnly(roof),false,false).triangleCount());
        }
    }

    @Test void curvedAndConcavePlanarRoofEdgesHaveNoOpaqueRectangularFill() {
        for (boolean concave : new boolean[]{false,true})
            for (Roof.Type type : new Roof.Type[]{Roof.Type.FLAT,Roof.Type.GABLE,Roof.Type.HIP,Roof.Type.SHED}) {
                Project p = new Project();
                if (concave) ProjectOps.addWalls(p,List.of(new Pt(0,0),new Pt(8,0),new Pt(8,3),new Pt(3,3),new Pt(3,8),new Pt(0,8)),true,"L");
                else {
                    p.walls.add(new Wall(0,0,6,0,0.2,2.7));
                    Wall curve = new Wall(6,0,6,6,0.2,2.7); curve.arcAngle = 180;
                    p.walls.add(curve); p.walls.add(new Wall(6,6,0,6,0.2,2.7)); p.walls.add(new Wall(0,6,0,0,0.2,2.7));
                }
                Roof roof = RoofFitter.fit(p,p.walls.getFirst());
                roof.type = type;
                RenderScene scene = RenderScene.build(roofOnly(roof),false,true);
                vertical(scene,concave ? 6 : 9.4,concave ? 6 : 0,true,false);
                vertical(scene,concave ? 1 : 8.5,concave ? 5 : 3,true,true);
                RoofGeometry geometry = new RoofGeometry(roof);
                for (double[] point : new double[][]{{1,1},{2,2},{1,5}}) {
                    var hit = vertical(scene,point[0],point[1],true,true);
                    assertEquals(geometry.heightAt(point[0],point[1])+geometry.thickness,30-hit.t,0.0001);
                }
            }
    }

    @Test void circularPlanarRoofHasRealSkylightAndTerraceCutouts() {
        for (RoofElement.Type type : new RoofElement.Type[]{RoofElement.Type.SKYLIGHT,RoofElement.Type.TERRACE}) {
            Roof roof = round(Roof.Type.FLAT);
            RoofElement e = new RoofElement(); e.type = type;
            ProjectOps.placeRoofElement(roof,e); roof.elements.add(e);
            RoofGeometry.validate(roof);
            RenderScene scene = RenderScene.build(roofOnly(roof),false,true);
            var hit = vertical(scene,roof.x+e.x+e.width/2,roof.y+e.y+e.depth/2,true,true);
            if (type == RoofElement.Type.SKYLIGHT)
                assertTrue(scene.mats[scene.tm[hit.tri]].glass);
            else assertEquals(roof.baseHeight-e.floorDrop,30-hit.t,1e-5);
        }
    }
}
