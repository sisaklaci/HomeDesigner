package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.Wall;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/** Open fence members follow the wall axis, including circular arcs and sloping ground. */
public final class FenceGeometry {
    private FenceGeometry() {}

    public static List<GeoPart> build(Wall w,DoubleBinaryOperator ground) {
        w.validate();
        var material=MaterialLibrary.resolve(w.left);
        MeshBuilder mesh=new MeshBuilder(material.tileSize);
        double length=w.length();
        int bays=Math.max(1,(int)Math.ceil(length/w.fencePostSpacing));
        double post=Math.max(0.05,w.thickness), h=w.height;
        for(int i=0;i<=bays;i++) {
            double u=length*i/bays;
            member(mesh,w,ground,Math.max(0,u-post/2),Math.min(length,u+post/2),0,post,-0.03,h);
        }
        if(w.fence==Wall.Fence.SOLID) {
            strip(mesh,w,ground,0,length,0,w.thickness,0,h);
        } else if(w.fence==Wall.Fence.HORIZONTAL) {
            int rows=Math.max(1,(int)Math.ceil(h/0.2));
            double pitch=h/rows;
            for(int i=0;i<rows;i++)
                strip(mesh,w,ground,0,length,0,w.thickness*0.55,i*pitch,i*pitch+pitch*0.7);
        } else {
            double rail=Math.min(0.07,h*0.1);
            for(double y:new double[]{h*0.22,h*0.78})
                strip(mesh,w,ground,0,length,post*0.22,post*0.55,y,y+rail);
            double pitch=w.fence==Wall.Fence.PICKET ? 0.14 : w.fence==Wall.Fence.METAL ? 0.13:0.15;
            int count=Math.max(1,(int)Math.ceil(length/pitch));
            double width=w.fence==Wall.Fence.PICKET ? Math.min(0.09,length/count*0.65):
                    w.fence==Wall.Fence.METAL ? 0.022:0.008;
            for(int i=0;i<count;i++) {
                double u=length*(i+0.5)/count;
                member(mesh,w,ground,Math.max(0,u-width/2),Math.min(length,u+width/2),
                        -post*0.2,Math.min(post*0.5,width),0.04,h-0.025);
            }
            if(w.fence==Wall.Fence.MESH) {
                int rows=Math.max(1,(int)Math.ceil(h/0.15));
                for(int i=1;i<rows;i++) strip(mesh,w,ground,0,length,-post*0.2,0.008,h*i/rows,h*i/rows+0.008);
            }
        }
        return List.of(new GeoPart(w,material,mesh.build()));
    }

    private static void strip(MeshBuilder mesh,Wall w,DoubleBinaryOperator ground,double a,double b,
                              double offset,double width,double bottom,double top) {
        int n=Math.max(1,(int)Math.ceil((b-a)/0.2));
        for(int i=0;i<n;i++) member(mesh,w,ground,a+(b-a)*i/n,a+(b-a)*(i+1)/n,offset,width,bottom,top);
    }

    private static void member(MeshBuilder mesh,Wall w,DoubleBinaryOperator ground,double a,double b,
                               double offset,double width,double bottom,double top) {
        double[] pa=w.point(a,0),pb=w.point(b,0);
        double ha=w.fenceElevation+(w.fenceFollowTerrain ? ground.applyAsDouble(pa[0],pa[1])+0.01:0);
        double hb=w.fenceElevation+(w.fenceFollowTerrain ? ground.applyAsDouble(pb[0],pb[1])+0.01:0);
        double[][] v=new double[8][];
        for(int i=0;i<8;i++) {
            boolean end=(i&1)!=0,upper=(i&2)!=0;
            double[] p=w.point(end ? b:a,offset+((i&4)!=0 ? width/2:-width/2));
            v[i]=new double[]{p[0],(end ? hb:ha)+(upper ? top:bottom),p[1]};
        }
        int[][] faces={{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3},{0,2,3,1},{4,5,7,6}};
        double[] center=new double[3];
        for(double[] p:v) for(int j=0;j<3;j++) center[j]+=p[j]/8;
        for(int[] face:faces) {
            double[] p=v[face[0]],q=v[face[1]],r=v[face[2]],s=v[face[3]];
            double ux=q[0]-p[0],uy=q[1]-p[1],uz=q[2]-p[2],vx=r[0]-p[0],vy=r[1]-p[1],vz=r[2]-p[2];
            double[] normal={uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx};
            double size=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);
            if(size<1e-12) continue;
            double dot=0;
            for(int j=0;j<3;j++) { normal[j]/=size; dot+=normal[j]*((p[j]+q[j]+r[j]+s[j])/4-center[j]); }
            double mid=(a+b)/2;
            double[] along={w.tangentX(mid),0,w.tangentY(mid)},up={0,1,0};
            if(Math.abs(normal[1])>0.9) up=new double[]{w.tangentY(mid),0,-w.tangentX(mid)};
            if(dot<0) {
                for(int j=0;j<3;j++) normal[j]=-normal[j];
                mesh.quad(p,s,r,q,normal,along,up);
            } else mesh.quad(p,q,r,s,normal,along,up);
        }
    }
}
