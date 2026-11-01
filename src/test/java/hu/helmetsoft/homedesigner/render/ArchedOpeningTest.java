package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchedOpeningTest {
    private static Opening opening(Opening.Top top) {
        Opening o=Opening.create(Opening.Type.PASSAGE,2); o.width=1.2; o.height=2.3; o.top=top; return o;
    }
    private static boolean across(RenderScene scene,Wall w,double u,double h) {
        double[] p=w.point(u,0);
        return scene.intersect((float)(p[0]+w.tangentY(u)),(float)h,(float)(p[1]-w.tangentX(u)),
                (float)-w.tangentY(u),0,(float)w.tangentX(u),0.001f,2,new RenderScene.Hit());
    }
    @Test void semicircleAndQuarterCircleProfilesHaveExactEndpointsAndRise() {
        Opening o=opening(Opening.Top.SEMICIRCLE);
        assertEquals(1.7,OpeningGeometry.top(o,0),1e-9); assertEquals(2.3,OpeningGeometry.top(o,0.5),1e-9);
        o.top=Opening.Top.QUARTER_LEFT;
        assertEquals(2.3,OpeningGeometry.top(o,0),1e-9); assertEquals(1.1,OpeningGeometry.top(o,1),1e-9);
        o.top=Opening.Top.QUARTER_RIGHT;
        assertEquals(1.1,OpeningGeometry.top(o,0),1e-9); assertEquals(2.3,OpeningGeometry.top(o,1),1e-9);
        OpeningGeometry.validate(o);
        o.height=1.25; assertThrows(IllegalArgumentException.class,() -> OpeningGeometry.validate(o));
    }
    @Test void photographicRaysSeeActualArchedHolesAndTheSolidSpandrelsOnStraightAndCurvedWalls() {
        for(double arc:new double[]{0,60,-60}) for(Opening.Top top:Opening.Top.values()) {
            Project p=new Project(); p.env.showGround=false;
            Wall w=new Wall(0,0,4,0,0.2,3); w.arcAngle=arc;
            Opening o=opening(top); o.pos=w.length()/2; w.openings.add(o); p.walls.add(w);
            RenderScene scene=RenderScene.build(p,false,false);
            assertFalse(across(scene,w,o.pos,0.8),top+" arc "+arc);
            for(double fraction:new double[]{0.12,0.32,0.51,0.73,0.88}) {
                double u=o.pos-o.width/2+fraction*o.width,h=OpeningGeometry.top(o,fraction);
                assertFalse(across(scene,w,u,h-0.035),top+" below profile, arc "+arc+" fraction "+fraction);
                assertTrue(across(scene,w,u,h+0.035),top+" above profile, arc "+arc);
            }
            assertTrue(across(scene,w,0.5,1));
            assertTrue(across(scene,w,o.pos,2.7));
        }
    }
    @Test void archedFrameAndGlazingFollowTheSameShapeAndDoorLeafCanBeFlipped() {
        for(Opening.Type type:Opening.Type.values()) for(Opening.Top top:Opening.Top.values()) {
            Project p=new Project(); p.env.showGround=false; Wall w=new Wall(0,0,4,0,0.2,3.2);
            Opening o=opening(top); o.type=type; o.mirror=true; o.flip=true; w.openings.add(o); p.walls.add(w);
            var parts=StructureBuilder.build(p,new StructureBuilder.Options());
            assertEquals(type!=Opening.Type.PASSAGE,parts.stream().anyMatch(part -> part.owner==o));
            if(type==Opening.Type.WINDOW || type==Opening.Type.BALCONY_DOOR)
                assertTrue(parts.stream().anyMatch(part -> part.owner==o && part.material.glass));
            RenderScene scene=RenderScene.build(p,false,false);
            assertTrue(across(scene,w,o.pos,2.6));
            if(type==Opening.Type.PASSAGE) assertFalse(across(scene,w,o.pos,1));
            for(var part:parts) for(float v:part.mesh.pos) assertTrue(Float.isFinite(v));
        }
    }
    @Test void profileSettingsSurviveSaveAndLegacyOpeningsKeepRectangularShape() {
        Project p=new Project(); Wall w=new Wall(0,0,4,0,0.2,3); p.walls.add(w); w.openings.add(opening(Opening.Top.QUARTER_LEFT));
        assertEquals(ProjectIO.toJson(p),ProjectIO.toJson(ProjectIO.copy(p)));
        String legacy=ProjectIO.toJson(p).replace(",\"top\":\"QUARTER_LEFT\"","").replace("\"top\":\"QUARTER_LEFT\",","");
        assertEquals(Opening.Top.RECTANGLE,ProjectIO.fromJson(legacy).walls.getFirst().openings.getFirst().top);
        w.openings.getFirst().width=3; assertThrows(IllegalArgumentException.class,() -> ProjectOps.validateCurves(p));
    }
    @Test void rectangularFrameHeaderBridgesRemainClosedWhenAnArchSharesTheCurvedWall() {
        for(double arc:new double[]{60,-60}) {
            Project p=new Project(); p.env.showGround=false;
            Wall w=new Wall(0,0,4,0,0.2,3); w.arcAngle=arc; p.walls.add(w);
            Opening rectangle=Opening.create(Opening.Type.WINDOW,1); rectangle.width=1; rectangle.sill=0.8; rectangle.height=1.2;
            w.openings.add(rectangle);
            double offset=-Math.signum(arc)*w.thickness/2;
            double[] a=w.point(0.5,offset),b=w.point(1.5,offset),c=w.point(1,offset);
            float x=(float)((a[0]+b[0]+2*c[0])/4),z=(float)((a[1]+b[1]+2*c[1])/4);
            for(boolean mixed:new boolean[]{false,true}) {
                if(mixed) { Opening arch=opening(Opening.Top.SEMICIRCLE); arch.pos=3; w.openings.add(arch); }
                RenderScene scene=RenderScene.build(p,false,false);
                assertTrue(scene.intersect(x,1.9f,z,0,1,0,0.001f,0.2f,new RenderScene.Hit()),"arc="+arc+" mixed="+mixed);
            }
        }
    }
}
