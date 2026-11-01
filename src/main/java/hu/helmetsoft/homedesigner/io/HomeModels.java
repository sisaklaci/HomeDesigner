package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.material.*;
import hu.helmetsoft.homedesigner.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Original, unbranded household models; all dimensions in metres. */
public final class HomeModels {
    public static final String FOLDER="HelmetSoft_Otthon";
    public enum Category {
        CHAIRS("Székek"), TABLES("Asztalok"), SOFAS("Kanapék"), ARMCHAIRS("Fotelek"), CABINETS("Szekrények"),
        SANITARY("Szaniterek"), BATH("Fürdőszobabútorok"), KITCHEN("Konyhafelszerelés"), SINKS("Mosogatók"),
        APPLIANCES("Konyhai gépek"), FRIDGES("Hűtőszekrények"),
        ELECTRONICS("Szórakoztatóelektronika"), COMPUTERS("Számítógépek és tartozékok"),
        BOOKS("Könyvek"), HEATING("Radiátorok és konvektorok"), FANS("Ventilátorok"),
        BEDS("Ágyak"), LAMPS("Lámpatestek"), HALL("Előszobabútorok"), ELECTRICAL("Villamos berendezések"),
        GARDEN("Kerti bútorok és felszerelések"),
        MECHANICAL("Bojlerek és klímák"), POOL("Medence és felszerelései"), GARDEN_BUILDINGS("Kerti házak és tárolók"),
        SOLAR("Napelemek"), COLUMNS("Oszlopok"), BEAMS("Gerendák"),
        WATER("Kutak és vízellátás"), CARAVANS("Lakókocsik"),
        GARDEN_PLANTS("Kerti növények"), INDOOR_PLANTS("Szobanövények"),
        IMPORTED("Importált modellek");
        public final String label;
        Category(String label) { this.label=label; }
        @Override public String toString() { return hu.helmetsoft.homedesigner.util.I18n.display(label); }
    }
    public record Asset(String name, Category category, int variant) {}
    public static final List<Asset> ASSETS;
    static {
        List<Asset> assets=new ArrayList<>();
        group(assets,Category.CHAIRS,"Etkezoszek_fa","Etkezoszek_karpitozott","Barszek","Irodaszek","Pad_ketszemelyes");
        group(assets,Category.TABLES,"Etkezoasztal_140","Etkezoasztal_180","Etkezoasztal_kerek","Dohanyzoasztal","Iroasztal");
        group(assets,Category.SOFAS,"Kanape_2_szemelyes","Kanape_3_szemelyes","Sarokkanape_bal","Sarokkanape_jobb","Kanape_kompakt");
        group(assets,Category.ARMCHAIRS,"Fotel_klasszikus","Fotel_modern","Olvasofotel","Labtarto");
        group(assets,Category.CABINETS,"Ruhasszekreny_2_ajtos","Ruhasszekreny_3_ajtos","Komod_6_fiokos","Konyvespolc","TV_szekreny");
        group(assets,Category.KITCHEN,"Konyhai_alsoelem_60","Konyhai_fiokoselem_80","Konyhai_falialelem_80","Konyhasziget_160","Vagodeszka","Edeny_fazek","Vizforralo");
        group(assets,Category.SINKS,"Mosogato_egymedences","Mosogato_duplamedences","Mosogato_csepegtetos");
        group(assets,Category.APPLIANCES,"Mosogatogep_60","Mosogatogep_45","Mikrohullamu_suto","Beepitheto_suto","Tuzhely_60","Fozolap_60","Paraelszivo","Kavefozo");
        group(assets,Category.FRIDGES,"Huto_kombinalt_185","Huto_egyajtos_150","Huto_pultala_85","Huto_side_by_side","Huto_french_door");
        group(assets,Category.ELECTRONICS,"TV_43","TV_55","TV_65","Hi_fi_torony","Hangfalpar","Hangprojektor","Jatekkonzol");
        group(assets,Category.COMPUTERS,"Szamitogep_torony","Monitor_27","Laptop_nyitott","Billentyuzet_es_eger","Nyomtato","Szamitogep_egybeepitett");
        group(assets,Category.BOOKS,"Konyv_allo","Konyvsor","Konyvhalom","Konyv_nyitott");
        group(assets,Category.HEATING,"Gazkonvektor","Radiator_aluminium","Radiator_vas","Lapradiator_60","Lapradiator_120",
                "Radiator_cellas","Radiator_elektromos","Radiator_olajtoltesu","Radiator_elektromos_fali");
        group(assets,Category.FANS,"Ventilator_asztali","Ventilator_fali","Ventilator_mennyezeti","Ventilator_allo","Ventilator_oszlop");
        group(assets,Category.BEDS,"Agy_egyszemelyes_90","Agy_ketszemelyes_160","Agy_francia_180","Agy_gyerek_80");
        group(assets,Category.LAMPS,"Csillar_5_karu","Csillar_fuggesztek_3","Ufolampa","Allolampa","Fali_olvasolampa");
        group(assets,Category.HALL,"Ruhatarto_allo","Ciposszekreny","Eloszobafal_akasztos","Cipotarto_nyitott","Ruhatar_allvany");
        group(assets,Category.ELECTRICAL,"Villanyoraszekreny","Villanyoraszekreny_kulteri","Napelemes_inverter","Inverter_wifi_modul",
                "TV_antenna","Parabolaantenna");
        group(assets,Category.SOLAR,"Napelem_tetore","Napelem_talajra");
        group(assets,Category.COLUMNS,"Oszlop_negyzetes","Oszlop_kor");
        group(assets,Category.BEAMS,"Fagerenda_vizszintes");
        group(assets,Category.WATER,"Hazi_vizszivattyu","Kut_kerekes","Kut_tetos","Kut_keziszivattyus");
        group(assets,Category.CARAVANS,"Lakokocsi_tura_540");
        group(assets,Category.GARDEN,"Kerti_asztal_160","Kerti_asztal_kerek","Kerti_szek","Kerti_karosszek",
                "Kerti_pad","Napozoagy","Szalonnasuto","Grillsuto","Hintaagy_3_szemelyes","Hintaagy_fekvo",
                "Napernyo","Viraglada","Piknikasztal_padokkal","Kerti_kanape",
                "Kerti_pad_tamasztott","Kerti_pad_tamla_nelkul","Kerti_asztal_200",
                "Sorgarnitura_2_szemelyes","Sorgarnitura_4_szemelyes","Sorgarnitura_6_szemelyes");
        group(assets,Category.GARDEN_PLANTS,"Lombos_fa","Gyumolcsfa","Nyirfa","Fenyo",
                "Tuja_oszlopos","Tuja_gomb","Bokor","Viragzo_cserje","Soveny","Rozsabokor","Tulipanok","Viragagyas",
                "Szolotoke","Kordonos_szolo",
                "Cseresznyefa","Kortefa","Datolyaszilvafa","Diofa","Meggyfa","Almafa","Naspolyafa",
                "Szolo_lugas","Szolo_voros","Szolo_feher",
                "Ribizlibokor","Egresbokor","Malnabokor","Afonyabokor");
        group(assets,Category.MECHANICAL,"Gazbojler","Villanybojler","Klima_belteri","Klima_kulteri");
        group(assets,Category.POOL,"Medence_6x3","Medence_kapaszkodokorlat","Vizforgato_homokszurovel");
        group(assets,Category.GARDEN_BUILDINGS,"Kerti_kishaz","Szerszamtarolo","Palapa","Filagoria");
        group(assets,Category.INDOOR_PLANTS,"Fikusz_cserepben","Monstera_cserepben","Palma_cserepben","Anyosnyelv",
                "Pozsgas_cserepben","Kaktusz_cserepben","Orchidea_cserepben","Muskatli_cserepben");
        ASSETS=List.copyOf(assets);
    }
    private static final SurfaceMaterial WOOD=MaterialLibrary.resolve(new Finish("oak_wood",null));
    private static final SurfaceMaterial WHITE=ModelBuilder.material("Feher_front",0.91,0.92,0.92,0.23,false,false);
    private static final SurfaceMaterial METAL=ModelBuilder.material("Rozsdamentes_acel",0.65,0.68,0.71,0.18,true,false);
    private static final SurfaceMaterial DARK=ModelBuilder.material("Grafit",0.045,0.052,0.065,0.25,false,false);
    private static final SurfaceMaterial GLASS=ModelBuilder.material("Sotet_uveg",0.08,0.13,0.16,0.09,false,false);
    private HomeModels() {}
    private static void group(List<Asset> list,Category category,String... names) {
        for (int i=0;i<names.length;i++) list.add(new Asset(names[i],category,i));
    }

    public static Category category(Path path) {
        for (Path part:path) if (part.toString().equals(FOLDER)) {
            String folder=path.getParent().getFileName().toString();
            for (Category c:Category.values()) if (c.name().equals(folder)) return c;
        }
        if (path.getParent()!=null && path.getParent().getFileName().toString().equals(BuiltinModels.FOLDER)) {
            String name=path.getFileName().toString();
            return name.contains("szekreny") || name.startsWith("Torolkozo") || name.startsWith("Mosogep") ? Category.BATH : Category.SANITARY;
        }
        return Category.IMPORTED;
    }

    public static ModelData create(Asset asset) {
        ModelBuilder b=new ModelBuilder(); int v=asset.variant;
        switch(asset.category) {
            case CHAIRS -> chair(b,v);
            case TABLES -> table(b,v);
            case SOFAS -> sofa(b,v,false);
            case ARMCHAIRS -> sofa(b,v,true);
            case CABINETS -> {
                Furniture f=new Furniture(); f.cabinet=new Cabinet();
                f.width=v==1 ? 1.8 : v==4 ? 1.6 : v==3 ? 0.9 : 1.2;
                f.height=v<2 ? 2.1 : v==3 ? 1.9 : 0.75; f.depth=v<2 ? 0.6 : 0.4;
                f.cabinet.columns=v==1 ? 3 : 2; f.cabinet.shelves=v==3 ? 5 : v==4 ? 1 : 3;
                if(v==2) { f.cabinet.drawers=3; f.cabinet.doors=false; f.cabinet.shelves=0; }
                if(v==3 || v==4) f.cabinet.doors=false;
                return CabinetGeometry.build(f);
            }
            case KITCHEN -> kitchen(b,v);
            case SINKS -> sink(b,v);
            case APPLIANCES -> appliance(b,v);
            case FRIDGES -> fridge(b,v);
            case ELECTRONICS -> electronics(b,v);
            case COMPUTERS -> computer(b,v);
            case BOOKS -> books(b,v);
            case HEATING -> heating(b,v);
            case FANS -> fan(b,v);
            case BEDS -> bed(b,v);
            case LAMPS -> lamp(b,v);
            case HALL -> hall(b,v);
            case ELECTRICAL -> electrical(b,v);
            case COLUMNS -> column(b,v);
            case BEAMS -> b.box(WOOD,-2,0,-0.1,4,0.2,0.2);
            case WATER -> waterEquipment(b,v);
            case CARAVANS -> caravan(b);
            case SOLAR -> {
                Furniture f=new Furniture(); f.solar=new SolarPanel();
                f.solar.type=v==0 ? SolarPanel.Type.ROOF : SolarPanel.Type.GROUND;
                return SolarGeometry.build(f);
            }
            case GARDEN -> garden(b,v);
            case MECHANICAL -> mechanical(b,v);
            case POOL -> pool(b,v);
            case GARDEN_BUILDINGS -> gardenBuilding(b,v);
            case GARDEN_PLANTS -> gardenPlant(b,v);
            case INDOOR_PLANTS -> indoorPlant(b,v);
            default -> throw new IllegalArgumentException("Nincs generátor: "+asset.category);
        }
        return b.build(asset.name);
    }

    public static boolean isColumn(String path) {
        return path!=null && category(Path.of(path))==Category.COLUMNS;
    }

    public static boolean isBeam(String path) {
        return path!=null && category(Path.of(path))==Category.BEAMS;
    }

    private static void waterEquipment(ModelBuilder b,int variant) {
        SurfaceMaterial stone=MaterialLibrary.resolve(new Finish("stone",null));
        SurfaceMaterial green=ModelBuilder.material("Szivattyu_zold",0.10,0.28,0.16,0.4,false,false);
        if(variant==0) {
            for(int side:new int[]{-1,1}) b.box(DARK,side*0.22-0.035,0,-0.16,0.07,0.08,0.32);
            b.shell(green,0,0,1,new double[][]{{0.001,0.04,0.001},{0.16,0.08,0.16},
                    {0.22,0.18,0.22},{0.22,0.40,0.22},{0.16,0.5,0.16},{0.001,0.54,0.001}});
            b.box(METAL,-0.22,0.51,-0.17,0.44,0.035,0.34);
            b.rod(green,new double[]{-0.2,0.67,0},new double[]{0.15,0.67,0},0.10);
            for(int i=0;i<9;i++) b.rod(DARK,new double[]{-0.2+i*0.03,0.67,0},
                    new double[]{-0.19+i*0.03,0.67,0},0.108);
            b.rod(METAL,new double[]{0.15,0.67,0},new double[]{0.3,0.67,0},0.085);
            b.rod(METAL,new double[]{0.29,0.67,0},new double[]{0.29,0.85,0},0.024);
            b.rod(METAL,new double[]{0.31,0.68,0},new double[]{0.4,0.68,0},0.025);
            b.box(DARK,-0.14,0.77,-0.07,0.17,0.07,0.14);
            b.rod(METAL,new double[]{0.18,0.7,0},new double[]{0.18,0.79,0},0.012);
            b.rod(WHITE,new double[]{0.18,0.8,-0.015},new double[]{0.18,0.8,0.02},0.045);
            b.rod(DARK,new double[]{0.18,0.8,0.021},new double[]{0.2,0.825,0.021},0.003);
            return;
        }
        if(variant==3) {
            b.box(stone,-0.35,0,-0.35,0.7,0.12,0.7);
            handPump(b,green,0.12);
            lathe(b,METAL,0,-0.35,new double[][]{{0.00001,0.12},{0.15,0.12},
                    {0.18,0.23},{0.16,0.25},{0.11,0.15},{0.00001,0.15}});
            return;
        }
        wellRing(b,stone);
        b.rod(DARK,new double[]{0,0.07,0},new double[]{0,0.08,0},0.47);
        for(int side:new int[]{-1,1}) b.box(WOOD,side*0.66-0.065,0,-0.065,0.13,variant==2 ? 2.15:1.65,0.13);
        b.rod(WOOD,new double[]{-0.75,1.38,0},new double[]{0.77,1.38,0},0.055);
        b.rod(METAL,new double[]{0.78,1.38,0},new double[]{0.78,1.13,0},0.016);
        b.rod(WOOD,new double[]{0.78,1.13,0},new double[]{0.92,1.13,0},0.025);
        for(int i=0;i<12;i++) b.rod(WOOD,new double[]{-0.06+i*0.01,1.38,0},
                new double[]{-0.05+i*0.01,1.38,0},0.071);
        b.rod(fabric(1),new double[]{0,1.32,0},new double[]{0,0.48,0},0.009);
        lathe(b,METAL,0,0,new double[][]{{0.00001,0.27},{0.12,0.27},{0.15,0.5},
                {0.13,0.5},{0.10,0.30},{0.00001,0.30}});
        if(variant==2) {
            for(int side:new int[]{-1,1}) {
                double[][] p={{side*0.95,1.98,-0.85},{0,2.48,-0.85},{0,2.48,0.85},{side*0.95,1.98,0.85}};
                double[] n={side*0.466,0.885,0};
                MeshBuilder mesh=b.part(MaterialLibrary.resolve(new Finish("roof_red",null)));
                if(side<0) mesh.quad(p[0],p[3],p[2],p[1],n,new double[]{1,0,0},new double[]{0,0,1});
                else mesh.quad(p[0],p[1],p[2],p[3],n,new double[]{1,0,0},new double[]{0,0,1});
                double[] down={-n[0],-n[1],0};
                if(side<0) mesh.quad(p[0],p[1],p[2],p[3],down,new double[]{1,0,0},new double[]{0,0,1});
                else mesh.quad(p[0],p[3],p[2],p[1],down,new double[]{1,0,0},new double[]{0,0,1});
            }
        }
    }

