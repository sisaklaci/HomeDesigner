package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.Room;
import hu.helmetsoft.homedesigner.util.I18n;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Clipped vaulted ceiling shells; room height denotes the spring line, not the crown. */
public final class VaultGeometry {
    private VaultGeometry() {}

    public static void validate(Room r) {
        if(r.vault==null) r.vault=Room.Vault.NONE;
        if(r.vault!=Room.Vault.NONE && (!Double.isFinite(r.vaultRise) || r.vaultRise<0.15 || r.vaultRise>5))
            throw new IllegalArgumentException(I18n.text("A boltozat emelkedése 15–500 cm legyen."));
    }

    public static double height(Room r,double spring,double x,double z) {
        Rectangle2D bounds=SlabGeometry.roomArea(r).getBounds2D();
        return height(r,spring,x,z,bounds);
    }

    private static double height(Room r,double spring,double x,double z,Rectangle2D bounds) {
        double u=2*(x-bounds.getCenterX())/Math.max(0.01,bounds.getWidth());
        double v=2*(z-bounds.getCenterY())/Math.max(0.01,bounds.getHeight());
        double cross=r.vaultAlongX ? v : u;
        double curve=profile(r.vault,cross);
        if(r.vault==Room.Vault.GROIN) curve=Math.max(profile(r.vault,u),profile(r.vault,v));
        return spring+r.vaultRise*curve;
    }

    private static double profile(Room.Vault type,double u) {
        u=Math.min(1,Math.abs(u));
        if(type==Room.Vault.POINTED) return (Math.sqrt(4-Math.pow(1+u,2)))/Math.sqrt(3);
        return Math.sqrt(Math.max(0,1-u*u));
    }

    public static List<GeoPart> build(Room r,double spring) {
        validate(r);
        if(!r.hasCeiling || r.vault==Room.Vault.NONE) return List.of();
        Area ceiling=SlabGeometry.surface(r,true);
        Rectangle2D bounds=SlabGeometry.roomArea(r).getBounds2D();
        if(ceiling.isEmpty()) return List.of();
        var material=MaterialLibrary.resolve(r.ceiling);
        MeshBuilder mesh=new MeshBuilder(material.tileSize);
        int nx=Math.max(16,Math.min(100,(int)Math.ceil(bounds.getWidth()/0.18)));
        int nz=Math.max(16,Math.min(100,(int)Math.ceil(bounds.getHeight()/0.18)));
        double dx=bounds.getWidth()/nx,dz=bounds.getHeight()/nz;
        for(int ix=0;ix<nx;ix++) for(int iz=0;iz<nz;iz++) {
            Area cell=new Area(new Rectangle2D.Double(bounds.getMinX()+ix*dx,bounds.getMinY()+iz*dz,dx,dz));
            cell.intersect(ceiling);
            for(double[][] triangle:SlabGeometry.triangles(cell)) {
                double[][] vertices=new double[3][];
                for(int j=0;j<3;j++) vertices[j]=new double[]{triangle[j][0],height(r,spring,triangle[j][0],triangle[j][1],bounds),triangle[j][1]};
                face(mesh,vertices[0],vertices[1],vertices[2],false);
                for(double[] p:vertices) p[1]+=0.12;
                face(mesh,vertices[2],vertices[1],vertices[0],true);
            }
        }
        for(double[][] contour:RoofOutline.contours(ceiling)) for(int i=0;i<contour.length;i++) {
            double[] a=contour[i],b=contour[(i+1)%contour.length];
            int n=Math.max(1,(int)Math.ceil(Math.hypot(a[0]-b[0],a[1]-b[1])/0.18));
            for(int j=0;j<n;j++) {
                double t=(double)j/n,q=(double)(j+1)/n;
                double x=a[0]+(b[0]-a[0])*t,z=a[1]+(b[1]-a[1])*t;
                double xx=a[0]+(b[0]-a[0])*q,zz=a[1]+(b[1]-a[1])*q;
                double y=height(r,spring,x,z,bounds),yy=height(r,spring,xx,zz,bounds);
                face(mesh,new double[]{x,y,z},new double[]{xx,yy,zz},new double[]{xx,yy+0.12,zz},true);
                face(mesh,new double[]{x,y,z},new double[]{xx,yy+0.12,zz},new double[]{x,y+0.12,z},true);
            }
        }
        for(double[][] contour:RoofOutline.contours(SlabGeometry.roomArea(r))) for(int i=0;i<contour.length;i++) {
            double[] a=contour[i],b=contour[(i+1)%contour.length];
            int count=Math.max(1,(int)Math.ceil(Math.hypot(a[0]-b[0],a[1]-b[1])/0.18));
            for(int j=0;j<count;j++) {
                double t=(double)j/count,q=(double)(j+1)/count;
                double x=a[0]+(b[0]-a[0])*t,z=a[1]+(b[1]-a[1])*t;
                double xx=a[0]+(b[0]-a[0])*q,zz=a[1]+(b[1]-a[1])*q;
                double[] p={x,spring,z},p1={xx,spring,zz};
                double[] p2={xx,height(r,spring,xx,zz,bounds),zz},p3={x,height(r,spring,x,z,bounds),z};
                face(mesh,p,p1,p2,true); face(mesh,p,p2,p3,true);
                face(mesh,p2,p1,p,true); face(mesh,p3,p2,p,true);
            }
        }
        if(r.vaultRibs) {
            ModelBuilder ribs=new ModelBuilder();
            double length=r.vaultAlongX ? bounds.getWidth() : bounds.getHeight();
            int count=Math.max(2,Math.min(8,(int)Math.ceil(length/1.5)));
            for(int i=0;i<=count;i++) {
                double along=(r.vaultAlongX ? bounds.getMinX() : bounds.getMinY())+length*i/count;
                double crossMin=r.vaultAlongX ? bounds.getMinY() : bounds.getMinX();
                double crossLength=r.vaultAlongX ? bounds.getHeight() : bounds.getWidth();
                for(int j=0;j<48;j++) {
                    double cross0=crossMin+crossLength*j/48,cross1=crossMin+crossLength*(j+1)/48;
                    double x0=r.vaultAlongX ? along : cross0,z0=r.vaultAlongX ? cross0 : along;
                    double x1=r.vaultAlongX ? along : cross1,z1=r.vaultAlongX ? cross1 : along;
                    if(!ceiling.contains((x0+x1)/2,(z0+z1)/2)) continue;
                    ribs.rod(material,new double[]{x0,height(r,spring,x0,z0,bounds)-0.035,z0},
                            new double[]{x1,height(r,spring,x1,z1,bounds)-0.035,z1},0.055);
                }
            }
            for(var part:ribs.build("Vault_ribs").parts) mesh.append(part.mesh);
        }
        return List.of(new GeoPart(r,material,mesh.build()));
    }

    private static void face(MeshBuilder mesh,double[] a,double[] b,double[] c,boolean outward) {
        double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,n=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(n<1e-10) return;
        if(!outward && ny>0) { double[] swap=b; b=c; c=swap; nx=-nx; ny=-ny; nz=-nz; }
        mesh.triangle(a,b,c,new double[]{nx/n,ny/n,nz/n},new double[]{1,0,0},new double[]{0,0,1});
    }
}
