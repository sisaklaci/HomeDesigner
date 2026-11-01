package hu.helmetsoft.homedesigner.render;

import java.util.stream.IntStream;

/** Edge-aware à-trous wavelet denoiser guided by albedo, normal, depth and per-pixel variance. */
final class Denoiser {
    private Denoiser() {
    }

    private static final float[] K = {1f / 16, 1f / 4, 3f / 8, 1f / 4, 1f / 16};

    static float[] denoise(float[] col, float[] alb, float[] nrm, float[] dep, float[] var, int w, int h) {
        int n = w * h;
        // demodulate albedo so textures stay sharp
        float[] ill = new float[n * 3];
        for (int i = 0; i < n * 3; i++) ill[i] = col[i] / Math.max(0.02f, alb[i]);
        float[] v = var.clone();
        for (int i = 0; i < n; i++) {
            float a = Math.max(0.02f, (alb[i * 3] + alb[i * 3 + 1] + alb[i * 3 + 2]) / 3);
            v[i] /= a * a;
        }
        float[] tmp = new float[n * 3], tv = new float[n];
        for (int it = 0; it < 5; it++) {
            int step = 1 << it;
            float[] src = ill, dst = tmp, sv = v, dv = tv;
            IntStream.range(0, h).parallel().forEach(y -> {
                for (int x = 0; x < w; x++) {
                    int p = y * w + x;
                    float lp = lum(src, p);
                    float sigL = (float) (4 * Math.sqrt(Math.max(1e-10, sv[p]))) + 1e-4f;
                    float npx = nrm[p * 3], npy = nrm[p * 3 + 1], npz = nrm[p * 3 + 2];
                    float dp = dep[p];
                    float wsum = 0, r = 0, g = 0, b = 0, vs = 0;
                    for (int j = -2; j <= 2; j++) {
                        int qy = y + j * step;
                        if (qy < 0 || qy >= h) continue;
                        for (int i = -2; i <= 2; i++) {
                            int qx = x + i * step;
                            if (qx < 0 || qx >= w) continue;
                            int q = qy * w + qx;
                            float k = K[i + 2] * K[j + 2];
                            float dn = npx * nrm[q * 3] + npy * nrm[q * 3 + 1] + npz * nrm[q * 3 + 2];
                            float wn = (float) Math.pow(Math.max(0, dn), 32);
                            if (npx == 0 && npy == 0 && npz == 0) wn = 1;
                            float wz = (float) Math.exp(-Math.abs(dp - dep[q]) / (0.03f * dp * step + 0.01f));
                            float da = Math.abs(alb[p * 3] - alb[q * 3]) + Math.abs(alb[p * 3 + 1] - alb[q * 3 + 1]) + Math.abs(alb[p * 3 + 2] - alb[q * 3 + 2]);
                            float wa = (float) Math.exp(-da * 6);
                            float wl = (float) Math.exp(-Math.abs(lp - lum(src, q)) / sigL);
                            float wt = k * wn * wz * wa * wl;
                            wsum += wt;
                            r += wt * src[q * 3];
                            g += wt * src[q * 3 + 1];
                            b += wt * src[q * 3 + 2];
                            vs += wt * wt * sv[q];
                        }
                    }
                    if (wsum > 1e-12f) {
                        dst[p * 3] = r / wsum;
                        dst[p * 3 + 1] = g / wsum;
                        dst[p * 3 + 2] = b / wsum;
                        dv[p] = vs / (wsum * wsum);
                    } else {
                        dst[p * 3] = src[p * 3];
                        dst[p * 3 + 1] = src[p * 3 + 1];
                        dst[p * 3 + 2] = src[p * 3 + 2];
                        dv[p] = sv[p];
                    }
                }
            });
            float[] s = ill;
            ill = tmp;
            tmp = s;
            float[] s2 = v;
            v = tv;
            tv = s2;
        }
        float[] out = new float[n * 3];
        for (int i = 0; i < n * 3; i++) out[i] = ill[i] * Math.max(0.02f, alb[i]);
        return out;
    }

    private static float lum(float[] c, int p) {
        return 0.2126f * c[p * 3] + 0.7152f * c[p * 3 + 1] + 0.0722f * c[p * 3 + 2];
    }
}
