package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.*;

public final class CabinetGeometry {
    private CabinetGeometry() {}

    public static void validate(Furniture f) {
        Cabinet c = f.cabinet;
        if (c == null) return;
        for (double v : new double[]{f.width,f.height,f.depth,f.x,f.y,f.angle,f.elevation,c.board,c.plinth})
            if (!Double.isFinite(v)) throw new IllegalArgumentException(I18n.text("A szekrény adatai véges számok legyenek."));
        if (f.stair != null || f.width < 0.2 || f.width > 8 || f.height < 0.3 || f.height > 4 || f.depth < 0.15 || f.depth > 1.5
                || c.columns < 1 || c.columns > 10 || c.shelves < 0 || c.shelves > 12 || c.drawers < 0 || c.drawers > 8
                || c.board < 0.01 || c.board > 0.05 || c.plinth < 0 || c.plinth > 0.3
                || c.body == null || c.fronts == null || c.handles == null
                || (f.width-(c.columns+1)*c.board)/c.columns < 0.12
                || (f.height-c.plinth-2*c.board)/(c.shelves+1) < c.board+0.04
                || f.height-c.plinth-2*c.board < c.drawers*0.13+(c.doors ? 0.15 : 0.04)
                || c.shelves>0 && (f.height-c.plinth-2*c.board-c.drawers*drawerHeight(f))/(c.shelves+1)<c.board+0.04
                || f.depth < 4*c.board)
            throw new IllegalArgumentException(I18n.text("Érvénytelen szekrényméret vagy túl sok rekesz/polc/fiók. Rekeszenként legalább 12 cm szélesség és fiókonként 13 cm magasság szükséges."));
    }

    public static ModelData build(Furniture f) {
        validate(f); Cabinet c = f.cabinet;
        ModelBuilder b = new ModelBuilder();
        var body = MaterialLibrary.resolve(c.body); var front = MaterialLibrary.resolve(c.fronts); var metal = MaterialLibrary.resolve(c.handles);
        double t=c.board, w=f.width, h=f.height, d=f.depth, x=-w/2, z=-d/2, bottom=c.plinth;
        if (bottom > 0) b.box(body,x+t,0,z+t,w-2*t,bottom,d-2*t);
        b.box(body,x,bottom,z,t,h-bottom,d);
        b.box(body,x+w-t,bottom,z,t,h-bottom,d);
        b.box(body,x+t,bottom,z,w-2*t,t,d);
        b.box(body,x+t,h-t,z,w-2*t,t,d);
        b.box(body,x+t,bottom+t,z,w-2*t,h-bottom-2*t,t/2);
        double cell=(w-(c.columns+1)*t)/c.columns, innerH=h-bottom-2*t;
        double drawerH=drawerHeight(f), drawerSpace=c.drawers*drawerH;
        for (int col=0; col<c.columns; col++) {
            double left=x+t+col*(cell+t);
            if (col > 0) b.box(body,left-t,bottom+t,z+t/2,t,innerH,d-t/2);
            for (int s=1; s<=c.shelves; s++) b.box(body,left,bottom+t+drawerSpace+(innerH-drawerSpace)*s/(c.shelves+1),z+t/2,cell,t,d-2*t);
            for (int row=0; row<c.drawers; row++) {
                double y=bottom+t+row*drawerH;
                b.box(front,left+0.002,y+0.002,d/2-t,cell-0.004,drawerH-0.004,t);
                b.box(body,left+t,y+t,z+2*t,cell-2*t,t,d-4*t);
                for(double side:new double[]{left,left+cell-t})
                    b.box(body,side,y+t,z+2*t,t,drawerH-2*t,d-4*t);
                b.rod(metal,new double[]{left+cell*0.3,y+drawerH*0.65,d/2+0.018},
                        new double[]{left+cell*0.7,y+drawerH*0.65,d/2+0.018},0.007);
            }
            if (c.doors) {
                double y=bottom+t+c.drawers*drawerH, dh=h-t-y-0.004;
                if (c.openDoors) {
                    b.box(front,left,y,d/2-t,t,dh,cell-0.004);
                    b.rod(metal,new double[]{left+t+0.015,y+dh*0.4,d/2+cell-0.08},
                            new double[]{left+t+0.015,y+dh*0.6,d/2+cell-0.08},0.007);
                } else {
                    b.box(front,left+0.002,y,d/2-t,cell-0.004,dh,t);
                    b.rod(metal,new double[]{left+cell-0.04,y+dh*0.4,d/2+0.018},
                            new double[]{left+cell-0.04,y+dh*0.6,d/2+0.018},0.007);
                }
            }
        }
        ModelData model=b.build("Paraméteres szekrény");
        // Keep carcass dimensions stable when doors project beyond the footprint.
        model.minX=(float)(-w/2); model.maxX=(float)(w/2); model.minY=0; model.maxY=(float)h;
        model.minZ=(float)(-d/2); model.maxZ=(float)(d/2);
        return model;
    }

    private static double drawerHeight(Furniture f) {
        Cabinet c=f.cabinet;
        return c.drawers==0 ? 0 : Math.min(0.24,(f.height-c.plinth-2*c.board)/(c.drawers+(c.doors ? 1 : 0.2)));
    }
}
