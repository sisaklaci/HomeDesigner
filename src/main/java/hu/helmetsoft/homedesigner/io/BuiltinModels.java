package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.Finish;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Original, unbranded metric assets, generated locally without external downloads. */
public final class BuiltinModels {
    public static final String FOLDER = "HelmetSoft_Furdoszoba";
    public static final List<String> NAMES = List.of("Mosdoszekreny_80","Dupla_mosdoszekreny_120","Mosdo_talpazattal_60",
            "Mosdotal_pultra_50","WC_tartalyos","WC_fali","Bide","Kad_szabadonallo_170","Kad_egyenes_170",
            "Zuhanykabin_90","Zuhanyfal_walk_in_120","Furdoszobaszekreny_40","Tukros_szekreny_80",
            "Mosogep_60","Torolkozoszarito_50");
    private static final SurfaceMaterial CERAMIC = ModelBuilder.material("Feher_keramia",0.96,0.965,0.955,0.13,false,false);
    private static final SurfaceMaterial CHROME = ModelBuilder.material("Krom",0.8,0.83,0.86,0.08,true,false);
    private static final SurfaceMaterial GLASS = ModelBuilder.material("Atlatszo_uveg",0.86,0.95,0.96,0.02,false,true);
    private static final SurfaceMaterial OAK = MaterialLibrary.resolve(new Finish("oak_wood",null));
    private static final SurfaceMaterial WHITE = ModelBuilder.material("Feher_lakk",0.91,0.92,0.91,0.25,false,false);
    private static final SurfaceMaterial DARK = ModelBuilder.material("Antracit",0.055,0.065,0.075,0.35,false,false);
    private static final SurfaceMaterial SEAT = ModelBuilder.material("Ulo_ke",0.99,0.99,0.98,0.22,false,false);
    private static final SurfaceMaterial MIRROR = ModelBuilder.material("Tukor",0.88,0.91,0.94,0.01,true,false);

    private BuiltinModels() {}

    public static ModelData create(String name) {
        if (!NAMES.contains(name)) throw new IllegalArgumentException(I18n.text("Ismeretlen beépített modell: ")+name);
        ModelBuilder b = new ModelBuilder();
        switch (name) {
            case "Mosdoszekreny_80" -> vanity(b,0.8,false);
            case "Dupla_mosdoszekreny_120" -> vanity(b,1.2,true);
            case "Mosdo_talpazattal_60" -> {
                b.shell(CERAMIC,0,0,0.7,new double[][]{{0.001,0,0.001},{0.18,0,0.16},{0.15,0.06,0.14},
                        {0.09,0.6,0.1},{0.16,0.72,0.16},{0.001,0.72,0.001}});
                basin(b,0,0,0.71,0.6,0.47); faucet(b,0,0.88,-0.18);
            }
            case "Mosdotal_pultra_50" -> basin(b,0,0,0,0.5,0.38);
            case "WC_tartalyos" -> toilet(b,true);
            case "WC_fali" -> toilet(b,false);
            case "Bide" -> {
                b.shell(CERAMIC,0,0,0.8,new double[][]{{0.001,0,0.001},{0.16,0,0.19},{0.16,0.22,0.23},
                        {0.185,0.35,0.28},{0.18,0.4,0.28},{0.15,0.4,0.245},{0.12,0.3,0.19},{0.025,0.23,0.025},{0.001,0.23,0.001}});
                drain(b,0,0.232,0); faucet(b,0,0.4,-0.19);
            }
            case "Kad_szabadonallo_170" -> bathtub(b,true);
            case "Kad_egyenes_170" -> bathtub(b,false);
            case "Zuhanykabin_90" -> shower(b,false);
            case "Zuhanyfal_walk_in_120" -> shower(b,true);
            case "Furdoszobaszekreny_40" -> cabinet(b);
            case "Tukros_szekreny_80" -> {
                b.box(WHITE,-0.4,0,-0.08,0.8,0.7,0.16);
                for (int side : new int[]{-1,1}) {
                    b.box(MIRROR,side < 0 ? -0.39 : 0.005,0.01,0.081,0.385,0.68,0.008);
                    b.box(CHROME,side*0.025-0.006,0.1,0.09,0.012,0.1,0.025);
                }
            }
            case "Mosogep_60" -> washingMachine(b);
            case "Torolkozoszarito_50" -> {
                for (int side : new int[]{-1,1}) b.rod(CHROME,new double[]{side*0.235,0,0},new double[]{side*0.235,1.2,0},0.018);
                for (int i = 0; i < 18; i++) {
                    double y = 0.05+i*0.064;
                    b.rod(CHROME,new double[]{-0.235,y,0},new double[]{0.235,y,0},0.013);
                }
                for (int side : new int[]{-1,1}) for (double y : new double[]{0.1,1.1})
                    b.rod(CHROME,new double[]{side*0.235,y,0},new double[]{side*0.235,y,-0.08},0.012);
            }
        }
        return b.build(name);
    }

