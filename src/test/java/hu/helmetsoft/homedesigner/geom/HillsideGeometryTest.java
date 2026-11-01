package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HillsideGeometryTest {
    @Test void terrainMeshHasExactSlopeAzimuthAndDatumWithoutTiltingRooms() {
        Project p=new Project();
        for(double slope:new double[]{0,12,35,60}) for(double azimuth:new double[]{0,45,90,180,270}) {
            p.env.terrainSlope=slope; p.env.terrainAzimuth=azimuth;
            p.env.terrainHeight=1.2; p.env.terrainOriginX=4; p.env.terrainOriginY=-3;
            var parts=TerrainGeometry.build(p);
            assertEquals(1.19,surface(parts,4,-3),1e-5);
            double a=Math.toRadians(azimuth);
            assertEquals(1.19+Math.tan(Math.toRadians(slope))*2,
                    surface(parts,4+Math.sin(a)*2,-3-Math.cos(a)*2),1e-4);
            assertWinding(parts);
        }
    }

    @Test void foundationsHaveFlatTopsAndBasementExcavationReachesLowestOverlappingRoom() {
        Project p=new Project();
        Room lower=ProjectOps.addRoom(p,ProjectOps.rect(-3,-2,6,4),"Cellar");
        Room upper=ProjectOps.addRoom(p,ProjectOps.rect(-3,-2,6,4),"Ground");
        p.env.terrainSlope=20; p.env.terrainHeight=1;
        for(var mode:new Environment.Foundation[]{Environment.Foundation.BLOCK,Environment.Foundation.EMBEDDED}) {
            p.env.foundation=mode;
            var parts=TerrainGeometry.build(p.env,List.of(new TerrainGeometry.Pad(upper,0),
                    new TerrainGeometry.Pad(lower,-2.8)),p.wallBounds());
            assertEquals(-2.805,surface(parts,0.1,0.2),1e-5,"No soil or foundation may bury cellar");
            assertEquals(TerrainGeometry.height(p.env,9,5),surface(parts,9,5),1e-5);
            assertWinding(parts);
        }
        p.env.showGround=false;
        var hiddenTerrain=TerrainGeometry.build(p);
        assertEquals(1,hiddenTerrain.size(),"Hiding terrain must preserve the foundation");
        assertEquals(-0.005,surface(hiddenTerrain,1,1),1e-5);
    }

    @Test void vaultProfilesAreClippedToRoomAndKeepActualSlabHoles() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Cellar");
        r.vaultRibs=false; r.vaultRise=1;
        SlabOpening hole=new SlabOpening();
        hole.x=3; hole.y=2; hole.width=hole.depth=0.8; hole.cutCeiling=true;
        r.slabOpenings.add(hole);
        for(Room.Vault vault:Room.Vault.values()) if(vault!=Room.Vault.NONE) {
            r.vault=vault;
            assertEquals(3.4,VaultGeometry.height(r,2.4,3,2),1e-8);
            assertEquals(2.4,VaultGeometry.height(r,2.4,vault==Room.Vault.GROIN ? 0:3,0),1e-8);
            var parts=VaultGeometry.build(r,2.4);
            assertFalse(parts.isEmpty());
            assertEquals(Double.NEGATIVE_INFINITY,surface(parts,3,2),"Slab opening must stay open");
            double observed=surface(parts,1.1,1.4)-0.12;
            assertEquals(VaultGeometry.height(r,2.4,1.1,1.4),observed,0.025);
            assertEquals(Double.NEGATIVE_INFINITY,surface(parts,-0.5,2));
            assertWinding(parts);
    }

    r.vaultRibs=true;
    assertTrue(VaultGeometry.build(r,2.4).getFirst().mesh.triangleCount()>1000);
    }

    @Test void bothFoundationModesPreserveRecessedInteriorAndFloorOpenings() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,8,6),"Room");
        FloorZone zone=new FloorZone();
        zone.outline.x=4; zone.outline.y=3; zone.outline.width=zone.outline.depth=3;
        zone.offset=-0.51; r.floorZones.add(zone);
        SlabOpening opening=new SlabOpening();
        opening.x=4; opening.y=3; opening.width=opening.depth=0.8;
        opening.cutFloor=true; r.slabOpenings.add(opening);
        for(var mode:new Environment.Foundation[]{Environment.Foundation.BLOCK,Environment.Foundation.EMBEDDED}) {
            p.env.foundation=mode;
            var parts=TerrainGeometry.build(p);
            assertEquals(-0.515,surface(parts,3.1,3.1),1e-5);
            assertEquals(-0.005,surface(parts,1,1),1e-5);
            assertTrue(surface(parts,4,3)<-0.6,"Foundation must not fill a floor opening at walking height");
            assertWinding(parts);
        }
    }

    @Test void settingsPersistAndInvalidValuesAreExplicitlyRejected() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Cellar");
        r.vault=Room.Vault.POINTED; r.vaultRise=1.1;
        p.env.terrainSlope=22; p.env.foundation=Environment.Foundation.EMBEDDED;
        Project copy=ProjectIO.copy(p);
        assertEquals(p.env.terrainSlope,copy.env.terrainSlope);
        assertEquals(r.vault,copy.rooms.getFirst().vault);
        assertEquals(1.1,copy.rooms.getFirst().vaultRise);
        p.env.terrainSlope=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> TerrainGeometry.validate(p.env));
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.copy(p));
        p.env.terrainSlope=22;
        r.vaultRise=-1;
        assertThrows(IllegalArgumentException.class,() -> VaultGeometry.validate(r));
        assertThrows(IllegalArgumentException.class,() -> ProjectIO.copy(p));
    }

    @Test void terrainFoundationAndVaultRespectTheChosenMaterialPatternScale() {
        Project p=new Project();
        Room r=ProjectOps.addRoom(p,ProjectOps.rect(0,0,6,4),"Vault");
        p.env.ground.scale=2.5; p.env.foundationFinish.scale=1.8;
        p.env.foundation=Environment.Foundation.BLOCK;
        r.vault=Room.Vault.BARREL; r.vaultRibs=false; r.ceiling.scale=2;
        var parts=new java.util.ArrayList<>(TerrainGeometry.build(p));
        parts.addAll(VaultGeometry.build(r,2.4));
        for(GeoPart part:parts) for(int i=0;i<part.mesh.vertexCount();i++) {
            assertEquals(part.mesh.pos[i*3]/part.material.tileSize,part.mesh.uv[i*2],1e-5);
            assertEquals(part.mesh.pos[i*3+2]/part.material.tileSize,part.mesh.uv[i*2+1],1e-5);
        }
    }

    private static double surface(List<GeoPart> parts,double x,double z) {
        double highest=Double.NEGATIVE_INFINITY;
        for(GeoPart part:parts) {
            Mesh m=part.mesh;
            for(int i=0;i<m.idx.length;i+=3) {
                int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ax=m.pos[a],az=m.pos[a+2],bx=m.pos[b],bz=m.pos[b+2],cx=m.pos[c],cz=m.pos[c+2];
                double det=(bz-cz)*(ax-cx)+(cx-bx)*(az-cz);
                if(Math.abs(det)<1e-10) continue;
                double u=((bz-cz)*(x-cx)+(cx-bx)*(z-cz))/det;
                double v=((cz-az)*(x-cx)+(ax-cx)*(z-cz))/det;
                if(u>=-1e-7 && v>=-1e-7 && u+v<=1+1e-7)
                    highest=Math.max(highest,u*m.pos[a+1]+v*m.pos[b+1]+(1-u-v)*m.pos[c+1]);
            }
        }
        return highest;
    }

    private static void assertWinding(List<GeoPart> parts) {
        for(var part:parts) {
            Mesh m=part.mesh;
            for(float v:m.pos) assertTrue(Float.isFinite(v));
            for(int i=0;i<m.idx.length;i+=3) {
                int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>-1e-7,"Visible face winding");
            }
        }
    }
}
