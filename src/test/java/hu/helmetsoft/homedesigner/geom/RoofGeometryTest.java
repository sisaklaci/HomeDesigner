package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class RoofGeometryTest {
    static Roof roof(Roof.Type type, boolean alongX) {
        Roof r = new Roof();
        r.type = type; r.ridgeAlongX = alongX;
        r.x = -2; r.y = 3; r.w = 12; r.d = 10;
        return r;
    }

    static Project project(Roof r) {
        Project p = new Project();
        p.roofs.add(r);
        return p;
    }

    static StructureBuilder.Options options() {
        StructureBuilder.Options o = new StructureBuilder.Options();
        o.ground = false; o.lamps = false;
        return o;
    }

    @Test void subtractionPreservesExactArea() {
        double[][] p = RoofGeometry.rect(0,0,10,8);
        double[] hole = {2,3,5,7};
        List<double[][]> pieces = RoofGeometry.subtract(p,hole);
        assertEquals(68,pieces.stream().mapToDouble(RoofGeometry::area).sum(),1e-9);
        assertEquals(80,RoofGeometry.subtract(p,new double[]{11,0,12,8}).stream().mapToDouble(RoofGeometry::area).sum(),1e-9);
        assertTrue(RoofGeometry.subtract(p,new double[]{-1,-1,11,9}).isEmpty());
        double[][] triangle = {{0,0},{10,0},{0,10}};
        assertEquals(34,RoofGeometry.subtract(triangle,new double[]{1,1,5,5}).stream().mapToDouble(RoofGeometry::area).sum(),1e-9);
    }

    @Test void allRoofTypesAndDirectionsHaveRealCutoutsAndFiniteMeshes() {
        for (Roof.Type type : new Roof.Type[]{Roof.Type.GABLE,Roof.Type.HIP,Roof.Type.SHED,Roof.Type.FLAT}) for (boolean alongX : new boolean[]{true,false})
            for (RoofElement.Type elementType : RoofElement.Type.values()) {
                Roof r = roof(type,alongX);
                RoofElement e = new RoofElement();
                e.type = elementType;
                ProjectOps.placeRoofElement(r,e);
                r.elements.add(e);
                RoofGeometry.validate(r);
                List<GeoPart> parts = StructureBuilder.build(project(r),options());
                assertTrue(parts.stream().anyMatch(p -> p.owner == e && p.mesh.triangleCount() > 0));
                for (GeoPart p : parts) {
                    for (float v : p.mesh.pos) assertTrue(Float.isFinite(v));
                    for (float v : p.mesh.nrm) assertTrue(Float.isFinite(v));
                    for (float v : p.mesh.uv) assertTrue(Float.isFinite(v));
                }
                // Roof top and underside lose exactly the horizontal opening area.
                double area = 0;
                for (GeoPart p : parts) if (p.owner == r) {
                    Mesh m = p.mesh;
                    for (int i = 0; i < m.idx.length; i += 3) {
                        int a = m.idx[i]*3, b = m.idx[i+1]*3, c = m.idx[i+2]*3;
                        if (Math.abs(m.nrm[a+1]) < 0.1) continue; // vertical fascia / gable
                        area += Math.abs((m.pos[b]-m.pos[a])*(m.pos[c+2]-m.pos[a+2])
                                -(m.pos[b+2]-m.pos[a+2])*(m.pos[c]-m.pos[a]))/2;
                    }
                }
                double expected = 2*((r.w+2*r.overhang)*(r.d+2*r.overhang)-e.width*e.depth);
                assertEquals(expected,area,0.0002,type+" / "+alongX+" / "+elementType);
            }
    }

    @Test void openingsCannotOverlapOrCrossRidge() {
        Roof r = roof(Roof.Type.GABLE,true);
        RoofElement e = new RoofElement();
        e.x = 2; e.y = 4.5;
        r.elements.add(e);
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
        e.y = 1;
        RoofElement q = new RoofElement();
        q.x = 2.5; q.y = 1;
        r.elements.add(q);
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
        q.x = 4;
        assertDoesNotThrow(() -> RoofGeometry.validate(r));
        q.width = Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> RoofGeometry.validate(r));
    }

    @Test void multipleOpeningsRemoveSumOfTheirAreas() {
        Roof r = roof(Roof.Type.GABLE,true);
        for (RoofElement.Type type : RoofElement.Type.values()) {
            RoofElement e = new RoofElement(); e.type = type;
            ProjectOps.placeRoofElement(r,e); r.elements.add(e);
        }
        assertDoesNotThrow(() -> RoofGeometry.validate(r));
        assertEquals(3,r.elements.size());
        assertTrue(StructureBuilder.build(project(r),options()).stream().anyMatch(p -> p.owner == r.elements.get(2)));
    }

    @Test void projectRoundTripsAndOldFilesRemainCompatible() {
        Project old = ProjectIO.fromJson("{\"roofs\":[{}]}");
        assertNotNull(old.balconies); assertNotNull(old.roofs.get(0).elements);
        Project explicitNull = ProjectIO.fromJson("{\"balconies\":null,\"roofs\":[{\"elements\":null}]}");
        assertNotNull(explicitNull.balconies); assertNotNull(explicitNull.roofs.get(0).elements);
        Project p = project(roof(Roof.Type.HIP,false));
        for (RoofElement.Type type : RoofElement.Type.values()) {
            RoofElement e = new RoofElement(); e.type = type;
            ProjectOps.placeRoofElement(p.roofs.get(0),e); p.roofs.get(0).elements.add(e);
        }
        Balcony b = new Balcony(); b.angle = 37; b.railing = Balcony.Railing.GLASS; b.backRail = true;
        p.balconies.add(b);
        String json = ProjectIO.toJson(p);
        assertEquals(json,ProjectIO.toJson(ProjectIO.fromJson(json)));
    }

    @Test void balconiesBuildWithRoofHiddenAtEveryRailingStyleAndAngle() {
        for (Balcony.Railing style : Balcony.Railing.values()) for (double angle : new double[]{0,90,37,180,270}) {
            Project p = new Project();
            Balcony b = new Balcony(); b.angle = angle; b.railing = style;
            p.balconies.add(b);
            StructureBuilder.Options o = options(); o.roofs = false;
            List<GeoPart> parts = StructureBuilder.build(p,o);
            assertFalse(parts.isEmpty());
            assertTrue(parts.stream().allMatch(g -> g.owner == b));
            double[] center = b.point(b.width/2,b.depth/2);
            assertTrue(b.contains(center[0],center[1]));
            assertFalse(b.contains(b.point(-1,-1)[0],b.point(-1,-1)[1]));
            double maxY = parts.stream().flatMapToDouble(g -> {
                java.util.stream.DoubleStream.Builder s = java.util.stream.DoubleStream.builder();
                for (int i = 1; i < g.mesh.pos.length; i += 3) s.add(g.mesh.pos[i]);
                return s.build();
            }).max().orElseThrow();
            assertEquals(b.elevation+b.railingHeight,maxY,1e-6);
        }
    }
}
