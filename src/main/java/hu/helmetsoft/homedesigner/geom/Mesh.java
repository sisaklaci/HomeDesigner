package hu.helmetsoft.homedesigner.geom;

/** Indexed triangle mesh: positions, normals, uvs (v=0 top of texture). */
public final class Mesh {
    public float[] pos, nrm, uv;
    public int[] idx;

    public Mesh(float[] pos, float[] nrm, float[] uv, int[] idx) {
        this.pos = pos;
        this.nrm = nrm;
        this.uv = uv;
        this.idx = idx;
    }

    public int vertexCount() {
        return pos.length / 3;
    }

    public int triangleCount() {
        return idx.length / 3;
    }

    /** Recomputes smooth vertex normals from faces. */
    public void computeNormals() {
        float[] n = new float[pos.length];
        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
            float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
            float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int k : new int[]{a, b, c}) {
                n[k] += nx;
                n[k + 1] += ny;
                n[k + 2] += nz;
            }
        }
        for (int i = 0; i < n.length; i += 3) {
            float l = (float) Math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]);
            if (l > 0) {
                n[i] /= l;
                n[i + 1] /= l;
                n[i + 2] /= l;
            } else n[i + 1] = 1;
        }
        nrm = n;
    }
}