    private static void handPump(ModelBuilder b,SurfaceMaterial paint,double y) {
        b.rod(paint,new double[]{0,y,0},new double[]{0,y+0.08,0},0.15);
        b.rod(paint,new double[]{0,y+0.08,0},new double[]{0,y+0.85,0},0.06);
        b.sphere(paint,0,y+0.85,0,0.09,0.11,0.09);
        b.rod(paint,new double[]{0,y+0.57,0},new double[]{0,y+0.57,-0.27},0.035);
        b.rod(paint,new double[]{0,y+0.57,-0.27},new double[]{0,y+0.48,-0.27},0.035);
        b.rod(METAL,new double[]{0,y+0.86,0},new double[]{0.26,y+0.63,0},0.018);
        b.rod(paint,new double[]{0.26,y+0.63,0},new double[]{0.35,y+0.24,0},0.025);
        b.rod(WOOD,new double[]{0.35,y+0.24,-0.07},new double[]{0.35,y+0.24,0.07},0.03);
    }

    private static void wellRing(ModelBuilder b,SurfaceMaterial material) {
        double[][] profile={{0.60,0},{0.60,0.76},{0.65,0.76},{0.65,0.84},
                {0.48,0.84},{0.48,0.15},{0.60,0}};
        lathe(b,material,0,0,profile);
    }

    private static void lathe(ModelBuilder b,SurfaceMaterial material,double x,double z,double[][] profile) {
        MeshBuilder mesh=b.part(material);
        for(int row=0;row<profile.length-1;row++) for(int i=0;i<64;i++) {
            double a=2*Math.PI*i/64,c=2*Math.PI*(i+1)/64;
            double r=profile[row][0],y=profile[row][1],rr=profile[row+1][0],yy=profile[row+1][1];
            double[] p={x+r*Math.cos(a),y,z+r*Math.sin(a)},q={x+rr*Math.cos(a),yy,z+rr*Math.sin(a)};
            double[] s={x+r*Math.cos(c),y,z+r*Math.sin(c)},t={x+rr*Math.cos(c),yy,z+rr*Math.sin(c)};
            double dy=yy-y,dr=rr-r,len=Math.hypot(dy,dr),mid=(a+c)/2;
            mesh.quad(p,q,t,s,new double[]{dy*Math.cos(mid)/len,-dr/len,dy*Math.sin(mid)/len},
                    new double[]{-Math.sin(mid),0,Math.cos(mid)},
                    Math.abs(dy)<1e-9 ? new double[]{Math.cos(mid),0,Math.sin(mid)}:new double[]{0,1,0});
        }
    }

    private static void caravan(ModelBuilder b) {
        SurfaceMaterial body=ModelBuilder.material("Lakokocsi_fenyezes",0.91,0.89,0.81,0.25,false,false);
        SurfaceMaterial trim=ModelBuilder.material("Lakokocsi_diszcsik",0.12,0.26,0.34,0.3,false,false);
        b.box(DARK,-2.7,0.38,-1.05,5.4,0.18,2.1);
        b.shell(body,0,0,0.22,new double[][]{{0.001,0.50,0.001},{2.5,0.50,1.10},
                {2.7,0.68,1.13},{2.7,2.25,1.13},{2.45,2.55,1.10},
                {2.25,2.65,0.95},{0.001,2.65,0.001}});
        b.rod(METAL,new double[]{0,0.36,-1.15},new double[]{0,0.36,1.15},0.045);
        for(int side:new int[]{-1,1}) {
            b.rod(DARK,new double[]{0,0.34,side*1.03},new double[]{0,0.34,side*1.22},0.34);
            b.rod(METAL,new double[]{0,0.34,side*1.225},new double[]{0,0.34,side*1.24},0.19);
            b.rod(DARK,new double[]{0,0.34,side*1.245},new double[]{0,0.34,side*1.255},0.055);
            double z=side<0 ? -1.145:1.125;
            b.box(trim,-2.35,0.9,z,4.7,0.12,0.025);
            for(double x:new double[]{-1.75,0.4,1.65}) {
                b.box(DARK,x-0.42,1.5,z,0.84,0.63,0.035);
                b.box(GLASS,x-0.38,1.54,z+side*0.016,0.76,0.55,0.018);
            }
            for(double x:new double[]{-2.48,2.36})
                b.box(ModelBuilder.material(side<0 ? "Fenyvisszavero_piros":"Fenyvisszavero_borostyan",
                        0.75,side<0 ? 0.06:0.35,0.03,0.2,false,false),x,0.75,z,0.12,0.12,0.035);
        }
        b.box(DARK,-1.05,0.56,1.14,0.70,1.57,0.025);
        b.box(body,-1.01,0.59,1.17,0.62,1.5,0.025);
        b.box(GLASS,-0.94,1.5,1.20,0.48,0.46,0.02);
        b.box(METAL,-0.5,1.18,1.22,0.055,0.10,0.03);
        b.box(DARK,-1.1,0.24,1.14,0.85,0.05,0.38);
        b.box(WHITE,-0.45,2.64,-0.35,0.9,0.10,0.7);
        b.box(GLASS,-0.38,2.74,-0.28,0.76,0.025,0.56);
        for(int side:new int[]{-1,1}) {
            b.rod(METAL,new double[]{2.3,0.45,side*0.85},new double[]{3.9,0.43,0},0.045);
            for(int x:new int[]{-1,1}) b.rod(METAL,new double[]{x*2.2,0.42,side*0.86},
                    new double[]{x*2.28,0.04,side*0.95},0.022);
        }
        b.rod(METAL,new double[]{3.85,0.43,0},new double[]{4.13,0.43,0},0.065);
        b.rod(METAL,new double[]{3.5,0.44,0},new double[]{3.5,0.12,0},0.025);
        b.rod(DARK,new double[]{3.5,0.12,-0.055},new double[]{3.5,0.12,0.055},0.12);
        b.box(WHITE,2.58,0.55,-0.48,0.55,0.67,0.96);
    }

    private static void column(ModelBuilder b,int variant) {
        SurfaceMaterial material=MaterialLibrary.resolve(new Finish(variant==0 ? "oak_wood":"stone",null));
        if(variant==0) {
            b.box(material,-0.18,0,-0.18,0.36,0.1,0.36);
            b.box(material,-0.15,0.1,-0.15,0.3,2.2,0.3);
            b.box(material,-0.18,2.3,-0.18,0.36,0.1,0.36);
        } else {
            b.shell(material,0,0,1,new double[][]{
                    {0.00001,0,0.00001},{0.18,0,0.18},{0.18,0.08,0.18},
                    {0.15,0.12,0.15},{0.15,2.28,0.15},{0.18,2.32,0.18},
                    {0.18,2.4,0.18},{0.00001,2.4,0.00001}});
        }
    }

