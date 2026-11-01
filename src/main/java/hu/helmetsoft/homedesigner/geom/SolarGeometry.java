package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.io.HomeModels;
import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.model.SolarPanel;
import java.nio.file.Path;

/** Shared metric solar geometry for the catalog, editor and both renderers. */
public final class SolarGeometry {
    private SolarGeometry() {}
    private static final hu.helmetsoft.homedesigner.material.SurfaceMaterial FRAME=
            ModelBuilder.material("Solar_aluminium",0.64,0.68,0.73,0.22,true,false);
    private static final hu.helmetsoft.homedesigner.material.SurfaceMaterial CELL=
            ModelBuilder.material("Solar_cells",0.018,0.045,0.12,0.16,false,false);
    private static final hu.helmetsoft.homedesigner.material.SurfaceMaterial BACK=
            ModelBuilder.material("Solar_backing",0.12,0.14,0.18,0.5,false,false);

    public static SolarPanel.Type fromPath(String path) {
        if(path==null) return null;
        Path p=Path.of(path);
        if(HomeModels.category(p)!=HomeModels.Category.SOLAR) return null;
        return switch(p.getFileName().toString()) {
            case "Napelem_tetore.obj" -> SolarPanel.Type.ROOF;
            case "Napelem_talajra.obj" -> SolarPanel.Type.GROUND;
            default -> null;
        };
    }

    private static void validate(SolarPanel s) {
        if(s.type==null || !Double.isFinite(s.tilt) || s.tilt<0 || s.tilt>85
                || !Double.isFinite(s.width) || s.width<0.3 || s.width>5
                || !Double.isFinite(s.length) || s.length<0.3 || s.length>5)
            throw new IllegalArgumentException(hu.helmetsoft.homedesigner.util.I18n.text("A napelem dőlésszöge 0–85°, szélessége és hossza 30–500 cm legyen."));
    }

    public static void validate(Furniture f) {
        if(f.solar==null) return;
        validate(f.solar);
        if(f.stair!=null || f.cabinet!=null || f.modelPath!=null || f.upFix!=0
                || !Double.isFinite(f.width) || f.width<=0
                || !Double.isFinite(f.depth) || f.depth<=0 || !Double.isFinite(f.height) || f.height<=0
                || !Double.isFinite(f.x) || !Double.isFinite(f.y) || !Double.isFinite(f.elevation) || !Double.isFinite(f.angle))
            throw new IllegalArgumentException(hu.helmetsoft.homedesigner.util.I18n.text("Érvénytelen napelemméretek vagy ütköző paraméteres objektumtípus."));
    }

    public static double height(SolarPanel s) {
        validate(s);
        double a=Math.toRadians(s.tilt);
        return (s.type==SolarPanel.Type.GROUND ? 0.22 : 0.08)+s.length*Math.sin(a)+0.045*Math.cos(a);
    }

    public static void updateSize(Furniture f) {
        ModelData model=build(f);
        f.width=model.maxX-model.minX;
        f.depth=model.maxZ-model.minZ;
        f.height=model.maxY-model.minY;
    }

    public static ModelData build(Furniture f) {
        SolarPanel s=f.solar; validate(s);
        ModelBuilder b=new ModelBuilder();
        double a=Math.toRadians(s.tilt),c=Math.cos(a),sin=Math.sin(a),w=s.width,l=s.length;
        double low=s.type==SolarPanel.Type.GROUND ? 0.22 : 0.08,z0=-l*c/2;
        double[] x={1,0,0},y={0,c,-sin},z={0,sin,c};
        b.part(BACK).box(new double[]{-w/2,low,z0},x,y,z,w,0.035,l);
        for(int side:new int[]{-1,1}) {
            b.part(FRAME).box(new double[]{side<0 ? -w/2 : w/2-0.025,low,z0},x,y,z,0.025,0.045,l);
            double dz=side<0 ? 0 : l-0.025;
            b.part(FRAME).box(new double[]{-w/2,low+dz*sin,z0+dz*c},x,y,z,w,0.045,0.025);
        }
        for(int row=0;row<10;row++) for(int col=0;col<6;col++) {
            double px=-w/2+0.035+col*(w-0.07)/6,pz=0.035+row*(l-0.07)/10;
            b.part(CELL).box(new double[]{px,low+pz*sin+0.036*c,z0+pz*c-0.036*sin},
                    x,y,z,(w-0.07)/6-0.004,0.003,(l-0.07)/10-0.004);
        }
        for(int side:new int[]{-1,1}) {
            double px=side*w*0.36;
            double za=z0+l*0.12*c,zb=z0+l*0.88*c;
            double ya=low+l*0.12*sin,yb=low+l*0.88*sin;
            if(s.type==SolarPanel.Type.GROUND) {
                double start=z0-0.1,end=-z0+0.1;
                b.box(FRAME,px-0.025,0,start,0.05,0.035,end-start);
                b.rod(FRAME,new double[]{px,0.035,za},new double[]{px,ya,za},0.018);
                b.rod(FRAME,new double[]{px,0.035,zb},new double[]{px,yb,zb},0.018);
                b.rod(FRAME,new double[]{px,0.035,za},new double[]{px,yb,zb},0.014);
            } else {
                for(double[] mount:new double[][]{{ya,za},{yb,zb}}) {
                    b.box(FRAME,px-0.045,0,mount[1]-0.045,0.09,0.015,0.09);
                    b.rod(FRAME,new double[]{px,0.015,mount[1]},new double[]{px,mount[0],mount[1]},0.014);
                }
            }
        }
        return b.build("Solar_"+s.type);
    }
}
