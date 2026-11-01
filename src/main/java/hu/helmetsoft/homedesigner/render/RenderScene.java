package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.geom.*;
import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.io.ModelLibrary;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.*;
import hu.helmetsoft.homedesigner.util.Noise;

import java.util.*;

/** Flattened triangle scene with a binned-SAH BVH for the path tracer. */
public final class RenderScene {
    public static final int MAX_FULL_TRIANGLES = 4_000_000;

    // triangle data (after BVH reordering)
    float[] tv, tn, tuv, tt;
    int[] tm;
    int triCount;
    // materials
    SurfaceMaterial[] mats;
    // BVH
    float[] nb; // 6 per node: min xyz, max xyz
    int[] nd;   // 2 per node: leaf -> (first, count) ; inner -> (left, 0)
    int nodeCount;
    // lamps
    double[][] lamps; // x,y,z,r, R,G,B intensity
    float[] sceneMin = new float[3], sceneMax = new float[3];

    private RenderScene() {
    }

    /** Collects geometry. Furniture models must already be loaded in {@link ModelLibrary}. */
    public static RenderScene build(Project p, boolean ceilings, boolean roofs) {
        StructureBuilder.Options o = new StructureBuilder.Options();
        o.ceilings = ceilings;
        o.roofs = roofs;
        o.lamps = false;
        o.ground = true;
        List<GeoPart> parts = StructureBuilder.build(p, o);
        p = p.worldProject();

        Acc acc = new Acc();
        for (GeoPart g : parts) acc.add(g.mesh, g.material, null, null, null);

        int furnitureTris = 0;
        for (Furniture f : p.furniture) {
            ModelData md = ModelLibrary.placed(f);
            if (md != null) furnitureTris += md.triangleCount();
        }
        boolean full = furnitureTris <= MAX_FULL_TRIANGLES;
        for (Furniture f : p.furniture) {
            ModelData md = ModelLibrary.placed(f);
            if (md == null) continue;
            double[] m = FurnitureTransform.matrix(md, f);
            double[] nm = FurnitureTransform.normalMatrix(m);
            SurfaceMaterial ov = f.override != null ? MaterialLibrary.resolve(f.override) : null;
            for (ModelData.Part part : md.parts) {
                Mesh mesh = full ? part.mesh : part.viewMesh();
                float[] uv = null;
                if (ov != null) {
                    uv = FurnitureTransform.boxUv(mesh);
                    float s = (float) (1 / Math.max(1e-3, ov.tileSize));
                    for (int i = 0; i < uv.length; i++) uv[i] *= s;
                }
                acc.add(mesh, ov != null ? ov : part.material, m, nm, uv);
            }
        }

        RenderScene s = new RenderScene();
        s.mats = acc.matList.toArray(new SurfaceMaterial[0]);
        s.triCount = acc.count;
        List<double[]> ls = new ArrayList<>();
        for (LightSource l : p.lights) {
            double[] c = Noise.hex(l.color, new double[]{1, 0.9, 0.7});
            double I = 1.4 * Math.max(0, l.power);
            ls.add(new double[]{l.x, l.height, l.y, Math.max(0.02, l.radius), lin(c[0]) * I, lin(c[1]) * I, lin(c[2]) * I});
        }
        s.lamps = ls.toArray(new double[0][]);
        s.buildBvh(acc);
        return s;
    }

    static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Growing triangle soup. */
    private static final class Acc {
        float[] v = new float[9 * 4096], n = new float[9 * 4096], uv = new float[6 * 4096], t = new float[3 * 4096];
        int[] m = new int[4096];
        int count;
        final List<SurfaceMaterial> matList = new ArrayList<>();
        final Map<SurfaceMaterial, Integer> matIdx = new IdentityHashMap<>();

        void ensure(int extra) {
            int need = count + extra;
            if (need <= m.length) return;
            int cap = Math.max(need, m.length * 2);
            v = Arrays.copyOf(v, cap * 9);
            n = Arrays.copyOf(n, cap * 9);
            uv = Arrays.copyOf(uv, cap * 6);
            t = Arrays.copyOf(t, cap * 3);
            m = Arrays.copyOf(m, cap);
        }