    private static SurfaceMaterial fabric(int v) {
        double[][] colors={{0.28,0.38,0.43},{0.62,0.53,0.42},{0.34,0.40,0.31},{0.31,0.34,0.53},{0.64,0.40,0.25}};
        double[] c=colors[v%colors.length];
        return ModelBuilder.material("Karpit_"+v,c[0],c[1],c[2],0.92,false,false);
    }
    private static void legs(ModelBuilder b,double w,double d,double h,SurfaceMaterial mat) {
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1})
            b.box(mat,x*(w/2-0.045)-0.025,0,z*(d/2-0.045)-0.025,0.05,h,0.05);
    }
    private static void chair(ModelBuilder b,int v) {
        double w=v==4 ? 1.2 : 0.48, d=0.46, seat=v==2 ? 0.76 : 0.45;
        var mat=v==0 || v==4 ? WOOD : fabric(v);
        if(v==3) {
            b.rod(METAL,new double[]{0,0.06,0},new double[]{0,seat,0},0.045);
            for(int i=0;i<5;i++) { double a=2*Math.PI*i/5; double x=0.3*Math.cos(a),z=0.3*Math.sin(a);
                b.rod(METAL,new double[]{0,0.08,0},new double[]{x,0.05,z},0.018);
                b.sphere(DARK,x,0.035,z,0.035,0.035,0.035);
            }
        } else legs(b,w,d,seat,WOOD);
        b.box(mat,-w/2,seat,-d/2,w,0.065,d);
        if(v!=4) {
            for(int side:new int[]{-1,1}) b.box(v==0 ? WOOD : METAL,side*(w/2-0.035)-0.015,seat,-d/2,0.03,0.43,0.035);
            b.box(mat,-w/2,seat+0.18,-d/2-0.01,w,0.28,0.065);
        } else b.box(WOOD,-w/2,seat+0.18,-d/2,w,0.2,0.04);
        if(v==2) for(int side:new int[]{-1,1}) b.rod(METAL,new double[]{side*0.19,0.3,-0.18},new double[]{side*0.19,0.3,0.18},0.012);
    }
    private static void table(ModelBuilder b,int v) {
        double w=v==1 ? 1.8 : v==3 ? 1.1 : 1.4,d=v==4 ? 0.65 : v==3 ? 0.6 : 0.85,h=v==3 ? 0.42 : 0.75;
        if(v==2) {
            b.rod(WOOD,new double[]{0,h-0.04,0},new double[]{0,h,0},0.6);
            b.rod(METAL,new double[]{0,0.06,0},new double[]{0,h-0.04,0},0.06);
            b.rod(METAL,new double[]{0,0,0},new double[]{0,0.04,0},0.35);
        } else {
            legs(b,w-0.06,d-0.06,h-0.04,v==4 ? METAL : WOOD);
            b.box(WOOD,-w/2,h-0.04,-d/2,w,0.04,d);
            if(v==4) b.box(WHITE,w/2-0.38,0.1,-d/2+0.03,0.3,0.6,d-0.06);
        }
    }
    private static void sofa(ModelBuilder b,int v,boolean armchair) {
        double w=armchair ? (v==3 ? 0.6 : 0.86) : v==1 ? 2.25 : v==4 ? 1.45 : v>=2 ? 2.5 : 1.7;
        double d=armchair ? 0.85 : 0.92;
        var cloth=fabric(v);
        legs(b,w-0.12,d-0.12,0.16,WOOD);
        b.box(cloth,-w/2,0.14,-d/2,w,0.22,d);
        int cushions=armchair ? 1 : v==1 || v==2 || v==3 ? 3 : 2;
        for(int i=0;i<cushions;i++) {
            double cw=(w-0.2)/cushions;
            b.box(cloth,-w/2+0.1+i*cw+0.008,0.36,-d/2+0.16,cw-0.016,0.13,d-0.18);
            if(!armchair || v!=3) b.box(cloth,-w/2+0.1+i*cw+0.008,0.49,-d/2+0.015,cw-0.016,0.4,0.18);
        }
        if(!armchair || v!=3) for(int side:new int[]{-1,1}) b.box(cloth,side<0 ? -w/2 : w/2-0.12,0.3,-d/2,0.12,0.34,d);
        if(!armchair && (v==2 || v==3)) {
            double x=v==2 ? -w/2 : w/2-0.85;
            b.box(cloth,x,0.16,d/2,0.85,0.2,0.85);
            b.box(cloth,x+0.02,0.36,d/2,0.81,0.13,0.83);
            for(double xx:new double[]{x+0.08,x+0.77}) b.box(WOOD,xx,0,d/2+0.72,0.05,0.16,0.05);
        }
    }
    private static void kitchen(ModelBuilder b,int v) {
        if(v<4) {
            Furniture f=new Furniture(); f.cabinet=new Cabinet(); f.width=v==3 ? 1.6 : v==0 ? 0.6 : 0.8;
            f.depth=v==2 ? 0.32 : v==3 ? 0.85 : 0.6; f.height=v==2 ? 0.72 : 0.9;
            f.cabinet.columns=v==3 ? 2 : 1; f.cabinet.shelves=1;
            if(v==1) { f.cabinet.drawers=3; f.cabinet.doors=false; f.cabinet.shelves=0; }
            ModelData md=CabinetGeometry.build(f);
            for(var part:md.parts) b.part(part.material).append(part.mesh);
            if(v!=2) b.box(WOOD,-f.width/2-0.015,f.height,-f.depth/2-0.015,f.width+0.03,0.03,f.depth+0.03);
        } else if(v==4) {
            b.box(WOOD,-0.16,0,-0.11,0.32,0.02,0.22);
            b.rod(WOOD,new double[]{0.16,0.01,0},new double[]{0.28,0.01,0},0.025);
        } else if(v==5) {
            b.shell(METAL,0,0,1,new double[][]{{0.001,0,0.001},{0.12,0,0.12},{0.13,0.18,0.13},{0.115,0.18,0.115},{0.11,0.02,0.11},{0.001,0.02,0.001}});
            for(int side:new int[]{-1,1}) b.rod(DARK,new double[]{side*0.13,0.13,0},new double[]{side*0.2,0.13,0},0.018);
            b.rod(METAL,new double[]{0,0.185,0},new double[]{0,0.2,0},0.135);
            b.sphere(DARK,0,0.22,0,0.025,0.02,0.025);
        } else {
            b.shell(METAL,0,0,1,new double[][]{{0.001,0,0.001},{0.08,0,0.08},{0.085,0.04,0.085},{0.07,0.21,0.07},{0.001,0.23,0.001}});
            b.rod(DARK,new double[]{0,0.04,-0.09},new double[]{0,0.2,-0.12},0.018);
            b.rod(METAL,new double[]{0,0.15,0.06},new double[]{0,0.2,0.14},0.022);
        }
    }
    private static void sink(ModelBuilder b,int v) {
        double w=v==0 ? 0.55 : 0.9;
        int count=v==1 ? 2 : 1;
        for(int i=0;i<count;i++) {
            double x=count==2 ? (i==0 ? -0.22 : 0.22) : v==2 ? -0.18 : 0,rx=count==2 ? 0.2 : 0.235;
            b.shell(METAL,x,0,0.35,new double[][]{{0.001,0,0.001},{rx*0.8,0,0.15},{rx,0.18,0.22},{rx+0.025,0.19,0.245},
                    {rx-0.015,0.19,0.205},{rx*0.75,0.02,0.14},{0.001,0.02,0.001}});
            b.rod(DARK,new double[]{x,0.02,0},new double[]{x,0.022,0},0.02);
        }
        if(v==2) {
            b.box(METAL,0.08,0.18,-0.245,w/2-0.08,0.01,0.49);
            for(int i=0;i<8;i++) b.box(DARK,0.12+i*0.037,0.191,-0.18,0.004,0.002,0.36);
        }
        b.rod(METAL,new double[]{0,0.19,-0.23},new double[]{0,0.48,-0.23},0.018);
        b.rod(METAL,new double[]{0,0.48,-0.23},new double[]{0,0.48,0.03},0.018);
        b.rod(METAL,new double[]{0,0.48,0.03},new double[]{0,0.43,0.03},0.018);
    }
    private static void appliance(ModelBuilder b,int v) {
        if(v==5) {
            b.box(GLASS,-0.3,0,-0.26,0.6,0.018,0.52);
            for(double x:new double[]{-0.16,0.16}) for(double z:new double[]{-0.13,0.13})
                b.rod(DARK,new double[]{x,0.019,z},new double[]{x,0.022,z},0.09);
            return;
        }
        if(v==6) {
            b.box(METAL,-0.3,0,-0.25,0.6,0.06,0.5);
            b.box(METAL,-0.12,0.06,-0.15,0.24,0.5,0.22);
            b.box(DARK,-0.26,-0.002,-0.21,0.52,0.004,0.4); return;
        }
        double w=v==1 ? 0.45 : v==2 ? 0.49 : v==7 ? 0.26 : 0.6;
        double h=v==2 ? 0.29 : v==7 ? 0.36 : v==3 ? 0.6 : 0.85,d=v==2 ? 0.38 : v==7 ? 0.35 : 0.6;
        b.box(v<2 ? WHITE : METAL,-w/2,0,-d/2,w,h,d);
        if(v<2) b.box(WHITE,-w/2+0.01,0.03,d/2+0.002,w-0.02,h-0.13,0.018);
        else {
            b.box(GLASS,-w/2+0.025,h*0.12,d/2+0.002,w*0.7,h*0.65,0.018);
            b.rod(METAL,new double[]{-w*0.34,h*0.82,d/2+0.04},new double[]{w*0.2,h*0.82,d/2+0.04},0.012);
        }
        for(int i=0;i<4;i++) b.rod(DARK,new double[]{w/2-0.06,h*0.5+i*h*0.1,d/2+0.005},
                new double[]{w/2-0.06,h*0.5+i*h*0.1,d/2+0.012},0.012);
        if(v==4) for(double x:new double[]{-0.16,0.16}) for(double z:new double[]{-0.16,0.16})
            b.rod(DARK,new double[]{x,h,z},new double[]{x,h+0.015,z},0.09);
        if(v==7) { b.box(DARK,-0.1,0,-0.02,0.2,0.02,0.25); b.rod(METAL,new double[]{0,0.25,0.21},new double[]{0,0.16,0.21},0.012); }
    }
    private static void fridge(ModelBuilder b,int v) {
        double w=v>=3 ? 0.92 : v==2 ? 0.55 : 0.6,h=v==0 ? 1.85 : v==1 ? 1.5 : v==2 ? 0.85 : 1.8,d=v>=3 ? 0.72 : 0.64;
        b.box(v>=3 ? METAL : WHITE,-w/2,0,-d/2,w,h,d);
        int cols=v>=3 ? 2 : 1,rows=v==0 || v==4 ? 2 : 1;
        for(int col=0;col<cols;col++) for(int row=0;row<rows;row++) {
            double low=rows==1 ? 0.04 : row==0 ? 0.04 : h*0.35;
            double top=rows==1 ? h-0.01 : row==0 ? h*0.35-0.006 : h-0.01;
            double x=-w/2+col*w/cols+0.004,cw=w/cols-0.008;
            b.box(v>=3 ? METAL : WHITE,x,low,d/2+0.002,cw,top-low,0.025);
            double hx=cols==1 ? x+0.04 : col==0 ? x+cw-0.04 : x+0.04;
            b.rod(METAL,new double[]{hx,low+(top-low)*0.35,d/2+0.055},new double[]{hx,low+(top-low)*0.75,d/2+0.055},0.012);
        }
        if(v==3) { b.box(GLASS,-w*0.4,h*0.49,d/2+0.03,0.2,0.28,0.01); b.box(DARK,-w*0.38,h*0.49,d/2+0.04,0.16,0.025,0.06); }
    }

    private static final SurfaceMaterial SCREEN=ModelBuilder.material("Kepernyo",0.035,0.12,0.19,0.12,false,false);
    private static final SurfaceMaterial PAPER=ModelBuilder.material("Papir",0.88,0.85,0.74,0.9,false,false);

    private static void display(ModelBuilder b,double w,double h,boolean computer) {
        b.box(DARK,-w*0.23,0,-0.11,w*0.46,0.018,0.22);
        b.box(METAL,-0.025,0.018,-0.025,0.05,0.13,0.05);
        b.box(DARK,-w/2,0.14,-0.025,w,h,0.045);
        b.box(SCREEN,-w/2+0.012,0.152,0.021,w-0.024,h-0.024,0.003);
        if(computer) b.box(METAL,-w/2,0.14,0.024,w,0.026,0.002);
        b.rod(WHITE,new double[]{w/2-0.023,0.147,0.025},new double[]{w/2-0.023,0.147,0.027},0.002);
    }

    private static void speaker(ModelBuilder b,double x,double w,double h,double d) {
        b.box(DARK,x-w/2,0,-d/2,w,h,d);
        for(int i=0;i<3;i++) {
            double r=i==2 ? w*0.14 : w*0.32,y=h*(0.2+i*0.3);
            b.rod(METAL,new double[]{x,y,d/2},new double[]{x,y,d/2+0.008},r);
            b.rod(DARK,new double[]{x,y,d/2+0.009},new double[]{x,y,d/2+0.014},r*0.84);
            b.sphere(GLASS,x,y,d/2+0.015,r*0.32,r*0.32,0.012);
        }
    }

    private static void electronics(ModelBuilder b,int v) {
        if(v<3) { double w=new double[]{0.97,1.23,1.45}[v]; display(b,w,w*9/16,false); }
        else if(v==3) {
            for(int i=0;i<3;i++) {
                b.box(METAL,-0.215,i*0.105,-0.16,0.43,0.1,0.32);
                b.box(GLASS,-0.17,i*0.105+0.035,0.161,0.19,0.03,0.003);
                b.rod(DARK,new double[]{0.14,i*0.105+0.05,0.16},new double[]{0.14,i*0.105+0.05,0.177},0.022);
            }
            speaker(b,-0.38,0.22,0.38,0.25); speaker(b,0.38,0.22,0.38,0.25);
        } else if(v==4) { speaker(b,-0.3,0.23,0.85,0.28); speaker(b,0.3,0.23,0.85,0.28); }
        else if(v==5) {
            b.box(DARK,-0.45,0,-0.055,0.9,0.065,0.11);
            for(int i=0;i<36;i++) b.box(METAL,-0.43+i*0.024,0.01,0.056,0.002,0.042,0.002);
        } else {
            b.box(WHITE,-0.14,0,-0.1,0.28,0.055,0.2);
            b.box(DARK,-0.135,0.024,0.101,0.27,0.006,0.002);
            for(int i=0;i<10;i++) b.box(DARK,0.141,0.008+i*0.004,-0.08,0.002,0.002,0.15);
        }
    }

    private static void computer(ModelBuilder b,int v) {
        if(v==1 || v==5) { display(b,v==5 ? 0.62 : 0.61,0.35,true); return; }
        if(v==0) {
            b.box(DARK,-0.1,0,-0.21,0.2,0.44,0.42);
            b.box(METAL,-0.088,0.015,0.211,0.176,0.41,0.004);
            for(int i=0;i<16;i++) b.box(DARK,-0.07,0.03+i*0.016,0.216,0.14,0.005,0.002);
            b.rod(SCREEN,new double[]{0.06,0.4,0.216},new double[]{0.06,0.4,0.22},0.009);
        } else if(v==2) {
            b.box(METAL,-0.18,0,-0.12,0.36,0.014,0.24);
            b.box(DARK,-0.18,0.014,-0.128,0.36,0.225,0.012);
            b.box(SCREEN,-0.168,0.026,-0.115,0.336,0.201,0.002);
            keys(b,-0.15,0.015,-0.09,0.3,0.1);
            b.box(GLASS,-0.045,0.015,0.035,0.09,0.002,0.055);
        } else if(v==3) {
            b.box(DARK,-0.22,0,-0.075,0.44,0.018,0.15);
            keys(b,-0.21,0.018,-0.065,0.42,0.125);
            b.sphere(DARK,0.28,0.025,0,0.032,0.025,0.055);
            b.box(METAL,0.278,0.044,-0.025,0.004,0.005,0.018);
        } else {
            b.box(WHITE,-0.2,0,-0.16,0.4,0.19,0.32);
            b.box(DARK,-0.19,0.19,-0.15,0.38,0.025,0.3);
            b.box(DARK,-0.15,0.045,0.161,0.3,0.035,0.004);
            b.box(PAPER,-0.105,0.047,0.16,0.21,0.001,0.16);
            b.box(SCREEN,0.11,0.16,0.161,0.06,0.02,0.002);
        }
    }

    private static void keys(ModelBuilder b,double x,double y,double z,double w,double d) {
        for(int row=0;row<5;row++) for(int col=0;col<14;col++)
            b.box(WHITE,x+col*w/14,y,z+row*d/5,w/14*0.8,0.003,d/5*0.75);
    }

    private static void book(ModelBuilder b,double x,double y,double z,double w,double h,double d,int color,boolean flat) {
        SurfaceMaterial cover=fabric(color);
        if(flat) {
            b.box(PAPER,x,y+0.003,z+0.004,w,h-0.006,d-0.008);
            b.box(cover,x-0.003,y,z,w+0.006,0.003,d);
            b.box(cover,x-0.003,y+h-0.003,z,w+0.006,0.003,d);
            b.box(cover,x-0.003,y,z,0.003,h,d);
        } else {
            b.box(PAPER,x+0.003,y+0.004,z,w-0.006,h-0.008,d-0.004);
            b.box(cover,x,y,z,0.003,h,d);
            b.box(cover,x+w-0.003,y,z,0.003,h,d);
            b.box(cover,x,y,z+d-0.004,w,h,0.004);
            for(int i=0;i<3;i++) b.box(METAL,x+0.005,y+h*(0.55+i*0.12),z+d,w-0.01,0.005,0.001);
        }
    }

    private static void books(ModelBuilder b,int v) {
        if(v==0) book(b,-0.018,0,-0.1,0.036,0.25,0.2,0,false);
        else if(v==1) {
            double x=-0.32;
            for(int i=0;i<16;i++) { double w=0.025+(i%4)*0.008;
                book(b,x,0,-0.1,w,0.2+(i%5)*0.018,0.18+(i%3)*0.015,i,false); x+=w+0.003; }
        } else if(v==2) {
            for(int i=0;i<5;i++) book(b,-0.11+(i%2)*0.012,i*0.035,-0.15,0.22,0.035,0.3,i,true);
        } else {
            book(b,-0.22,0,-0.15,0.22,0.025,0.3,2,true);
            book(b,0,0,-0.15,0.22,0.025,0.3,2,true);
            b.box(PAPER,-0.216,0.025,-0.146,0.212,0.001,0.292);
            b.box(PAPER,0.004,0.025,-0.146,0.212,0.001,0.292);
            for(int i=0;i<15;i++) for(int side:new int[]{-1,1})
                b.box(DARK,side<0 ? -0.2 : 0.02,0.026,-0.13+i*0.017,0.18,0.0003,0.001);
        }
    }

    private static void heating(ModelBuilder b,int v) {
        double w=v==4 ? 1.2 : v==3 ? 0.6 : v==0 ? 0.72 : v==7 ? 0.5 : v==8 ? 0.9 : 0.8;
        double h=v==0 ? 0.62 : v==8 ? 0.45 : 0.6,d=v==0 ? 0.25 : v==7 ? 0.18 : v==8 ? 0.055 : 0.12;
        if(v==0 || v==6 || v==8) {
            b.box(WHITE,-w/2,0,-d/2,w,h,d);
            if(v==8) {
                for(int i=0;i<20;i++) b.box(DARK,-w/2+0.03+i*0.042,h-0.012,-0.012,0.025,0.013,0.002);
                b.box(SCREEN,w/2-0.075,0.025,d/2+0.001,0.055,0.022,0.002);
            } else {
                b.box(DARK,-w/2+0.025,h*0.58,d/2+0.001,w-0.05,h*0.34,0.002);
                for(int i=0;i<10;i++) b.box(WHITE,-w/2+0.025,h*0.6+i*h*0.03,d/2+0.004,w-0.05,0.008,0.004);
            }
            b.rod(DARK,new double[]{w*0.36,h*0.25,d/2},new double[]{w*0.36,h*0.25,d/2+0.012},0.018);
            if(v==0) b.rod(METAL,new double[]{0,h*0.5,-d/2},new double[]{0,h*0.5,-d/2-0.12},0.065);
        } else if(v==3 || v==4) {
            for(double z:new double[]{-0.04,0.04}) {
                b.box(WHITE,-w/2,0,z-0.012,w,h,0.024);
                for(int i=0;i<(int)(w/0.025);i++) b.box(WHITE,-w/2+0.01+i*0.025,0.015,z+0.012,0.007,h-0.03,0.004);
            }
            for(int i=0;i<(int)(w/0.025);i++) b.box(METAL,-w/2+0.01+i*0.025,h-0.015,-0.028,0.003,0.01,0.056);
        } else {
            int count=v==7 ? 9 : 12;
            SurfaceMaterial mat=v==2 ? ModelBuilder.material("Ontottvas",0.36,0.38,0.36,0.7,true,false) : WHITE;
            for(int i=0;i<count;i++) {
                double x=-w/2+(i+0.5)*w/count;
                if(v==2 || v==5) {
                    for(double z:new double[]{-d*0.32,d*0.32})
                        b.rod(mat,new double[]{x,0.04,z},new double[]{x,h-0.04,z},w/count*0.3);
                    b.rod(mat,new double[]{x,0.04,-d*0.32},new double[]{x,0.04,d*0.32},0.018);
                    b.rod(mat,new double[]{x,h-0.04,-d*0.32},new double[]{x,h-0.04,d*0.32},0.018);
                } else b.box(mat,x-w/count*0.36,0,-d/2,w/count*0.72,h,d);
            }
            for(double y:new double[]{0.04,h-0.04}) b.rod(mat,new double[]{-w/2,y,0},new double[]{w/2,y,0},0.022);
        }
        if(v!=0 && v!=6 && v!=8) {
            b.rod(METAL,new double[]{w/2,0.08,0},new double[]{w/2+0.06,0.08,0},0.012);
            b.rod(WHITE,new double[]{w/2+0.06,0.08,0},new double[]{w/2+0.06,0.14,0},0.025);
        }
        if(v==7) for(double x:new double[]{-0.18,0.18}) b.box(DARK,x-0.025,0,-0.18,0.05,0.035,0.36);
    }

    private static void fanRing(ModelBuilder b,double y,double z,double r) {
        MeshBuilder mesh=b.part(METAL);
        int[][] vertices=new int[49][7];
        for(int i=0;i<=48;i++) for(int j=0;j<=6;j++) {
            double a=2*Math.PI*(i%48)/48,t=2*Math.PI*(j%6)/6,c=Math.cos(a),s=Math.sin(a),ct=Math.cos(t),st=Math.sin(t);
            double radius=r+0.0015*ct;
            vertices[i][j]=mesh.vertex(radius*c,y+radius*s,z+0.0015*st,c*ct,s*ct,st,a*r,t*0.0015);
        }
        for(int i=0;i<48;i++) for(int j=0;j<6;j++)
            mesh.quad(vertices[i][j],vertices[i+1][j],vertices[i+1][j+1],vertices[i][j+1]);
    }

    private static void fan(ModelBuilder b,int v) {
        if(v==2) {
            b.rod(WHITE,new double[]{0,0.08,0},new double[]{0,0.37,0},0.025);
            b.rod(WHITE,new double[]{0,0.36,0},new double[]{0,0.4,0},0.1);
            b.sphere(METAL,0,0.065,0,0.095,0.065,0.095);
            b.sphere(WHITE,0,0.025,0,0.06,0.025,0.06);
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2,c=Math.cos(a),s=Math.sin(a);
                b.part(WOOD).box(new double[]{0.08*c+0.055*s,0.075,0.08*s-0.055*c},
                        new double[]{c,0,s},new double[]{0,1,0},new double[]{-s,0,c},0.49,0.012,0.11);
            }
            return;
        }
        if(v==4) {
            b.rod(DARK,new double[]{0,0,0},new double[]{0,0.025,0},0.15);
            b.box(WHITE,-0.065,0.025,-0.06,0.13,0.85,0.12);
            for(int i=0;i<32;i++) b.box(DARK,-0.045,0.09+i*0.022,0.061,0.09,0.01,0.002);
            return;
        }
        double y=v==3 ? 1.15 : v==1 ? 0.23 : 0.29,r=v==3 ? 0.2 : 0.16;
        if(v==1) {
            b.box(WHITE,-0.05,0,-0.15,0.1,0.15,0.025);
            b.rod(WHITE,new double[]{0,0.08,-0.13},new double[]{0,y,-0.045},0.018);
        } else {
            b.rod(WHITE,new double[]{0,0,0},new double[]{0,0.025,0},v==3 ? 0.22 : 0.12);
            b.rod(METAL,new double[]{0,0.025,0},new double[]{0,y,-0.025},0.018);
        }
        b.rod(DARK,new double[]{0,y,-0.09},new double[]{0,y,0.025},0.04);
        for(double z:new double[]{-0.04,0.045}) {
            for(double f:new double[]{0.33,0.66,1}) fanRing(b,y,z,r*f);
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6;
                b.rod(METAL,new double[]{0,y,z},new double[]{r*Math.cos(a),y+r*Math.sin(a),z},0.0012);
            }
        }
        for(int i=0;i<3;i++) {
            double a=i*2*Math.PI/3,c=Math.cos(a),s=Math.sin(a);
            b.part(WHITE).box(new double[]{0.025*c-0.025*s,y+0.025*s+0.025*c,-0.005},
                    new double[]{c,s,0},new double[]{-s,c,0},new double[]{0,0,1},r*0.75,0.05,0.008);
        }
        b.sphere(WHITE,0,y,0.035,0.032,0.032,0.015);
    }

    private static void bed(ModelBuilder b,int v) {
        double w=new double[]{0.9,1.6,1.8,0.8}[v],d=v==3 ? 1.6 : 2;
        SurfaceMaterial body=v==2 ? fabric(1) : WOOD;
        legs(b,w+0.08,d+0.08,0.18,body);
        b.box(body,-w/2-0.04,0.16,-d/2-0.04,w+0.08,0.19,d+0.08);
        b.box(WHITE,-w/2,0.35,-d/2,w,0.18,d);
        b.box(body,-w/2-0.04,0,-d/2-0.08,w+0.08,v==3 ? 0.75 : 1,0.08);
        b.box(fabric(v),-w/2-0.015,0.53,-d/2+0.48,w+0.03,0.045,d-0.46);
        int pillows=w>1.2 ? 2 : 1;
        for(int i=0;i<pillows;i++) {
            double x=-w/2+(i+0.5)*w/pillows;
            b.sphere(WHITE,x,0.57,-d/2+0.24,w/pillows*0.42,0.075,0.18);
        }
        b.box(fabric((v+2)%5),-w/2-0.02,0.58,d/2-0.45,w+0.04,0.012,0.35);
    }

    private static final SurfaceMaterial SHADE=ModelBuilder.material("Opal_lampabura",0.98,0.94,0.8,0.3,false,false);
    private static void lamp(ModelBuilder b,int v) {
        if(v==2) {
            b.rod(METAL,new double[]{0,0.065,0},new double[]{0,0.08,0},0.2);
            b.sphere(SHADE,0,0.04,0,0.19,0.04,0.19);
        } else if(v==3) {
            b.rod(METAL,new double[]{0,0,0},new double[]{0,0.025,0},0.16);
            b.rod(METAL,new double[]{0,0.025,0},new double[]{0,1.35,0},0.013);
            b.shell(SHADE,0,0,1,new double[][]{{0.001,1.3,0.001},{0.23,1.3,0.23},{0.15,1.65,0.15},{0.001,1.65,0.001}});
            b.sphere(WHITE,0,1.4,0,0.045,0.06,0.045);
        } else if(v==4) {
            b.box(METAL,-0.045,0,-0.025,0.09,0.16,0.025);
            b.rod(METAL,new double[]{0,0.08,0},new double[]{0,0.18,0.13},0.012);
            b.rod(METAL,new double[]{0,0.18,0.13},new double[]{0,0.13,0.25},0.012);
            b.rod(DARK,new double[]{0,0.13,0.25},new double[]{0,0.08,0.28},0.05);
            b.rod(SHADE,new double[]{0,0.08,0.28},new double[]{0,0.074,0.284},0.043);
        } else {
            double top=v==0 ? 0.65 : 0.85;
            b.rod(METAL,new double[]{0,top-0.025,0},new double[]{0,top,0},0.09);
            b.rod(METAL,new double[]{0,0.08,0},new double[]{0,top-0.025,0},0.012);
            int count=v==0 ? 5 : 3;
            for(int i=0;i<count;i++) {
                double a=2*Math.PI*i/count,x=0.3*Math.cos(a),z=0.3*Math.sin(a);
                double y=v==0 ? 0.12 : 0.1+i*0.16;
                if(v==0) {
                    b.rod(METAL,new double[]{0,0.14,0},new double[]{x,0.04,z},0.012);
                    b.rod(METAL,new double[]{x,0.04,z},new double[]{x,y,z},0.012);
                    b.sphere(SHADE,x,y+0.07,z,0.07,0.085,0.07);
                } else {
                    b.rod(DARK,new double[]{0,top-0.03,0},new double[]{x,y+0.17,z},0.003);
                    b.shell(SHADE,x,z,1,new double[][]{{0.001,y,0.001},{0.1,y,0.1},{0.035,y+0.18,0.035},{0.001,y+0.18,0.001}});
                }
            }
            // The suspended model's bottom is the lowest shade, not the ceiling attachment.
            b.sphere(METAL,0,0.035,0,0.045,0.035,0.045);
        }
    }

    private static void hook(ModelBuilder b,double x,double y,double z) {
        b.rod(METAL,new double[]{x,y,z},new double[]{x,y,z+0.06},0.008);
        b.rod(METAL,new double[]{x,y,z+0.06},new double[]{x,y+0.035,z+0.06},0.008);
    }

    private static void hall(ModelBuilder b,int v) {
        if(v==0) {
            b.rod(WOOD,new double[]{0,0,0},new double[]{0,1.65,0},0.028);
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2,x=Math.cos(a),z=Math.sin(a);
                b.rod(WOOD,new double[]{0,0.15,0},new double[]{x*0.28,0.018,z*0.28},0.018);
                b.rod(METAL,new double[]{0,1.48,0},new double[]{x*0.2,1.72,z*0.2},0.012);
            }
        } else if(v==1) {
            Furniture f=new Furniture(); f.cabinet=new Cabinet(); f.width=0.8; f.depth=0.3; f.height=0.95;
            f.cabinet.columns=2; f.cabinet.shelves=3;
            for(var part:CabinetGeometry.build(f).parts) b.part(part.material).append(part.mesh);
        } else if(v==2) {
            b.box(WOOD,-0.55,0,-0.1,1.1,1.9,0.035);
            b.box(WOOD,-0.55,1.8,-0.065,1.1,0.035,0.3);
            b.box(WOOD,-0.55,0.42,-0.065,1.1,0.04,0.38);
            for(double x:new double[]{-0.5,0.5}) b.box(WOOD,x-0.025,0,-0.065,0.05,0.42,0.35);
            for(int i=0;i<5;i++) hook(b,-0.4+i*0.2,1.5,-0.065);
            b.box(METAL,-0.48,0.08,-0.045,0.96,0.018,0.3);
        } else if(v==3) {
            for(double x:new double[]{-0.36,0.36}) for(double z:new double[]{-0.14,0.14})
                b.box(METAL,x-0.012,0,z-0.012,0.024,0.5,0.024);
            for(double y:new double[]{0.06,0.25,0.44}) for(int i=0;i<5;i++)
                b.box(WOOD,-0.38,y,-0.15+i*0.065,0.76,0.015,0.035);
        } else {
            for(double x:new double[]{-0.5,0.5}) {
                b.rod(METAL,new double[]{x,0,-0.25},new double[]{x,0,0.25},0.018);
                b.rod(METAL,new double[]{x,0,0},new double[]{x,1.65,0},0.018);
            }
            b.rod(METAL,new double[]{-0.5,1.65,0},new double[]{0.5,1.65,0},0.018);
            for(int i=0;i<5;i++) {
                double x=-0.35+i*0.175;
                b.rod(WOOD,new double[]{x,1.57,0},new double[]{x-0.15,1.43,0},0.008);
                b.rod(WOOD,new double[]{x-0.15,1.43,0},new double[]{x+0.15,1.43,0},0.008);
                b.rod(WOOD,new double[]{x+0.15,1.43,0},new double[]{x,1.57,0},0.008);
                hook(b,x,1.57,-0.03);
            }
        }
    }

    private static void electrical(ModelBuilder b,int v) {
        if(v>=4) { antenna(b,v==5); return; }
        if(v==3) {
            b.box(DARK,-0.025,0,-0.015,0.05,0.08,0.03);
            b.box(METAL,-0.01,0.08,-0.006,0.02,0.018,0.012);
            b.rod(DARK,new double[]{0,0.065,0},new double[]{0,0.18,0},0.004);
            b.rod(SCREEN,new double[]{0,0.02,0.015},new double[]{0,0.02,0.017},0.003);
        } else if(v==2) {
            b.box(WHITE,-0.22,0,-0.09,0.44,0.6,0.18);
            b.box(DARK,-0.19,0.02,0.091,0.38,0.12,0.008);
            b.box(SCREEN,-0.06,0.24,0.091,0.12,0.055,0.003);
            for(int i=0;i<18;i++) for(int side:new int[]{-1,1})
                b.box(METAL,side<0 ? -0.23 : 0.22,0.03+i*0.03,-0.075,0.01,0.007,0.15);
            for(int i=0;i<5;i++) b.rod(DARK,new double[]{-0.14+i*0.07,0,-0.015},new double[]{-0.14+i*0.07,-0.035,-0.015},0.009);
        } else {
            double w=v==0 ? 0.45 : 0.6,h=v==0 ? 0.65 : 0.85,d=v==0 ? 0.16 : 0.22;
            b.box(WHITE,-w/2,0,-d/2,w,h,d);
            b.box(DARK,-w/2+0.025,0.15,d/2+0.001,w-0.05,h-0.18,0.004);
            b.box(WHITE,-0.1,h*0.48,d/2+0.006,0.2,0.2,0.015);
            b.box(SCREEN,-0.07,h*0.48+0.09,d/2+0.022,0.14,0.045,0.002);
            for(int i=0;i<6;i++) {
                b.box(WHITE,-0.12+i*0.04,0.19,d/2+0.007,0.035,0.08,0.018);
                b.box(DARK,-0.115+i*0.04,0.22,d/2+0.026,0.025,0.02,0.006);
            }
            b.box(ModelBuilder.material("Ajto_uveg",0.65,0.72,0.74,0.12,false,true),
                    -w/2+0.018,0.14,d/2+0.04,w-0.036,h-0.16,0.004);
            if(v==1) b.box(WHITE,-w/2-0.02,h,-d/2-0.02,w+0.04,0.025,d+0.04);
        }
    }

    private static final SurfaceMaterial STONE=ModelBuilder.material("Kerti_termesko",0.46,0.43,0.37,0.95,false,false);
    private static final SurfaceMaterial THATCH=ModelBuilder.material("Palmafonat",0.68,0.46,0.22,0.98,false,false);
    private static final SurfaceMaterial THATCH_LIGHT=ModelBuilder.material("Napfakult_nad",0.79,0.59,0.32,0.95,false,false);
    private static final SurfaceMaterial FRUIT_RED=ModelBuilder.material("Gyumolcs_piros",0.78,0.075,0.035,0.42,false,false);
    private static final SurfaceMaterial FRUIT_CHERRY=ModelBuilder.material("Gyumolcs_meggy",0.52,0.025,0.075,0.42,false,false);
    private static final SurfaceMaterial FRUIT_APPLE=ModelBuilder.material("Gyumolcs_alma",0.88,0.18,0.075,0.42,false,false);
    private static final SurfaceMaterial FRUIT_GOLD=ModelBuilder.material("Gyumolcs_arany",0.94,0.54,0.055,0.42,false,false);
    private static final SurfaceMaterial FRUIT_ORANGE=ModelBuilder.material("Gyumolcs_narancs",0.94,0.27,0.035,0.42,false,false);
    private static final SurfaceMaterial FRUIT_DARK=ModelBuilder.material("Gyumolcs_barna",0.24,0.095,0.025,0.76,false,false);
    private static final SurfaceMaterial BERRY_BLUE=ModelBuilder.material("Bogyos_kek",0.12,0.22,0.62,0.45,false,false);
    private static final SurfaceMaterial BERRY_RED=ModelBuilder.material("Bogyos_piros",0.68,0.035,0.075,0.42,false,false);
    private static final SurfaceMaterial BERRY_RASPBERRY=ModelBuilder.material("Bogyos_malna",0.82,0.08,0.19,0.42,false,false);
    private static final SurfaceMaterial BERRY_GREEN=ModelBuilder.material("Bogyos_zold",0.43,0.62,0.16,0.5,false,false);

    private static void garden(ModelBuilder b,int v) {
        if(v>=14) {
            gardenBenchSet(b,v-14);
        } else if(v==0 || v==1) {
            if(v==0) {
                legs(b,1.6,0.85,0.72,METAL);
                for(int i=0;i<9;i++) b.box(WOOD,-0.8,0.72,-0.425+i*0.095,1.6,0.04,0.085);
            } else {
                legs(b,0.75,0.75,0.72,METAL);
                b.rod(WOOD,new double[]{0,0.72,0},new double[]{0,0.76,0},0.6);
            }
        } else if(v==2 || v==3 || v==4 || v==13) {
            double w=v==4 ? 1.6 : v==13 ? 1.8 : 0.54;
            legs(b,w,0.58,0.44,METAL);
            SurfaceMaterial seat=v==13 ? fabric(2) : WOOD;
            for(int i=0;i<5;i++) b.box(seat,-w/2,0.44,-0.29+i*0.115,w,0.045,0.1);
            for(int side:new int[]{-1,1}) {
                b.box(METAL,side*(w/2-0.025)-0.015,0.44,-0.29,0.03,0.5,0.03);
                if(v!=2) {
                    b.box(METAL,side*(w/2-0.025)-0.015,0.44,0.22,0.03,0.22,0.03);
                    b.box(WOOD,side*(w/2-0.025)-0.035,0.66,-0.3,0.07,0.035,0.6);
                }
            }
            for(int i=0;i<4;i++) b.box(seat,-w/2,0.55+i*0.105,-0.3,w,0.09,0.04);
        } else if(v==5) {
            legs(b,0.72,1.95,0.28,METAL);
            for(int i=0;i<14;i++) b.box(WOOD,-0.36,0.28,-0.2+i*0.085,0.72,0.035,0.075);
            for(int i=0;i<8;i++) {
                double y=0.32+i*0.065,z=-0.25-i*0.09;
                b.rod(WOOD,new double[]{-0.36,y,z},new double[]{0.36,y,z},0.035);
            }
            b.box(fabric(2),-0.31,0.315,-0.18,0.62,0.035,1.08);
        } else if(v==6) {
            b.shell(STONE,0,0,1,new double[][]{{0.6,0,0.6},{0.6,0.4,0.6},{0.43,0.4,0.43},{0.43,0.08,0.43}});
            b.rod(DARK,new double[]{0,0.075,0},new double[]{0,0.09,0},0.43);
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/4;
                b.rod(WOOD,new double[]{-0.28*Math.cos(a),0.13,-0.28*Math.sin(a)},
                        new double[]{0.28*Math.cos(a),0.13,0.28*Math.sin(a)},0.035);
            }
            for(int side:new int[]{-1,1}) {
                b.rod(METAL,new double[]{side*0.7,0,0},new double[]{side*0.7,0.95,0},0.018);
                b.rod(METAL,new double[]{side*0.7,0.85,-0.12},new double[]{side*0.7,0.85,0.12},0.012);
            }
        } else if(v==7) {
            legs(b,0.85,0.5,0.7,METAL);
            b.box(DARK,-0.45,0.62,-0.28,0.9,0.08,0.56);
            for(int side:new int[]{-1,1}) b.box(DARK,side<0 ? -0.45 : 0.42,0.7,-0.28,0.03,0.22,0.56);
            b.box(DARK,-0.45,0.7,-0.28,0.9,0.22,0.03);
            for(int i=0;i<15;i++) b.rod(METAL,new double[]{-0.4+i*0.057,0.86,-0.24},new double[]{-0.4+i*0.057,0.86,0.24},0.006);
            b.box(WOOD,0.46,0.82,-0.25,0.3,0.035,0.5);
        } else if(v==8 || v==9) {
            double d=v==9 ? 1.75 : 0.65;
            for(int side:new int[]{-1,1}) {
                b.rod(METAL,new double[]{side*1.05,0,-0.85},new double[]{side*1.05,1.95,0},0.035);
                b.rod(METAL,new double[]{side*1.05,0,0.85},new double[]{side*1.05,1.95,0},0.035);
                for(int z:new int[]{-1,1})
                    b.rod(METAL,new double[]{side*0.82,1.92,z*0.1},new double[]{side*0.82,0.48,z*d*0.43},0.009);
            }
            b.rod(METAL,new double[]{-1.1,1.95,0},new double[]{1.1,1.95,0},0.04);
            b.box(WOOD,-0.9,0.42,-d/2,1.8,0.06,d);
            b.box(fabric(2),-0.88,0.48,-d/2+0.02,1.76,0.1,d-0.04);
            if(v==8) b.box(fabric(2),-0.9,0.58,-d/2,1.8,0.55,0.1);
            else b.sphere(WHITE,0,0.64,-0.62,0.65,0.08,0.18);
            b.box(fabric(1),-1.17,2,-0.98,2.34,0.045,1.96);
        } else if(v==10) {
            b.rod(STONE,new double[]{0,0,0},new double[]{0,0.08,0},0.35);
            b.rod(METAL,new double[]{0,0.08,0},new double[]{0,2.4,0},0.025);
            b.shell(fabric(1),0,0,1,new double[][]{{0.015,2.36,0.015},{1.35,1.98,1.35},{1.35,2.02,1.35},{0.015,2.4,0.015}});
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4;
                b.rod(METAL,new double[]{0,2.36,0},new double[]{1.32*Math.cos(a),2,1.32*Math.sin(a)},0.007);
            }
        } else if(v==11) {
            b.box(WOOD,-0.6,0,-0.23,1.2,0.08,0.46);
            for(int side:new int[]{-1,1}) {
                b.box(WOOD,-0.6,0.08,side<0 ? -0.23 : 0.19,1.2,0.42,0.04);
                b.box(WOOD,side<0 ? -0.6 : 0.56,0.08,-0.19,0.04,0.42,0.38);
            }
            b.box(STONE,-0.55,0.39,-0.18,1.1,0.04,0.36);
        } else {
            legs(b,1.8,0.72,0.72,WOOD);
            for(int i=0;i<6;i++) b.box(WOOD,-0.9,0.72,-0.36+i*0.12,1.8,0.05,0.11);
            for(int side:new int[]{-1,1}) {
                b.box(WOOD,-0.9,0.43,side*0.68-0.16,1.8,0.05,0.32);
                for(int x:new int[]{-1,1}) {
                    b.box(WOOD,x*0.66-0.04,0,side*0.68-0.04,0.08,0.43,0.08);
                    b.box(METAL,x*0.66-0.03,0.28,side<0 ? -0.78 : 0,0.06,0.07,0.78);
                }
            }
        }
    }

    private static void gardenBenchSet(ModelBuilder b,int variant) {
        if(variant==0 || variant==1) {
            double length=variant==0 ? 1.8 : 1.55, seatY=0.46, depth=0.43;
            legs(b,length-0.12,depth,seatY,WOOD);
            for(int i=0;i<5;i++)
                b.box(WOOD,-length/2,seatY,-depth/2+i*depth/5,length,0.035,depth/5*0.9);
            if(variant==0) {
                for(double x:new double[]{-length/2+0.06,length/2-0.11}) {
                    b.rod(WOOD,new double[]{x,seatY-0.06,depth*0.38},new double[]{x,1.02,depth*0.38},0.035);
                    b.rod(WOOD,new double[]{x,1.02,depth*0.38},new double[]{x,1.02,-depth*0.48},0.035);
                }
                for(int i=0;i<4;i++)
                    b.box(WOOD,-length/2+0.04,0.61+i*0.095,-depth/2,length-0.08,0.07,0.055);
            }
            b.rod(METAL,new double[]{-length*0.42,0.2,0},new double[]{length*0.42,0.2,0},0.012);
            return;
        }
        if(variant==2) {
            double w=2.0,d=0.82,h=0.74;
            legs(b,w-0.14,d-0.14,h-0.045,WOOD);
            b.box(WOOD,-w/2,h-0.045,-d/2,w,0.045,d);
            for(int i=1;i<8;i++) b.box(WOOD,-w/2+i*w/8,h-0.046,-d/2,0.012,0.048,d);
            b.rod(METAL,new double[]{-w*0.38,0.16,-d*0.38},new double[]{w*0.38,0.16,-d*0.38},0.012);
            b.rod(METAL,new double[]{-w*0.38,0.16,d*0.38},new double[]{w*0.38,0.16,d*0.38},0.012);
            return;
        }
        double length=variant==3 ? 1.2 : variant==4 ? 1.8 : 2.4;
        double seatY=0.46, tableY=0.74, seatDepth=0.29, tableDepth=0.62;
        double tableLegSpan=length-0.12;
        for(double x:new double[]{-tableLegSpan/2,tableLegSpan/2}) {
            b.rod(WOOD,new double[]{x,0,-tableDepth*0.35},new double[]{x,tableY-0.04,-tableDepth*0.35},0.045);
            b.rod(WOOD,new double[]{x,0,tableDepth*0.35},new double[]{x,tableY-0.04,tableDepth*0.35},0.045);
            b.rod(WOOD,new double[]{x,0,-tableDepth*0.35},new double[]{x,0,tableDepth*0.35},0.035);
        }
        b.box(WOOD,-length/2,tableY-0.04,-tableDepth/2,length,0.04,tableDepth);
        for(int i=1;i<10;i++) b.box(WOOD,-length/2+i*length/10,tableY-0.041,-tableDepth/2,0.012,0.042,tableDepth);
        for(double z:new double[]{-0.57,0.57}) {
            for(double x:new double[]{-tableLegSpan/2,tableLegSpan/2}) {
                b.rod(WOOD,new double[]{x,0,z},new double[]{x,seatY-0.035,z},0.035);
            }
            b.box(WOOD,-length/2,seatY-0.035,z-seatDepth/2,length,0.035,seatDepth);
            for(int i=1;i<7;i++)
                b.box(WOOD,-length/2+i*length/7,seatY-0.036,z-seatDepth/2,0.01,0.037,seatDepth);
        }
        b.rod(METAL,new double[]{-tableLegSpan/2,0.22,0},new double[]{tableLegSpan/2,0.22,0},0.012);
    }

    private static void mechanical(ModelBuilder b,int v) {
        if(v<2) {
            if(v==0) {
                b.box(WHITE,-0.21,0.13,-0.15,0.42,0.7,0.3);
                b.box(DARK,-0.17,0.22,0.151,0.34,0.12,0.012);
                b.rod(METAL,new double[]{0,0.83,0},new double[]{0,1.07,0},0.055);
                b.box(DARK,-0.06,0.55,0.151,0.12,0.06,0.008);
            } else {
                b.rod(WHITE,new double[]{0,0.16,0},new double[]{0,1.03,0},0.24);
                crown(b,WHITE,0,1.03,0,0.24,0.09,0.24);
                crown(b,WHITE,0,0.16,0,0.24,0.07,0.24);
                b.box(DARK,-0.08,0.25,0.23,0.16,0.08,0.025);
                b.rod(DARK,new double[]{0,0.72,0.235},new double[]{0,0.72,0.25},0.035);
            }
            for(int side:new int[]{-1,1}) {
                b.rod(side<0 ? METAL : POT,new double[]{side*0.1,0,0},new double[]{side*0.1,0.16,0},0.014);
                b.box(METAL,-0.15,0.3+0.35*(side+1)/2,-0.19,0.3,0.035,0.045);
            }
            for(int i=0;i<2;i++)
                b.rod(METAL,new double[]{-0.09+i*0.18,0.28, v==0 ? 0.17 : 0.263},
                        new double[]{-0.09+i*0.18,0.28,v==0 ? 0.18 : 0.273},0.022);
        } else if(v==2) {
            b.box(WHITE,-0.43,0,-0.11,0.86,0.29,0.22);
            b.box(DARK,-0.38,0.025,0.111,0.76,0.065,0.012);
            for(int i=0;i<3;i++) b.box(WHITE,-0.37,0.035+i*0.018,0.124,0.74,0.008,0.025);
            b.box(GLASS,0.26,0.17,0.111,0.08,0.035,0.005);
        } else {
            b.box(WHITE,-0.4,0.08,-0.16,0.8,0.55,0.32);
            for(int side:new int[]{-1,1}) b.box(METAL,side*0.28-0.03,0,-0.22,0.06,0.08,0.44);
            b.rod(DARK,new double[]{-0.11,0.355,0.162},new double[]{-0.11,0.355,0.18},0.225);
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2;
                b.rod(METAL,new double[]{-0.11,0.355,0.185},
                        new double[]{-0.11+0.18*Math.cos(a),0.355+0.18*Math.sin(a),0.185},0.025);
            }
            for(int i=1;i<14;i++) {
                double x=-0.32+i*0.03,h=Math.sqrt(Math.max(0,0.21*0.21-(x+0.11)*(x+0.11)));
                b.rod(METAL,new double[]{x,0.355-h,0.215},new double[]{x,0.355+h,0.215},0.004);
            }
            for(int i=0;i<10;i++) b.box(DARK,0.18,0.14+i*0.042,0.162,0.16,0.012,0.008);
            for(int i=0;i<2;i++) b.rod(METAL,new double[]{0.4,0.17+i*0.09,-0.06},
                    new double[]{0.46,0.17+i*0.09,-0.06},0.015);
        }
    }

    private static final SurfaceMaterial POOL_TILE=ModelBuilder.material("Medence_kek_burkolat",0.18,0.57,0.75,0.3,false,false);
    private static final SurfaceMaterial WATER=ModelBuilder.material("Medence_vizfelulet",0.10,0.48,0.64,0.08,false,false);

    private static void pool(ModelBuilder b,int v) {
        if(v==0) {
            b.box(POOL_TILE,-3.2,0,-1.7,6.4,0.12,3.4);
            for(int side:new int[]{-1,1}) {
                b.box(POOL_TILE,side<0 ? -3.2 : 3,0.12,-1.7,0.2,1.28,3.4);
                b.box(POOL_TILE,-3,0.12,side<0 ? -1.7 : 1.5,6,1.28,0.2);
                b.box(WHITE,side<0 ? -3.28 : 2.98,1.4,-1.78,0.3,0.06,3.56);
                b.box(WHITE,-2.98,1.4,side<0 ? -1.78 : 1.48,5.96,0.06,0.3);
            }
            b.box(WATER,-3,1.19,-1.5,6,0.015,3);
            b.box(WHITE,-2.98,0.12,-0.5,0.6,0.42,1);
            b.box(WHITE,-2.98,0.54,-0.5,0.3,0.42,1);
            for(int side:new int[]{-1,1}) b.rod(WHITE,new double[]{side*1.8,0.75,-1.495},
                    new double[]{side*1.8,0.75,-1.48},0.09);
        } else if(v==1) {
            for(int side:new int[]{-1,1}) {
                double x=side*0.29;
                b.rod(METAL,new double[]{x,0,-0.36},new double[]{x,0.8,-0.36},0.022);
                for(int i=0;i<12;i++) {
                    double a=Math.PI*i/12,c=Math.PI*(i+1)/12;
                    b.rod(METAL,new double[]{x,0.8+0.28*Math.sin(a),-0.08-0.28*Math.cos(a)},
                            new double[]{x,0.8+0.28*Math.sin(c),-0.08-0.28*Math.cos(c)},0.022);
                }
                b.rod(METAL,new double[]{x,0.8,0.2},new double[]{x,0,0.2},0.022);
                for(double z:new double[]{-0.36,0.2}) {
                    b.rod(DARK,new double[]{x,0,z},new double[]{x,0.005,z},0.065);
                    b.rod(METAL,new double[]{x,0.005,z},new double[]{x,0.025,z},0.06);
                }
            }
        } else {
            b.box(DARK,-0.55,0,-0.32,1.1,0.06,0.64);
            b.rod(DARK,new double[]{-0.19,0.06,0},new double[]{-0.19,0.18,0},0.18);
            crown(b,POOL_TILE,-0.19,0.39,0,0.27,0.28,0.27);
            b.rod(DARK,new double[]{-0.19,0.64,0},new double[]{-0.19,0.75,0},0.075);
            b.rod(DARK,new double[]{-0.19,0.73,0},new double[]{0.02,0.73,0},0.018);
            b.rod(METAL,new double[]{-0.19,0.75,0},new double[]{-0.19,0.8,0},0.035);
            b.rod(DARK,new double[]{0.3,0.16,-0.18},new double[]{0.3,0.16,0.16},0.105);
            b.box(DARK,0.2,0.06,-0.22,0.2,0.055,0.42);
            b.rod(METAL,new double[]{-0.19,0.7,0.08},new double[]{0.3,0.7,0.08},0.025);
            b.rod(METAL,new double[]{0.3,0.7,0.08},new double[]{0.3,0.23,0.08},0.025);
            b.rod(METAL,new double[]{-0.19,0.7,-0.08},new double[]{-0.19,0.7,-0.35},0.025);
        }
    }

    private static void gardenBuilding(ModelBuilder b,int v) {
        if(v==2) { palapa(b); return; }
        if(v==3) { gazebo(b); return; }
        double w=v==0 ? 3 : 1.8,d=v==0 ? 2.4 : 1.2,h=v==0 ? 2.1 : 1.85,rise=0.45;
        SurfaceMaterial wall=v==0 ? WOOD : NEEDLE;
        b.box(DARK,-w/2,0,-d/2,w,0.09,d);
        b.box(wall,-w/2,0.09,-d/2,w,h-0.09,0.08);
        for(int side:new int[]{-1,1}) b.box(wall,side<0 ? -w/2 : w/2-0.08,0.09,-d/2,0.08,h-0.09,d);
        double door=v==0 ? 0.85 : 1.2,dh=1.75;
        for(int side:new int[]{-1,1})
            b.box(wall,side<0 ? -w/2 : door/2,0.09,d/2-0.08,(w-door)/2,dh-0.09,0.08);
        b.box(wall,-w/2,dh,d/2-0.08,w,h-dh,0.08);
        b.box(WOOD,-door/2,0.09,d/2-0.06,door,dh-0.09,0.05);
        for(int i=1;i<8;i++) b.box(DARK,-door/2+i*door/8,0.1,d/2-0.008,0.008,dh-0.12,0.008);
        b.rod(METAL,new double[]{door/2-0.12,0.94,d/2},new double[]{door/2-0.12,1.06,d/2},0.014);
        if(v==0) {
            b.box(WHITE,0.65,0.85,d/2+0.001,0.6,0.65,0.035);
            b.box(GLASS,0.69,0.89,d/2+0.037,0.52,0.57,0.008);
            b.box(WHITE,0.94,0.89,d/2+0.046,0.025,0.57,0.012);
        }
        for(int side:new int[]{-1,1}) {
            double z=side<0 ? -d/2 : d/2;
            double[] a={-w/2,h,z},c={w/2,h,z},top={0,h+rise,z};
            MeshBuilder mesh=b.part(wall);
            mesh.triangle(side<0 ? c : a,side<0 ? a : c,top,new double[]{0,0,side},
                    new double[]{1,0,0},new double[]{0,1,0});
        }
        // The roof ridge runs along Z, so each roof half slopes along X.
        double half=w/2+0.15,slope=rise/(w/2),angle=Math.atan(slope),c=Math.cos(angle),s=Math.sin(angle);
        for(int side:new int[]{-1,1}) {
            b.part(DARK).box(new double[]{side<0 ? -half : 0,h+rise-(side<0 ? half*slope : 0),-d/2-0.15},
                    new double[]{c,-side*s,0},new double[]{side*s,c,0},new double[]{0,0,1},
                    half/c,0.055,d+0.3);
        }
        for(int i=1;i<(int)(h/0.15);i++) {
            b.box(DARK,-w/2,0.09+i*0.15,-d/2-0.004,w,0.009,0.004);
            for(int side:new int[]{-1,1}) b.box(DARK,side<0 ? -w/2-0.004 : w/2,0.09+i*0.15,-d/2,0.004,0.009,d);
        }
    }

    private static void palapa(ModelBuilder b) {
        double postRadius=1.48, eaveY=2.18, roofY=2.2;
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,x=postRadius*Math.cos(a),z=postRadius*Math.sin(a);
            b.rod(WOOD,new double[]{x,0,z},new double[]{x,eaveY,z},0.085);
            b.rod(WOOD,new double[]{x*0.78,1.68,z*0.78},new double[]{x,eaveY,z},0.035);
        }
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4,c=Math.cos(a),s=Math.sin(a),a2=(i+1)*Math.PI/4;
            b.rod(WOOD,new double[]{postRadius*c,eaveY,postRadius*s},
                    new double[]{postRadius*Math.cos(a2),eaveY,postRadius*Math.sin(a2)},0.055);
        }
        b.shell(THATCH,0,0,1,new double[][]{
                {1.98,roofY-0.12,1.98},{1.62,2.30,1.62},{1.05,2.54,1.05},
                {0.48,2.84,0.48},{0.22,3.00,0.22},{0.001,3.18,0.001}});
        for(int i=0;i<28;i++) {
            double a=2*Math.PI*i/28,c=Math.cos(a),s=Math.sin(a);
            b.rod(i%2==0 ? THATCH_LIGHT : THATCH,new double[]{0.015,3.16,0.015},
                    new double[]{1.95*c,roofY-0.1,1.95*s},0.014);
        }
        b.rod(WOOD,new double[]{0,2.18,0},new double[]{0,3.14,0},0.065);
        for(double y:new double[]{0.54,0.82}) {
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4,a2=(i+1)*Math.PI/4;
                double inset=y==0.54 ? 0.38 : 0.62;
                b.rod(WOOD,new double[]{(postRadius-inset)*Math.cos(a),y,(postRadius-inset)*Math.sin(a)},
                        new double[]{(postRadius-inset)*Math.cos(a2),y,(postRadius-inset)*Math.sin(a2)},0.022);
            }
        }
    }

    private static void gazebo(ModelBuilder b) {
        double halfW=1.55,halfD=1.3,postY=2.18,ridgeY=2.82,ridgeHalf=0;
        for(double x:new double[]{-halfW,halfW}) for(double z:new double[]{-halfD,halfD}) {
            b.rod(WOOD,new double[]{x,0,z},new double[]{x,postY,z},0.075);
            b.rod(WOOD,new double[]{x,0.7,z},new double[]{x*0.72,postY,z*0.72},0.03);
        }
        for(double x:new double[]{-halfW,halfW}) b.rod(WOOD,new double[]{x,postY,-halfD},new double[]{x,postY,halfD},0.055);
        for(double z:new double[]{-halfD,halfD}) b.rod(WOOD,new double[]{-halfW,postY,z},new double[]{halfW,postY,z},0.055);
        for(double z:new double[]{-halfD,halfD}) {
            b.box(WOOD,-halfW+0.08,0.48,z<0 ? -halfD+0.08 : halfD-0.34,halfW*2-0.16,0.055,0.26);
            for(int side:new int[]{-1,1})
                b.box(WOOD,side<0 ? -halfW+0.08 : halfW-0.15,0.53,z<0 ? -halfD+0.1 : halfD-0.32,0.07,0.55,0.06);
        }
        double eaveX=halfW+0.12,eaveY=postY-0.12,eaveZ=halfD+0.14;
        for(int side:new int[]{-1,1}) {
            double ridgeX=side*ridgeHalf;
            for(int ix=0;ix<8;ix++) for(int iz=0;iz<12;iz++) {
                double xa=ix/8.0,xb=(ix+1)/8.0,za=-eaveZ+2*eaveZ*iz/12.0,zb=-eaveZ+2*eaveZ*(iz+1)/12.0;
                double x0=side<0 ? -eaveX+(eaveX-ridgeHalf)*xa : ridgeHalf+(eaveX-ridgeHalf)*xa;
                double x1=side<0 ? -eaveX+(eaveX-ridgeHalf)*xb : ridgeHalf+(eaveX-ridgeHalf)*xb;
                double y0=eaveY+(ridgeY-eaveY)*(side<0 ? xa : 1-xa);
                double y1=eaveY+(ridgeY-eaveY)*(side<0 ? xb : 1-xb);
                double[][] top={{x0,y0,za},{x1,y1,za},{x1,y1,zb},{x0,y0,zb}};
                double[][] underside={{x0,y0-0.075,za},{x1,y1-0.075,za},{x1,y1-0.075,zb},{x0,y0-0.075,zb}};
                roofQuad(b,top,new double[]{0,1,0});
                roofQuad(b,underside,new double[]{0,-1,0});
            }
            for(int line=0;line<=10;line++) {
                double z=-eaveZ+2*eaveZ*line/10.0;
                b.rod(line%2==0 ? THATCH_LIGHT : THATCH,new double[]{side<0 ? -eaveX : ridgeHalf,
                        side<0 ? eaveY : ridgeY,z},new double[]{side<0 ? -ridgeHalf : eaveX,
                        side<0 ? ridgeY : eaveY,z},0.012);
            }
        }
        // Close the gable ends below the ridge so the roof has no exposed sky gaps.
        for(double z:new double[]{-eaveZ,eaveZ}) {
            double[][] gable=z<0
                    ? new double[][]{{-eaveX,eaveY,z},{eaveX,eaveY,z},{0,ridgeY,z}}
                    : new double[][]{{eaveX,eaveY,z},{-eaveX,eaveY,z},{0,ridgeY,z}};
            roofTriangle(b,gable,new double[]{0,0,z<0 ? -1 : 1});
            double[][] inside={{gable[0][0],gable[0][1]-0.075,z},{gable[1][0],gable[1][1]-0.075,z},
                    {gable[2][0],gable[2][1]-0.075,z}};
            roofTriangle(b,inside,new double[]{0,0,z<0 ? 1 : -1});
        }
        for(double z:new double[]{-eaveZ,eaveZ})
            b.rod(THATCH_LIGHT,new double[]{-eaveX,eaveY,z},new double[]{eaveX,eaveY,z},0.045);
        for(double x:new double[]{-eaveX,eaveX})
            b.rod(THATCH_LIGHT,new double[]{x,eaveY,-eaveZ},new double[]{x,eaveY,eaveZ},0.045);
        b.rod(THATCH_LIGHT,new double[]{0,ridgeY,-eaveZ},new double[]{0,ridgeY,eaveZ},0.065);
    }

    private static void roofQuad(ModelBuilder b,double[][] p,double[] desiredNormal) {
        double ux=p[1][0]-p[0][0],uy=p[1][1]-p[0][1],uz=p[1][2]-p[0][2];
        double vx=p[2][0]-p[0][0],vy=p[2][1]-p[0][1],vz=p[2][2]-p[0][2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        if(nx*desiredNormal[0]+ny*desiredNormal[1]+nz*desiredNormal[2]<0) {
            double[] swap=p[1];p[1]=p[3];p[3]=swap;
            nx=-nx;ny=-ny;nz=-nz;
        }
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        b.part(THATCH).quad(p[0],p[1],p[2],p[3],new double[]{nx/length,ny/length,nz/length},
                new double[]{1,0,0},new double[]{0,0,1});
    }

    private static void roofTriangle(ModelBuilder b,double[][] p,double[] desiredNormal) {
        double[] a=p[0],q=p[1],r=p[2];
        double ux=q[0]-a[0],uy=q[1]-a[1],uz=q[2]-a[2];
        double vx=r[0]-a[0],vy=r[1]-a[1],vz=r[2]-a[2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        if(nx*desiredNormal[0]+ny*desiredNormal[1]+nz*desiredNormal[2]<0) {
            double[] swap=q;q=r;r=swap;nx=-nx;ny=-ny;nz=-nz;
        }
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz),normal[]={nx/length,ny/length,nz/length};
        MeshBuilder mesh=b.part(THATCH);
        mesh.triangle(a,q,r,normal,new double[]{1,0,0},new double[]{0,0,1});
    }

    private static void antenna(ModelBuilder b,boolean dish) {
        b.box(METAL,-0.13,0,-0.13,0.26,0.035,0.26);
        b.rod(METAL,new double[]{0,0.035,0},new double[]{0,dish ? 1.15 : 1.4,0},0.025);
        if(!dish) {
            b.rod(METAL,new double[]{0,1.35,-0.65},new double[]{0,1.35,0.65},0.014);
            for(int i=0;i<9;i++) {
                double z=-0.58+i*0.14,half=0.38-i*0.022;
                b.rod(METAL,new double[]{-half,1.35,z},new double[]{half,1.35,z},0.007);
            }
            for(int i=0;i<5;i++)
                b.rod(METAL,new double[]{-0.45,1.15+i*0.1,-0.6},new double[]{0.45,1.15+i*0.1,-0.6},0.009);
            b.box(DARK,-0.06,1.29,-0.42,0.12,0.1,0.15);
        } else {
            MeshBuilder mesh=b.part(WHITE);
            int rings=8,segments=32;
            for(int r=0;r<rings;r++) for(int i=0;i<segments;i++) {
                double r0=0.015+r*0.39/rings,r1=0.015+(r+1)*0.39/rings;
                double a=i*2*Math.PI/segments,c=(i+1)*2*Math.PI/segments;
                double[][] points=new double[4][];
                double[] radii={r0,r1,r1,r0},angles={a,a,c,c};
                for(int j=0;j<4;j++) {
                    double x=radii[j]*Math.cos(angles[j]),y=radii[j]*Math.sin(angles[j]);
                    points[j]=new double[]{x,1.0+y,0.08+0.6*radii[j]*radii[j]};
                }
                double ux=points[1][0]-points[0][0],uy=points[1][1]-points[0][1],uz=points[1][2]-points[0][2];
                double vx=points[3][0]-points[0][0],vy=points[3][1]-points[0][1],vz=points[3][2]-points[0][2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);
                mesh.quad(points[0],points[1],points[2],points[3],new double[]{nx/n,ny/n,nz/n},new double[]{1,0,0},new double[]{0,1,0});
                mesh.quad(points[3],points[2],points[1],points[0],new double[]{-nx/n,-ny/n,-nz/n},new double[]{1,0,0},new double[]{0,1,0});
            }
            b.rod(WHITE,new double[]{0,1,0.08},new double[]{0,1,0.09},0.016);
            b.rod(METAL,new double[]{0,0.64,0.1},new double[]{0,0.72,0.55},0.016);
            b.rod(DARK,new double[]{0,0.72,0.5},new double[]{0,0.78,0.5},0.045);
            b.box(METAL,-0.07,0.9,-0.05,0.14,0.2,0.1);
        }
    }

    private static final SurfaceMaterial BARK=ModelBuilder.material("Fakereg",0.29,0.17,0.08,0.98,false,false);
    private static final SurfaceMaterial LEAF=ModelBuilder.material("Lomb_zold",0.16,0.38,0.08,0.9,false,false);
    private static final SurfaceMaterial LEAF_LIGHT=ModelBuilder.material("Friss_level",0.31,0.52,0.12,0.85,false,false);
    private static final SurfaceMaterial NEEDLE=ModelBuilder.material("Tuja_tulevel",0.08,0.25,0.12,0.95,false,false);
    private static final SurfaceMaterial PETAL=ModelBuilder.material("Viragszirom",0.88,0.12,0.32,0.65,false,false);
    private static final SurfaceMaterial YELLOW=ModelBuilder.material("Viragkozepe",0.98,0.68,0.08,0.7,false,false);
    private static final SurfaceMaterial POT=ModelBuilder.material("Terrakotta",0.63,0.28,0.14,0.9,false,false);
    private static final SurfaceMaterial SOIL=ModelBuilder.material("Viragfold",0.10,0.065,0.03,1,false,false);

    private static void crown(ModelBuilder b,SurfaceMaterial material,double x,double y,double z,double rx,double ry,double rz) {
        MeshBuilder mesh=b.part(material);
        int rings=7,segments=12;
        int[][] indices=new int[rings-1][segments];
        int top=mesh.vertex(x,y+ry,z,0,1,0,0.5,0),bottom=mesh.vertex(x,y-ry,z,0,-1,0,0.5,1);
        for(int j=1;j<rings;j++) for(int i=0;i<segments;i++) {
            double a=Math.PI*j/rings,p=2*Math.PI*i/segments;
            double nx=Math.sin(a)*Math.cos(p),ny=Math.cos(a),nz=Math.sin(a)*Math.sin(p);
            double length=Math.sqrt(nx*nx/(rx*rx)+ny*ny/(ry*ry)+nz*nz/(rz*rz));
            indices[j-1][i]=mesh.vertex(x+rx*nx,y+ry*ny,z+rz*nz,nx/rx/length,ny/ry/length,nz/rz/length,(double)i/segments,(double)j/rings);
        }
        for(int i=0;i<segments;i++) {
            int next=(i+1)%segments;
            mesh.tri(top,indices[0][next],indices[0][i]);
            mesh.tri(bottom,indices[rings-2][i],indices[rings-2][next]);
            for(int j=0;j<rings-2;j++) mesh.quad(indices[j][i],indices[j][next],indices[j+1][next],indices[j+1][i]);
        }
    }

    private static void leaf(ModelBuilder b,SurfaceMaterial material,double[] start,double[] end,double width) {
        double dx=end[0]-start[0],dy=end[1]-start[1],dz=end[2]-start[2],length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double horizontal=Math.hypot(dx,dz),sx=horizontal<1e-9 ? 1 : -dz/horizontal,sz=horizontal<1e-9 ? 0 : dx/horizontal;
        double[] left={(start[0]+end[0])/2+sx*width,(start[1]+end[1])/2,(start[2]+end[2])/2+sz*width};
        double[] right={(start[0]+end[0])/2-sx*width,(start[1]+end[1])/2,(start[2]+end[2])/2-sz*width};
        double[] ridge={(start[0]+end[0])/2,(start[1]+end[1])/2+length*0.08,(start[2]+end[2])/2};
        double[][] points={start,left,end,right};
        MeshBuilder mesh=b.part(material);
        for(int i=0;i<4;i++) {
            double[] a=points[i],c=points[(i+1)%4];
            double ux=c[0]-a[0],uy=c[1]-a[1],uz=c[2]-a[2],vx=ridge[0]-a[0],vy=ridge[1]-a[1],vz=ridge[2]-a[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double[] normal={nx/n,ny/n,nz/n},back={-nx/n,-ny/n,-nz/n};
            mesh.triangle(a,c,ridge,normal,new double[]{1,0,0},new double[]{0,0,1});
            mesh.triangle(ridge,c,a,back,new double[]{1,0,0},new double[]{0,0,1});
        }
    }

    private static void flower(ModelBuilder b,double x,double y,double z,double size,SurfaceMaterial color) {
        for(int i=0;i<6;i++) {
            double a=i*Math.PI/3;
            leaf(b,color,new double[]{x,y,z},new double[]{x+Math.cos(a)*size,y+size*0.18,z+Math.sin(a)*size},size*0.28);
        }
        crown(b,YELLOW,x,y+size*0.12,z,size*0.23,size*0.16,size*0.23);
    }

    private static void pot(ModelBuilder b,double radius,double height) {
        b.shell(POT,0,0,1,new double[][]{{0.001,0,0.001},{radius*0.68,0,radius*0.68},
                {radius,height*0.92,radius},{radius*1.04,height*0.92,radius*1.04},
                {radius*1.04,height,radius*1.04},{radius*0.87,height,radius*0.87},{radius*0.87,height*0.85,radius*0.87}});
        b.rod(SOIL,new double[]{0,height*0.85,0},new double[]{0,height*0.87,0},radius*0.87);
    }

    private static void grapevine(ModelBuilder b,boolean cordon) {
        double span=cordon ? 2.8 : 1.2;
        if(cordon) {
            for(int side:new int[]{-1,1}) b.box(WOOD,side*1.4-0.035,0,-0.035,0.07,1.85,0.07);
            for(double y:new double[]{0.8,1.25,1.7})
                b.rod(METAL,new double[]{-1.4,y,0},new double[]{1.4,y,0},0.004);
        }
        b.rod(BARK,new double[]{0,0,0},new double[]{0.06,0.85,0},0.045);
        for(int side:new int[]{-1,1}) {
            b.rod(BARK,new double[]{0.06,0.85,0},new double[]{side*span*0.44,0.95,0},0.022);
            for(int i=0;i<4;i++) {
                double x=side*span*(0.08+i*0.11),top=1.45+(i%2)*0.24;
                b.rod(BARK,new double[]{x,0.92,0},new double[]{x+side*0.08,top,0.03},0.008);
                for(int j=0;j<5;j++) {
                    double y=1.02+j*0.13,z=(j%2==0 ? 1 : -1)*0.2;
                    leaf(b,j%2==0 ? LEAF : LEAF_LIGHT,new double[]{x,y,0},
                            new double[]{x+side*0.18,y+0.08,z},0.13);
                }
                double gx=x+0.08,gz=0.15,gy=1.04;
                SurfaceMaterial grape=cordon ? NEEDLE : PETAL;
                for(int j=0;j<7;j++) {
                    double a=j*2.4,r=j<4 ? 0.045 : 0.025;
                    crown(b,grape,gx+r*Math.cos(a),gy-j*0.022,gz+r*Math.sin(a),0.028,0.032,0.028);
                }
            }
        }
    }

    private static void fruitTree(ModelBuilder b,int species) {
        double[] heights={4.1,3.8,3.5,5.0,3.9,3.6,3.25};
        double[] spreads={1.38,1.18,1.2,1.65,1.22,1.35,1.08};
        double[] canopyY={2.95,2.85,2.55,3.55,2.8,2.65,2.4};
        double h=heights[species],spread=spreads[species],cy=canopyY[species];
        SurfaceMaterial fruit=switch(species) {
            case 2 -> FRUIT_ORANGE;
            case 3 -> FRUIT_DARK;
            case 1 -> FRUIT_GOLD;
            case 4 -> FRUIT_CHERRY;
            case 5 -> FRUIT_APPLE;
            case 6 -> FRUIT_DARK;
            default -> FRUIT_RED;
        };
        double trunkRadius=species==3 ? 0.14 : 0.095;
        b.rod(BARK,new double[]{0,0,0},new double[]{0,h*0.7,0},trunkRadius);
        for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+0.18*Math.sin(i*2.4);
            double y=h*(0.36+0.035*(i%3)),r=spread*(species==1 && i%2==0 ? 0.62 : 0.78);
            double x=r*Math.cos(a),z=r*Math.sin(a);
            b.rod(BARK,new double[]{0,y-0.28,0},new double[]{x,y,z},0.035);
            b.rod(BARK,new double[]{x,y,z},new double[]{x*1.12,cy+0.22,z*1.12},0.018);
        }
        int layers=species==3 ? 3 : 2;
        for(int layer=0;layer<layers;layer++) {
            int lobes=species==3 ? 7 : 6;
            double tierY=cy+(layer-(layers-1)*0.5)*spread*0.34;
            double tierRadius=spread*(species==1 ? (layer==0 ? 0.55 : 0.72)
                    : species==2 ? (layer==0 ? 0.72 : 0.6)
                    : species==3 ? (layer==1 ? 1.0 : 0.73) : (layer==0 ? 0.78 : 0.91));
            for(int i=0;i<lobes;i++) {
                double a=2*Math.PI*i/lobes+layer*0.31;
                double x=tierRadius*Math.cos(a),z=tierRadius*Math.sin(a);
                double ry=species==3 ? spread*0.35 : spread*0.3;
                if(species==1) ry=spread*0.38;
                double rx=species==3 ? spread*0.36 : spread*0.32;
                double rz=rx*(species==1 && i%2==0 ? 0.72 : 0.9);
                orchardLeafCluster(b,x,tierY,z,rx,ry,rz,12,(i+layer)%3==0);
                for(int sprig=0;sprig<3;sprig++) {
                    double twigAngle=a+(sprig-1)*0.63;
                    double sx=x+Math.cos(twigAngle)*rx*0.24,sz=z+Math.sin(twigAngle)*rz*0.24;
                    double ex=x+Math.cos(twigAngle)*rx*0.87,ez=z+Math.sin(twigAngle)*rz*0.87;
                    double sy=tierY+ry*(sprig==1 ? -0.12 : 0.08),ey=sy+0.035;
                    b.rod(BARK,new double[]{sx,sy,sz},new double[]{ex,ey,ez},0.009);
                    leaf(b,(i+sprig)%3==0 ? LEAF_LIGHT : LEAF,
                            new double[]{ex,ey,ez},
                            new double[]{ex+Math.cos(twigAngle)*0.15,ey+0.045+(sprig%2)*0.07,
                                    ez+Math.sin(twigAngle)*0.15},0.047);
                }
                if(species==3 && layer==1) {
                    // Walnut fruit has a green husk with the darker nut showing at the tip.
                    for(int j=0;j<1;j++) {
                        double a2=a+j*2.7;
                        double fx=x+Math.cos(a2)*rx*0.52,fz=z+Math.sin(a2)*rz*0.52;
                        crown(b,BERRY_GREEN,fx,tierY-0.08,fz,0.055,0.075,0.055);
                        crown(b,fruit,fx,tierY-0.13,fz+0.018,0.027,0.035,0.027);
                    }
                } else if(species!=3 && (layer+i)%2==0) {
                    int count=species==2 ? 2 : 3;
                    for(int j=0;j<count;j++) {
                        double a2=a+j*2.1,fr=rx*0.62;
                        double fx=x+Math.cos(a2)*fr,fz=z+Math.sin(a2)*rz*0.62;
                        double fy=tierY-ry*0.35+(j%2)*0.07;
                        crown(b,fruit,fx,fy,fz,0.065,0.075,0.065);
                        b.rod(BARK,new double[]{fx,fy+0.07,fz},new double[]{fx+0.012,fy+0.11,fz},0.006);
                    }
                }
            }
        }
        orchardLeafCluster(b,0,cy+spread*0.55,0,spread*0.48,spread*0.32,spread*0.48,20,true);
    }

    private static void orchardLeafCluster(ModelBuilder b,double x,double y,double z,double rx,double ry,double rz,
            int count,boolean lightStart) {
        // An open leaf canopy: folded, double-sided blades form the crown without opaque ball-shaped foliage.
        double goldenAngle=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<count;i++) {
            double ny=1-2*(i+0.5)/count,ring=Math.sqrt(Math.max(0,1-ny*ny)),a=i*goldenAngle;
            double nx=ring*Math.cos(a),nz=ring*Math.sin(a);
            double bx=x+nx*rx*0.57,by=y+ny*ry*0.57,bz=z+nz*rz*0.57;
            double azimuth=a+(i%3-1)*0.37;
            double upward=0.12+0.46*((i*7)%5)/4.0+ny*0.18;
            double length=0.15+0.055*((i*11)%4);
            double[] start={bx-nx*0.045,by-0.025,bz-nz*0.045};
            double[] end={bx+Math.cos(azimuth)*length,by+upward,bz+Math.sin(azimuth)*length};
            SurfaceMaterial tone=(i%4==0 || (lightStart && i%3==0)) ? LEAF_LIGHT : LEAF;
            leaf(b,tone,start,end,0.045+0.008*(i%3));
        }
    }

    private static void berryBush(ModelBuilder b,int kind) {
        SurfaceMaterial berry=kind==1 ? BERRY_GREEN : kind==2 ? BERRY_RASPBERRY : kind==3 ? BERRY_BLUE : BERRY_RED;
        int canes=kind==2 ? 7 : 6;
        double height=kind==2 ? 1.55 : kind==3 ? 1.05 : kind==1 ? 1.08 : 1.25;
        double spread=kind==2 ? 0.56 : kind==1 ? 0.72 : kind==3 ? 0.52 : 0.62;
        for(int i=0;i<canes;i++) {
            double a=2*Math.PI*i/canes,x=Math.cos(a)*spread*0.48,z=Math.sin(a)*spread*0.48;
            double lean=kind==2 ? 0.14 : 0.07;
            double tipX=x+Math.cos(a)*lean,tipZ=z+Math.sin(a)*lean;
            b.rod(BARK,new double[]{0,0,0},new double[]{tipX,height*(0.85+(i%3)*0.06),tipZ},kind==2 ? 0.018 : 0.025);
            if(kind==1) {
                for(int j=1;j<=3;j++) {
                    double t=j*0.22,px=tipX*t,pz=tipZ*t,py=height*t;
                    b.rod(BARK,new double[]{px,py,pz},
                            new double[]{px+Math.cos(a+Math.PI/2)*0.075,py+0.12,pz+Math.sin(a+Math.PI/2)*0.075},0.006);
                }
            }
            int leafCount=kind==1 ? 2 : 3;
            for(int j=0;j<leafCount;j++) {
                double t=(kind==1 ? 0.38 : 0.32)+j*(kind==1 ? 0.27 : 0.2),px=tipX*t,pz=tipZ*t,py=height*t+0.12;
                b.rod(BARK,new double[]{px,py,pz},
                        new double[]{px+Math.cos(a+0.8)*0.17,py+0.12,pz+Math.sin(a+0.8)*0.17},0.009);
                crown(b,(i+j)%3==0 ? LEAF_LIGHT : LEAF,px,py,pz,0.16,0.12,0.14);
                double bx=px+Math.cos(a+0.8)*0.16,bz=pz+Math.sin(a+0.8)*0.16,by=py-0.06;
                int berries=kind==2 ? 2 : 3;
                for(int k=0;k<berries;k++) {
                    double ba=a+k*Math.PI/2,br=0.075;
                    double berrySize=kind==1 ? 0.037 : kind==3 ? 0.048 : 0.043;
                    crown(b,berry,bx+Math.cos(ba)*br,by-(k%2)*0.035,bz+Math.sin(ba)*br,
                            berrySize,berrySize*1.08,berrySize);
                }
            }
        }
    }

    private static void grapevineVariant(ModelBuilder b,int style) {
        double halfSpan=style==0 ? 1.45 : 1.3;
        if(style==0) {
            for(double x:new double[]{-halfSpan,halfSpan}) for(double z:new double[]{-0.72,0.72})
                b.rod(WOOD,new double[]{x,0,z},new double[]{x,2.0,z},0.055);
            for(double z:new double[]{-0.72,0.72})
                b.rod(WOOD,new double[]{-halfSpan,2.0,z},new double[]{halfSpan,2.0,z},0.045);
            for(int i=0;i<5;i++) {
                double x=-halfSpan+i*halfSpan*0.5;
                b.rod(WOOD,new double[]{x,2.0,-0.72},new double[]{x,2.0,0.72},0.035);
            }
        } else {
            for(double x:new double[]{-halfSpan,halfSpan})
                b.rod(WOOD,new double[]{x,0,0},new double[]{x,style==1 ? 1.8 : 1.65,0},0.06);
            for(double y:new double[]{0.7,1.15,1.6})
                b.rod(METAL,new double[]{-halfSpan,y,0},new double[]{halfSpan,y,0},0.009);
        }
        SurfaceMaterial grape=style==1 ? BERRY_RED : style==2 ? FRUIT_GOLD : BERRY_BLUE;
        int clusters=style==0 ? 8 : 7;
        for(int i=0;i<clusters;i++) {
            double t=i/(double)(clusters-1),x=-halfSpan+2*halfSpan*t;
            double z=style==0 ? (i%2==0 ? -0.48 : 0.48) : (i%2==0 ? -0.2 : 0.2);
            double y=style==0 ? 2.08 : 1.18+(i%3)*0.18;
            b.rod(BARK,new double[]{x,y-0.18,z},new double[]{x+0.11,y,z},0.016);
            for(int j=0;j<3;j++) {
                double a=j*2*Math.PI/3;
                double endY=style==0 ? 2.05 : y+0.18;
                leaf(b,j%2==0 ? LEAF : LEAF_LIGHT,new double[]{x,y,z},
                        new double[]{x+Math.cos(a)*0.22,endY,z+Math.sin(a)*0.22},0.12);
            }
            for(int j=0;j<5;j++) {
                double a=j*2.4,r=j<4 ? 0.047 : 0.03;
                crown(b,grape,x+0.08+r*Math.cos(a),y-0.12-j*0.022,z+r*Math.sin(a),0.032,0.037,0.032);
            }
        }
    }

    private static void gardenPlant(ModelBuilder b,int v) {
        if(v==12 || v==13) { grapevine(b,v==13); return; }
        if(v>=14 && v<=20) { fruitTree(b,v-14); return; }
        if(v>=21 && v<=23) { grapevineVariant(b,v-21); return; }
        if(v>=24) { berryBush(b,v-24); return; }
        if(v<=2) {
            double height=v==0 ? 4.2 : v==1 ? 2.8 : 4.8,radius=v==1 ? 0.85 : v==2 ? 0.7 : 1.3;
            SurfaceMaterial trunk=v==2 ? WHITE : BARK;
            b.rod(trunk,new double[]{0,0,0},new double[]{0,height*0.78,0},v==2 ? 0.075 : 0.12);
            for(int i=0;i<9;i++) {
                double a=i*2.39996,y=height*(0.4+0.045*i),r=radius*(0.6+0.15*(i%3));
                double x=Math.cos(a)*r,z=Math.sin(a)*r;
                b.rod(BARK,new double[]{0,y-0.45,0},new double[]{x,y,z},0.035);
                crown(b,i%2==0 ? LEAF : LEAF_LIGHT,x,y+0.2,z,radius*0.6,height*0.2,radius*0.6);
                if(v==1) for(int j=0;j<3;j++)
                    crown(b,PETAL,x+0.24*Math.cos(j*2.1),y-0.22,z+0.24*Math.sin(j*2.1),0.045,0.05,0.045);
            }
        } else if(v==3 || v==4) {
            double h=v==3 ? 3.6 : 2.2;
            b.rod(BARK,new double[]{0,0,0},new double[]{0,h*0.9,0},0.07);
            for(int i=0;i<8;i++) {
                double y=0.25+i*h*0.095,r=(v==3 ? 0.95 : 0.44)*(1-i*0.095);
                b.shell(NEEDLE,0,0,1,new double[][]{{0.001,y,0.001},{r,y,r},{0.012,Math.min(h,y+h*0.3),0.012}});
            }
        } else if(v==5 || v==6 || v==7 || v==8 || v==9) {
            int count=7;
            for(int i=0;i<count;i++) {
                double a=i*2.39996,x=v==8 ? -1.2+i*0.4 : Math.cos(a)*0.27,z=v==8 ? 0 : Math.sin(a)*0.27;
                double h=v==8 ? 0.75 : v==9 ? 0.7 : v==5 ? 0.8 : 0.9;
                b.rod(BARK,new double[]{x,0,z},new double[]{x,h,z},0.025);
                crown(b,v==5 || v==8 ? NEEDLE : i%2==0 ? LEAF : LEAF_LIGHT,x,h*0.7,z,v==8 ? 0.35 : 0.4,h*0.5,0.4);
                if(v==7 || v==9) flower(b,x,h*1.24,z,0.1,v==9 ? PETAL : WHITE);
            }
        } else {
            int count=v==10 ? 7 : 20;
            for(int i=0;i<count;i++) {
                double x=(i%5)*0.16-0.32,z=(i/5)*0.15-0.22,h=0.3+(i%3)*0.045;
                b.rod(LEAF,new double[]{x,0,z},new double[]{x,h,z},0.006);
                leaf(b,LEAF_LIGHT,new double[]{x,0.08,z},new double[]{x+0.1,h*0.7,z+0.04},0.025);
                if(v==10) crown(b,i%2==0 ? PETAL : YELLOW,x,h,z,0.035,0.06,0.035);
                else flower(b,x,h,z,0.065,i%2==0 ? PETAL : WHITE);
            }
        }
    }

    private static void indoorPlant(ModelBuilder b,int v) {
        double ph=v<3 ? 0.38 : 0.18,pr=v<3 ? 0.23 : 0.13;
        pot(b,pr,ph);
        if(v==0) {
            b.rod(BARK,new double[]{0,ph,0},new double[]{0,1.4,0},0.02);
            for(int i=0;i<18;i++) {
                double a=i*2.39996,h=0.55+i*0.045,x=0.32*Math.cos(a),z=0.32*Math.sin(a);
                b.rod(BARK,new double[]{0,h-0.08,0},new double[]{x,h,z},0.005);
                leaf(b,i%2==0 ? LEAF : LEAF_LIGHT,new double[]{x,h,z},new double[]{x*1.5,h+0.1,z*1.5},0.06);
            }
        } else if(v==1 || v==2) {
            b.rod(BARK,new double[]{0,ph,0},new double[]{0,v==1 ? 0.9 : 1.4,0},0.025);
            for(int i=0;i<(v==1 ? 10 : 12);i++) {
                double a=i*2.39996,h=v==1 ? 0.6+i*0.045 : 1.3+(i%3)*0.1;
                double x=Math.cos(a)*(v==1 ? 0.35 : 0.65),z=Math.sin(a)*(v==1 ? 0.35 : 0.65);
                b.rod(LEAF,new double[]{0,ph+0.1,0},new double[]{x,h,z},0.008);
                if(v==1) for(int j=0;j<4;j++) {
                    double t=j/4.0;
                    leaf(b,LEAF,new double[]{x*t,h-0.15,z*t},new double[]{x*(t+0.45)-z*0.25,h+0.08,z*(t+0.45)+x*0.25},0.07);
                } else for(int j=1;j<=9;j++) for(int side:new int[]{-1,1}) {
                    double t=j/10.0;
                    leaf(b,j%2==0 ? LEAF : LEAF_LIGHT,new double[]{x*t,h+0.12*(1-t),z*t},
                            new double[]{x*t-side*Math.sin(a)*0.2,h-0.08,z*t+side*Math.cos(a)*0.2},0.025);
                }
            }
        } else if(v==3 || v==4) {
            for(int i=0;i<12;i++) {
                double a=i*2.39996,r=v==3 ? 0.11 : 0.24,h=v==3 ? 0.5+(i%3)*0.12 : 0.12+(i%3)*0.05;
                leaf(b,i%3==0 ? LEAF_LIGHT : LEAF,new double[]{0,ph*0.88,0},
                        new double[]{r*Math.cos(a),ph+h,r*Math.sin(a)},v==3 ? 0.025 : 0.045);
            }
        } else if(v==5) {
            crown(b,NEEDLE,0,0.43,0,0.065,0.27,0.065);
            for(int side:new int[]{-1,1}) {
                b.rod(NEEDLE,new double[]{0,0.36,0},new double[]{side*0.13,0.36,0},0.035);
                crown(b,NEEDLE,side*0.13,0.44,0,0.04,0.1,0.04);
            }
            for(int i=0;i<18;i++) {
                double a=i*2.39996,y=0.24+i*0.023,x=0.062*Math.cos(a),z=0.062*Math.sin(a);
                b.rod(WHITE,new double[]{x,y,z},new double[]{x*1.18,y+0.012,z*1.18},0.002);
            }
        } else if(v==6) {
            for(int i=0;i<5;i++) {
                double a=i*2.39996;
                leaf(b,LEAF,new double[]{0,ph,0},new double[]{0.22*Math.cos(a),ph+0.06,0.22*Math.sin(a)},0.055);
            }
            b.rod(LEAF,new double[]{0,ph,0},new double[]{0.04,0.65,0},0.005);
            for(int i=0;i<4;i++) flower(b,0.04+i*0.025,0.43+i*0.075,0,0.065,WHITE);
        } else {
            for(int i=0;i<12;i++) {
                double a=i*2.39996,x=0.16*Math.cos(a),z=0.16*Math.sin(a),h=0.26+(i%3)*0.04;
                b.rod(LEAF,new double[]{0,ph,0},new double[]{x,h,z},0.004);
                leaf(b,LEAF,new double[]{0,ph,0},new double[]{x*1.3,h,z*1.3},0.045);
                flower(b,x,h+0.05,z,0.05,PETAL);
            }
        }
    }

    public static void write(Path root) throws IOException {
        Map<hu.helmetsoft.homedesigner.util.Texture,Path> textures=new IdentityHashMap<>();
        Set<Path> written=new HashSet<>();
        for(Asset asset:ASSETS) {
            Path directory=root.resolve(asset.category.name()); Files.createDirectories(directory);
            ModelData model=create(asset);
            for(var part:model.parts) if(part.material.albedo!=null) {
                Path texture=textures.computeIfAbsent(part.material.albedo,k -> root.resolve("texture_"+textures.size()+".png"));
                if(written.add(texture)) {
                    var image=new java.awt.image.BufferedImage(part.material.albedo.w,part.material.albedo.h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0,0,part.material.albedo.w,part.material.albedo.h,part.material.albedo.argb,0,part.material.albedo.w);
                    if(!javax.imageio.ImageIO.write(image,"png",texture.toFile())) throw new IOException("PNG képkódoló nem található.");
                }
            }
            BuiltinModels.writeObj(model,directory.resolve(asset.name+".obj"),textures);
        }
    }
    public static void main(String[] args) throws IOException {
        if(args.length!=1) throw new IllegalArgumentException("Használat: HomeModels <célmappa>");
        write(Path.of(args[0])); System.out.println(ASSETS.size()+" eredeti otthonmodell elkészült.");
    }
}
