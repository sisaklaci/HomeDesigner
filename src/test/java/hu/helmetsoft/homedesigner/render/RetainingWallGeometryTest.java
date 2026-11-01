package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetainingWallGeometryTest {
    private static double surface(RenderScene scene, double x, double z) {
        RenderScene.Hit hit = new RenderScene.Hit();
        assertTrue(scene.intersect((float) x, 20, (float) z, 0, -1, 0, 0.0001f, 100, hit));
        return 20 - hit.t;
    }

    private static Project slope() {
        Project p = new Project();
        p.env.terrainSlope = 15; p.env.terrainAzimuth = 90;
        return p;
    }

    private static RetainingWall wall(double x1, double y1, double x2, double y2, double top) {
        RetainingWall w = new RetainingWall();
        w.x1 = x1; w.y1 = y1; w.x2 = x2; w.y2 = y2;
        w.top = top; w.topAbsolute = true; w.followTerrain = true; w.embedment = 0.3;
        return w;
    }

    private static List<GeoPart> owned(List<GeoPart> parts, Object owner) {
        List<GeoPart> out = new ArrayList<>();
        for (GeoPart g : parts) if (g.owner == owner) out.add(g);
        return out;
    }

    private static double[] yRange(List<GeoPart> parts) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (GeoPart g : parts) for (int v = 1; v < g.mesh.pos.length; v += 3) { lo = Math.min(lo, g.mesh.pos[v]); hi = Math.max(hi, g.mesh.pos[v]); }
        return new double[]{lo, hi};
    }

    /** Front faces wind counter-clockwise around their stored normal and, for a convex prism, point away from its centre. */
    private static void assertOutward(List<GeoPart> parts, double cx, double cy, double cz) {
        for (GeoPart part : parts) {
            Mesh m = part.mesh;
            for (float v : m.pos) assertTrue(Float.isFinite(v));
            for (int i = 0; i < m.idx.length; i += 3) {
                int a = m.idx[i] * 3, b = m.idx[i + 1] * 3, c = m.idx[i + 2] * 3;
                double ux = m.pos[b] - m.pos[a], uy = m.pos[b + 1] - m.pos[a + 1], uz = m.pos[b + 2] - m.pos[a + 2];
                double vx = m.pos[c] - m.pos[a], vy = m.pos[c + 1] - m.pos[a + 1], vz = m.pos[c + 2] - m.pos[a + 2];
                double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                assertTrue(nx * m.nrm[a] + ny * m.nrm[a + 1] + nz * m.nrm[a + 2] > -1e-7, "Visible face winding");
                double gx = (m.pos[a] + m.pos[b] + m.pos[c]) / 3, gy = (m.pos[a + 1] + m.pos[b + 1] + m.pos[c + 1]) / 3, gz = (m.pos[a + 2] + m.pos[b + 2] + m.pos[c + 2]) / 3;
                if (Math.abs(m.nrm[a + 1]) > 0.5) continue; // top/bottom checked separately (bottom follows terrain)
                double d = (gx - cx) * m.nrm[a] + (gz - cz) * m.nrm[a + 2];
                assertTrue(d > -1e-6, "Side normal must point outward");
            }
        }
    }

    @Test void slopedTerrainWallHasFlatTopTerrainFollowingBottomAndOutwardNormals() {
        Project p = slope();
        for (RetainingWall w : new RetainingWall[]{wall(-3, 0, 3, 0, 2.0), wall(0, -3, 0, 3, 2.0)}) {
            w.footing = false;
            RetainingWallGeometry.validate(w);
            List<GeoPart> parts = RetainingWallGeometry.build(w, p.env, 0);
            assertEquals(1, parts.size());
            assertSame(w, parts.get(0).owner);
            double[] r = yRange(parts);
            assertEquals(2.0, r[1], 1e-5, "Crest is the horizontal absolute top");
            Mesh m = parts.get(0).mesh;
            double l = w.length(), dx = (w.x2 - w.x1) / l, dy = (w.y2 - w.y1) / l;
            double bmin = Double.POSITIVE_INFINITY, bmax = Double.NEGATIVE_INFINITY;
            for (int v = 0; v < m.pos.length; v += 3) {
                double y = m.pos[v + 1];
                assertTrue(y <= 2.0 + 1e-5);
                if (y > 2.0 - 1e-5) continue;
                double s = (m.pos[v] - w.x1) * dx + (m.pos[v + 2] - w.y1) * dy;
                double[] mid = w.point(s, 0);
                double expected = TerrainGeometry.height(p.env, mid[0], mid[1]) - w.embedment;
                assertEquals(expected, y, 1e-4, "Bottom follows the sloped terrain below the ground");
                bmin = Math.min(bmin, y); bmax = Math.max(bmax, y);
            }
            assertOutward(parts, (w.x1 + w.x2) / 2, 0, (w.y1 + w.y2) / 2);
            if (w.y1 == 0) assertTrue(bmax - bmin > 0.5, "Wall across the slope steps with the terrain: " + bmin + ".." + bmax);
        }
    }

    @Test void storeyRelativeTopFixedBottomAndFootingBelowBody() {
        Project p = slope();
        RetainingWall w = wall(-2, 1, 2, 1, 0.5);
        w.topAbsolute = false; w.followTerrain = false; w.bottom = -1.2; w.footing = false;
        double[] noFooting = yRange(RetainingWallGeometry.build(w, p.env, 3));
        assertEquals(3.5, noFooting[1], 1e-5);
        assertEquals(1.8, noFooting[0], 1e-5);
        w.footing = true; w.footingWidth = 0.8; w.footingHeight = 0.4;
        List<GeoPart> parts = RetainingWallGeometry.build(w, p.env, 3);
        assertEquals(1.4, yRange(parts)[0], 1e-5, "Footing hangs below the wall body");
        double minX = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        for (int v = 0; v < parts.get(0).mesh.pos.length; v += 3) {
            minZ = Math.min(minZ, parts.get(0).mesh.pos[v + 2]); maxZ = Math.max(maxZ, parts.get(0).mesh.pos[v + 2]);
        }
        assertEquals(0.8, maxZ - minZ, 1e-5, "Footing is wider than the 25 cm body");
        assertThrows(IllegalArgumentException.class, () -> { RetainingWall bad = w.copy(); bad.bottom = 0.6; RetainingWallGeometry.validate(bad); });
        assertThrows(IllegalArgumentException.class, () -> { RetainingWall bad = w.copy(); bad.thickness = 0.01; RetainingWallGeometry.validate(bad); });
        assertThrows(IllegalArgumentException.class, () -> { RetainingWall bad = w.copy(); bad.x2 = bad.x1; bad.y2 = bad.y1; RetainingWallGeometry.validate(bad); });
    }

    @Test void terraceSlabIsFlatWalkableAndSupportsReachTheSlopedTerrain() {
        for (Balcony.Support support : new Balcony.Support[]{Balcony.Support.SOLID, Balcony.Support.PIERS}) {
            Project p = slope();
            double hi = Double.NEGATIVE_INFINITY, lo = Double.POSITIVE_INFINITY;
            for (double[] c : new double[][]{{2, -2}, {6, -2}, {2, 2}, {6, 2}}) {
                hi = Math.max(hi, SiteGeometry.height(p, c[0], c[1]));
                lo = Math.min(lo, SiteGeometry.height(p, c[0], c[1]));
            }
            assertTrue(hi - lo > 0.8, "Test terrace spans a real slope");
            Balcony t = Balcony.terrace(2, -2, 4, 4, Math.ceil((hi + 0.01) * 100) / 100);
            t.support = support;
            p.balconies.add(t);
            assertFalse(t.leftRail || t.rightRail || t.frontRail || t.backRail, "Terraces have no rails by default");
            List<GeoPart> parts = owned(StructureBuilder.build(p, new StructureBuilder.Options()), t);
            assertFalse(parts.isEmpty());
            double[] r = yRange(parts);
            assertEquals(t.elevation, r[1], 1e-4, "Flat slab top at the terrace level");
            assertEquals(lo - t.supportSize, r[0], 0.1, "Support reaches below the lowest terrain corner");
            assertEquals(t.elevation, FloorLevels.floorAt(p, 4, 0), 1e-9);
            assertEquals(t.elevation, FloorLevels.walkHeight(p, 4, 0, t.elevation - 0.1), 1e-9);
            RenderScene scene = RenderScene.build(p, false, false);
            assertEquals(t.elevation, surface(scene, 4, 0), 1e-4);
            assertEquals(t.elevation, surface(scene, 5.9, -1.9), 1e-4);
            // every point under the slab has clearance: terrain is below the walking surface
            for (double x = 2.1; x < 6; x += 0.4) assertTrue(SiteGeometry.height(p, x, 0) < t.elevation);
            for (GeoPart g : parts) {
                Mesh m = g.mesh;
                for (int i = 0; i < m.idx.length; i += 3) {
                    int a = m.idx[i] * 3, b = m.idx[i + 1] * 3, c = m.idx[i + 2] * 3;
                    double ux = m.pos[b] - m.pos[a], uy = m.pos[b + 1] - m.pos[a + 1], uz = m.pos[b + 2] - m.pos[a + 2];
                    double vx = m.pos[c] - m.pos[a], vy = m.pos[c + 1] - m.pos[a + 1], vz = m.pos[c + 2] - m.pos[a + 2];
                    double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    assertTrue(nx * m.nrm[a] + ny * m.nrm[a + 1] + nz * m.nrm[a + 2] > -1e-7, "Terrace winding");
                }
            }
        }
    }

    @Test void legacyBalconyKeepsNoSupportAndNoTerrace() {
        Balcony b = new Balcony();
        assertFalse(b.terrace);
        assertEquals(Balcony.Support.NONE, b.support);
        Project p = new Project(); p.balconies.add(b);
        double[] r = yRange(owned(StructureBuilder.build(p, new StructureBuilder.Options()), b));
        assertTrue(r[0] > 2.0, "Facade balcony stays a cantilever without supports");
    }

    @Test void fillPerimeterWallsSitOutsideAndHoldTheFillLevel() {
        SiteFill circle = new SiteFill();
        circle.outline.shape = SlabOpening.Shape.CIRCLE; circle.outline.width = circle.outline.depth = 6; circle.elevation = 1.2;
        List<RetainingWall> ring = RetainingWallGeometry.aroundFill(circle, 0.3);
        assertEquals(24, ring.size(), "Curves are tessellated to a bounded 24 segments");
        for (RetainingWall w : ring) {
            RetainingWallGeometry.validate(w);
            assertTrue(w.topAbsolute);
            assertEquals(1.2, w.top, 1e-12);
            assertTrue(w.followTerrain);
            double[] body = w.point(w.length() / 2, (w.offsets()[0] + w.offsets()[1]) / 2);
            assertTrue(Math.hypot(body[0], body[1]) > 2.9, "Body outside the fill");
        }

        Project p = slope();
        SiteFill f = new SiteFill();
        f.outline.width = 4; f.outline.depth = 4; double peak = Math.max(Math.max(SiteGeometry.height(p, 2, 2), SiteGeometry.height(p, -2, 2)), Math.max(SiteGeometry.height(p, 2, -2), SiteGeometry.height(p, -2, -2))); f.elevation = Math.ceil(peak * 100) / 100 + 1; f.retaining = true;
        p.env.siteFills.add(f);
        List<RetainingWall> walls = RetainingWallGeometry.aroundFill(f, 0.25);
        assertEquals(4, walls.size());
        p.retainingWalls.addAll(walls);
        RenderScene scene = RenderScene.build(p, false, false);
        double top = f.elevation;
        assertEquals(top - 0.01, surface(scene, 1.9, 0), 1e-4, "Flat fill top inside");
        assertEquals(top, surface(scene, 2.12, 0), 1e-4, "Wall crest beside the fill, not buried");
        assertEquals(top, surface(scene, -2.12, 1.5), 1e-4);
        assertEquals(top, surface(scene, 2.12, 2.12), 1e-4, "Corners are closed");
        double outside = surface(scene, 2.6, 0);
        assertEquals(SiteGeometry.height(p, 2.6, 0), outside, 0.02, "Natural terrain outside the wall");
        assertTrue(outside < top - 0.5);
        RenderScene.Hit hit = new RenderScene.Hit();
        float mid = (float) ((top + TerrainGeometry.height(p.env, 2.6, 0)) / 2);
        assertTrue(scene.intersect(3, mid, 0, -1, 0, 0, 0.001f, 5, hit));
        assertEquals(3 - 2.25, hit.t, 1e-4, "Outer wall face holds the fill");
        assertEquals(top, FloorLevels.walkHeight(p, 2.12, 0, top - 0.05), 1e-9, "Wall crest is walkable");
    }

    @Test void threeDimensionalOwnersAreTheOriginalFillAndStoreyWalls() {
        Project p = new Project();
        SiteFill f = new SiteFill(); f.elevation = 0.8; f.outline.width = 3; f.outline.depth = 3;
        p.env.siteFills.add(f);
        RetainingWall ground = wall(-4, 4, 4, 4, 1);
        p.retainingWalls.add(ground);
        BuildingLevel upper = new BuildingLevel(); upper.name = "Upper"; upper.baseHeight = 3;
        p.ensureLevels(); p.captureActive(); p.levels.add(upper);
        p.bindLevel(upper.id);
        RetainingWall relative = wall(-4, 6, 4, 6, 0.5); relative.topAbsolute = false; relative.followTerrain = true;
        p.retainingWalls.add(relative);
        p.captureActive();

        List<GeoPart> parts = StructureBuilder.build(p, new StructureBuilder.Options());
        assertFalse(owned(parts, f).isEmpty(), "Fill geometry owned by the very same SiteFill");
        assertSame(f, p.worldProject().env.siteFills.get(0));
        assertFalse(owned(parts, ground).isEmpty());
        List<GeoPart> rel = owned(parts, relative);
        assertEquals(3.5, yRange(rel)[1], 1e-5, "Storey-relative crest on raised storey");
        assertSame(upper, p.levelOf(relative));
        assertSame(p.levels.get(0), p.levelOf(ground));
        assertNull(p.levelOf(f), "Fills are global");

        Project world = p.worldProject();
        RetainingWall worldRel = null;
        for (RetainingWall w : world.retainingWalls) if (world.sourceOf(w) == relative) worldRel = w;
        assertNotNull(worldRel);
        assertTrue(worldRel.topAbsolute);
        assertEquals(3.5, worldRel.top, 1e-9);
        assertEquals(0.5, relative.top, 1e-12, "Original stays storey-relative");
    }

    @Test void persistenceRoundTripAndLegacyFiles() {
        Project p = slope();
        Balcony t = Balcony.terrace(1, 1, 3, 2, 0.4); t.support = Balcony.Support.PIERS; t.supportSize = 0.4;
        p.balconies.add(t);
        RetainingWall w = wall(0, 0, 5, 1, 1.4); w.side = RetainingWall.Side.LEFT; w.thickness = 0.35; w.footing = false;
        w.followTerrain = false; w.bottom = -0.7; w.finish = new Finish("brick", "#884422");
        p.retainingWalls.add(w);
        SiteFill f = new SiteFill(); f.elevation = 0.9; f.retaining = true; f.outline.shape = SlabOpening.Shape.ELLIPSE;
        p.env.siteFills.add(f);
        Project q = ProjectIO.fromJson(ProjectIO.toJson(p));
        Balcony qt = q.balconies.get(0);
        assertTrue(qt.terrace); assertEquals(Balcony.Support.PIERS, qt.support); assertEquals(0.4, qt.supportSize, 1e-12);
        RetainingWall qw = q.retainingWalls.get(0);
        assertEquals(RetainingWall.Side.LEFT, qw.side); assertEquals(0.35, qw.thickness, 1e-12); assertTrue(qw.topAbsolute);
        assertFalse(qw.followTerrain); assertFalse(qw.footing); assertEquals(-0.7, qw.bottom, 1e-12); assertEquals("brick", qw.finish.preset);
        assertEquals(1, q.env.siteFills.size()); assertTrue(q.env.siteFills.get(0).retaining);
        assertEquals(ProjectIO.toJson(p), ProjectIO.toJson(q));

        JsonObject json = JsonParser.parseString(ProjectIO.toJson(p)).getAsJsonObject();
        strip(json);
        Project legacy = ProjectIO.fromJson(json.toString());
        assertNotNull(legacy.retainingWalls);
        assertTrue(legacy.retainingWalls.isEmpty());
        assertFalse(legacy.balconies.get(0).terrace);
        assertEquals(Balcony.Support.NONE, legacy.balconies.get(0).support);
        StructureBuilder.build(legacy, new StructureBuilder.Options());
    }

    private static void strip(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String k : new String[]{"retainingWalls", "terrace", "support", "supportSize"}) o.remove(k);
            for (var entry : o.entrySet()) strip(entry.getValue());
        } else if (e.isJsonArray()) for (JsonElement c : e.getAsJsonArray()) strip(c);
    }
}