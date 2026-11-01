package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.geom.FurnitureTransform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeModelsTest {
    @TempDir Path temporary;
    @Test void originalHouseholdPackRoundTripsAllCategoriesWithMetricDimensionsAndMaterials() throws Exception {
        Path root=temporary.resolve(HomeModels.FOLDER);
        HomeModels.write(root);
        assertEquals(175,HomeModels.ASSETS.size());
        Set<String> names=new HashSet<>();
        Set<hu.helmetsoft.homedesigner.util.Texture> loadedTextures=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<hu.helmetsoft.homedesigner.util.Texture> originalTextures=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var asset:HomeModels.ASSETS) {
            assertTrue(names.add(asset.name()));
            Path file=root.resolve(asset.category().name()).resolve(asset.name()+".obj");
            assertEquals(asset.category(),HomeModels.category(file));
            ModelData original=HomeModels.create(asset), loaded=ModelLoader.load(file,null);
            assertTrue(loaded.triangleCount()>30 || (asset.category()==HomeModels.Category.BEAMS && loaded.triangleCount()==12),asset.name());
            assertEquals(original.triangleCount(),loaded.triangleCount());
            assertEquals(original.parts.size(),loaded.parts.size());
            original.computeBounds();
            assertEquals(original.maxX-original.minX,loaded.maxX-loaded.minX,1e-5,asset.name());
            assertEquals(original.maxY-original.minY,loaded.maxY-loaded.minY,1e-5,asset.name());
            assertEquals(original.maxZ-original.minZ,loaded.maxZ-loaded.minZ,1e-5,asset.name());
            assertEquals(0,loaded.minY,1e-5);
            double maxHeight=switch(asset.category()) {
                case GARDEN_PLANTS -> 8;
                case GARDEN_BUILDINGS -> 3.5;
                case CARAVANS -> 3;
                default -> 2.5;
            };
            assertTrue(loaded.maxY>0.01 && loaded.maxY<=maxHeight,asset.name());
            for(var part:loaded.parts) {
                if(part.material.albedo!=null) loadedTextures.add(part.material.albedo);
                for(float p:part.mesh.pos) assertTrue(Float.isFinite(p),asset.name());
                for(int i=0;i<part.mesh.nrm.length;i+=3)
                    assertEquals(1,Math.sqrt(Math.pow(part.mesh.nrm[i],2)+Math.pow(part.mesh.nrm[i+1],2)+Math.pow(part.mesh.nrm[i+2],2)),1e-5);
            }
            for(var part:original.parts) if(part.material.albedo!=null) originalTextures.add(part.material.albedo);
            assertNotNull(FurnitureTransform.topView(loaded,0,64));
        }
        assertFalse(originalTextures.isEmpty());
        assertEquals(originalTextures.size(),loadedTextures.size(),"Common model textures must be loaded once, not once per asset");
        try(var files=Files.walk(root)) {
            assertEquals(originalTextures.size(),files.filter(p -> p.toString().endsWith(".png")).count());
        }
        for(var category:List.of(HomeModels.Category.CHAIRS,HomeModels.Category.TABLES,HomeModels.Category.SOFAS,
                HomeModels.Category.ARMCHAIRS,HomeModels.Category.CABINETS,HomeModels.Category.FRIDGES)) {
            assertTrue(HomeModels.ASSETS.stream().filter(a -> a.category()==category).count()>=4);
        }
    }
    @Test void sanitaryAndBathroomFurnitureAreCategorizedWithoutBreakingOldPaths() {
        Path bath=temporary.resolve(BuiltinModels.FOLDER);
        assertEquals(HomeModels.Category.SANITARY,HomeModels.category(bath.resolve("WC_fali.obj")));
        assertEquals(HomeModels.Category.BATH,HomeModels.category(bath.resolve("Mosdoszekreny_80.obj")));
        assertEquals(HomeModels.Category.IMPORTED,HomeModels.category(temporary.resolve("WC_fali.obj")));
    }
    @Test void requestedElectronicsBooksHeatersAndFansHaveDistinctMetricVariants() {
        Map<HomeModels.Category,Integer> counts=Map.of(HomeModels.Category.ELECTRONICS,7,HomeModels.Category.COMPUTERS,6,
                HomeModels.Category.BOOKS,4,HomeModels.Category.HEATING,9,HomeModels.Category.FANS,5);
        counts.forEach((category,count) -> assertEquals(count.longValue(),
                HomeModels.ASSETS.stream().filter(a -> a.category()==category).count(),category.label));
        Map<String,ModelData> models=new HashMap<>();
        for(var asset:HomeModels.ASSETS) if(counts.containsKey(asset.category())) {
            ModelData model=HomeModels.create(asset); models.put(asset.name(),model);
            assertTrue(model.triangleCount()<50000,asset.name()+" should be inexpensive enough for the catalog");
            if(asset.category()==HomeModels.Category.FANS) assertTrue(model.triangleCount()<12000,asset.name());
            assertTrue(model.parts.size()>=2,asset.name()+" should retain separate material parts");
        }
        assertEquals(1.23,width(models.get("TV_55")),0.001);
        assertTrue(width(models.get("TV_65"))>width(models.get("TV_43")));
        assertEquals(0.44,height(models.get("Szamitogep_torony")),0.001);
        assertTrue(width(models.get("Konyvsor"))>width(models.get("Konyv_allo"))*10);
        assertTrue(height(models.get("Konyvhalom"))>height(models.get("Konyv_nyitott"))*5);
        assertTrue(width(models.get("Lapradiator_120"))>width(models.get("Lapradiator_60"))*1.4);
        assertTrue(models.get("Gazkonvektor").maxZ-models.get("Gazkonvektor").minZ>0.35);
        assertTrue(width(models.get("Ventilator_mennyezeti"))>1.1);
        assertTrue(height(models.get("Ventilator_allo"))>1.3);
        assertTrue(height(models.get("Ventilator_asztali"))<0.5);
        assertTrue(height(models.get("Ventilator_oszlop"))>0.8);
    }
    private static double width(ModelData m) { return m.maxX-m.minX; }
    private static double height(ModelData m) { return m.maxY-m.minY; }
    @Test void squareAndRoundColumnsHaveClosedUprightMetricBodiesAndOutwardFaces() {
        var columns=HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.COLUMNS).toList();
        assertEquals(2,columns.size());
        for(var asset:columns) {
            ModelData model=HomeModels.create(asset);
            assertEquals(0,model.minY,1e-6);
            assertEquals(2.4,height(model),1e-6);
            assertEquals(0.36,width(model),1e-6);
            assertEquals(0.36,model.maxZ-model.minZ,1e-6);
            assertTrue(model.triangleCount()<2000);
            for(var part:model.parts) {
                assertNotNull(part.material.albedo);
                var m=part.mesh;
                for(int i=0;i<m.idx.length;i+=3) {
                    int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                    double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                    double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                    double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                    assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>=-1e-7,asset.name());
                }
                for(int i=0;i<m.vertexCount();i++) if(m.pos[i*3+1]>0.2 && m.pos[i*3+1]<2.25) {
                    if(asset.variant()==1)
                        assertEquals(0.15,Math.hypot(m.pos[i*3],m.pos[i*3+2]),1e-6);
                    else assertEquals(0.15,Math.max(Math.abs(m.pos[i*3]),Math.abs(m.pos[i*3+2])),1e-6);
                }
            }
        }
        assertFalse(HomeModels.isColumn("ordinary.obj"));
    }
    @Test void gardenPackHasMetricFurnitureFirepitSwingsAndAccessories() {
        var assets=HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.GARDEN).toList();
        assertEquals(20,assets.size());
        Map<String,ModelData> models=new HashMap<>();
        for(var asset:assets) {
            var model=HomeModels.create(asset); models.put(asset.name(),model);
            assertTrue(model.parts.size()>=2,asset.name());
            assertTrue(model.triangleCount()<20000,asset.name());
        }
        assertEquals(1.6,width(models.get("Kerti_asztal_160")),1e-5);
        assertTrue(width(models.get("Kerti_pad"))>1.5);
        assertTrue(height(models.get("Szalonnasuto"))<1);
        assertTrue(height(models.get("Hintaagy_3_szemelyes"))>2);
        assertEquals(2.4,height(models.get("Napernyo")),1e-5);
        assertTrue(models.get("Hintaagy_fekvo").maxZ-models.get("Hintaagy_fekvo").minZ>1.7);
    }
    @Test void indoorAndOutdoorPlantsHaveAllRequestedTypesAndAffordableMeshes() {
        assertEquals(28,HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.GARDEN_PLANTS).count());
        assertEquals(8,HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.INDOOR_PLANTS).count());
        for(var asset:HomeModels.ASSETS) if(asset.category()==HomeModels.Category.GARDEN_PLANTS || asset.category()==HomeModels.Category.INDOOR_PLANTS) {
            var model=HomeModels.create(asset);
            assertTrue(model.parts.size()>=2,asset.name());
            assertTrue(model.triangleCount()<12000,asset.name()+" catalog cost");
            assertTrue(model.maxY-model.minY>0.15,asset.name());
            if(asset.category()==HomeModels.Category.INDOOR_PLANTS) assertTrue(model.maxY<2);
            assertWinding(model,asset.name());
        }
    }

    @Test void expandedGardenModelsRoundTripWithExactCatalogMembershipAndMetricGeometry() throws Exception {
        Map<String,HomeModels.Category> added=new LinkedHashMap<>();
        for(String name:List.of("Kerti_pad_tamasztott","Kerti_pad_tamla_nelkul","Kerti_asztal_200",
                "Sorgarnitura_2_szemelyes","Sorgarnitura_4_szemelyes","Sorgarnitura_6_szemelyes"))
            added.put(name,HomeModels.Category.GARDEN);
        for(String name:List.of("Cseresznyefa","Kortefa","Datolyaszilvafa","Diofa","Meggyfa","Almafa","Naspolyafa",
                "Szolo_lugas","Szolo_voros","Szolo_feher","Ribizlibokor","Egresbokor","Malnabokor","Afonyabokor"))
            added.put(name,HomeModels.Category.GARDEN_PLANTS);
        added.put("Palapa",HomeModels.Category.GARDEN_BUILDINGS);
        added.put("Filagoria",HomeModels.Category.GARDEN_BUILDINGS);
        assertEquals(22,added.size());
        assertEquals(20,HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.GARDEN).count());
        assertEquals(28,HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.GARDEN_PLANTS).count());
        assertEquals(4,HomeModels.ASSETS.stream().filter(a -> a.category()==HomeModels.Category.GARDEN_BUILDINGS).count());
        Map<String,HomeModels.Asset> assets=new HashMap<>();
        for(var asset:HomeModels.ASSETS) assets.put(asset.name(),asset);
        Path root=temporary.resolve("expanded-garden");
        HomeModels.write(root);
        for(var entry:added.entrySet()) {
            var asset=assets.get(entry.getKey());
            assertNotNull(asset,entry.getKey());
            assertEquals(entry.getValue(),asset.category(),entry.getKey());
            ModelData original=HomeModels.create(asset);
            ModelData loaded=ModelLoader.load(root.resolve(asset.category().name()).resolve(asset.name()+".obj"),null);
            assertEquals(original.triangleCount(),loaded.triangleCount(),entry.getKey());
            assertTrue(original.triangleCount()<12000,entry.getKey());
            assertTrue(original.parts.size()>=2,entry.getKey());
            original.computeBounds();
            assertEquals(original.maxX-original.minX,loaded.maxX-loaded.minX,1e-5,entry.getKey());
            assertEquals(original.maxY-original.minY,loaded.maxY-loaded.minY,1e-5,entry.getKey());
            assertEquals(original.maxZ-original.minZ,loaded.maxZ-loaded.minZ,1e-5,entry.getKey());
            assertEquals(0,loaded.minY,1e-5,entry.getKey());
            assertWinding(original,entry.getKey());
        }
        assertTrue(height(HomeModels.create(assets.get("Palapa")))>3.0);
        assertTrue(width(HomeModels.create(assets.get("Palapa")))>3.7);
        assertTrue(width(HomeModels.create(assets.get("Filagoria")))>3.0);
        assertTrue(height(HomeModels.create(assets.get("Diofa")))>4.5);
        assertTrue(width(HomeModels.create(assets.get("Sorgarnitura_6_szemelyes")))>
                width(HomeModels.create(assets.get("Sorgarnitura_2_szemelyes")))*1.5);
    }

    @Test void pavilionRoofMeshesCoverInteriorRayGridAndPlantsKeepDistinctFruitDetails() {
        var assets=new HashMap<String,HomeModels.Asset>();
        for(var asset:HomeModels.ASSETS) assets.put(asset.name(),asset);
        ModelData gazebo=HomeModels.create(assets.get("Filagoria"));
        for(int ix=0;ix<=24;ix++) for(int iz=0;iz<=20;iz++) {
            double x=-1.6+ix*(3.2/24),z=-1.4+iz*(2.8/20);
            assertVerticalRayHit(gazebo,x,z,2.05,3.0,"Filagoria roof");
        }
        ModelData palapa=HomeModels.create(assets.get("Palapa"));
        for(int ir=0;ir<=12;ir++) for(int ia=0;ia<24;ia++) {
            double radius=ir*1.85/12,angle=ia*Math.PI/12;
            assertVerticalRayHit(palapa,radius*Math.cos(angle),radius*Math.sin(angle),2.0,3.25,"Palapa thatch");
        }
        Map<String,String> fruitMaterials=Map.of(
                "Cseresznyefa","Gyumolcs_piros","Kortefa","Gyumolcs_arany","Datolyaszilvafa","Gyumolcs_narancs",
                "Diofa","Gyumolcs_barna","Meggyfa","Gyumolcs_meggy","Almafa","Gyumolcs_alma","Naspolyafa","Gyumolcs_barna");
        fruitMaterials.forEach((name,material) -> assertTrue(HomeModels.create(assets.get(name)).parts.stream()
                .anyMatch(part -> part.material.key.equals(material)),name+" fruit color"));
        for(String name:List.of("Cseresznyefa","Kortefa","Datolyaszilvafa","Diofa","Meggyfa","Almafa","Naspolyafa")) {
            ModelData tree=HomeModels.create(assets.get(name));
            int leafTriangles=tree.parts.stream()
                    .filter(part -> part.material.key.equals("Lomb_zold") || part.material.key.equals("Friss_level"))
                    .mapToInt(part -> part.mesh.triangleCount()).sum();
            assertTrue(leafTriangles>=1000,name+" should use many individually folded leaf blades");
            assertTrue(tree.triangleCount()<10000,name+" foliage should remain affordable");
        }
        ModelData walnut=HomeModels.create(assets.get("Diofa"));
        int walnutLeafTriangles=walnut.parts.stream()
                .filter(part -> part.material.key.equals("Lomb_zold") || part.material.key.equals("Friss_level"))
                .mapToInt(part -> part.mesh.triangleCount()).sum();
        assertTrue(walnutLeafTriangles>1500,"walnut canopy should read as foliage, not smooth spherical crowns");
        assertTrue(HomeModels.create(assets.get("Egresbokor")).parts.stream()
                .anyMatch(part -> part.material.key.equals("Bogyos_zold")));
        assertTrue(HomeModels.create(assets.get("Malnabokor")).parts.stream()
                .anyMatch(part -> part.material.key.equals("Bogyos_malna")));
        assertTrue(HomeModels.create(assets.get("Afonyabokor")).parts.stream()
                .anyMatch(part -> part.material.key.equals("Bogyos_kek")));
    }

    private static void assertVerticalRayHit(ModelData model,double x,double z,double minY,double maxY,String label) {
        for(var part:model.parts) {
            var mesh=part.mesh;
            for(int i=0;i<mesh.idx.length;i+=3) {
                int a=mesh.idx[i]*3,b=mesh.idx[i+1]*3,c=mesh.idx[i+2]*3;
                double ax=mesh.pos[a],az=mesh.pos[a+2],bx=mesh.pos[b],bz=mesh.pos[b+2],cx=mesh.pos[c],cz=mesh.pos[c+2];
                double den=(bz-cz)*(ax-cx)+(cx-bx)*(az-cz);
                if(Math.abs(den)<1e-10) continue;
                double wa=((bz-cz)*(x-cx)+(cx-bx)*(z-cz))/den;
                double wb=((cz-az)*(x-cx)+(ax-cx)*(z-cz))/den,wc=1-wa-wb;
                if(wa>=-1e-6 && wb>=-1e-6 && wc>=-1e-6) {
                    double y=wa*mesh.pos[a+1]+wb*mesh.pos[b+1]+wc*mesh.pos[c+1];
                    if(y>=minY && y<=maxY) return;
                }
            }
        }
        fail(label+" has an uncovered vertical ray at ("+x+", "+z+")");
    }

    private static void assertWinding(ModelData model,String name) {
        for(var part:model.parts) for(int i=0;i<part.mesh.idx.length;i+=3) {
                var m=part.mesh; int a=m.idx[i]*3,b=m.idx[i+1]*3,c=m.idx[i+2]*3;
                double ux=m.pos[b]-m.pos[a],uy=m.pos[b+1]-m.pos[a+1],uz=m.pos[b+2]-m.pos[a+2];
                double vx=m.pos[c]-m.pos[a],vy=m.pos[c+1]-m.pos[a+1],vz=m.pos[c+2]-m.pos[a+2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                assertTrue(nx*m.nrm[a]+ny*m.nrm[a+1]+nz*m.nrm[a+2]>-1e-8,
                        name+" winding must match its normal: "+part.material.key+" triangle "+i/3);
        }
    }
    @Test void boilersAirConditionersPoolShedsAndGrapevinesHaveDistinctMetricGeometry() {
        Map<HomeModels.Category,Integer> counts=Map.of(HomeModels.Category.MECHANICAL,4,
                HomeModels.Category.POOL,3,HomeModels.Category.GARDEN_BUILDINGS,4);
        counts.forEach((category,count) -> assertEquals(count.longValue(),
                HomeModels.ASSETS.stream().filter(a -> a.category()==category).count(),category.label));
        Map<String,ModelData> models=new HashMap<>();
        for(var asset:HomeModels.ASSETS) if(counts.containsKey(asset.category()) || asset.name().contains("Szolo")
                || asset.name().equals("Kordonos_szolo")) {
            var model=HomeModels.create(asset); models.put(asset.name(),model);
            assertTrue(model.parts.size()>=2,asset.name());
            assertTrue(model.triangleCount()<12000,asset.name());
            assertWinding(model,asset.name());
        }
        assertEquals(16,models.size());
        assertEquals(0.42,width(models.get("Gazbojler")),1e-5);
        assertTrue(height(models.get("Villanybojler"))>1);
        assertEquals(0.86,width(models.get("Klima_belteri")),1e-5);
        assertTrue(height(models.get("Klima_kulteri"))>0.6);
        ModelData pool=models.get("Medence_6x3");
        assertEquals(6.56,width(pool),1e-5);
        assertEquals(3.56,pool.maxZ-pool.minZ,1e-5);
        var water=pool.parts.stream().filter(p -> p.material.key.equals("Medence_vizfelulet")).findFirst().orElseThrow();
        for(int i=1;i<water.mesh.pos.length;i+=3) assertTrue(water.mesh.pos[i]>=1.19-1e-5 && water.mesh.pos[i]<=1.205+1e-5);
        assertTrue(height(models.get("Medence_kapaszkodokorlat"))>1);
        assertTrue(height(models.get("Vizforgato_homokszurovel"))>0.75);
        assertTrue(width(models.get("Kerti_kishaz"))>width(models.get("Szerszamtarolo")));
        assertTrue(width(models.get("Kordonos_szolo"))>2.8);
        assertTrue(width(models.get("Szolotoke"))<1.8);
    }
    @Test void bedsLampsHallwayAndElectricalEquipmentHaveAllRequestedVariants() {
        Map<HomeModels.Category,Integer> counts=Map.of(HomeModels.Category.BEDS,4,HomeModels.Category.LAMPS,5,
                HomeModels.Category.HALL,5,HomeModels.Category.ELECTRICAL,6);
        counts.forEach((category,count) -> assertEquals(count.longValue(),
                HomeModels.ASSETS.stream().filter(a -> a.category()==category).count()));
        for(var asset:HomeModels.ASSETS) if(counts.containsKey(asset.category())) {
            var model=HomeModels.create(asset);
            assertTrue(model.parts.size()>=2,asset.name());
            assertTrue(model.triangleCount()<25000,asset.name());
            if(asset.category()==HomeModels.Category.BEDS) {
                assertTrue(model.maxZ-model.minZ>=1.6,asset.name());
                assertTrue(height(model)<1.1,asset.name());
            }
        }
    }
    @Test void antennasAndBothSolarCatalogVariantsRetainSeparateMaterialsAndOutwardFaces() {
        var assets=HomeModels.ASSETS.stream().filter(a -> a.name().equals("TV_antenna")
                || a.name().equals("Parabolaantenna") || a.category()==HomeModels.Category.SOLAR).toList();
        assertEquals(4,assets.size());
        for(var asset:assets) {
            var model=HomeModels.create(asset);
            assertTrue(model.parts.size()>=2,asset.name());
            assertTrue(model.triangleCount()<6000,asset.name());
            assertWinding(model,asset.name());
            assertEquals(0,model.minY,1e-6);
            assertTrue(model.maxY>0.9,asset.name());
        }
    }
}