        void add(Mesh mesh, SurfaceMaterial mat, double[] xf, double[] nm, float[] uvOverride) {
            int mi = matIdx.computeIfAbsent(mat, k -> {
                matList.add(k);
                return matList.size() - 1;
            });
            int tris = mesh.triangleCount();
            ensure(tris);
            float[] P = mesh.pos, N = mesh.nrm, U = uvOverride != null ? uvOverride : mesh.uv;
            int nv = mesh.vertexCount();
            float[] wp = P, wn = N;
            if (xf != null) {
                wp = new float[nv * 3];
                wn = new float[nv * 3];
                for (int i = 0; i < nv; i++) {
                    double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
                    wp[i * 3] = (float) (xf[0] * x + xf[1] * y + xf[2] * z + xf[3]);
                    wp[i * 3 + 1] = (float) (xf[4] * x + xf[5] * y + xf[6] * z + xf[7]);
                    wp[i * 3 + 2] = (float) (xf[8] * x + xf[9] * y + xf[10] * z + xf[11]);
                    double a = N[i * 3], b = N[i * 3 + 1], c = N[i * 3 + 2];
                    double nx = nm[0] * a + nm[1] * b + nm[2] * c, ny = nm[3] * a + nm[4] * b + nm[5] * c, nz = nm[6] * a + nm[7] * b + nm[8] * c;
                    double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (l < 1e-12) l = 1;
                    wn[i * 3] = (float) (nx / l);
                    wn[i * 3 + 1] = (float) (ny / l);
                    wn[i * 3 + 2] = (float) (nz / l);
                }
            }
            int[] I = mesh.idx;
            for (int k = 0; k < I.length; k += 3) {
                int a = I[k], b = I[k + 1], c = I[k + 2];
                int o = count;
                for (int j = 0; j < 3; j++) {
                    int vi = j == 0 ? a : j == 1 ? b : c;
                    v[o * 9 + j * 3] = wp[vi * 3];
                    v[o * 9 + j * 3 + 1] = wp[vi * 3 + 1];
                    v[o * 9 + j * 3 + 2] = wp[vi * 3 + 2];
                    n[o * 9 + j * 3] = wn[vi * 3];
                    n[o * 9 + j * 3 + 1] = wn[vi * 3 + 1];
                    n[o * 9 + j * 3 + 2] = wn[vi * 3 + 2];
                    uv[o * 6 + j * 2] = U[vi * 2];
                    uv[o * 6 + j * 2 + 1] = U[vi * 2 + 1];
                }
                // degenerate check
                float e1x = v[o * 9 + 3] - v[o * 9], e1y = v[o * 9 + 4] - v[o * 9 + 1], e1z = v[o * 9 + 5] - v[o * 9 + 2];
                float e2x = v[o * 9 + 6] - v[o * 9], e2y = v[o * 9 + 7] - v[o * 9 + 1], e2z = v[o * 9 + 8] - v[o * 9 + 2];
                float cx = e1y * e2z - e1z * e2y, cy = e1z * e2x - e1x * e2z, cz = e1x * e2y - e1y * e2x;
                if (cx * cx + cy * cy + cz * cz < 1e-18f) continue;
                // tangent dP/du
                float du1 = uv[o * 6 + 2] - uv[o * 6], dv1 = uv[o * 6 + 3] - uv[o * 6 + 1];
                float du2 = uv[o * 6 + 4] - uv[o * 6], dv2 = uv[o * 6 + 5] - uv[o * 6 + 1];
                float det = du1 * dv2 - du2 * dv1;
                if (Math.abs(det) > 1e-12f) {
                    float r = 1f / det;
                    t[o * 3] = (e1x * dv2 - e2x * dv1) * r;
                    t[o * 3 + 1] = (e1y * dv2 - e2y * dv1) * r;
                    t[o * 3 + 2] = (e1z * dv2 - e2z * dv1) * r;
                } else {
                    t[o * 3] = t[o * 3 + 1] = t[o * 3 + 2] = 0;
                }
                m[o] = mi;
                count++;
            }
        }
    }

    // ---------------------------------------------------------------- BVH

    private static final int BINS = 16, LEAF = 4;

