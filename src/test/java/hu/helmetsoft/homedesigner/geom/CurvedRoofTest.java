package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.*;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurvedRoofTest {
    public static Project roundedBuilding() {
        Project p = new Project();
        p.env.showGround = false;
        p.walls.add(new Wall(0,0,6,0,0.2,2.7));
        Wall arc = new Wall(6,0,6,6,0.2,2.7); arc.arcAngle = 180; p.walls.add(arc);
        p.walls.add(new Wall(6,6,0,6,0.2,2.7));
        p.walls.add(new Wall(0,6,0,0,0.2,2.7));
        return p;
    }

    private static StructureBuilder.Options options() {
        var o = new StructureBuilder.Options(); o.ground = false; o.lamps = false;
        return o;
    }

    @Test void rotundaFitsTrueCircularConeAndCanSwitchToDome() {
        Project p = new Project();
        Room room = ProjectOps.addRotunda(p,4,7,3);
        Roof roof = RoofFitter.fit(p,room);
        assertTrue(roof.circular);
        assertEquals(Roof.Type.CONE,roof.type);
        assertEquals(6.15,roof.w,0.002); assertEquals(roof.w,roof.d,1e-8);
        assertEquals(4,roof.x+roof.w/2,1e-8); assertEquals(7,roof.y+roof.d/2,1e-8);
        for (Roof.Type type : new Roof.Type[]{Roof.Type.CONE,Roof.Type.DOME}) {
            roof.type = type; p.roofs.clear(); p.roofs.add(roof);
            RoofGeometry.validate(roof);
            var geometry = new RoofGeometry(roof);
            double cx = roof.x+roof.w/2, cy = roof.y+roof.d/2;
            double expectedPeak = roof.baseHeight+(type == Roof.Type.CONE ? roof.w/2*geometry.slope : roof.domeRise);
            assertEquals(expectedPeak,geometry.heightAt(cx,cy),1e-9);
            var parts = StructureBuilder.build(p,options());
            assertTrue(parts.stream().anyMatch(part -> part.owner == roof));
            for (GeoPart part : parts) if (part.owner == roof) {
                for (float v : part.mesh.pos) assertTrue(Float.isFinite(v));
                for (float v : part.mesh.nrm) assertTrue(Float.isFinite(v));
                for (int i = 0; i < part.mesh.pos.length; i += 3) {
                    double radius = Math.hypot(part.mesh.pos[i]-cx,part.mesh.pos[i+2]-cy);
                    assertTrue(radius <= roof.w/2+roof.overhang+1e-5);
                    assertEquals(1,Math.sqrt(Math.pow(part.mesh.nrm[i],2)+Math.pow(part.mesh.nrm[i+1],2)+Math.pow(part.mesh.nrm[i+2],2)),1e-6);
                }
            }
        }
    }

    @Test void circularRoofsFitAtDifferentCoordinatesAndConesRespectExactPitchLimits() {
        for (double[] position : new double[][]{{0,0},{1,2},{-43.121,27.391},{999,-999}}) {
            Project p = new Project();
            Room room = ProjectOps.addRotunda(p,position[0],position[1],3);
            Roof roof = RoofFitter.fit(p,room);
            assertTrue(roof.circular); assertEquals(6.15,roof.w,0.002);
            for (double pitch : new double[]{0,0.5,35,75}) {
                roof.pitch = pitch;
                RoofGeometry.validate(roof);
                RoofGeometry geometry = new RoofGeometry(roof);
                assertEquals(roof.baseHeight+roof.w/2*Math.tan(Math.toRadians(pitch)),
                        geometry.heightAt(position[0],position[1]),1e-8);
            }
            p.roofs.add(roof);
            roof.d = 4;
            assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson(ProjectIO.toJson(p)));
        }
    }

    @Test void roundedBuildingUsesWallOutlineNotBoundingRectangleAndOffsetIsUniform() {
        Project p = roundedBuilding();
        Roof r = RoofFitter.fit(p,p.walls.get(1));
        assertFalse(r.circular); assertFalse(r.footprint.isEmpty());
        assertTrue(RoofOutline.contains(r,8.5,3));
        assertFalse(RoofOutline.contains(r,9.5,0));
        assertFalse(RoofOutline.contains(r,9.5,6));
        double[][] wall = RoofOutline.boundary(r,false), eave = RoofOutline.boundary(r,true);
        double expectedArea = 6.1*6.2+Math.PI*3.1*3.1/2;
        assertEquals(expectedArea,RoofGeometry.area(wall),0.02);
        for (double[] point : eave) assertEquals(r.overhang,RoofOutline.distance(wall,point[0],point[1]),0.002);
        assertTrue(RoofGeometry.area(eave) < (r.w+2*r.overhang)*(r.d+2*r.overhang)-2);
    }

    @Test void everyPlanarFormAndDirectionIsClippedToRoundAndConcaveOutlinesWithoutLosingArea() {
        for (boolean concave : new boolean[]{false,true}) {
            Project p = concave ? concaveBuilding() : roundedBuilding();
            Roof r = RoofFitter.fit(p,p.walls.getFirst());
            for (Roof.Type type : new Roof.Type[]{Roof.Type.GABLE,Roof.Type.HIP,Roof.Type.SHED,Roof.Type.FLAT})
                for (boolean alongX : new boolean[]{false,true}) {
                    r.type = type; r.ridgeAlongX = alongX;
                    p.roofs.clear(); p.roofs.add(r);
                    RoofGeometry geometry = new RoofGeometry(r);
                    double expected = RoofGeometry.area(geometry.outline);
                    assertEquals(expected,geometry.surfaceFaces().stream().mapToDouble(f -> RoofGeometry.area(f.polygon())).sum(),0.0001);
                    double area = projectedRoofArea(StructureBuilder.build(p,options()),r);
                    assertEquals(expected*2,area,0.001,type+" concave "+concave+" direction "+alongX);
                }
        }
    }

    public static Project concaveBuilding() {
        Project p = new Project(); p.env.showGround = false;
        ProjectOps.addWalls(p,List.of(new Pt(0,0),new Pt(8,0),new Pt(8,3),new Pt(3,3),new Pt(3,8),new Pt(0,8)),true,"L alak");
        return p;
    }

    private static double projectedRoofArea(List<GeoPart> parts, Roof roof) {
        double area = 0;
        for (GeoPart part : parts) if (part.owner == roof) {
            Mesh m = part.mesh;
            for (int i = 0; i < m.idx.length; i += 3) {
                int a = 3*m.idx[i], b = 3*m.idx[i+1], c = 3*m.idx[i+2];
                area += Math.abs((m.pos[b]-m.pos[a])*(m.pos[c+2]-m.pos[a+2])-(m.pos[b+2]-m.pos[a+2])*(m.pos[c]-m.pos[a]))/2;
            }
        }
        return area;
    }

    @Test void customFootprintRetainsTrueCutoutsAndRejectsOpeningsOverTheMissingCorners() {
        Project p = roundedBuilding();
        Roof r = RoofFitter.fit(p,p.walls.getFirst()); p.roofs.add(r);
        r.type = Roof.Type.FLAT;
        for (RoofElement.Type type : RoofElement.Type.values()) {
            RoofElement e = new RoofElement(); e.type = type;
            ProjectOps.placeRoofElement(r,e); r.elements.add(e);
            RoofGeometry.validate(r);
        }
        double expected = 2*(RoofGeometry.area(RoofOutline.boundary(r,true))-r.elements.stream().mapToDouble(e -> e.width*e.depth).sum());
        assertEquals(expected,projectedRoofArea(StructureBuilder.build(p,options()),r),0.001);
        RoofElement invalid = new RoofElement(); invalid.x = r.w-1; invalid.y = 0.1; invalid.width = 0.5; invalid.depth = 0.5;
        r.elements.add(invalid);
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
    }

    @Test void separateBuildingsSelectAndRefitIndependentlyIncludingRoomAndOpeningSelection() {
        Project p = roundedBuilding();
        Room round = ProjectOps.addRotunda(p,20,5,2);
        Room rect = ProjectOps.addRoomWithWalls(p,ProjectOps.rect(-10,0,4,4),"Melléképület");
        assertEquals(3,RoofFitter.buildings(p).size());
        assertThrows(IllegalArgumentException.class,() -> RoofFitter.fit(p,null));
        Roof a = RoofFitter.fit(p,p.walls.get(0));
        Roof b = RoofFitter.fit(p,round);
        Roof c = RoofFitter.fit(p,rect);
        assertTrue(a.x > -1 && a.x < 1); assertTrue(b.x > 17); assertTrue(c.x < -9);
        Opening o = ProjectOps.addOpening(p.walls.get(0),Opening.Type.DOOR,3);
        assertEquals(a.w,RoofFitter.fit(p,o).w,1e-8);
        p.roofs.addAll(List.of(a,b,c));
        ProjectOps.updateRotunda(p,round,20,5,3);
        RoofFitter.refit(p,b);
        assertEquals(6.15,b.w,0.002); assertTrue(b.circular);
        assertEquals(c.x,-10.075,0.002);
        assertEquals(a.w,RoofFitter.fit(p,p.walls.get(0)).w,1e-8);
    }

    @Test void overlappingBuildingBoundsDoNotRefitTheWrongRoof() {
        Project p = concaveBuilding();
        Room room = ProjectOps.addRoomWithWalls(p,ProjectOps.rect(4,4,2,2),"Külön épület az L öblében");
        Roof l = RoofFitter.fit(p,p.walls.getFirst()), small = RoofFitter.fit(p,room);
        double x = small.x, y = small.y, width = small.w;
        RoofFitter.refit(p,small);
        assertEquals(x,small.x,1e-9); assertEquals(y,small.y,1e-9); assertEquals(width,small.w,1e-9);
        RoofFitter.refit(p,l);
        assertFalse(l.footprint.isEmpty()); assertEquals(8.15,l.w,0.001);
    }

    @Test void invalidOpenBuildingsAndRadialRoofEditsAreExplicitlyRejected() {
        Project p = new Project();
        p.walls.add(new Wall(0,0,4,0,0.15,2.7));
        assertThrows(IllegalArgumentException.class,() -> RoofFitter.fit(p,null));
        Roof r = new Roof(); r.type = Roof.Type.CONE;
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
        r.circular = true; r.d = r.w;
        assertDoesNotThrow(() -> RoofGeometry.validate(r));
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.placeRoofElement(r,new RoofElement()));
        r.elements.add(new RoofElement());
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
        r.elements.clear(); r.d = 4;
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
    }

    @Test void oldAndNewProjectFilesRetainShapeAndMeshAfterRoundTrip() {
        Project old = ProjectIO.fromJson("{\"roofs\":[{}]}");
        assertFalse(old.roofs.getFirst().circular); assertTrue(old.roofs.getFirst().footprint.isEmpty());
        Project p = roundedBuilding();
        p.roofs.add(RoofFitter.fit(p,p.walls.getFirst()));
        Room r = ProjectOps.addRotunda(p,20,5,3);
        Roof dome = RoofFitter.fit(p,r); dome.type = Roof.Type.DOME; p.roofs.add(dome);
        String json = ProjectIO.toJson(p);
        Project restored = ProjectIO.fromJson(json);
        assertEquals(json,ProjectIO.toJson(restored));
        assertEquals(p.roofs.getFirst().footprint.size(),restored.roofs.getFirst().footprint.size());
        assertEquals(Roof.Type.DOME,restored.roofs.get(1).type);
        assertDoesNotThrow(() -> RoofGeometry.validate(restored));
        assertFalse(StructureBuilder.build(restored,options()).isEmpty());
    }
}
