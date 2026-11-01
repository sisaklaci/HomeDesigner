package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlabRenderTest {
    private static Project project() {
        Project p = new Project(); p.env.showGround = false;
        Room room = new Room(); room.points.addAll(ProjectOps.rect(0,0,10,8)); room.hasCeiling = true; room.ceilingHeight = 2.8;
        p.rooms.add(room); return p;
    }
    private static boolean ray(RenderScene scene, double x, double y, double z, boolean up) {
        return scene.intersect((float)x,(float)y,(float)z,0,up ? 1 : -1,0,0.001f,1,new RenderScene.Hit());
    }

    @Test void everyShapeMakesRealRayHolesInCeilingButKeepsFloorUntilEnabled() {
        for (SlabOpening.Shape shape : SlabOpening.Shape.values()) {
            Project p = project(); Room r = p.rooms.getFirst();
            SlabOpening o = shape == SlabOpening.Shape.POLYGON ? SlabGeometry.polygon(ProjectOps.rect(3,3,2,2)) : new SlabOpening();
            o.shape = shape; o.x = 4; o.y = 4; o.width = 2; o.depth = 2; o.angle = 37; r.slabOpenings.add(o);
            RenderScene scene = RenderScene.build(p,true,false);
            assertFalse(ray(scene,4,2.4,4,true),shape.toString());
            assertTrue(ray(scene,1,2.4,1,true));
            assertTrue(ray(scene,4,0.4,4,false));
            o.cutFloor = true;
            scene = RenderScene.build(p,true,false);
            assertFalse(ray(scene,4,0.4,4,false));
            assertTrue(ray(scene,1,0.4,1,false));
            o.cutCeiling = false;
            scene = RenderScene.build(p,true,false);
            assertTrue(ray(scene,4,2.4,4,true));
            assertFalse(ray(scene,4,0.4,4,false));
        }
    }

    @Test void densePhotographicRaysAgreeWithAreaIncludingRotatedOverlappingAndConcaveCuts() {
        Project p = project(); Room r = p.rooms.getFirst();
        SlabOpening ellipse = new SlabOpening(); ellipse.shape = SlabOpening.Shape.ELLIPSE;
        ellipse.x = 4; ellipse.y = 4; ellipse.width = 4; ellipse.depth = 2; ellipse.angle = 31; r.slabOpenings.add(ellipse);
        r.slabOpenings.add(SlabGeometry.polygon(List.of(new Pt(4,2),new Pt(8,2),new Pt(8,3),new Pt(5,3),new Pt(5,6),new Pt(4,6))));
        SlabOpening crossing = new SlabOpening(); crossing.x = 0; crossing.y = 6; crossing.width = 3; crossing.depth = 4;
        r.slabOpenings.add(crossing);
        var area = SlabGeometry.surface(r,true);
        RenderScene scene = RenderScene.build(p,true,false);
        for (double x = 0.031; x < 9.99; x += 0.113) for (double z = 0.043; z < 7.99; z += 0.127)
            assertEquals(area.contains(x,z),ray(scene,x,2.4,z,true),"At "+x+", "+z);
    }

    @Test void cutsBelongToOneRoomAndCeilingVisibilityDoesNotDisableFloorHoles() {
        Project p = project(); Room r = p.rooms.getFirst();
        SlabOpening o = new SlabOpening(); o.x = 10; o.y = 4; o.width = 4; o.depth = 2; o.cutFloor = true;
        r.slabOpenings.add(o);
        Room other = new Room(); other.points.addAll(ProjectOps.rect(10,0,8,8)); other.hasCeiling = true; other.ceilingHeight = 2.8; p.rooms.add(other);
        RenderScene scene = RenderScene.build(p,true,false);
        assertFalse(ray(scene,9,2.4,4,true));
        assertTrue(ray(scene,11,2.4,4,true));
        scene = RenderScene.build(p,false,false);
        assertFalse(ray(scene,9,0.4,4,false));
        assertTrue(ray(scene,11,0.4,4,false));
        assertFalse(ray(scene,11,2.4,4,true));
    }

    @Test void realtimeAndPhotoUseTheSameRemovedSurfaceAreaAndNormals() {
        Project p = project(); Room r = p.rooms.getFirst(); SlabOpening o = new SlabOpening();
        o.x = 4; o.y = 4; o.width = 2; o.depth = 3; o.cutFloor = true; r.slabOpenings.add(o);
        StructureBuilder.Options options = new StructureBuilder.Options(); options.ceilings = true; options.ground = false; options.roofs = false;
        double floorArea = 0, ceilingArea = 0;
        for (GeoPart part : StructureBuilder.build(p,options)) if (part.owner == r) {
            Mesh mesh = part.mesh;
            for (int i = 0; i < mesh.idx.length; i += 3) {
                int a = mesh.idx[i]*3, b = mesh.idx[i+1]*3, c = mesh.idx[i+2]*3;
                double area = Math.abs((mesh.pos[b]-mesh.pos[a])*(mesh.pos[c+2]-mesh.pos[a+2])
                        -(mesh.pos[b+2]-mesh.pos[a+2])*(mesh.pos[c]-mesh.pos[a]))/2;
                if (mesh.nrm[a+1] > 0) floorArea += area; else ceilingArea += area;
            }
        }
        assertEquals(74,floorArea,1e-6); assertEquals(74,ceilingArea,1e-6);
    }
}
