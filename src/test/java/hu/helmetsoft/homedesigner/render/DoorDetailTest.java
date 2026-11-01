package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoorDetailTest {
    @Test void onlySelectedRectangleIsGlassAndHandlesFollowBothHingesAndOpeningDirections() {
        for(double arc:new double[]{0,50,-50}) for(Opening.Top top:Opening.Top.values())
            for(boolean mirror:new boolean[]{false,true}) for(boolean flip:new boolean[]{false,true}) {
                Project p=new Project(); p.env.showGround=false;
                Wall w=new Wall(0,0,4,0,0.2,3.4); w.arcAngle=arc; p.walls.add(w);
                Opening o=Opening.create(Opening.Type.DOOR,w.length()/2);
                o.width=1.2; o.height=2.8; o.top=top; o.glazed=true; o.mirror=mirror; o.flip=flip;
                o.glassX=0.3; o.glassWidth=0.3; o.glassY=0.3; o.glassHeight=0.2;
                w.openings.add(o); OpeningGeometry.validate(o);
                var parts=StructureBuilder.build(p,new StructureBuilder.Options());
                assertTrue(parts.stream().anyMatch(part -> part.owner==o && part.material.metal));
                assertTrue(parts.stream().anyMatch(part -> part.owner==o && part.material.glass));
                RenderScene scene=RenderScene.build(p,false,false);
                assertTrue(leafRay(scene,w,o,0.45,0.4),"pane "+arc+" "+top);
                assertFalse(leafRay(scene,w,o,0.45,0.2),"solid panel "+arc+" "+top);
                assertFalse(leafRay(scene,w,o,0.75,0.4),"solid side "+arc+" "+top);
            }
    }

    private static boolean leafRay(RenderScene scene,Wall wall,Opening o,double x,double y) {
        Wall w=wall; Opening op=o;
        if(wall.curved() && o.top==Opening.Top.RECTANGLE) {
            double offset=-Math.signum(wall.arcAngle)*wall.thickness/2;
            double[] a=wall.point(o.pos-o.width/2,offset),b=wall.point(o.pos+o.width/2,offset);
            w=new Wall(a[0],a[1],b[0],b[1],wall.thickness,wall.height);
            op=Opening.create(Opening.Type.DOOR,w.length()/2); op.width=w.length(); op.flip=o.flip; op.mirror=o.mirror;
        }
        double hinge=op.pos+(op.mirror ? 1 : -1)*(op.width/2-0.055),u=op.pos-op.width/2+x*op.width;
        double sign=op.flip ? -1 : 1,distance=op.mirror ? hinge-u : u-hinge;
        double[] a=w.point(hinge,sign*w.thickness/2);
        double px=a[0]+sign*w.nx()*distance,pz=a[1]+sign*w.ny()*distance;
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect((float)(px+w.tangentX(hinge)*0.2),(float)(o.sill+y*o.height),
                (float)(pz+w.tangentY(hinge)*0.2),(float)-w.tangentX(hinge),0,(float)-w.tangentY(hinge),0.001f,0.4f,hit));
        return scene.mats[scene.tm[hit.tri]].glass;
    }

    @Test void glazingPersistsDefaultsToSolidAndRejectsOutsideOrCurvedEdgeIntersections() {
        Project p=new Project(); Wall w=new Wall(0,0,4,0,0.2,3); p.walls.add(w);
        Opening o=Opening.create(Opening.Type.DOOR,2); w.openings.add(o); o.glazed=true;
        assertEquals(ProjectIO.toJson(p),ProjectIO.toJson(ProjectIO.copy(p)));
        o.glassX=0; assertThrows(IllegalArgumentException.class,() -> OpeningGeometry.validate(o));
        o.glassX=0.25; o.glassWidth=Double.NaN; assertThrows(IllegalArgumentException.class,() -> OpeningGeometry.validate(o));
        o.glassWidth=0.5; o.top=Opening.Top.SEMICIRCLE; o.glassY=0.8;
        assertThrows(IllegalArgumentException.class,() -> OpeningGeometry.validate(o));
        o.glazed=false; o.glassY=0.4;
        assertFalse(ProjectIO.copy(p).walls.getFirst().openings.getFirst().glazed);
        assertFalse(StructureBuilder.build(p,new StructureBuilder.Options()).stream().anyMatch(part -> part.owner==o && part.material.glass));
    }
}
