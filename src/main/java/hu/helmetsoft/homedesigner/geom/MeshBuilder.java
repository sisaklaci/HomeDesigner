package hu.helmetsoft.homedesigner.geom;

import java.util.Arrays;

/** Incremental mesh construction; UVs are given in meters and divided by the tile size. */
public final class MeshBuilder {
    private float[] p = new float[3 * 64], n = new float[3 * 64], t = new float[2 * 64];
    private int[] ix = new int[96];
    private int vc, ic;
    private final double invTile;

    public MeshBuilder(double tileSize) {
        invTile = 1.0 / (tileSize > 0 ? tileSize : 1);
    }

    public int vertex(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        if ((vc + 1) * 3 > p.length) {
            p = Arrays.copyOf(p, p.length * 2);
            n = Arrays.copyOf(n, n.length * 2);
            t = Arrays.copyOf(t, t.length * 2);
        }
        p[vc * 3] = (float) x;
        p[vc * 3 + 1] = (float) y;
        p[vc * 3 + 2] = (float) z;
        n[vc * 3] = (float) nx;
        n[vc * 3 + 1] = (float) ny;
        n[vc * 3 + 2] = (float) nz;
        t[vc * 2] = (float) (u * invTile);
        t[vc * 2 + 1] = (float) (v * invTile);
        return vc++;
    }

    public void tri(int a, int b, int c) {
        if (ic + 3 > ix.length) ix = Arrays.copyOf(ix, ix.length * 2);
        ix[ic++] = a;
        ix[ic++] = b;
        ix[ic++] = c;
    }

    public void append(Mesh mesh) {
        int offset=vc;
        for(int i=0;i<mesh.vertexCount();i++)
            vertex(mesh.pos[i*3],mesh.pos[i*3+1],mesh.pos[i*3+2],mesh.nrm[i*3],mesh.nrm[i*3+1],mesh.nrm[i*3+2],
                    mesh.uv[i*2]/invTile,mesh.uv[i*2+1]/invTile);
        for(int i=0;i<mesh.idx.length;i+=3) tri(offset+mesh.idx[i],offset+mesh.idx[i+1],offset+mesh.idx[i+2]);
    }

    public void quad(int a, int b, int c, int d) {
        tri(a, b, c);
        tri(a, c, d);
    }

    /** Planar quad from 4 world points with given normal; uv computed by projecting on tangent axes tu, tv (meters). */
    public void quad(double[] a, double[] b, double[] c, double[] d, double[] nrm, double[] tu, double[] tv) {
        int i0 = v(a, nrm, tu, tv), i1 = v(b, nrm, tu, tv), i2 = v(c, nrm, tu, tv), i3 = v(d, nrm, tu, tv);
        quad(i0, i1, i2, i3);
    }

    public void triangle(double[] a, double[] b, double[] c, double[] nrm, double[] tu, double[] tv) {
        tri(v(a, nrm, tu, tv), v(b, nrm, tu, tv), v(c, nrm, tu, tv));
    }

    private int v(double[] q, double[] nrm, double[] tu, double[] tv) {
        double u = q[0] * tu[0] + q[1] * tu[1] + q[2] * tu[2];
        double w = q[0] * tv[0] + q[1] * tv[1] + q[2] * tv[2];
        return vertex(q[0], q[1], q[2], nrm[0], nrm[1], nrm[2], u, w);
    }

    /** Oriented box: origin corner o, unit axes ax, ay, az with sizes sx, sy, sz. */
    public void box(double[] o, double[] ax, double[] ay, double[] az, double sx, double sy, double sz) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            double fx = (i & 1) * sx, fy = ((i >> 1) & 1) * sy, fz = ((i >> 2) & 1) * sz;
            c[i] = new double[]{o[0] + ax[0] * fx + ay[0] * fy + az[0] * fz, o[1] + ax[1] * fx + ay[1] * fy + az[1] * fz,
                    o[2] + ax[2] * fx + ay[2] * fy + az[2] * fz};
        }
        double[] nax = neg(ax), nay = neg(ay), naz = neg(az);
        quad(c[0], c[4], c[6], c[2], nax, az, neg(ay));      // -x
        quad(c[1], c[3], c[7], c[5], ax, az, neg(ay));       // +x
        quad(c[0], c[1], c[5], c[4], nay, ax, az);           // -y
        quad(c[2], c[6], c[7], c[3], ay, ax, az);            // +y
        quad(c[0], c[2], c[3], c[1], naz, ax, neg(ay));      // -z
        quad(c[4], c[5], c[7], c[6], az, ax, neg(ay));       // +z
    }

    static double[] neg(double[] a) {
        return new double[]{-a[0], -a[1], -a[2]};
    }

    public boolean isEmpty() {
        return ic == 0;
    }

    public Mesh build() {
        return new Mesh(Arrays.copyOf(p, vc * 3), Arrays.copyOf(n, vc * 3), Arrays.copyOf(t, vc * 2), Arrays.copyOf(ix, ic));
    }
}
