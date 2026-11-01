package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurvedWallTest {
    private static Wall arc(double angle) {
        Wall w = new Wall(3,0,0,3,0.2,2.7);
        w.arcAngle = angle;
        return w;
    }

    @Test void pointsTangentsProjectionAndDistanceForBothDirectionsAndMajorArcs() {
        for (double angle : new double[]{30,-30,90,-90,180,-180,270,-270,350,-350}) {
            Wall w = arc(angle);
            w.validate();
            assertArrayEquals(new double[]{w.x1,w.y1},w.point(0,0),1e-9);
            assertArrayEquals(new double[]{w.x2,w.y2},w.point(w.length(),0),1e-9);
            double[] center = w.center();
            for (int i = 0; i <= 20; i++) {
                double u = w.length()*i/20;
                double[] p = w.point(u,0), off = w.point(u,0.07);
                assertEquals(w.radius(),Math.hypot(p[0]-center[0],p[1]-center[1]),1e-9);
                assertEquals(u,w.project(p[0],p[1]),1e-8,"angle "+angle+" fraction "+i);
                assertEquals(0.07,w.distance(off[0],off[1]),1e-8);
                assertEquals(1,Math.hypot(w.tangentX(u),w.tangentY(u)),1e-9);
            }
            double[] before = w.point(-0.01,0), after = w.point(w.length()+0.01,0);
            assertTrue(w.project(before[0],before[1]) < 0);
            assertTrue(w.project(after[0],after[1]) > w.length());
        }
    }

    @Test void threePointConstructionPassesThroughAllPointsAndRejectsDegenerateArcs() {
        for (Wall original : List.of(arc(90),arc(-90),arc(270),arc(-270))) {
            double[] mid = original.point(original.length()*0.35,0);
            Wall w = Wall.through(new Pt(original.x1,original.y1),new Pt(mid[0],mid[1]),
                    new Pt(original.x2,original.y2),0.2,2.7);
            assertEquals(original.arcAngle,w.arcAngle,1e-9);
            assertEquals(0,w.distance(mid[0],mid[1]),1e-9);
        }
        assertThrows(IllegalArgumentException.class,() -> Wall.through(new Pt(0,0),new Pt(1,1),new Pt(2,2),0.2,2.7));
        assertThrows(IllegalArgumentException.class,() -> Wall.through(new Pt(0,0),new Pt(1,1),new Pt(0,0),0.2,2.7));
        Wall small = new Wall(0.05,0,0,0.05,0.2,2.7); small.arcAngle = 90;
        assertThrows(IllegalArgumentException.class,small::validate);
        Wall invalid = arc(360);
        assertThrows(IllegalArgumentException.class,invalid::validate);
    }

    @Test void boundsIncludeEveryCardinalExtremumNotJustEndpoints() {
        Wall w = arc(270);
        assertArrayEquals(new double[]{0,0,6,6},w.bounds(),1e-8);
        Project p = new Project(); p.walls.add(w);
        assertArrayEquals(w.bounds(),p.wallBounds(),1e-8);
        for (double angle : new double[]{90,-90,270,-270,350}) {
            w.arcAngle = angle;
            double[] b = w.bounds();
            for (int i = 0; i <= 1000; i++) {
                double[] q = w.point(w.length()*i/1000,0);
                assertTrue(q[0] >= b[0]-1e-8 && q[0] <= b[2]+1e-8 && q[1] >= b[1]-1e-8 && q[1] <= b[3]+1e-8);
            }
        }
    }

    @Test void rotundaHasJoinedWallsCircularFloorAndExactDimensions() {
        Project p = new Project();
        Room r = ProjectOps.addRotunda(p,2,-1,3);
        assertEquals(4,p.walls.size());
        assertEquals(Math.PI*9,r.area(),1e-10);
        assertArrayEquals(new double[]{2,-1},r.centroid(),1e-10);
        assertTrue(r.contains(2,1.9)); assertFalse(r.contains(2,2.1));
        assertEquals(6*Math.PI,p.walls.stream().mapToDouble(Wall::length).sum(),1e-9);
        var corners = WallGeometry.compute(p.walls);
        for (int i = 0; i < 4; i++) {
            Wall a = p.walls.get(i), b = p.walls.get((i+1)%4);
            assertArrayEquals(a.point(a.length(),0),b.point(0,0),1e-9);
            assertArrayEquals(corners.get(a).aE,corners.get(b).aS,1e-9);
            assertArrayEquals(corners.get(a).bE,corners.get(b).bS,1e-9);
            assertFalse(corners.get(a).capStart); assertFalse(corners.get(a).capEnd);
        }
        double polygonArea = Math.abs(ProjectOps.signedArea(r.points));
        assertTrue(Math.abs(polygonArea-r.area())/r.area() < 0.001);
        ProjectOps.validateCurves(p);
    }

    @Test void rotundaResizeMovesFloorWallsOpeningsAndAttachedWallTogether() {
        Project p = new Project();
        Room r = ProjectOps.addRotunda(p,0,0,3);
        Wall first = p.walls.get(0);
        Opening o = ProjectOps.addOpening(first,Opening.Type.WINDOW,first.length()/2);
        double pos = o.pos, width = o.width;
        Wall attached = new Wall(3,0,6,0,0.15,2.7); p.walls.add(attached);
        ProjectOps.updateRotunda(p,r,1,2,4);
        ProjectOps.validateCurves(p);
        assertEquals(5,attached.x1,1e-9); assertEquals(2,attached.y1,1e-9);
        assertEquals(6,attached.x2,1e-9);
        assertEquals(width,o.width,1e-9); assertEquals(pos*4/3,o.pos,1e-9);
        assertEquals(16*Math.PI,r.area(),1e-9);
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.updateRotunda(p,r,0,0,0.3));
        assertEquals(4,r.circleRadius,1e-9);
    }

    @Test void splittingPreservesCircleAndOpeningWorldPositionAndDetachesGroup() {
        Project p = new Project(); Room r = ProjectOps.addRotunda(p,0,0,3);
        Wall w = p.walls.get(0);
        double len = w.length(), radius = w.radius();
        Opening o = ProjectOps.addOpening(w,Opening.Type.WINDOW,len*0.7);
        double[] location = w.point(o.pos,0);
        ProjectOps.splitWall(p,w,len*0.3);
        Wall b = p.walls.get(1);
        assertEquals(5,p.walls.size());
        assertEquals(radius,w.radius(),1e-9); assertEquals(radius,b.radius(),1e-9);
        assertEquals(90,w.arcAngle+b.arcAngle,1e-9);
        assertEquals(len,w.length()+b.length(),1e-9);
        assertTrue(b.openings.contains(o)); assertFalse(w.openings.contains(o));
        assertArrayEquals(location,b.point(o.pos,0),1e-9);
        assertNull(r.rotundaId); assertTrue(p.walls.stream().allMatch(a -> a.rotundaId == null));
        String before = ProjectIO.toJson(p);
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.splitWall(p,b,o.pos));
        assertEquals(before,ProjectIO.toJson(p));
    }

    @Test void curvedMeshesHaveFiniteUnitNormalsAccurateSmoothSkinsAndMetricUvs() {
        for (double angle : new double[]{90,-90,270,-270}) {
            Project p = new Project(); p.env.showGround = false;
            Wall w = arc(angle); p.walls.add(w);
            var options = new StructureBuilder.Options(); options.lamps = false;
            var parts = StructureBuilder.build(p,options);
            assertFalse(parts.isEmpty());
            for (GeoPart part : parts) {
                Mesh m = part.mesh;
                for (float v : m.pos) assertTrue(Float.isFinite(v));
                for (float v : m.nrm) assertTrue(Float.isFinite(v));
                for (float v : m.uv) assertTrue(Float.isFinite(v));
                for (int i = 0; i < m.nrm.length; i += 3) {
                    double n = Math.sqrt(m.nrm[i]*m.nrm[i]+m.nrm[i+1]*m.nrm[i+1]+m.nrm[i+2]*m.nrm[i+2]);
                    assertEquals(1,n,1e-6);
                }
            }
            assertTrue(w.radius()*(1-Math.cos(Math.abs(Math.toRadians(angle))/w.segments()/2)) <= 0.00100001);
            assertTrue(w.segments() >= Math.abs(angle)/2);
        }
    }

    @Test void straightLegacyAndCurvedRotundaProjectsRoundTrip() {
        Project old = ProjectIO.fromJson("{\"walls\":[{\"x1\":0,\"y1\":0,\"x2\":4,\"y2\":0}]}");
        Wall straight = old.walls.get(0);
        assertFalse(straight.curved()); assertEquals(4,straight.length(),1e-9);
        assertArrayEquals(new double[]{2,0},straight.point(2,0),1e-9);
        Project p = new Project();
        Room r = ProjectOps.addRotunda(p,2,1,3);
        ProjectOps.addOpening(p.walls.get(0),Opening.Type.WINDOW,2);
        String json = ProjectIO.toJson(p);
        Project copy = ProjectIO.fromJson(json);
        assertEquals(json,ProjectIO.toJson(copy));
        assertEquals(r.rotundaId,copy.rooms.get(0).rotundaId);
        ProjectOps.updateRotunda(copy,copy.rooms.get(0),0,0,4);
        ProjectOps.validateCurves(copy);
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson("{\"walls\":[{\"x1\":0,\"y1\":0,\"x2\":4,\"y2\":0,\"arcAngle\":360}]}"));
    }

    @Test void straighteningCannotSilentlyClipAnOpeningAndInvalidNumbersAreRejected() {
        Wall w = arc(270);
        ProjectOps.addOpening(w,Opening.Type.WINDOW,w.length()-1);
        double before = w.arcAngle;
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.setArcAngle(w,0));
        assertEquals(before,w.arcAngle,1e-9);
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.setArcAngle(w,Double.NaN));
        Project p = new Project(); p.walls.add(w);
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.fromJson(ProjectIO.toJson(p).replace("\"arcAngle\":270.0","\"arcAngle\":NaN")));
        assertThrows(IllegalArgumentException.class,() -> ProjectOps.addRotunda(p,Double.NaN,0,3));
        w.openings.clear();
        ProjectOps.setArcAngle(w,0);
        assertFalse(w.curved()); assertEquals(w.chord(),w.length(),1e-9);
    }
}
