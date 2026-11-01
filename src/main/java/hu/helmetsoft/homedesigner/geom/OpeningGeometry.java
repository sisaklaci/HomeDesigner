package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.model.Opening;
import java.util.ArrayList;
import java.util.List;

/** Profiles in wall distance/height coordinates, counterclockwise. */
public final class OpeningGeometry {
    private OpeningGeometry() {}
    public static void validate(Opening o) {
        if(o.top==null) throw new IllegalArgumentException(I18n.text("Hiányzó nyílásforma."));
        if(o.type==Opening.Type.DOOR && o.glazed) {
            if(!Double.isFinite(o.glassX) || !Double.isFinite(o.glassY) || !Double.isFinite(o.glassWidth) || !Double.isFinite(o.glassHeight)
                    || o.glassWidth<=0 || o.glassHeight<=0 || o.glassX<0 || o.glassY<0
                    || o.glassX+o.glassWidth>1 || o.glassY+o.glassHeight>1)
                throw new IllegalArgumentException(I18n.text("Az üvegbetét mérete és helyzete legyen az ajtólapon belül."));
            var allowed=RoofOutline.area(profile(o));
            allowed.subtract(new java.awt.geom.Area(new java.awt.BasicStroke(0.16f)
                    .createStrokedShape(RoofOutline.path(profile(o)))));
            var outside=glassArea(o); outside.subtract(allowed);
            if(!outside.isEmpty()) throw new IllegalArgumentException(I18n.text("Az üvegbetét az ív alatt, az ajtó széleitől legalább 8 cm-re legyen."));
        }
        if(o.top==Opening.Top.RECTANGLE) return;
        double rise=o.top==Opening.Top.SEMICIRCLE ? o.width/2 : o.width;
        if(!Double.isFinite(o.width) || !Double.isFinite(o.height) || !Double.isFinite(o.pos) || !Double.isFinite(o.sill)
                || o.width<0.2 || o.height-rise<0.15 || o.sill<0)
            throw new IllegalArgumentException(I18n.text("Az ív alatt legalább 15 cm egyenes szár szükséges. Félkörív sugara a szélesség fele, negyedkörívé a teljes szélesség."));
    }
    public static java.awt.geom.Area glassArea(Opening o) {
        double x=o.pos-o.width/2+o.glassX*o.width,y=o.sill+o.glassY*o.height;
        return RoofOutline.area(RoofGeometry.rect(x,y,x+o.glassWidth*o.width,y+o.glassHeight*o.height));
    }
    public static double top(Opening o,double fraction) {
        double t=Math.max(0,Math.min(1,fraction));
        return o.sill+o.height-switch(o.top) {
            case RECTANGLE -> 0;
            case SEMICIRCLE -> o.width/2*(1-Math.sqrt(Math.max(0,1-(2*t-1)*(2*t-1))));
            case QUARTER_LEFT -> o.width*(1-Math.sqrt(Math.max(0,1-t*t)));
            case QUARTER_RIGHT -> o.width*(1-Math.sqrt(Math.max(0,1-(1-t)*(1-t))));
        };
    }
    public static double[][] profile(Opening o) {
        List<double[]> p=new ArrayList<>();
        double start=o.pos-o.width/2;
        p.add(new double[]{start,o.sill}); p.add(new double[]{start+o.width,o.sill});
        int n=o.top==Opening.Top.RECTANGLE ? 1 : 64;
        for(int i=n;i>=0;i--) {
            double fraction;
            // Equal angular steps give sub-millimetre chord error on ordinary joinery.
            if(o.top==Opening.Top.SEMICIRCLE) fraction=(1-Math.cos(Math.PI*i/n))/2;
            else if(o.top==Opening.Top.QUARTER_LEFT) fraction=Math.sin(Math.PI*0.5*i/n);
            else if(o.top==Opening.Top.QUARTER_RIGHT) fraction=1-Math.cos(Math.PI*0.5*i/n);
            else fraction=(double)i/n;
            p.add(new double[]{start+o.width*fraction,top(o,fraction)});
        }
        return p.toArray(double[][]::new);
    }
}
