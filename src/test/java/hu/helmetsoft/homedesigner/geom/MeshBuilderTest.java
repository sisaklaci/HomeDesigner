package hu.helmetsoft.homedesigner.geom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshBuilderTest {
    @Test void axisAlignedAndSlopedBoxesHaveOutwardWindingMatchingTheirNormals() {
        for(double angle:new double[]{0,0.3,-0.5,Math.PI/2}) {
            double c=Math.cos(angle),s=Math.sin(angle);
            MeshBuilder builder=new MeshBuilder(1);
            double[] origin={2,3,4},x={c,s,0},y={-s,c,0},z={0,0,1};
            builder.box(origin,x,y,z,3,0.07,2);
            Mesh mesh=builder.build();
            assertEquals(36,mesh.idx.length);
            double cx=origin[0]+1.5*c-0.035*s,cy=origin[1]+1.5*s+0.035*c,cz=5;
            for(int i=0;i<mesh.idx.length;i+=3) {
                int a=mesh.idx[i]*3,b=mesh.idx[i+1]*3,d=mesh.idx[i+2]*3;
                double ux=mesh.pos[b]-mesh.pos[a],uy=mesh.pos[b+1]-mesh.pos[a+1],uz=mesh.pos[b+2]-mesh.pos[a+2];
                double vx=mesh.pos[d]-mesh.pos[a],vy=mesh.pos[d+1]-mesh.pos[a+1],vz=mesh.pos[d+2]-mesh.pos[a+2];
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
                assertTrue(nx*mesh.nrm[a]+ny*mesh.nrm[a+1]+nz*mesh.nrm[a+2]>0);
                assertTrue((mesh.pos[a]-cx)*mesh.nrm[a]+(mesh.pos[a+1]-cy)*mesh.nrm[a+1]
                        +(mesh.pos[a+2]-cz)*mesh.nrm[a+2]>0);
            }
        }
    }
}
