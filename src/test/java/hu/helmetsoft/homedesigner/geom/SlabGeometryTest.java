package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import java.awt.geom.Area;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlabGeometryTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    private static Room room() {
        Room r = new Room(); r.points.addAll(ProjectOps.rect(0,0,10,8)); return r;
    }
    private static SlabOpening opening(SlabOpening.Shape shape, double x, double y, double w, double d) {
        SlabOpening o = new SlabOpening(); o.shape = shape; o.x = x; o.y = y; o.width = w; o.depth = d;
        return o;
    }
    private static double area(Area area) {
        return SlabGeometry.triangles(area).stream().mapToDouble(RoofGeometry::area).sum();
    }
    private static void checkSamples(Area area) {
        List<double[][]> triangles = SlabGeometry.triangles(area);
        for (double[][] triangle : triangles) {
            double x = (triangle[0][0]+triangle[1][0]+triangle[2][0])/3;
            double y = (triangle[0][1]+triangle[1][1]+triangle[2][1])/3;
            assertTrue(area.contains(x,y),"Triangle bridges a removed surface at "+x+", "+y);
        }
    }

    @Test void rectangleCutsOnlyEnabledSurfaceAndCanBeDisabledIndependently() {
        Room r = room(); SlabOpening o = opening(SlabOpening.Shape.RECTANGLE,4,3,2,3); r.slabOpenings.add(o);
        assertEquals(80,area(SlabGeometry.surface(r,false)),1e-8);
        assertEquals(74,area(SlabGeometry.surface(r,true)),1e-8);
        o.cutCeiling = false; o.cutFloor = true;
        assertEquals(74,area(SlabGeometry.surface(r,false)),1e-8);
        assertEquals(80,area(SlabGeometry.surface(r,true)),1e-8);
        o.cutFloor = false;
        assertEquals(80,area(SlabGeometry.surface(r,false)),1e-8);
    }

    @Test void circlesAndRotatedEllipsesHaveAccurateAreaAndNoBridgingTriangles() {
        for (SlabOpening.Shape shape : List.of(SlabOpening.Shape.CIRCLE,SlabOpening.Shape.ELLIPSE)) {
            Room r = room(); SlabOpening o = opening(shape,5,4,4,shape == SlabOpening.Shape.CIRCLE ? 4 : 2);
            o.angle = 37; r.slabOpenings.add(o);
            Area surface = SlabGeometry.surface(r,true);
            assertEquals(80-Math.PI*o.width*o.depth/4,area(surface),0.005);
            assertFalse(surface.contains(o.x,o.y)); checkSamples(surface);
        }
    }

    @Test void overlappingHolesAreUnionedAndCrossingRoomEdgeIsClipped() {
        Room r = room();
        r.slabOpenings.add(opening(SlabOpening.Shape.RECTANGLE,3,3,4,4));
        r.slabOpenings.add(opening(SlabOpening.Shape.RECTANGLE,5,3,4,4));
        r.slabOpenings.add(opening(SlabOpening.Shape.RECTANGLE,10,7,4,4));
        Area surface = SlabGeometry.surface(r,true);
        assertEquals(80-24-6,area(surface),1e-8); checkSamples(surface);
        r.slabOpenings.add(opening(SlabOpening.Shape.RECTANGLE,40,50,2,2));
        assertEquals(50,area(SlabGeometry.surface(r,true)),1e-8);
    }

    @Test void concaveHoleAndConcaveRoomWorkInEitherWinding() {
        for (boolean reversed : new boolean[]{false,true}) {
            Room r = room();
            List<Pt> points = new ArrayList<>(List.of(new Pt(2,2),new Pt(6,2),new Pt(6,3),new Pt(3,3),new Pt(3,6),new Pt(2,6)));
            if (reversed) { Collections.reverse(points); Collections.reverse(r.points); }
            r.slabOpenings.add(SlabGeometry.polygon(points));
            assertEquals(73,area(SlabGeometry.surface(r,true)),1e-8); checkSamples(SlabGeometry.surface(r,true));
            r.points = new ArrayList<>(List.of(new Pt(0,0),new Pt(10,0),new Pt(10,4),new Pt(7,4),new Pt(7,8),new Pt(0,8)));
            assertEquals(61,area(SlabGeometry.surface(r,true)),1e-8); checkSamples(SlabGeometry.surface(r,true));
        }
    }

    @Test void disjointSurfacesAndNestedIslandsAreTessellatedWithoutFillingHoles() {
        Area area = RoofOutline.area(RoofGeometry.rect(0,0,10,10));
        area.subtract(RoofOutline.area(RoofGeometry.rect(2,2,8,8)));
        area.add(RoofOutline.area(RoofGeometry.rect(4,4,6,6)));
        area.add(RoofOutline.area(RoofGeometry.rect(12,0,14,2)));
        assertEquals(72,area(area),1e-8); checkSamples(area);
        assertTrue(SlabGeometry.triangles(new Area()).isEmpty());
    }

    @Test void rotatedPolygonVertexEditPreservesOtherWorldVerticesAndRotation() {
        SlabOpening o = SlabGeometry.polygon(ProjectOps.rect(1,2,2,3)); o.angle = 38;
        double[][] before = SlabGeometry.boundary(o);
        SlabGeometry.moveVertex(o,0,before[0][0]-0.2,before[0][1]-0.3);
        assertEquals(38,o.angle,1e-9);
        double[][] after = SlabGeometry.boundary(o);
        assertEquals(before[0][0]-0.2,after[0][0],1e-9);
        for (int i = 1; i < before.length; i++) assertArrayEquals(before[i],after[i],1e-9);
    }

    @Test void malformedOpeningsAreRejectedRatherThanFilled() {
        assertThrows(IllegalArgumentException.class,() -> SlabGeometry.polygon(List.of(new Pt(0,0),new Pt(2,2),new Pt(0,2),new Pt(2,0))));
        assertThrows(IllegalArgumentException.class,() -> SlabGeometry.polygon(List.of(new Pt(0,0),new Pt(2,0),new Pt(4,0))));
        SlabOpening o = opening(SlabOpening.Shape.CIRCLE,0,0,2,3);
        assertThrows(IllegalArgumentException.class,() -> SlabGeometry.validate(o));
        o.shape = SlabOpening.Shape.RECTANGLE; o.x = Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> SlabGeometry.validate(o));
    }

    @Test void persistenceAndLegacyRoomsRetainIndependentOpeningSettings() {
        Project p = new Project(); Room r = room(); p.rooms.add(r);
        SlabOpening o = SlabGeometry.polygon(ProjectOps.rect(2,2,3,2)); o.cutFloor = true; o.angle = 29; r.slabOpenings.add(o);
        String json = ProjectIO.toJson(p);
        assertEquals(json,ProjectIO.toJson(ProjectIO.fromJson(json)));
        SlabOpening copy = o.copy(); copy.points.getFirst().x = 0.1;
        assertEquals(0,o.points.getFirst().x,1e-9);
        assertTrue(ProjectIO.fromJson("{\"rooms\":[{\"name\":\"Régi\",\"points\":[]}]}").rooms.getFirst().slabOpenings.isEmpty());
        o.width = -3;
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson(ProjectIO.toJson(p)));
    }

    @Test void movingRotundaMovesOwnedOpeningsWithoutResizingThem() {
        Project p = new Project(); Room r = ProjectOps.addRotunda(p,10,20,3);
        SlabOpening o = opening(SlabOpening.Shape.RECTANGLE,11,20,1,2); r.slabOpenings.add(o);
        ProjectOps.updateRotunda(p,r,12,24,4);
        assertEquals(13,o.x,1e-9); assertEquals(24,o.y,1e-9); assertEquals(1,o.width,1e-9);
        checkSamples(SlabGeometry.surface(r,true));
    }

    @Test void diskProjectSaveLoadRetainsEveryShapeAndBothIndependentSwitches() throws java.io.IOException {
        Project p = new Project(); Room r = room(); p.rooms.add(r);
        for (SlabOpening.Shape shape : SlabOpening.Shape.values()) {
            SlabOpening o = shape == SlabOpening.Shape.POLYGON ? SlabGeometry.polygon(ProjectOps.rect(2,2,2,2))
                    : opening(shape,4,4,2,2);
            o.name = "Nyílás "+shape.label; o.cutFloor = true; o.cutCeiling = shape != SlabOpening.Shape.RECTANGLE; r.slabOpenings.add(o);
        }
        java.nio.file.Path file = temporary.resolve("nyilasok.hdesign");
        ProjectIO.save(p,file);
        assertEquals(ProjectIO.toJson(p),ProjectIO.toJson(ProjectIO.load(file)));
        r.slabOpenings.getFirst().width = -2;
        ProjectIO.save(p,file);
        assertThrows(java.io.IOException.class,() -> ProjectIO.load(file));
    }
}
