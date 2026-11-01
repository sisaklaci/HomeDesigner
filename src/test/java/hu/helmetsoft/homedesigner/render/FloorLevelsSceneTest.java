package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloorLevelsSceneTest {
    private static float height(RenderScene scene,float x,float z) {
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(x,5,z,0,-1,0,0.001f,20,hit));
        return 5-hit.t;
    }
    @Test void groundNeverCoversRecessesAndBothRenderersShareTrueFloorsRisersAndAbsoluteCeilings() {
        for(double roomLevel:new double[]{0,-0.17,0.17}) for(double offset:new double[]{-0.34,0.34}) {
            Project p=new Project(); p.env.showGround=true;
            Room room=ProjectOps.addRoom(p,ProjectOps.rect(0,0,8,6),"Szoba"); room.elevation=roomLevel;
            FloorZone z=new FloorZone(); z.outline.x=4; z.outline.y=3; z.outline.width=z.outline.depth=3; z.offset=offset;
            room.floorZones.add(z); FloorLevels.validate(p);
            RenderScene scene=RenderScene.build(p,false,false);
            assertEquals(roomLevel+offset+0.002,height(scene,4,3),1e-5);
            assertEquals(roomLevel+0.002,height(scene,1,1),1e-5);
            assertEquals(-0.01,height(scene,10,10),1e-5);
            var options=new StructureBuilder.Options(); options.ceilings=true;
            room.hasCeiling=true; room.ceilingHeight=2.7;
            var parts=StructureBuilder.build(p,options);
            assertTrue(parts.stream().filter(part -> part.owner==room).anyMatch(part -> {
                for(int i=1;i<part.mesh.pos.length;i+=3) if(Math.abs(part.mesh.pos[i]-2.7)<1e-5) return true;
                return false;
            }));
            for(var part:parts) if(part.owner==z) {
                var m=part.mesh; int vertical=0;
                for(int i=0;i<m.idx.length;i+=3) {
                    int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                    double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                    double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                    double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                    assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>0,"Face winding must match visible normals");
                    if(Math.abs(m.nrm[a+1])<0.01) vertical++;
                }
                assertEquals(8,vertical);
            }
            RenderScene.Hit hit=new RenderScene.Hit();
            float y=(float)(roomLevel+offset/2);
            assertTrue(scene.intersect(offset<0 ? 3 : 2,y,3,offset<0 ? -1 : 1,0,0,0.001f,1,hit));
            assertEquals(0.5,hit.t,1e-5,"Recess/platform must have a real vertical side");
        }
    }
}
