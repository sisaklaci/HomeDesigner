package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import java.awt.geom.Area;
import static org.junit.jupiter.api.Assertions.*;

class FloorLevelsTest {
    private static FloorZone zone(double x,double y,double width,double depth,double offset) {
        FloorZone z=new FloorZone();
        z.outline.x=x; z.outline.y=y; z.outline.width=width; z.outline.depth=depth; z.offset=offset;
        return z;
    }
    private static double area(Area area) {
        double sum=0;
        for(var tri:SlabGeometry.triangles(area))
            sum+=Math.abs((tri[1][0]-tri[0][0])*(tri[2][1]-tri[0][1])-(tri[1][1]-tri[0][1])*(tri[2][0]-tri[0][0]))/2;
        return sum;
    }
    @Test void threeByThreeRecessReplacesExactlyNineSquareMetresAndKeepsCeilingAndOpeningsIndependent() {
        Project p=new Project(); Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,8,6),"Szoba");
        r.hasCeiling=true; r.elevation=-0.17;
        FloorZone z=zone(4,3,3,3,-0.34); r.floorZones.add(z);
        FloorLevels.validate(p);
        assertEquals(39,area(FloorLevels.base(r)),1e-9);
        assertEquals(9,area(FloorLevels.surface(r,z)),1e-9);
        assertEquals(48,area(SlabGeometry.surface(r,true)),1e-9);
        SlabOpening hole=new SlabOpening(); hole.x=4; hole.y=3; hole.width=hole.depth=1;
        hole.cutFloor=true; hole.cutCeiling=false; r.slabOpenings.add(hole);
        assertEquals(8,area(FloorLevels.surface(r,z)),1e-9);
        assertEquals(39,area(FloorLevels.base(r)),1e-9);
        assertEquals(48,area(SlabGeometry.surface(r,true)),1e-9);
        z.outline.shape=SlabOpening.Shape.ELLIPSE;
        double ellipse=area(FloorLevels.surface(r,z));
        assertEquals(Math.PI*2.25-1,ellipse,0.015);
    }

    @Test void levelsPersistOldFilesDefaultToZeroAndInvalidRegionsAreRejected() {
        Project old=ProjectIO.fromJson("{\"rooms\":[{\"points\":[{\"x\":0,\"y\":0},{\"x\":8,\"y\":0},{\"x\":8,\"y\":6},{\"x\":0,\"y\":6}]}]}");
        Room r=old.rooms.getFirst();
        assertEquals(0,r.elevation); assertNotNull(r.floorZones); assertTrue(r.floorZones.isEmpty());
        r.elevation=-0.51; FloorZone z=zone(4,3,3,3,-0.34); r.floorZones.add(z);
        assertEquals(ProjectIO.toJson(old),ProjectIO.toJson(ProjectIO.copy(old)));
        assertEquals(-0.85,FloorLevels.floorAt(old,4,3),1e-9);
        assertEquals(-0.51,FloorLevels.floorAt(old,1,1),1e-9);
        assertEquals(0,FloorLevels.floorAt(old,20,20));
        z.outline.x=0;
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.validate(old)); z.outline.x=4;
        r.floorZones.add(z.copy());
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.validate(old)); r.floorZones.removeLast();
        z.offset=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.copy(old)); z.offset=-0.34;
        r.elevation=Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.validate(old)); r.elevation=0;
        z.offset=3; r.hasCeiling=true;
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.validate(old));
    }

    @Test void oneToThreeSolidStepsMeetEachRotatedEdgeWithCorrectRiseAndWalkHeights() {
        for(double offset:new double[]{-0.17,-0.34,-0.51,0.17,0.34,0.51})
            for(double rotation:new double[]{0,35,90,-45}) for(int edge=0;edge<4;edge++) {
                Project p=new Project(); Room r=ProjectOps.addRoom(p,ProjectOps.rect(-5,-5,10,10),"Szoba");
                r.elevation=-0.17;
                FloorZone zone=zone(0,0,3,3,offset); zone.outline.angle=rotation; r.floorZones.add(zone);
                int count=(int)Math.round(Math.abs(offset)/0.17);
                Furniture f=FloorLevels.steps(p,r,zone,edge,count,1,0.28); p.furniture.add(f);
                assertEquals(count,f.stair.steps); assertTrue(f.stair.solid);
                assertEquals(Math.abs(offset),f.height,1e-9);
                assertEquals(r.elevation+Math.min(0,offset),f.elevation,1e-9);
                var model=StairGeometry.build(f); var matrix=FurnitureTransform.matrix(model,f);
                for(int step=0;step<count;step++) {
                    double localZ=-f.depth/2+(step+0.5)*f.depth/count;
                    double x=matrix[2]*localZ+matrix[3],y=matrix[10]*localZ+matrix[11];
                    assertEquals(f.elevation+(step+1)*0.17,FloorLevels.walkHeight(p,x,y),1e-6);
                    if(offset<0) assertTrue(SlabGeometry.contains(zone.outline,x,y));
                    else assertFalse(SlabGeometry.contains(zone.outline,x,y));
                }
                double[][] poly=SlabGeometry.boundary(zone.outline);
                double[] a=poly[edge],b=poly[(edge+1)%4];
                assertEquals((a[0]+b[0])/2,matrix[2]*f.depth/2+matrix[3],1e-6);
                assertEquals((a[1]+b[1])/2,matrix[10]*f.depth/2+matrix[11],1e-6);
                assertEquals(f.elevation+f.height,matrix[5]*f.height+matrix[7],1e-6);
                assertEquals(ProjectIO.toJson(p),ProjectIO.toJson(ProjectIO.copy(p)));
            }
    }

    @Test void wholeRoomStepsAndInvalidStepRequestsAreHandledExplicitly() {
        Project p=new Project(); Room room=ProjectOps.addRoom(p,ProjectOps.rect(0,0,4,4),"Alacsonyabb");
        room.elevation=-0.51;
        Furniture f=FloorLevels.steps(p,room,null,0,3,1.2,0.28);
        assertEquals(-0.51,f.elevation); assertEquals(0,f.elevation+f.height,1e-9);
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.steps(p,room,null,0,3,5,0.28));
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.steps(p,room,null,0,1,1,0.28));
        room.elevation=0;
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.steps(p,room,null,0,1,1,0.28));
        Room adjacent=ProjectOps.addRoom(p,ProjectOps.rect(0,-4,4,4),"Magasabb");
        adjacent.elevation=0.34;
        Furniture connection=FloorLevels.steps(p,room,null,0,2,1,0.28);
        assertEquals(0,connection.elevation);
        assertEquals(0.34,connection.height,1e-9);
        assertEquals(2,connection.stair.steps);
    }

    @Test void zoneStepsRejectFootprintsOutsideTheLowerSurfaceOrAcrossFloorOpenings() {
        Project p=new Project(); Room room=ProjectOps.addRoom(p,ProjectOps.rect(0,0,8,6),"Szoba");
        FloorZone recess=zone(4,3,3,3,-0.34); room.floorZones.add(recess);
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.steps(p,room,recess,0,4,1,1));
        Furniture steps=FloorLevels.steps(p,room,recess,0,2,1,0.28);
        SlabOpening hole=new SlabOpening();
        hole.x=steps.x; hole.y=steps.y; hole.width=hole.depth=0.2; hole.cutFloor=true;
        room.slabOpenings.add(hole);
        assertThrows(IllegalArgumentException.class,() -> FloorLevels.steps(p,room,recess,0,2,1,0.28));
    }
}
