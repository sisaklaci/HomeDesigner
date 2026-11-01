package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingLevelsSceneTest {
    @Test void structuralMeshesRenderAllStoreysAtTheirOwnHeightsWithoutCrossStoreyWallJunctions() {
        Project p=new Project(); p.env.showGround=false;
        Room ground=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Ground");
        p.ensureLevels(); String groundId=p.activeLevelId;
        BuildingLevel cellar=new BuildingLevel(); cellar.name="Cellar"; cellar.baseHeight=-3;
        p.levels.add(cellar); p.bindLevel(cellar.id);
        Room basement=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Cellar");
        basement.hasCeiling=true; basement.ceilingHeight=2;
        BuildingLevel upper=new BuildingLevel(); upper.name="Upper"; upper.baseHeight=3;
        p.levels.add(upper); p.bindLevel(upper.id);
        Room upstairs=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Upper");
        upstairs.hasCeiling=true;
        var options=new StructureBuilder.Options(); options.ceilings=true; options.ground=false;
        var parts=StructureBuilder.build(p,options);
        for(Room room:new Room[]{ground,basement,upstairs}) {
            double expected=p.baseOf(room)+.002;
            assertTrue(parts.stream().filter(part -> part.owner==room).anyMatch(part -> {
                for(int i=1;i<part.mesh.pos.length;i+=3) if(Math.abs(part.mesh.pos[i]-expected)<1e-5) return true;
                return false;
            }));
        }
        for(var level:p.levels) for(Wall wall:level.walls)
            for(var part:parts) if(part.owner==wall)
                for(int i=1;i<part.mesh.pos.length;i+=3)
                    assertTrue(part.mesh.pos[i]>=level.baseHeight-1e-5 && part.mesh.pos[i]<=level.baseHeight+wall.height+1e-5);
        RenderScene scene=RenderScene.build(p,false,false);
        assertFloor(scene,1.5f,0.002);
        assertFloor(scene,4.5f,3.002);
        assertFloor(scene,-1.5f,-2.998);
        p.bindLevel(groundId);
        RenderScene fromGround=RenderScene.build(p,false,false);
        assertFloor(fromGround,4.5f,3.002);
    }

    private static void assertFloor(RenderScene scene,float eye,double expected) {
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(2,eye,2,0,-1,0,.001f,10,hit));
        assertEquals(expected,eye-hit.t,1e-5);
    }
}
