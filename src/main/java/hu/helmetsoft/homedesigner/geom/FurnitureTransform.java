package hu.helmetsoft.homedesigner.geom;

import hu.helmetsoft.homedesigner.io.ModelData;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.util.Texture;

/** Maps normalized model space to world space for a placed {@link Furniture}. */
public final class FurnitureTransform {
    public static final String[] UP_FIX_LABELS = {"Eredeti", "X +90°", "X −90°", "Z +90°", "Z −90°", "Fejjel lefelé"};

    private FurnitureTransform() {
    }

    /** Row-major 3x3 rotation for an up-axis fix. */
    public static double[] upFix(int fix) {
        return switch (fix) {
            case 1 -> new double[]{1, 0, 0, 0, 0, -1, 0, 1, 0};   // X +90
            case 2 -> new double[]{1, 0, 0, 0, 0, 1, 0, -1, 0};   // X -90
            case 3 -> new double[]{0, -1, 0, 1, 0, 0, 0, 0, 1};   // Z +90
            case 4 -> new double[]{0, 1, 0, -1, 0, 0, 0, 0, 1};   // Z -90
            case 5 -> new double[]{1, 0, 0, 0, -1, 0, 0, 0, -1};  // X 180
            default -> new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
        };
    }

    /** {sizeX, sizeY, sizeZ, offX, offY, offZ}: size after the up fix and the translation that re-centers it. */
    public static double[] fixedBounds(ModelData md, int fix) {
        double[] r = upFix(fix);
        double mnx = 1e30, mny = 1e30, mnz = 1e30, mxx = -1e30, mxy = -1e30, mxz = -1e30;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? md.minX : md.maxX, y = (i & 2) == 0 ? md.minY : md.maxY, z = (i & 4) == 0 ? md.minZ : md.maxZ;
            double X = r[0] * x + r[1] * y + r[2] * z, Y = r[3] * x + r[4] * y + r[5] * z, Z = r[6] * x + r[7] * y + r[8] * z;
            mnx = Math.min(mnx, X);
            mxx = Math.max(mxx, X);
            mny = Math.min(mny, Y);
            mxy = Math.max(mxy, Y);
            mnz = Math.min(mnz, Z);
            mxz = Math.max(mxz, Z);
        }
        return new double[]{Math.max(1e-4, mxx - mnx), Math.max(1e-4, mxy - mny), Math.max(1e-4, mxz - mnz),
                -(mnx + mxx) / 2, -mny, -(mnz + mxz) / 2};
    }

    /** Row-major 3x4 affine matrix (model -> world). */
    public static double[] matrix(ModelData md, Furniture f) {
        double[] b = fixedBounds(md, f.upFix);
        double[] r = upFix(f.upFix);
        double sx = f.width / b[0] * (f.mirror ? -1 : 1), sy = StairGeometry.totalHeight(f) / b[1], sz = f.depth / b[2];
        double a = Math.toRadians(f.angle), c = Math.cos(a), s = Math.sin(a);
        // M = T * Ry * S * C * R
        double[] m = new double[12];
        for (int row = 0; row < 3; row++) {
            // S*C*R rows
            double[] scr = new double[4];
            double sc = row == 0 ? sx : row == 1 ? sy : sz;
            for (int k = 0; k < 3; k++) scr[k] = sc * r[row * 3 + k];
            scr[3] = sc * b[3 + row];
            System.arraycopy(scr, 0, m, row * 4, 4);
        }
        double[] out = new double[12];
        for (int k = 0; k < 4; k++) {
            double x = m[k], y = m[4 + k], z = m[8 + k];
            out[k] = c * x - s * z;
            out[4 + k] = y;
            out[8 + k] = s * x + c * z;
        }
        out[3] += f.x;
        out[7] += f.elevation;
        out[11] += f.y;
        return out;
    }

    /** Row-major 3x3 normal matrix (inverse transpose of the linear part). */
    public static double[] normalMatrix(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double D = -(b * i - c * h), E = a * i - c * g, F = -(a * h - b * g);
        double G = b * f - c * e, H = -(a * f - c * d), I = a * e - b * d;
        // cofactor matrix == inverse transpose * det; sign of det keeps orientation
        double det = a * A + b * B + c * C;
        double sg = det < 0 ? -1 : 1;
        return new double[]{A * sg, B * sg, C * sg, D * sg, E * sg, F * sg, G * sg, H * sg, I * sg};
    }

    /** Box-projected UVs (meters in model space), used for material overrides. */
    public static float[] boxUv(Mesh m) {
        float[] uv = new float[m.vertexCount() * 2];
        for (int i = 0; i < m.vertexCount(); i++) {
            float nx = Math.abs(m.nrm[i * 3]), ny = Math.abs(m.nrm[i * 3 + 1]), nz = Math.abs(m.nrm[i * 3 + 2]);
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            if (ny >= nx && ny >= nz) {
                uv[i * 2] = x;
                uv[i * 2 + 1] = z;
            } else if (nx >= nz) {
                uv[i * 2] = z;
                uv[i * 2 + 1] = -y;
            } else {
                uv[i * 2] = x;
                uv[i * 2 + 1] = -y;
            }
        }
        return uv;
    }

    /** Shaded silhouette seen from above (in up-fixed model space), image X = model X, image Y = model Z. */
    public static Texture topView(ModelData md, int fix, int maxPx) {
        double[] b = fixedBounds(md, fix);
        double[] r = upFix(fix);
        int w, h;
        if (b[0] >= b[2]) {
            w = maxPx;
            h = Math.max(4, (int) Math.round(maxPx * b[2] / b[0]));
        } else {
            h = maxPx;
            w = Math.max(4, (int) Math.round(maxPx * b[0] / b[2]));
        }
        float[] depth = new float[w * h];
        java.util.Arrays.fill(depth, -1e30f);
        int[] px = new int[w * h];
        double kx = w / b[0], kz = h / b[2];
        float[] tmp = new float[4];
        for (ModelData.Part part : md.parts) {
            Mesh m = part.viewMesh();
            double[] base = part.material.color;
            double cr = base[0], cg = base[1], cb = base[2];
            if (part.material.albedo != null) {
                double ar = 0, ag = 0, ab = 0;
                int n = 0;
                for (double u = 0.05; u < 1; u += 0.2) for (double v = 0.05; v < 1; v += 0.2) {
                    part.material.albedo.sampleUncached(u, v, tmp);
                    ar += tmp[0];
                    ag += tmp[1];
                    ab += tmp[2];
                    n++;
                }
                cr *= Math.pow(ar / n, 1 / 2.2);
                cg *= Math.pow(ag / n, 1 / 2.2);
                cb *= Math.pow(ab / n, 1 / 2.2);
            }
            int nv = m.vertexCount();
            double[] X = new double[nv], Y = new double[nv], Z = new double[nv];
            for (int i = 0; i < nv; i++) {
                double x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
                X[i] = (r[0] * x + r[1] * y + r[2] * z + b[3] + b[0] / 2) * kx;
                Y[i] = r[3] * x + r[4] * y + r[5] * z + b[4];
                Z[i] = (r[6] * x + r[7] * y + r[8] * z + b[5] + b[2] / 2) * kz;
            }
            for (int t = 0; t < m.idx.length; t += 3) {
                int i0 = m.idx[t], i1 = m.idx[t + 1], i2 = m.idx[t + 2];
                double ux = X[i1] - X[i0], uz = Z[i1] - Z[i0], vx = X[i2] - X[i0], vz = Z[i2] - Z[i0];
                double area = ux * vz - uz * vx;
                if (Math.abs(area) < 1e-9) continue;
                // face normal in world-ish units for shading
                double ax = (X[i1] - X[i0]) / kx, ay = Y[i1] - Y[i0], az = (Z[i1] - Z[i0]) / kz;
                double bx = (X[i2] - X[i0]) / kx, by = Y[i2] - Y[i0], bz = (Z[i2] - Z[i0]) / kz;
                double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double shade = nl > 0 ? 0.55 + 0.45 * Math.abs(ny / nl) - 0.12 * (nx / nl) : 0.8;
                int x0 = Math.max(0, (int) Math.floor(Math.min(X[i0], Math.min(X[i1], X[i2]))));
                int x1 = Math.min(w - 1, (int) Math.ceil(Math.max(X[i0], Math.max(X[i1], X[i2]))));
                int z0 = Math.max(0, (int) Math.floor(Math.min(Z[i0], Math.min(Z[i1], Z[i2]))));
                int z1 = Math.min(h - 1, (int) Math.ceil(Math.max(Z[i0], Math.max(Z[i1], Z[i2]))));
                for (int pz = z0; pz <= z1; pz++) {
                    for (int pxx = x0; pxx <= x1; pxx++) {
                        double qx = pxx + 0.5 - X[i0], qz = pz + 0.5 - Z[i0];
                        double l1 = (qx * vz - qz * vx) / area, l2 = (ux * qz - uz * qx) / area;
                        if (l1 < -0.02 || l2 < -0.02 || l1 + l2 > 1.02) continue;
                        float d = (float) (Y[i0] + l1 * (Y[i1] - Y[i0]) + l2 * (Y[i2] - Y[i0]));
                        int o = pz * w + pxx;
                        if (d <= depth[o]) continue;
                        depth[o] = d;
                        double hs = 0.75 + 0.25 * Math.min(1, d / Math.max(0.05, b[1]));
                        int R = (int) Math.min(255, cr * shade * hs * 255), G = (int) Math.min(255, cg * shade * hs * 255),
                                B = (int) Math.min(255, cb * shade * hs * 255);
                        px[o] = 0xFF000000 | (R << 16) | (G << 8) | B;
                    }
                }
            }
        }
        // dark outline
        int[] out = px.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int o = y * w + x;
                if (px[o] == 0) continue;
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 || px[o - 1] == 0 || px[o + 1] == 0 || px[o - w] == 0 || px[o + w] == 0;
                if (edge) out[o] = 0xFF3A3F47;
            }
        return new Texture(w, h, out);
    }
}
