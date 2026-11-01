package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingLevelsTest {
    private static BuildingLevel add(Project p,String name,double base) {
        p.ensureLevels(); p.captureActive();
        BuildingLevel l=new BuildingLevel(); l.name=name; l.baseHeight=base;
        p.levels.add(l); p.bindLevel(l.id); return l;
    }

    @Test void overlappingStoreysHaveIndependentEditingAndWorldElevations() {
        Project p=new Project();
        Room ground=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Ground");
        p.ensureLevels(); String groundId=p.activeLevelId;
        BuildingLevel cellar=add(p,"Cellar",-3);
        Room basement=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Cellar");
        basement.elevation=-.2;
        BuildingLevel upper=add(p,"Upper",3.2);
        Room upstairs=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,6,4),"Upper");
        upstairs.elevation=.1; upstairs.ceilingHeight=2.8;
        Furniture chair=new Furniture(); chair.elevation=.1; p.furniture.add(chair);
        p.roofs.add(ProjectOps.fitRoof(p));
        assertEquals(4,p.walls.size()); assertEquals(1,p.rooms.size());
        assertEquals(.1,FloorLevels.floorAt(p,2,2),1e-9);
        Project world=p.worldProject();
        assertEquals(12,world.walls.size()); assertEquals(3,world.rooms.size());
        assertEquals(0,world.rooms.get(0).elevation);
        assertEquals(-3.2,world.rooms.get(1).elevation,1e-9);
        assertEquals(3.3,world.rooms.get(2).elevation,1e-9);
        assertEquals(6,world.rooms.get(2).ceilingHeight,1e-9);
        assertEquals(3.3,world.furniture.getFirst().elevation,1e-9);
        assertEquals(5.9,world.roofs.getFirst().baseHeight,1e-9);
        assertSame(chair,world.sourceOf(world.furniture.getFirst()));
        assertEquals(.1,chair.elevation); assertEquals(.1,upstairs.elevation);
        p.bindLevel(groundId);
        assertSame(ground,p.rooms.getFirst()); assertTrue(p.furniture.isEmpty());
        assertSame(p.env,p.levelViews().get(2).env);
        assertSame(cellar,p.levelOf(basement)); assertSame(upper,p.levelOf(chair));
    }

    @Test void serializationRestoresAliasesAllStoreysActiveSelectionAndLegacyGround() {
        Project p=ProjectIO.fromJson("{\"walls\":[{\"x1\":0,\"y1\":0,\"x2\":4,\"y2\":0}],\"rooms\":[]}");
        assertEquals(1,p.levels.size()); assertEquals(0,p.activeLevel().baseHeight);
        assertSame(p.walls,p.activeLevel().walls);
        String ground=p.activeLevelId;
        BuildingLevel upper=add(p,"Upper",3);
        ProjectOps.addRoomWithWalls(p,ProjectOps.rect(0,0,4,4),"Upstairs");
        String json=ProjectIO.toJson(p);
        Project restored=ProjectIO.fromJson(json);
        assertEquals(json,ProjectIO.toJson(restored));
        assertEquals(upper.id,restored.activeLevelId);
        assertSame(restored.rooms,restored.activeLevel().rooms);
        restored.bindLevel(ground);
        assertEquals(1,restored.walls.size()); assertTrue(restored.rooms.isEmpty());
        restored.walls.getFirst().height=3.4;
        restored=ProjectIO.copy(restored);
        assertEquals(3.4,restored.walls.getFirst().height);
        assertEquals(2,restored.levels.size());
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson(json.replace("\"baseHeight\":3.0","\"baseHeight\":NaN")));
    }

    @Test void walkingDoesNotJumpBetweenOverlappingFloorsAndTraversesStraightStairsBothWays() {
        Project p=new Project();
        ProjectOps.addRoom(p,ProjectOps.rect(-3,-4,6,10),"Ground"); p.ensureLevels();
        Furniture stair=new Furniture(); stair.stair=new Stair(); stair.stair.steps=15;
        stair.width=1; stair.depth=4; stair.height=3; p.furniture.add(stair);
        add(p,"Upper",3);
        Room upper=ProjectOps.addRoom(p,ProjectOps.rect(-3,-4,6,10),"Upper");
        SlabOpening hole=new SlabOpening(); hole.x=0; hole.y=-.05; hole.width=1.2; hole.depth=4.1;
        hole.cutFloor=true; hole.cutCeiling=false; upper.slabOpenings.add(hole);
        assertEquals(0,FloorLevels.walkHeight(p,2,2,0));
        assertEquals(3,FloorLevels.walkHeight(p,2,2,3));
        double h=0;
        for(int i=0;i<15;i++) {
            double z=-2+(i+.5)*4/15;
            h=FloorLevels.walkHeight(p,0,z,h);
            assertEquals((i+1)*.2,h,1e-9);
        }
        h=FloorLevels.walkHeight(p,0,2.1,h); assertEquals(3,h);
        for(int i=14;i>=0;i--) {
            double z=-2+(i+.5)*4/15;
            h=FloorLevels.walkHeight(p,0,z,h);
            assertEquals((i+1)*.2,h,1e-9);
        }
        assertEquals(0,FloorLevels.walkHeight(p,0,-2.2,h));
    }

    @Test void spiralWalkingSupportsRotatedClockwiseAndCounterclockwiseSteps() {
        for(boolean clockwise:new boolean[]{true,false}) {
            Project p=new Project(); ProjectOps.addRoom(p,ProjectOps.rect(-4,-4,8,8),"Ground");
            Furniture stair=new Furniture(); stair.stair=new Stair(); stair.stair.type=Stair.Type.SPIRAL;
            stair.stair.steps=16; stair.stair.turn=360; stair.stair.clockwise=clockwise;
            stair.width=stair.depth=2; stair.height=3.2; stair.angle=35; p.furniture.add(stair);
            double h=0;
            for(int i=0;i<16;i++) {
                double a=-Math.PI/2+(clockwise ? 1:-1)*(i+.5)*2*Math.PI/16;
                double x=.7*Math.cos(a),z=.7*Math.sin(a),rotation=Math.toRadians(stair.angle);
                h=FloorLevels.walkHeight(p,Math.cos(rotation)*x-Math.sin(rotation)*z,
                        Math.sin(rotation)*x+Math.cos(rotation)*z,h);
                assertEquals((i+1)*.2,h,1e-9);
            }
        }

    }

    @Test void maximumValidatedFortyCentimetreRisersWorkBothWaysWithoutRaisingTheNonStairThreshold() {
            for(Stair.Type type:Stair.Type.values()) for(boolean clockwise:new boolean[]{true,false}) {
                Project p=new Project(); ProjectOps.addRoom(p,ProjectOps.rect(-4,-4,8,8),"Ground");
                Furniture stair=new Furniture(); stair.stair=new Stair(); stair.stair.type=type;
                stair.stair.steps=8; stair.stair.turn=300; stair.stair.clockwise=clockwise;
                stair.width=stair.depth=3; stair.height=3.2; stair.angle=35; p.furniture.add(stair);
                StairGeometry.validate(stair);
                add(p,"Upper",3.2);
                Room upper=ProjectOps.addRoom(p,ProjectOps.rect(-4,-4,8,8),"Upper");
                SlabOpening hole=new SlabOpening(); hole.width=hole.depth=3.2;
                hole.cutFloor=true; hole.cutCeiling=false; upper.slabOpenings.add(hole);
                double h=0;
                for(int direction:new int[]{1,-1}) {
                    for(int n=0;n<8;n++) {
                        int i=direction>0 ? n : 7-n;
                        double x=0,z=-stair.depth/2+(i+.5)*stair.depth/8;
                        if(type==Stair.Type.SPIRAL) {
                            double a=-Math.PI/2+(clockwise ? 1:-1)*(i+.5)*Math.toRadians(stair.stair.turn)/8;
                            x=Math.cos(a); z=Math.sin(a);
                        }
                        double rotation=Math.toRadians(stair.angle);
                        h=FloorLevels.walkHeight(p,Math.cos(rotation)*x-Math.sin(rotation)*z,
                                Math.sin(rotation)*x+Math.cos(rotation)*z,h);
                        assertEquals((i+1)*.4,h,1e-9,type+" direction "+direction+" tread "+i);
                    }
                }
                assertEquals(0,FloorLevels.walkHeight(p,3,3,h),1e-9);
                assertEquals(0,FloorLevels.walkHeight(p,3,3,0),1e-9,"Upper floor cannot attract a walker below");
                assertEquals(3.2,FloorLevels.walkHeight(p,3,3,3.2),1e-9);
            }
            Project p=new Project(); Room platform=ProjectOps.addRoom(p,ProjectOps.rect(0,0,2,2),"Platform");
            platform.elevation=.4;
            assertEquals(0,FloorLevels.walkHeight(p,1,1,0),1e-9,"40 cm allowance belongs only to stair treads");
    }
}