    private static void basin(ModelBuilder b, double x, double z, double y, double width, double depth) {
        b.shell(CERAMIC,x,z,0.7,new double[][]{{0.001,y,0.001},{width*0.31,y,depth*0.3},
                {width*0.43,y+0.035,depth*0.43},{width*0.5,y+0.13,depth*0.5},{width*0.49,y+0.15,depth*0.49},
                {width*0.435,y+0.15,depth*0.425},{width*0.39,y+0.065,depth*0.37},
                {width*0.24,y+0.027,depth*0.23},{0.022,y+0.022,0.022},{0.001,y+0.022,0.001}});
        drain(b,x,y+0.024,z);
    }

    private static void drain(ModelBuilder b, double x, double y, double z) {
        b.rod(CHROME,new double[]{x,y-0.004,z},new double[]{x,y,z},0.022);
        b.rod(DARK,new double[]{x,y,z},new double[]{x,y+0.001,z},0.006);
    }

    private static void faucet(ModelBuilder b, double x, double y, double z) {
        b.rod(CHROME,new double[]{x,y,z},new double[]{x,y+0.18,z},0.018);
        b.rod(CHROME,new double[]{x,y+0.17,z},new double[]{x,y+0.17,z+0.13},0.016);
        b.rod(CHROME,new double[]{x,y+0.17,z+0.13},new double[]{x,y+0.14,z+0.13},0.016);
        b.box(CHROME,x-0.012,y+0.185,z-0.015,0.024,0.012,0.085);
    }

    private static void vanity(ModelBuilder b, double width, boolean two) {
        b.box(OAK,-width/2,0.12,-0.25,width,0.65,0.5);
        for (int side : new int[]{-1,1}) for (double z : new double[]{-0.2,0.2})
            b.box(DARK,side*(width/2-0.06)-0.015,0,z-0.015,0.03,0.12,0.03);
        for (int i = 0; i < 2; i++) {
            double x = -width/2+0.012+i*width/2;
            b.box(WHITE,x,0.14,0.252,width/2-0.024,0.61,0.018);
            b.rod(CHROME,new double[]{x+0.06,0.66,0.29},new double[]{x+width/2-0.08,0.66,0.29},0.008);
        }
        b.box(WHITE,-width/2-0.01,0.77,-0.26,width+0.02,0.035,0.52);
        for (double x : two ? new double[]{-width/4,width/4} : new double[]{0}) {
            basin(b,x,0.035,0.805,two ? 0.49 : 0.58,0.36);
            faucet(b,x,0.805,-0.19);
        }
    }

    private static void toilet(ModelBuilder b, boolean tank) {
        b.shell(CERAMIC,0,0.035,0.85,new double[][]{{0.001,0,0.001},{0.135,0,0.2},{0.14,0.1,0.22},
                {0.13,0.24,0.21},{0.18,0.35,0.27},{0.185,0.39,0.28},{0.15,0.4,0.245},
                {0.13,0.32,0.2},{0.065,0.22,0.11},{0.001,0.2,0.001}});
        b.shell(SEAT,0,0.035,0.85,new double[][]{{0.152,0.4,0.24},{0.19,0.4,0.285},
                {0.19,0.425,0.285},{0.15,0.425,0.235},{0.152,0.4,0.24}});
        drain(b,0,0.205,0.035);
        if (tank) {
            b.box(CERAMIC,-0.175,0.36,-0.37,0.35,0.4,0.18);
            b.box(SEAT,-0.18,0.76,-0.375,0.36,0.025,0.19);
            b.rod(CHROME,new double[]{0,0.785,-0.28},new double[]{0,0.79,-0.28},0.025);
        } else b.box(CERAMIC,-0.13,0.2,-0.3,0.26,0.21,0.12);
    }

    private static void bathtub(ModelBuilder b, boolean freestanding) {
        double exponent = freestanding ? 0.8 : 0.24;
        b.shell(CERAMIC,0,0,exponent,new double[][]{{0.001,0,0.001},{0.28,0,0.7},{0.36,0.08,0.8},
                {0.39,0.49,0.85},{0.39,0.57,0.85},{0.35,0.59,0.81},{0.32,0.56,0.77},
                {0.28,0.17,0.63},{0.22,0.12,0.55},{0.001,0.12,0.001}});
        drain(b,0,0.123,0.35);
        double x = freestanding ? 0.47 : 0.34;
        if (freestanding) b.rod(CHROME,new double[]{x,0,0.48},new double[]{x,0.64,0.48},0.025);
        b.rod(CHROME,new double[]{x,0.64,0.48},new double[]{0.22,0.64,0.48},0.018);
        b.rod(CHROME,new double[]{0.22,0.64,0.48},new double[]{0.22,0.59,0.48},0.018);
        b.box(CHROME,x-0.012,0.65,0.46,0.024,0.015,0.09);
    }

