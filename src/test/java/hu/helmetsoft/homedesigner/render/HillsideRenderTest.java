package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.StructureBuilder;
import hu.helmetsoft.homedesigner.geom.TerrainGeometry;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HillsideRenderTest {
    @Test void rendererAndRealtimeShareSlopingGroundAndExcavatedCellar() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Cellar");
        r.elevation=-2.8; r.hasCeiling=false;
        p.env.terrainSlope=20; p.env.terrainAzimuth=90;
        p.env.foundation=Environment.Foundation.EMBEDDED;
        RenderScene scene=RenderScene.build(p,false,false);
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(3,10,2,0,-1,0,0.001f,30,hit));
        assertEquals(-2.798,10-hit.t,1e-5,"Soil must not bury the cellar");
        assertTrue(scene.intersect(9,10,2,0,-1,0,0.001f,30,hit));
        assertEquals(TerrainGeometry.height(p.env,9,2),10-hit.t,1e-5);
        var parts=StructureBuilder.build(p,new StructureBuilder.Options());
        assertTrue(parts.stream().filter(part -> part.owner==p.env)
                .anyMatch(part -> part.material.key.equals(hu.helmetsoft.homedesigner.material.MaterialLibrary.resolve(p.env.foundationFinish).key)));
    }

    @Test void vaultUndersideIsVisibleFromInsideAndCeilingOpeningIsActuallyOpen() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Vaulted room");
        r.ceilingHeight=2.4; r.vaultRise=0.8; r.vaultRibs=false;
        for(Room.Vault type:Room.Vault.values()) if(type!=Room.Vault.NONE) {
            r.vault=type;
            RenderScene scene=RenderScene.build(p,true,false);
            RenderScene.Hit hit=new RenderScene.Hit();
            assertTrue(scene.intersect(3,1,2,0,1,0,0.001f,10,hit));
            assertEquals(3.2,1+hit.t,0.02,"A flat ceiling must not remain below the vault");
            SlabOpening o=new SlabOpening();
            o.x=3; o.y=2; o.width=o.depth=1; o.cutCeiling=true;
            r.slabOpenings.add(o);
            scene=RenderScene.build(p,true,false);
            assertFalse(scene.intersect(3,1,2,0,1,0,0.001f,10,hit));
            r.slabOpenings.clear();
        }
    }
}
