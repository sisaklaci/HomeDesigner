package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.Texture;

import java.util.Arrays;

/** Intermediate data produced by format readers before normalization. */
final class RawPart {
    String material = "default";
    double[] color = {0.7, 0.7, 0.7};
    boolean colorGiven;
    Texture texture;
    double opacity = 1, shininess = 20, specular = 0.2;
    boolean metal;
    double roughness = Double.NaN;
    float[] pos = new float[3 * 1024], nrm = new float[3 * 1024], uv = new float[2 * 1024];
    int[] idx = new int[3 * 1024];
    int vc, ic;
    boolean hasNormals = true, hasUV = true;

    int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        if ((vc + 1) * 3 > pos.length) {
            int n = pos.length * 2;
            pos = Arrays.copyOf(pos, n);
            nrm = Arrays.copyOf(nrm, n);
            uv = Arrays.copyOf(uv, n / 3 * 2);
        }
        pos[vc * 3] = x;
        pos[vc * 3 + 1] = y;
        pos[vc * 3 + 2] = z;
        nrm[vc * 3] = nx;
        nrm[vc * 3 + 1] = ny;
        nrm[vc * 3 + 2] = nz;
        uv[vc * 2] = u;
        uv[vc * 2 + 1] = v;
        return vc++;
    }

    void tri(int a, int b, int c) {
        if (ic + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[ic++] = a;
        idx[ic++] = b;
        idx[ic++] = c;
    }
}