    private static void shower(ModelBuilder b, boolean walkIn) {
        double width = walkIn ? 1.2 : 0.9, depth = 0.9;
        b.shell(CERAMIC,0,0,0.18,new double[][]{{0.001,0,0.001},{width/2,0,depth/2},{width/2,0.08,depth/2},
                {width/2-0.025,0.09,depth/2-0.025},{width/2-0.05,0.045,depth/2-0.05},{0.001,0.04,0.001}});
        drain(b,0,0.043,-0.25);
        b.box(GLASS,-width/2+0.015,0.09,-depth/2+0.015,0.008,1.91,depth-0.03);
        if (!walkIn) {
            b.box(GLASS,-width/2+0.02,0.09,depth/2-0.025,width-0.04,1.91,0.008);
            for (double x : new double[]{-width/2+0.015,width/2-0.015})
                b.rod(CHROME,new double[]{x,0.09,depth/2-0.025},new double[]{x,2,depth/2-0.025},0.013);
            b.rod(CHROME,new double[]{-width/2,2,depth/2-0.025},new double[]{width/2,2,depth/2-0.025},0.015);
            b.rod(CHROME,new double[]{0.08,0.95,depth/2},new double[]{0.08,1.15,depth/2},0.011);
            b.box(CHROME,-0.006,0.1,depth/2-0.026,0.012,1.88,0.012);
        } else {
            b.rod(CHROME,new double[]{-width/2+0.018,2,0.1},new double[]{width/2-0.04,2,0.1},0.012);
        }
        b.rod(CHROME,new double[]{0.22,0.75,-depth/2+0.05},new double[]{0.22,1.95,-depth/2+0.05},0.018);
        b.rod(CHROME,new double[]{0.22,1.95,-depth/2+0.05},new double[]{0.22,1.95,-0.05},0.018);
        b.rod(CHROME,new double[]{0.22,1.93,-0.05},new double[]{0.22,1.95,-0.05},0.12);
        b.rod(CHROME,new double[]{0.08,0.85,-depth/2+0.05},new double[]{0.36,0.85,-depth/2+0.05},0.025);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI*i/24, a1 = Math.PI*(i+1)/24;
            b.rod(CHROME,new double[]{0.22+0.12*Math.cos(a),0.8-0.35*Math.sin(a),-depth/2+0.08},
                    new double[]{0.22+0.12*Math.cos(a1),0.8-0.35*Math.sin(a1),-depth/2+0.08},0.006);
        }
    }

    private static void cabinet(ModelBuilder b) {
        b.box(OAK,-0.2,0.08,-0.18,0.4,1.72,0.36);
        for (int j = 0; j < 2; j++) {
            b.box(WHITE,-0.19,0.1+j*0.85,0.181,0.38,0.83,0.018);
            b.rod(CHROME,new double[]{0.13,0.5+j*0.85,0.215},new double[]{0.13,0.68+j*0.85,0.215},0.007);
        }
        for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
            b.box(DARK,x*0.15-0.01,0,z*0.13-0.01,0.02,0.08,0.02);
    }

    private static void washingMachine(ModelBuilder b) {
        b.box(WHITE,-0.3,0,-0.3,0.6,0.85,0.6);
        b.box(DARK,-0.27,0.7,0.301,0.54,0.09,0.012);
        b.rod(CHROME,new double[]{0.17,0.745,0.314},new double[]{0.17,0.745,0.33},0.028);
        b.box(GLASS,-0.18,0.72,0.315,0.18,0.04,0.008);
        // Front-facing rings use the same smooth profile primitive, then rotate Y into Z.
        ModelBuilder door = new ModelBuilder();
        door.shell(CHROME,0,0,1,new double[][]{{0.165,0,0.165},{0.215,0,0.215},{0.215,0.03,0.215},
                {0.165,0.04,0.165},{0.165,0,0.165}});
        door.sphere(DARK,0,0.015,0,0.165,0.025,0.165);
        ModelData model = door.build("door");
        for (ModelData.Part part : model.parts) {
            Mesh m = part.mesh; MeshBuilder target = b.part(part.material);
            int[] ids = new int[m.vertexCount()];
            for (int i = 0; i < ids.length; i++) ids[i] = target.vertex(m.pos[i*3],0.405+m.pos[i*3+2],0.315+m.pos[i*3+1],
                    m.nrm[i*3],m.nrm[i*3+2],m.nrm[i*3+1],m.uv[i*2],m.uv[i*2+1]);
            for (int i = 0; i < m.idx.length; i += 3) target.tri(ids[m.idx[i]],ids[m.idx[i+2]],ids[m.idx[i+1]]);
        }
    }

    public static void write(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (String name : NAMES) writeObj(create(name),directory.resolve(name+".obj"));
    }

    public static void writeObj(ModelData model, Path file) throws IOException {
        writeObj(model,file,Map.of());
    }

    static void writeObj(ModelData model, Path file, Map<hu.helmetsoft.homedesigner.util.Texture,Path> sharedTextures) throws IOException {
        String name = file.getFileName().toString();
        String mtlName = name.substring(0,name.lastIndexOf('.'))+".mtl";
        try (BufferedWriter obj = Files.newBufferedWriter(file,StandardCharsets.UTF_8);
             BufferedWriter mtl = Files.newBufferedWriter(file.resolveSibling(mtlName),StandardCharsets.UTF_8)) {
            obj.write("# Original helmetsoft.com model. CC0-1.0. Meters, Y up.\nmtllib "+mtlName+"\n");
            int offset = 1, partIndex = 0;
            for (ModelData.Part part : model.parts) {
                String id = "material_"+partIndex++;
                SurfaceMaterial mat = part.material;
                mtl.write("newmtl "+id+"\nKd "+mat.color[0]+" "+mat.color[1]+" "+mat.color[2]+"\nKs 0.8 0.8 0.8\nNs "
                        +Math.max(2,1000*(1-mat.roughness))+"\nd "+mat.opacity+"\nPm "+(mat.metal ? 1 : 0)+"\nPr "+mat.roughness
                        +"\nillum "+(mat.glass ? 7 : mat.metal ? 3 : 2)+"\nNi 1.5\n\n");
                if (mat.albedo != null) {
                    String textureName = name.substring(0,name.lastIndexOf('.'))+"_"+id+".png";
                    Path textureFile=sharedTextures.getOrDefault(mat.albedo,file.resolveSibling(textureName));
                    if(!sharedTextures.containsKey(mat.albedo) || !Files.exists(textureFile)) {
                        var image = new java.awt.image.BufferedImage(mat.albedo.w,mat.albedo.h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        image.setRGB(0,0,mat.albedo.w,mat.albedo.h,mat.albedo.argb,0,mat.albedo.w);
                        if (!javax.imageio.ImageIO.write(image,"png",textureFile.toFile()))
                            throw new IOException(I18n.text("PNG képkódoló nem található."));
                    }
                    mtl.write("map_Kd "+file.toAbsolutePath().getParent().relativize(textureFile.toAbsolutePath())+"\n");
                }
                Mesh mesh = part.mesh;
                for (int i = 0; i < mesh.vertexCount(); i++) {
                    obj.write("v "+mesh.pos[i*3]+" "+mesh.pos[i*3+1]+" "+mesh.pos[i*3+2]+"\n");
                    obj.write("vt "+mesh.uv[i*2]+" "+(1-mesh.uv[i*2+1])+"\n");
                    obj.write("vn "+mesh.nrm[i*3]+" "+mesh.nrm[i*3+1]+" "+mesh.nrm[i*3+2]+"\n");
                }
                obj.write("usemtl "+id+"\n");
                for (int i = 0; i < mesh.idx.length; i += 3) {
                    obj.write("f");
                    int a = mesh.idx[i]*3, b = mesh.idx[i+1]*3, c = mesh.idx[i+2]*3;
                    double ux = mesh.pos[b]-mesh.pos[a], uy = mesh.pos[b+1]-mesh.pos[a+1], uz = mesh.pos[b+2]-mesh.pos[a+2];
                    double vx = mesh.pos[c]-mesh.pos[a], vy = mesh.pos[c+1]-mesh.pos[a+1], vz = mesh.pos[c+2]-mesh.pos[a+2];
                    double dot = (uy*vz-uz*vy)*(mesh.nrm[a]+mesh.nrm[b]+mesh.nrm[c])
                            +(uz*vx-ux*vz)*(mesh.nrm[a+1]+mesh.nrm[b+1]+mesh.nrm[c+1])
                            +(ux*vy-uy*vx)*(mesh.nrm[a+2]+mesh.nrm[b+2]+mesh.nrm[c+2]);
                    for (int j = 0; j < 3; j++) {
                        int index = offset+mesh.idx[i+(dot < 0 && j != 0 ? 3-j : j)];
                        obj.write(" "+index+"/"+index+"/"+index);
                    }
                    obj.write("\n");
                }
                offset += mesh.vertexCount();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException(I18n.text("Használat: BuiltinModels <célmappa>"));
        write(Path.of(args[0]));
        System.out.println(NAMES.size()+" eredeti OBJ/MTL fürdőszobamodell elkészült: "+args[0]);
    }
}
