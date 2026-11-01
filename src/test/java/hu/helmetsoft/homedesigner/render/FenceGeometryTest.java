package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.*;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FenceGeometryTest {
    private static Wall fence(Wall.Fence type) {
        Wall wall=new Wall(0,0,4,0,0.12,1.5);
        wall.fence=type; wall.fenceFollowTerrain=false;
        wall.left=new Finish("oak_wood",null);
        return wall;
    }

    @Test void allFenceStylesUseSolidMembersWithOutwardNormalsAndBoundedMeshCostOnCurves() {
        for(Wall.Fence type:Wall.Fence.values()) if(type!=Wall.Fence.NONE) {
            Wall w=fence(type); w.arcAngle=70; w.fenceFollowTerrain=true;
            List<GeoPart> parts=FenceGeometry.build(w,(x,z) -> x*0.2);
            assertEquals(1,parts.size()); assertSame(w,parts.getFirst().owner);
            Mesh m=parts.getFirst().mesh;
            assertTrue(m.idx.length<120000,type.label);
            for(int i=0;i<m.idx.length;i+=3) {
                int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>0,type.label);
            }
            for(float v:m.pos) assertTrue(Float.isFinite(v));
        }
    }

    @Test void openFenceHasRealGapsWhereasMasonryIsSolidAndTerrainFollowingUsesWorldDatum() {
        for(Wall.Fence type:new Wall.Fence[]{Wall.Fence.PICKET,Wall.Fence.SOLID}) {
            Project p=new Project(); p.env.showGround=false;
            Wall w=fence(type); p.walls.add(w);
            RenderScene scene=RenderScene.build(p,false,false); RenderScene.Hit hit=new RenderScene.Hit();
            assertTrue(scene.intersect(2,0.6f,-1,0,0,1,0.001f,2,hit),"Centre post must be solid");
            assertEquals(type==Wall.Fence.SOLID,scene.intersect(0.14f,0.6f,-1,0,0,1,0.001f,2,hit),
                    "Picket gaps must not be opaque wall planes");
        }
        Project p=new Project(); p.env.showGround=false;
        p.env.terrainSlope=10; p.env.terrainAzimuth=90;
        p.ensureLevels(); p.activeLevel().baseHeight=3;
        Wall w=fence(Wall.Fence.SOLID); w.fenceFollowTerrain=true; w.fenceElevation=0.25; p.walls.add(w);
        RenderScene scene=RenderScene.build(p,false,false);
        RenderScene.Hit hit=new RenderScene.Hit();
        for(float x:new float[]{0.4f,1.4f,3.4f}) {
            assertTrue(scene.intersect(x,9,0,0,-1,0,0.001f,10,hit));
            assertEquals(Math.tan(Math.toRadians(10))*x+0.25+1.5,9-hit.t,1e-5);
        }
    }

    @Test void persistenceSplitAndBuildingOperationsKeepFencesIndependent() {
        Project p=new Project(); Wall w=fence(Wall.Fence.HORIZONTAL); p.walls.add(w);
        w.fenceElevation=0.3; w.fencePostSpacing=1.25;
        Project copy=ProjectIO.copy(p); Wall loaded=copy.walls.getFirst();
        assertEquals(w.fence,loaded.fence); assertEquals(0.3,loaded.fenceElevation);
        ProjectOps.splitWall(copy,loaded,1);
        for(Wall half:copy.walls) {
            assertEquals(w.fence,half.fence); assertEquals(0.3,half.fenceElevation);
            assertEquals(1.25,half.fencePostSpacing); assertFalse(half.fenceFollowTerrain);
        }
        assertTrue(RoofFitter.buildings(p).isEmpty());
        assertEquals(p.wallHeight,p.maxWallHeight());
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.addOpening(w,Opening.Type.DOOR,1));
        ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,4,3),"Room");
        assertEquals(5,p.walls.size(),"A fence cannot replace a required room wall");
        assertEquals(1,RoofFitter.buildings(p).size());
        assertFalse(ProjectIO.fromJson("{\"walls\":[{\"x2\":4}]}").walls.getFirst().isFence());
        w.fencePostSpacing=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.copy(p));
    }
}