    private void buildBvh(Acc a) {
        int n = a.count;
        float[] cen = new float[n * 3], bmin = new float[n * 3], bmax = new float[n * 3];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 3; k++) {
                float x0 = a.v[i * 9 + k], x1 = a.v[i * 9 + 3 + k], x2 = a.v[i * 9 + 6 + k];
                float lo = Math.min(x0, Math.min(x1, x2)), hi = Math.max(x0, Math.max(x1, x2));
                bmin[i * 3 + k] = lo;
                bmax[i * 3 + k] = hi;
                cen[i * 3 + k] = (lo + hi) * 0.5f;
            }
        }
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = i;
        int maxNodes = Math.max(1, 2 * n);
        nb = new float[maxNodes * 6];
        nd = new int[maxNodes * 2];
        nodeCount = 1;
        int[] stack = new int[256 * 3];
        int sp = 0;
        // node, start, end
        stack[sp++] = 0;
        stack[sp++] = 0;
        stack[sp++] = n;
        float[] binMin = new float[BINS * 3], binMax = new float[BINS * 3];
        int[] binCnt = new int[BINS];
        float[] rArea = new float[BINS];
        int[] rCnt = new int[BINS];
        while (sp > 0) {
            int end = stack[--sp], start = stack[--sp], node = stack[--sp];
            float mnx = Float.MAX_VALUE, mny = mnx, mnz = mnx, mxx = -Float.MAX_VALUE, mxy = mxx, mxz = mxx;
            float cmnx = mnx, cmny = mnx, cmnz = mnx, cmxx = mxx, cmxy = mxx, cmxz = mxx;
            for (int i = start; i < end; i++) {
                int t = ids[i];
                mnx = Math.min(mnx, bmin[t * 3]);
                mny = Math.min(mny, bmin[t * 3 + 1]);
                mnz = Math.min(mnz, bmin[t * 3 + 2]);
                mxx = Math.max(mxx, bmax[t * 3]);
                mxy = Math.max(mxy, bmax[t * 3 + 1]);
                mxz = Math.max(mxz, bmax[t * 3 + 2]);
                cmnx = Math.min(cmnx, cen[t * 3]);
                cmny = Math.min(cmny, cen[t * 3 + 1]);
                cmnz = Math.min(cmnz, cen[t * 3 + 2]);
                cmxx = Math.max(cmxx, cen[t * 3]);
                cmxy = Math.max(cmxy, cen[t * 3 + 1]);
                cmxz = Math.max(cmxz, cen[t * 3 + 2]);
            }
            if (end == start) {
                mnx = mny = mnz = mxx = mxy = mxz = 0;
            }
            nb[node * 6] = mnx;
            nb[node * 6 + 1] = mny;
            nb[node * 6 + 2] = mnz;
            nb[node * 6 + 3] = mxx;
            nb[node * 6 + 4] = mxy;
            nb[node * 6 + 5] = mxz;
            int count = end - start;
            if (count <= LEAF) {
                nd[node * 2] = start;
                nd[node * 2 + 1] = count;
                continue;
            }
            // choose axis = largest centroid extent
            float ex = cmxx - cmnx, ey = cmxy - cmny, ez = cmxz - cmnz;
            int axis = ex >= ey && ex >= ez ? 0 : ey >= ez ? 1 : 2;
            float cmin = axis == 0 ? cmnx : axis == 1 ? cmny : cmnz;
            float ext = axis == 0 ? ex : axis == 1 ? ey : ez;
            int mid;
            if (ext < 1e-9f) {
                mid = start + count / 2;
            } else {
                Arrays.fill(binCnt, 0);
                Arrays.fill(binMin, Float.MAX_VALUE);
                Arrays.fill(binMax, -Float.MAX_VALUE);
                float scale = BINS / ext * 0.9999f;
                for (int i = start; i < end; i++) {
                    int t = ids[i];
                    int b = (int) ((cen[t * 3 + axis] - cmin) * scale);
                    binCnt[b]++;
                    for (int k = 0; k < 3; k++) {
                        binMin[b * 3 + k] = Math.min(binMin[b * 3 + k], bmin[t * 3 + k]);
                        binMax[b * 3 + k] = Math.max(binMax[b * 3 + k], bmax[t * 3 + k]);
                    }
                }
                // sweep from right
                float ax = Float.MAX_VALUE, ay = ax, az = ax, bx = -Float.MAX_VALUE, by = bx, bz = bx;
                int c = 0;
                for (int b = BINS - 1; b > 0; b--) {
                    if (binCnt[b] > 0) {
                        ax = Math.min(ax, binMin[b * 3]);
                        ay = Math.min(ay, binMin[b * 3 + 1]);
                        az = Math.min(az, binMin[b * 3 + 2]);
                        bx = Math.max(bx, binMax[b * 3]);
                        by = Math.max(by, binMax[b * 3 + 1]);
                        bz = Math.max(bz, binMax[b * 3 + 2]);
                    }
                    c += binCnt[b];
                    rCnt[b] = c;
                    rArea[b] = c == 0 ? 0 : area(ax, ay, az, bx, by, bz);
                }
                ax = Float.MAX_VALUE;
                ay = ax;
                az = ax;
                bx = -Float.MAX_VALUE;
                by = bx;
                bz = bx;
                c = 0;
                float best = Float.MAX_VALUE;
                int bestB = -1;
                for (int b = 0; b < BINS - 1; b++) {
                    if (binCnt[b] > 0) {
                        ax = Math.min(ax, binMin[b * 3]);
                        ay = Math.min(ay, binMin[b * 3 + 1]);
                        az = Math.min(az, binMin[b * 3 + 2]);
                        bx = Math.max(bx, binMax[b * 3]);
                        by = Math.max(by, binMax[b * 3 + 1]);
                        bz = Math.max(bz, binMax[b * 3 + 2]);
                    }
                    c += binCnt[b];
                    if (c == 0 || rCnt[b + 1] == 0) continue;
                    float cost = c * area(ax, ay, az, bx, by, bz) + rCnt[b + 1] * rArea[b + 1];
                    if (cost < best) {
                        best = cost;
                        bestB = b;
                    }
                }
                float leafCost = count * area(mnx, mny, mnz, mxx, mxy, mxz);
                if (bestB < 0 || (best >= leafCost && count <= 16)) {
                    if (count <= 16) {
                        nd[node * 2] = start;
                        nd[node * 2 + 1] = count;
                        continue;
                    }
                    mid = start + count / 2;
                    // nth element-ish: simple sort by centroid on axis
                    sortRange(ids, start, end, cen, axis);
                } else {
                    // partition
                    int i = start, j = end - 1;
                    while (i <= j) {
                        int t = ids[i];
                        int b = (int) ((cen[t * 3 + axis] - cmin) * scale);
                        if (b <= bestB) i++;
                        else {
                            ids[i] = ids[j];
                            ids[j] = t;
                            j--;
                        }
                    }
                    mid = i;
                    if (mid == start || mid == end) mid = start + count / 2;
                }
            }
            int left = nodeCount;
            nodeCount += 2;
            if (nodeCount * 6 > nb.length) {
                nb = Arrays.copyOf(nb, nb.length * 2);
                nd = Arrays.copyOf(nd, nd.length * 2);
            }
            nd[node * 2] = left;
            nd[node * 2 + 1] = 0;
            if (sp + 6 > stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
            stack[sp++] = left;
            stack[sp++] = start;
            stack[sp++] = mid;
            stack[sp++] = left + 1;
            stack[sp++] = mid;
            stack[sp++] = end;
        }
        // reorder triangle data
        tv = new float[n * 9];
        tn = new float[n * 9];
        tuv = new float[n * 6];
        tt = new float[n * 3];
        tm = new int[n];
        for (int i = 0; i < n; i++) {
            int t = ids[i];
            System.arraycopy(a.v, t * 9, tv, i * 9, 9);
            System.arraycopy(a.n, t * 9, tn, i * 9, 9);
            System.arraycopy(a.uv, t * 6, tuv, i * 6, 6);
            System.arraycopy(a.t, t * 3, tt, i * 3, 3);
            tm[i] = a.m[t];
        }
        for (int k = 0; k < 3; k++) {
            sceneMin[k] = nb[k];
            sceneMax[k] = nb[3 + k];
        }
    }

    private static void sortRange(int[] ids, int start, int end, float[] cen, int axis) {
        Integer[] tmp = new Integer[end - start];
        for (int i = start; i < end; i++) tmp[i - start] = ids[i];
        Arrays.sort(tmp, Comparator.comparingDouble(t -> cen[t * 3 + axis]));
        for (int i = start; i < end; i++) ids[i] = tmp[i - start];
    }

    private static float area(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        return dx * dy + dy * dz + dz * dx;
    }

    public int triangleCount() {
        return triCount;
    }

    // ---------------------------------------------------------------- traversal

    /** Hit record. */
    static final class Hit {
        float t;
        int tri;
        float u, v;
        final int[] stack = new int[512];
    }

    /** Closest hit with t in (tMin, tMax); returns true if found. */
    boolean intersect(float ox, float oy, float oz, float dx, float dy, float dz, float tMin, float tMax, Hit h) {
        if (triCount == 0) return false;
        double ix = 1.0 / dx, iy = 1.0 / dy, iz = 1.0 / dz;
        int kz = Math.abs(dx) > Math.abs(dy) ? 0 : 1;
        if (Math.abs(dz) > (kz == 0 ? Math.abs(dx) : Math.abs(dy))) kz = 2;
        int kx = (kz+1)%3, ky = (kx+1)%3;
        double directionZ = kz == 0 ? dx : kz == 1 ? dy : dz;
        double shearX = (kx == 0 ? dx : kx == 1 ? dy : dz)/directionZ;
        double shearY = (ky == 0 ? dx : ky == 1 ? dy : dz)/directionZ;
        double scaleZ = 1/directionZ;
        double originX = kx == 0 ? ox : kx == 1 ? oy : oz;
        double originY = ky == 0 ? ox : ky == 1 ? oy : oz;
        double originZ = kz == 0 ? ox : kz == 1 ? oy : oz;
        int[] st = h.stack;
        int sp = 0;
        st[sp++] = 0;
        h.tri = -1;
        float best = tMax;
        while (sp > 0) {
            int node = st[--sp];
            int cnt = nd[node * 2 + 1];
            if (cnt > 0) {
                int first = nd[node * 2];
                for (int i = first; i < first + cnt; i++) {
                    int o = i * 9;
                    // Sheared edge functions evaluate shared edges identically: no cracks between thin curved strips.
                    double az = tv[o+kz]-originZ, bz = tv[o+3+kz]-originZ, cz = tv[o+6+kz]-originZ;
                    double ax = tv[o+kx]-originX-shearX*az, ay = tv[o+ky]-originY-shearY*az;
                    double bx = tv[o+3+kx]-originX-shearX*bz, by = tv[o+3+ky]-originY-shearY*bz;
                    double cx = tv[o+6+kx]-originX-shearX*cz, cy = tv[o+6+ky]-originY-shearY*cz;
                    double u = cx*by-cy*bx, v = ax*cy-ay*cx, w = bx*ay-by*ax;
                    if ((u < 0 || v < 0 || w < 0) && (u > 0 || v > 0 || w > 0)) continue;
                    double det = u+v+w;
                    if (det == 0) continue;
                    float t = (float)((u*az+v*bz+w*cz)*scaleZ/det);
                    if (t > tMin && t < best) {
                        best = t;
                        h.tri = i;
                        h.u = (float)(v/det);
                        h.v = (float)(w/det);
                    }
                }
            } else {
                int l = nd[node * 2], r = l + 1;
                float tl = box(l, ox, oy, oz, ix, iy, iz, best), tr = box(r, ox, oy, oz, ix, iy, iz, best);
                if (sp + 2 > st.length) continue;
                if (tl <= tr) {
                    if (tr < Float.MAX_VALUE) st[sp++] = r;
                    if (tl < Float.MAX_VALUE) st[sp++] = l;
                } else {
                    if (tl < Float.MAX_VALUE) st[sp++] = l;
                    if (tr < Float.MAX_VALUE) st[sp++] = r;
                }
            }
        }
        if (h.tri < 0) return false;
        h.t = best;
        return true;
    }

    private float box(int node, float ox, float oy, float oz, double ix, double iy, double iz, float tMax) {
        int o = node * 6;
        double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? ox : axis == 1 ? oy : oz;
            double inverse = axis == 0 ? ix : axis == 1 ? iy : iz;
            // Conservatively round stored float bounds outwards, including for rays parallel to a slab.
            double low = Math.nextDown(nb[o+axis]), high = Math.nextUp(nb[o+3+axis]);
            if (Double.isInfinite(inverse)) {
                if (origin < low || origin > high) return Float.MAX_VALUE;
            } else {
                double t1 = (low-origin)*inverse, t2 = (high-origin)*inverse;
                tmin = Math.max(tmin,Math.min(t1,t2));
                tmax = Math.min(tmax,Math.max(t1,t2));
            }
        }
        if (tmax < Math.max(tmin, 0) || tmin > tMax) return Float.MAX_VALUE;
        return (float)tmin;
    }
}
