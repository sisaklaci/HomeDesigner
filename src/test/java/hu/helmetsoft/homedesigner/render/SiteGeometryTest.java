package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SiteGeometryTest {
    private static SiteFill fill(Project p,double top,boolean vertical) {
        SiteFill f=new SiteFill();
        f.outline.x=0; f.outline.y=0; f.outline.width=4; f.outline.depth=4;
        f.elevation=top; f.retaining=vertical; f.slopeWidth=2;
        p.env.siteFills.add(f);
        return f;
    }
    private static double surface(RenderScene scene,double x,double z) {
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect((float)x,20,(float)z,0,-1,0,0.0001f,100,hit));
        return 20-hit.t;
    }

    @Test void manualFillHasFlatTopAndContinuousSlopedSkirtWithMatchingWalkHeight() {
        Project p=new Project(); fill(p,1,false);
        var scene=RenderScene.build(p,false,false);
        for(double[] sample:new double[][]{{0,0,0.99},{2.5,0,0.74},{3,0,0.49},{4,0,-0.01},{6,0,-0.01}}) {
            assertEquals(sample[2],SiteGeometry.height(p,sample[0],sample[1]),1e-8);
            assertEquals(sample[2],surface(scene,sample[0],sample[1]),0.015);
            assertEquals(sample[2]+0.01,FloorLevels.floorAt(p,sample[0],sample[1]),1e-8);
            assertEquals(sample[2]+0.01,FloorLevels.walkHeight(p,sample[0],sample[1]),1e-8);
        }
    }

    @Test void fillIsAdditiveAndOverlappingRegionsUseHigherEnvelopeNotDoubledSurfaces() {
        Project p=new Project(); p.env.terrainSlope=15; p.env.terrainAzimuth=90;
        fill(p,0.5,false);
        SiteFill high=fill(p,1.3,false); high.outline.x=3;
        var scene=RenderScene.build(p,false,false);
        for(double x=-5;x<=7;x+=0.27) {
            assertTrue(SiteGeometry.height(p,x,0)>=TerrainGeometry.height(p.env,x,0)-1e-8);
            assertEquals(SiteGeometry.height(p,x,0),surface(scene,x,0),0.035);
        }
    }

    @Test void verticalFillHasTrueVerticalSidesNotRamps() {
        Project p=new Project(); fill(p,1,true);
        RenderScene scene=RenderScene.build(p,false,false);
        assertEquals(0.99,surface(scene,1.9,0),1e-5);
        assertEquals(-0.01,surface(scene,2.1,0),1e-5);
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(3,0.5f,0,-1,0,0,0.001f,2,hit));
        assertEquals(1,hit.t,1e-5,"Raised fill needs a real vertical soil face");
    }

    @Test void automaticEmbankmentCoversExposedFoundationWithoutFillingTheHouse() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(-2,-2,4,4),"House"); r.elevation=1;
        p.env.foundation=Environment.Foundation.BLOCK;
        p.env.autoEmbankment=true; p.env.embankmentWidth=3; p.env.embankmentTopOffset=-0.15;
        RenderScene scene=RenderScene.build(p,false,false);
        assertEquals(1.002,surface(scene,0,0),1e-5);
        assertEquals(0.84,SiteGeometry.height(p,2,0),1e-8);
        assertEquals(0.415,SiteGeometry.height(p,3.5,0),1e-8);
        assertEquals(SiteGeometry.height(p,3.5,0),surface(scene,3.5,0),0.02);
        assertEquals(-0.01,surface(scene,5.2,0),1e-5);
    }

    @Test void cellarHolesAndSplitLevelFloorsAreNotBuriedAndUpperStoreyDoesNotMakeHugeMound() {
        Project p=new Project();
        Room cellar=ProjectOps.addRoom(p,ProjectOps.rect(-2,-2,4,4),"Cellar");
        cellar.elevation=-2.8; cellar.hasCeiling=false;
        p.ensureLevels();
        BuildingLevel upper=new BuildingLevel(); upper.baseHeight=3;
        p.levels.add(upper);
        p.bindLevel(upper.id);
        ProjectOps.addRoom(p,ProjectOps.rect(-2,-2,4,4),"Upper").hasCeiling=false;
        p.env.autoEmbankment=true; fill(p,1.5,false);
        p.env.foundation=Environment.Foundation.EMBEDDED;
        var scene=RenderScene.build(p,false,false);
        RenderScene.Hit hit=new RenderScene.Hit();
        assertTrue(scene.intersect(0,0,0,0,-1,0,0.001f,5,hit));
        assertEquals(-2.798,-hit.t,1e-5,"Earth must be clipped from the cellar");
        assertEquals(1.49,SiteGeometry.height(p,0,0),1e-8,"Upper storey must not seed a 3m mound");
    }

    @Test void legacyFilesDefaultToNoLandscapingAndNewFillsPersistAndValidate() {
        Project old=ProjectIO.fromJson("{}");
        assertFalse(old.env.autoEmbankment); assertTrue(old.env.siteFills.isEmpty());
        SiteFill f=fill(old,1.2,false);
        f.outline.angle=35; f.finish.scale=2;
        old.env.autoEmbankment=true;
        Project saved=ProjectIO.copy(old);
        assertEquals(ProjectIO.toJson(old),ProjectIO.toJson(saved));
        assertEquals(35,saved.env.siteFills.getFirst().outline.angle);
        f.elevation=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.copy(old));
        f.elevation=1; old.env.embankmentWidth=0;
        assertThrows(IllegalArgumentException.class,() -> SiteGeometry.validate(old.env));
    }

    @Test void earthworkFaceWindingAndPatternScaleRemainCorrect() {
        Project p=new Project();
        SiteFill f=fill(p,1,true); f.finish.scale=2;
        SiteFill slope=fill(p,1.8,false); slope.outline.x=5; slope.finish=new Finish("concrete",null);
        for(var part:TerrainGeometry.build(p)) {
            Mesh m=part.mesh;
            for(int i=0;i<m.idx.length;i+=3) {
                int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>=-1e-7);
            }
            for(int i=0;i<m.vertexCount();i++) {
                if(Math.abs(m.nrm[i*3+1])<0.01)
                    assertEquals(m.pos[i*3+1]/part.material.tileSize,m.uv[i*2+1],1e-5,"Soil faces need vertical texture coordinates");
                else {
                    assertEquals(m.pos[i*3]/part.material.tileSize,m.uv[i*2],1e-5);
                    assertEquals(m.pos[i*3+2]/part.material.tileSize,m.uv[i*2+1],1e-5);
                }
            }
        }

    }

    @Test void slopingAndVerticalEarthFillsSupportRotatedAndCurvedOutlines() {
        for(SlabOpening.Shape shape:new SlabOpening.Shape[]{SlabOpening.Shape.RECTANGLE,SlabOpening.Shape.ELLIPSE,SlabOpening.Shape.POLYGON}) {
            Project p=new Project();
            SiteFill f=fill(p,1,true); f.outline.shape=shape; f.outline.angle=37;
            if(shape==SlabOpening.Shape.POLYGON)
                f.outline.points.addAll(java.util.List.of(new Pt(0,0),new Pt(1,0),new Pt(0.5,1)));
            RenderScene scene=RenderScene.build(p,false,false);
            assertEquals(0.99,surface(scene,0,0),1e-5);
            double[] outside=f.outline.point(1.1,0.5);
            assertEquals(-0.01,surface(scene,outside[0],outside[1]),1e-5);
        }
    }

    @Test void automaticEarthworksFollowAnEdgeSplitLevelAndNeverCoverInteriorWithFoundationDisabled() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(-2,-2,4,4),"Split"); r.elevation=1;
        FloorZone zone=new FloorZone(); zone.outline.x=0; zone.outline.y=0; zone.outline.width=2; zone.outline.depth=4;
        zone.offset=-0.5; r.floorZones.add(zone);
        p.env.autoEmbankment=true;
        assertTrue(SiteGeometry.height(p,0,2)<0.84,"The recessed edge must reduce the automatic earth envelope");
        SiteFill f=fill(p,2,true); f.outline.width=8; f.outline.depth=8;
        RenderScene scene=RenderScene.build(p,false,false);
        assertEquals(0.502,surface(scene,0,0),1e-5,"Fill must not bury a lower interior floor");
        assertEquals(1.002,surface(scene,1.5,0),1e-5,"Fill must not bury the main interior either");
    }
}
