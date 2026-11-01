package hu.helmetsoft.homedesigner.render;

import hu.helmetsoft.homedesigner.io.ImageLoader;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.model.Environment;
import hu.helmetsoft.homedesigner.util.Texture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Progressive multithreaded Monte-Carlo path tracer producing photorealistic images. */
public final class PathTracer {
    private static final int MAX_DEPTH = 7;
    private static final double SUN_COS = Math.cos(Math.toRadians(0.27));

    private final RenderScene sc;
    private final RenderCamera cam;
    public final int width, height;
    private final float[] acc, accSq, gAlb, gNrm, gDep;
    private volatile int passes;
    private volatile boolean running;
    private Thread worker;
    private final int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    // environment
    private final double[] sunDir = new double[3], sunE = new double[3];
    private final double skyK, exposure;
    private float[] hdr;
    private int hdrW, hdrH;
    private final float[][] mColor;
    private final boolean[] includeSpecNee;

    public PathTracer(RenderScene scene, RenderCamera camera, Environment env, int w, int h) {
        sc = scene;
        cam = camera;
        width = w;
        height = h;
        acc = new float[w * h * 3];
        accSq = new float[w * h];
        gAlb = new float[w * h * 3];
        gNrm = new float[w * h * 3];
        gDep = new float[w * h];
        double az = Math.toRadians(env.sunAzimuth), el = Math.toRadians(Math.max(-5, Math.min(90, env.sunElevation)));
        sunDir[0] = Math.sin(az) * Math.cos(el);
        sunDir[1] = Math.sin(el);
        sunDir[2] = -Math.cos(az) * Math.cos(el);
        double low = Math.pow(1 - Math.max(0, Math.min(1, env.sunElevation / 90)), 4);
        double fade = Math.max(0, Math.min(1, Math.sin(el) * 5));
        double si = 3.2 * env.sunIntensity * fade;
        sunE[0] = si;
        sunE[1] = si * (0.94 - 0.35 * low);
        sunE[2] = si * (0.86 - 0.6 * low);
        skyK = env.skyIntensity;
        exposure = env.exposure;
        if (env.hdri != null && Files.isRegularFile(Path.of(env.hdri))) {
            try {
                int[] dims = new int[2];
                hdr = ImageLoader.loadHdr(Path.of(env.hdri), dims);
                hdrW = dims[0];
                hdrH = dims[1];
            } catch (Exception ignored) {
                hdr = null;
            }
        }
        mColor = new float[sc.mats.length][];
        includeSpecNee = new boolean[sc.mats.length];
        for (int i = 0; i < sc.mats.length; i++) {
            mColor[i] = sc.mats[i].linearColor();
            includeSpecNee[i] = sc.mats[i].roughness >= 0.08;
        }
    }

    public int passes() {
        return passes;
    }

    public boolean isRunning() {
        return running;
    }

    /** Starts progressive rendering; onPass is invoked (on the render thread) after each completed pass. */
    public synchronized void start(int maxPasses, Runnable onPass) {
        if (running) return;
        running = true;
        worker = new Thread(() -> {
            Thread[] pool = new Thread[threads];
            while (running && passes < maxPasses) {
                AtomicInteger row = new AtomicInteger();
                int pass = passes;
                for (int t = 0; t < threads; t++) {
                    long seed = 0x9E3779B97F4A7C15L * (pass * 131L + t + 1);
                    pool[t] = new Thread(() -> {
                        Ctx c = new Ctx(seed);
                        int y;
                        while (running && (y = row.getAndIncrement()) < height) renderRow(y, pass, c);
                    }, "pt-" + t);
                    pool[t].setDaemon(true);
                    pool[t].setPriority(Thread.MIN_PRIORITY);
                    pool[t].start();
                }
                for (Thread t : pool) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        running = false;
                    }
                }
                if (!running) break;
                passes = pass + 1;
                if (onPass != null) onPass.run();
            }
            running = false;
        }, "path-tracer");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        Thread w = worker;
        if (w != null) {
            try {
                w.join(3000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ per-thread context

    private static final class Ctx {
        long s;
        final RenderScene.Hit hit = new RenderScene.Hit(), shadow = new RenderScene.Hit();
        final float[] tex = new float[4];
        final double[] col = new double[3], trans = new double[3], f = new double[3], w = new double[3];
        final double[] n = new double[3], ng = new double[3], alb = new double[3], d = new double[3];
        // guide
        final double[] gA = new double[3], gN = new double[3];
        double gD;
        boolean gSet;

        Ctx(long seed) {
            s = seed == 0 ? 1 : seed;
        }

        double rnd() {
            s ^= s << 13;
            s ^= s >>> 7;
            s ^= s << 17;
            return (s >>> 11) * 0x1.0p-53;
        }
    }

    private void renderRow(int y, int pass, Ctx c) {
        double tanY = Math.tan(Math.toRadians(cam.fovY) / 2), tanX = tanY * width / height;
        for (int x = 0; x < width; x++) {
            double sx = ((x + c.rnd()) / width * 2 - 1) * tanX, sy = (1 - (y + c.rnd()) / height * 2) * tanY;
            double dx = cam.fwd[0] + cam.right[0] * sx + cam.up[0] * sy;
            double dy = cam.fwd[1] + cam.right[1] * sx + cam.up[1] * sy;
            double dz = cam.fwd[2] + cam.right[2] * sx + cam.up[2] * sy;
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            c.gSet = false;
            trace(cam.eye[0], cam.eye[1], cam.eye[2], dx / l, dy / l, dz / l, c);
            int i = y * width + x;
            double r = c.col[0], g = c.col[1], b = c.col[2];
            if (!(r == r) || !(g == g) || !(b == b) || Double.isInfinite(r + g + b)) r = g = b = 0;
            acc[i * 3] += (float) r;
            acc[i * 3 + 1] += (float) g;
            acc[i * 3 + 2] += (float) b;
            double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            accSq[i] += (float) (lum * lum);
            if (!c.gSet) {
                c.gA[0] = c.gA[1] = c.gA[2] = 1;
                c.gN[0] = c.gN[1] = c.gN[2] = 0;
                c.gD = 1e4;
            }
            gAlb[i * 3] += (float) c.gA[0];
            gAlb[i * 3 + 1] += (float) c.gA[1];
            gAlb[i * 3 + 2] += (float) c.gA[2];
            gNrm[i * 3] += (float) c.gN[0];
            gNrm[i * 3 + 1] += (float) c.gN[1];
            gNrm[i * 3 + 2] += (float) c.gN[2];
            gDep[i] += (float) c.gD;
        }
    }

    // ------------------------------------------------------------------ environment

    private void sky(double dx, double dy, double dz, double[] out) {
        if (hdr != null) {
            double u = 0.5 + Math.atan2(dx, -dz) / (2 * Math.PI), v = Math.acos(Math.max(-1, Math.min(1, dy))) / Math.PI;
            int px = Math.min(hdrW - 1, Math.max(0, (int) (u * hdrW))), py = Math.min(hdrH - 1, Math.max(0, (int) (v * hdrH)));
            int o = (py * hdrW + px) * 3;
            out[0] = hdr[o] * skyK;
            out[1] = hdr[o + 1] * skyK;
            out[2] = hdr[o + 2] * skyK;
            return;
        }
        double h = Math.max(0, dy);
        double t = Math.pow(1 - h, 4);
        double zr = 0.13, zg = 0.27, zb = 0.68, hr = 0.62, hg = 0.74, hb = 0.92;
        double r = zr + (hr - zr) * t, g = zg + (hg - zg) * t, b = zb + (hb - zb) * t;
        double cg = Math.max(0, dx * sunDir[0] + dy * sunDir[1] + dz * sunDir[2]);
        double glow = 0.25 * Math.pow(cg, 6) + 0.9 * Math.pow(cg, 80);
        double sunUp = Math.max(0, Math.min(1, sunDir[1] * 4 + 0.2));
        r += glow * 1.0 * sunUp;
        g += glow * 0.86 * sunUp;
        b += glow * 0.66 * sunUp;
        if (dy < 0) {
            double k = Math.min(1, -dy * 6);
            r = r * (1 - k) + 0.32 * k;
            g = g * (1 - k) + 0.30 * k;
            b = b * (1 - k) + 0.27 * k;
        }
        double day = 0.15 + 0.85 * Math.max(0, Math.min(1, sunDir[1] * 3 + 0.3));
        out[0] = r * skyK * day;
        out[1] = g * skyK * day;
        out[2] = b * skyK * day;
    }

    // ------------------------------------------------------------------ path tracing

    private void trace(double ox, double oy, double oz, double dx, double dy, double dz, Ctx c) {
        double tr = 1, tg = 1, tb = 1, Lr = 0, Lg = 0, Lb = 0;
        boolean specPrev = true;
        int passThrough = 0;
        RenderScene.Hit h = c.hit;
        float[] tex = c.tex;
        for (int depth = 0; depth < MAX_DEPTH; ) {
            boolean hitGeo = sc.intersect((float) ox, (float) oy, (float) oz, (float) dx, (float) dy, (float) dz, 1e-4f, 1e30f, h);
            double tHit = hitGeo ? h.t : 1e30;
            // lamps
            int lamp = -1;
            for (int li = 0; li < sc.lamps.length; li++) {
                double[] L = sc.lamps[li];
                double t = sphere(ox, oy, oz, dx, dy, dz, L[0], L[1], L[2], L[3]);
                if (t > 0 && t < tHit) {
                    tHit = t;
                    lamp = li;
                }
            }
            if (lamp >= 0) {
                double[] L = sc.lamps[lamp];
                if (specPrev) {
                    double k = 1 / (Math.PI * L[3] * L[3]);
                    Lr += tr * L[4] * k;
                    Lg += tg * L[5] * k;
                    Lb += tb * L[6] * k;
                }
                if (!c.gSet) setGuide(c, 1, 1, 1, -dx, -dy, -dz, tHit);
                break;
            }
            if (!hitGeo) {
                sky(dx, dy, dz, c.col);
                double er = c.col[0], eg = c.col[1], eb = c.col[2];
                if (specPrev && dx * sunDir[0] + dy * sunDir[1] + dz * sunDir[2] > SUN_COS) {
                    double k = 1 / (2 * Math.PI * (1 - SUN_COS));
                    er += sunE[0] * k;
                    eg += sunE[1] * k;
                    eb += sunE[2] * k;
                }
                if (depth > 0) {
                    double m = Math.max(er, Math.max(eg, eb)) * Math.max(tr, Math.max(tg, tb));
                    if (m > 30 && !specPrev) {
                        double s = 30 / m;
                        er *= s;
                        eg *= s;
                        eb *= s;
                    }
                }
                Lr += tr * er;
                Lg += tg * eg;
                Lb += tb * eb;
                break;
            }
            int tri = h.tri;
            int mi = sc.tm[tri];
            SurfaceMaterial m = sc.mats[mi];
            float u = h.u, v = h.v, w0 = 1 - u - v;
            double px = ox + dx * h.t, py = oy + dy * h.t, pz = oz + dz * h.t;
            int o9 = tri * 9, o6 = tri * 6;
            double tu = w0 * sc.tuv[o6] + u * sc.tuv[o6 + 2] + v * sc.tuv[o6 + 4];
            double tv = w0 * sc.tuv[o6 + 1] + u * sc.tuv[o6 + 3] + v * sc.tuv[o6 + 5];
            float[] mc = mColor[mi];
            double ar = mc[0], ag = mc[1], ab = mc[2], alpha = 1;
            if (m.albedo != null) {
                m.albedo.sample(tu, tv, tex);
                ar *= tex[0];
                ag *= tex[1];
                ab *= tex[2];
                alpha = tex[3];
            }
            // transparency (cutout / partial)
            double opacity = m.glass ? 1 : m.opacity * alpha;
            if (opacity < 1 && c.rnd() > opacity) {
                if (++passThrough > 24) break;
                ox = px + dx * 1e-4;
                oy = py + dy * 1e-4;
                oz = pz + dz * 1e-4;
                continue;
            }
            // geometric normal
            double e1x = sc.tv[o9 + 3] - sc.tv[o9], e1y = sc.tv[o9 + 4] - sc.tv[o9 + 1], e1z = sc.tv[o9 + 5] - sc.tv[o9 + 2];
            double e2x = sc.tv[o9 + 6] - sc.tv[o9], e2y = sc.tv[o9 + 7] - sc.tv[o9 + 1], e2z = sc.tv[o9 + 8] - sc.tv[o9 + 2];
            double gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
            double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
            gx /= gl;
            gy /= gl;
            gz /= gl;
            double nx = w0 * sc.tn[o9] + u * sc.tn[o9 + 3] + v * sc.tn[o9 + 6];
            double ny = w0 * sc.tn[o9 + 1] + u * sc.tn[o9 + 4] + v * sc.tn[o9 + 7];
            double nz = w0 * sc.tn[o9 + 2] + u * sc.tn[o9 + 5] + v * sc.tn[o9 + 8];
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-8) {
                nx = gx;
                ny = gy;
                nz = gz;
            } else {
                nx /= nl;
                ny /= nl;
                nz /= nl;
            }
            if (nx * gx + ny * gy + nz * gz < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            // face forward toward the incoming ray
            if (gx * dx + gy * dy + gz * dz > 0) {
                gx = -gx;
                gy = -gy;
                gz = -gz;
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            // glass: thin sheet
            if (m.glass) {
                double cosI = Math.abs(dx * nx + dy * ny + dz * nz);
                double F = 0.04 + 0.96 * Math.pow(1 - cosI, 5);
                if (c.rnd() < F) {
                    double k = 2 * (dx * nx + dy * ny + dz * nz);
                    dx -= k * nx;
                    dy -= k * ny;
                    dz -= k * nz;
                    ox = px + gx * 1e-4;
                    oy = py + gy * 1e-4;
                    oz = pz + gz * 1e-4;
                    specPrev = true;
                    depth++;
                } else {
                    tr *= 0.3 + 0.7 * ar;
                    tg *= 0.3 + 0.7 * ag;
                    tb *= 0.3 + 0.7 * ab;
                    ox = px + dx * 1e-4;
                    oy = py + dy * 1e-4;
                    oz = pz + dz * 1e-4;
                    if (++passThrough > 24) break;
                }
                continue;
            }
            // bump / normal map
            if (m.bump != null) {
                int t3 = tri * 3;
                double Tx = sc.tt[t3], Ty = sc.tt[t3 + 1], Tz = sc.tt[t3 + 2];
                double dot = Tx * nx + Ty * ny + Tz * nz;
                Tx -= dot * nx;
                Ty -= dot * ny;
                Tz -= dot * nz;
                double tl = Math.sqrt(Tx * Tx + Ty * Ty + Tz * Tz);
                if (tl > 1e-9) {
                    Tx /= tl;
                    Ty /= tl;
                    Tz /= tl;
                    // B = N x T, oriented so that texture v (down) maps consistently
                    double Bx = ny * Tz - nz * Ty, By = nz * Tx - nx * Tz, Bz = nx * Ty - ny * Tx;
                    int raw = rawSample(m.bump, tu, tv);
                    double mx = ((raw >> 16) & 255) / 127.5 - 1, my = ((raw >> 8) & 255) / 127.5 - 1, mz = (raw & 255) / 127.5 - 1;
                    double qx = Tx * mx - Bx * my + nx * mz, qy = Ty * mx - By * my + ny * mz, qz = Tz * mx - Bz * my + nz * mz;
                    double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    if (ql > 1e-9 && (qx * gx + qy * gy + qz * gz) > 0.05 * ql) {
                        nx = qx / ql;
                        ny = qy / ql;
                        nz = qz / ql;
                    }
                }
            }
            if (m.emission != null) {
                Lr += tr * m.emission[0];
                Lg += tg * m.emission[1];
                Lb += tb * m.emission[2];
                break;
            }
            if (!c.gSet) setGuide(c, m.metal ? Math.max(ar, 0.05) : ar, m.metal ? Math.max(ag, 0.05) : ag, m.metal ? Math.max(ab, 0.05) : ab, nx, ny, nz,
                    distance(px, py, pz));
            double vx = -dx, vy = -dy, vz = -dz;
            double rough = m.roughness;
            double alphaR = Math.max(0.002, rough * rough);
            double f0 = m.metal ? 1 : Math.min(0.08, 0.08 * m.specular);
            double sox = px + gx * 2e-4, soy = py + gy * 2e-4, soz = pz + gz * 2e-4;
            boolean neeSpec = includeSpecNee[mi];
            double cr = 0, cg = 0, cb = 0;
            // --- sun NEE
            if (sunE[0] > 0 && sunDir[1] > -0.05) {
                double ldx = sunDir[0], ldy = sunDir[1], ldz = sunDir[2];
                // jitter inside sun cone
                double r1 = c.rnd() * 2 * Math.PI, r2 = Math.sqrt(c.rnd()) * 0.0047;
                double[] t1 = c.d;
                ortho(ldx, ldy, ldz, t1);
                double bx = ldy * t1[2] - ldz * t1[1], by = ldz * t1[0] - ldx * t1[2], bz = ldx * t1[1] - ldy * t1[0];
                ldx += (t1[0] * Math.cos(r1) + bx * Math.sin(r1)) * r2;
                ldy += (t1[1] * Math.cos(r1) + by * Math.sin(r1)) * r2;
                ldz += (t1[2] * Math.cos(r1) + bz * Math.sin(r1)) * r2;
                double ll = Math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz);
                ldx /= ll;
                ldy /= ll;
                ldz /= ll;
                double nl2 = nx * ldx + ny * ldy + nz * ldz;
                if (nl2 > 0 && gx * ldx + gy * ldy + gz * ldz > 0) {
                    if (visible(sox, soy, soz, ldx, ldy, ldz, 1e30, c)) {
                        brdf(nx, ny, nz, vx, vy, vz, ldx, ldy, ldz, ar, ag, ab, alphaR, f0, m.metal, neeSpec, c.f);
                        cr += c.f[0] * nl2 * sunE[0] * c.trans[0];
                        cg += c.f[1] * nl2 * sunE[1] * c.trans[1];
                        cb += c.f[2] * nl2 * sunE[2] * c.trans[2];
                    }
                }
            }
            // --- lamps NEE
            int nLamps = sc.lamps.length;
            if (nLamps > 0) {
                int count = Math.min(nLamps, 4);
                for (int k = 0; k < count; k++) {
                    int li = nLamps <= 4 ? k : (int) (c.rnd() * nLamps);
                    double scale = nLamps <= 4 ? 1 : (double) nLamps / count;
                    double[] L = sc.lamps[li];
                    double z = 1 - 2 * c.rnd(), ph = 2 * Math.PI * c.rnd(), rr = Math.sqrt(Math.max(0, 1 - z * z));
                    double qx = L[0] + L[3] * rr * Math.cos(ph), qy = L[1] + L[3] * z, qz = L[2] + L[3] * rr * Math.sin(ph);
                    double lx = qx - px, ly = qy - py, lz = qz - pz;
                    double d2 = lx * lx + ly * ly + lz * lz, dist = Math.sqrt(d2);
                    lx /= dist;
                    ly /= dist;
                    lz /= dist;
                    double nl2 = nx * lx + ny * ly + nz * lz;
                    if (nl2 <= 0 || gx * lx + gy * ly + gz * lz <= 0) continue;
                    if (!visible(sox, soy, soz, lx, ly, lz, dist - L[3] * 1.01, c)) continue;
                    brdf(nx, ny, nz, vx, vy, vz, lx, ly, lz, ar, ag, ab, alphaR, f0, m.metal, neeSpec, c.f);
                    double inv = scale / Math.max(d2, 0.01);
                    cr += c.f[0] * nl2 * L[4] * inv * c.trans[0];
                    cg += c.f[1] * nl2 * L[5] * inv * c.trans[1];
                    cb += c.f[2] * nl2 * L[6] * inv * c.trans[2];
                }
            }
            cr *= tr;
            cg *= tg;
            cb *= tb;
            if (depth > 0) {
                double mm = Math.max(cr, Math.max(cg, cb));
                if (mm > 20) {
                    double s = 20 / mm;
                    cr *= s;
                    cg *= s;
                    cb *= s;
                }
            }
            Lr += cr;
            Lg += cg;
            Lb += cb;

            // --- sample next direction
            double nv = Math.max(1e-4, nx * vx + ny * vy + nz * vz);
            double Fv = f0 + (1 - f0) * Math.pow(1 - nv, 5);
            double ps = m.metal ? 1 : Math.max(0.08, Math.min(0.85, Fv * 2 + 0.15 * (1 - rough)));
            double[] t1 = c.d;
            ortho(nx, ny, nz, t1);
            double bx = ny * t1[2] - nz * t1[1], by = nz * t1[0] - nx * t1[2], bz = nx * t1[1] - ny * t1[0];
            double lx, ly, lz;
            boolean choseSpec = c.rnd() < ps;
            if (choseSpec) {
                double r1 = c.rnd(), r2 = c.rnd();
                double a2 = alphaR * alphaR;
                double cosT = Math.sqrt((1 - r2) / (1 + (a2 - 1) * r2)), sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
                double phi = 2 * Math.PI * r1;
                double hx = t1[0] * sinT * Math.cos(phi) + bx * sinT * Math.sin(phi) + nx * cosT;
                double hy = t1[1] * sinT * Math.cos(phi) + by * sinT * Math.sin(phi) + ny * cosT;
                double hz = t1[2] * sinT * Math.cos(phi) + bz * sinT * Math.sin(phi) + nz * cosT;
                double vh = vx * hx + vy * hy + vz * hz;
                lx = 2 * vh * hx - vx;
                ly = 2 * vh * hy - vy;
                lz = 2 * vh * hz - vz;
            } else {
                double r1 = c.rnd(), r2 = c.rnd();
                double rr = Math.sqrt(r1), phi = 2 * Math.PI * r2;
                double a = rr * Math.cos(phi), b = rr * Math.sin(phi), zz = Math.sqrt(Math.max(0, 1 - r1));
                lx = t1[0] * a + bx * b + nx * zz;
                ly = t1[1] * a + by * b + ny * zz;
                lz = t1[2] * a + bz * b + nz * zz;
            }
            double nlo = nx * lx + ny * ly + nz * lz;
            if (nlo <= 1e-6 || gx * lx + gy * ly + gz * lz <= 0) break;
            // combined pdf
            double hx = vx + lx, hy = vy + ly, hz = vz + lz;
            double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
            hx /= hl;
            hy /= hl;
            hz /= hl;
            double nh = Math.max(0, nx * hx + ny * hy + nz * hz), vh = Math.max(1e-6, vx * hx + vy * hy + vz * hz);
            double a2 = alphaR * alphaR, dd = nh * nh * (a2 - 1) + 1;
            double D = a2 / (Math.PI * dd * dd);
            double pdf = ps * D * nh / (4 * vh) + (1 - ps) * nlo / Math.PI;
            if (pdf < 1e-8) break;
            brdf(nx, ny, nz, vx, vy, vz, lx, ly, lz, ar, ag, ab, alphaR, f0, m.metal, true, c.f);
            double k = nlo / pdf;
            tr *= c.f[0] * k;
            tg *= c.f[1] * k;
            tb *= c.f[2] * k;
            specPrev = choseSpec && rough < 0.08;
            ox = sox;
            oy = soy;
            oz = soz;
            dx = lx;
            dy = ly;
            dz = lz;
            depth++;
            // russian roulette
            double q = Math.max(tr, Math.max(tg, tb));
            if (!(q > 0)) break;
            if (depth >= 3) {
                double surv = Math.min(0.95, q);
                if (c.rnd() > surv) break;
                tr /= surv;
                tg /= surv;
                tb /= surv;
            }
        }
        c.col[0] = Lr;
        c.col[1] = Lg;
        c.col[2] = Lb;
    }

    private double distance(double x, double y, double z) {
        double dx = x - cam.eye[0], dy = y - cam.eye[1], dz = z - cam.eye[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void setGuide(Ctx c, double r, double g, double b, double nx, double ny, double nz, double d) {
        c.gA[0] = r;
        c.gA[1] = g;
        c.gA[2] = b;
        c.gN[0] = nx;
        c.gN[1] = ny;
        c.gN[2] = nz;
        c.gD = d;
        c.gSet = true;
    }

    private static int rawSample(Texture t, double u, double v) {
        int x = (int) Math.floor((u - Math.floor(u)) * t.w), y = (int) Math.floor((v - Math.floor(v)) * t.h);
        x = Math.min(t.w - 1, Math.max(0, x));
        y = Math.min(t.h - 1, Math.max(0, y));
        return t.argb[y * t.w + x];
    }

    private static void ortho(double x, double y, double z, double[] out) {
        double ax = Math.abs(x) < 0.9 ? 1 : 0, ay = ax == 1 ? 0 : 1;
        // t = normalize(a x n)
        double tx = ay * z, ty = -ax * z, tz = ax * y - ay * x;
        double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
        out[0] = tx / l;
        out[1] = ty / l;
        out[2] = tz / l;
    }

    private static double sphere(double ox, double oy, double oz, double dx, double dy, double dz, double cx, double cy, double cz, double r) {
        double lx = cx - ox, ly = cy - oy, lz = cz - oz;
        double tca = lx * dx + ly * dy + lz * dz;
        double d2 = lx * lx + ly * ly + lz * lz - tca * tca;
        if (d2 > r * r) return -1;
        double thc = Math.sqrt(r * r - d2);
        double t0 = tca - thc;
        if (t0 > 1e-4) return t0;
        double t1 = tca + thc;
        return t1 > 1e-4 ? t1 : -1;
    }

    /** Diffuse + GGX specular BRDF value (without cosine). */
    private static void brdf(double nx, double ny, double nz, double vx, double vy, double vz, double lx, double ly, double lz,
                             double ar, double ag, double ab, double alpha, double f0, boolean metal, boolean spec, double[] out) {
        double nl = Math.max(1e-6, nx * lx + ny * ly + nz * lz), nv = Math.max(1e-4, nx * vx + ny * vy + nz * vz);
        double hx = vx + lx, hy = vy + ly, hz = vz + lz;
        double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
        double sr = 0, sg = 0, sb = 0;
        double F = f0;
        if (hl > 1e-9) {
            hx /= hl;
            hy /= hl;
            hz /= hl;
            double nh = Math.max(0, nx * hx + ny * hy + nz * hz), vh = Math.max(0, vx * hx + vy * hy + vz * hz);
            double fw = Math.pow(1 - vh, 5);
            if (spec) {
                double a2 = alpha * alpha, d = nh * nh * (a2 - 1) + 1;
                double D = a2 / (Math.PI * d * d);
                double G = g1(nl, a2) * g1(nv, a2);
                double common = D * G / (4 * nl * nv);
                if (metal) {
                    sr = common * (ar + (1 - ar) * fw);
                    sg = common * (ag + (1 - ag) * fw);
                    sb = common * (ab + (1 - ab) * fw);
                } else {
                    double Fs = f0 + (1 - f0) * fw;
                    sr = sg = sb = common * Fs;
                }
            }
            F = f0 + (1 - f0) * fw;
        }
        if (metal) {
            out[0] = sr;
            out[1] = sg;
            out[2] = sb;
        } else {
            double kd = (1 - F) / Math.PI;
            out[0] = ar * kd + sr;
            out[1] = ag * kd + sg;
            out[2] = ab * kd + sb;
        }
    }

    private static double g1(double x, double a2) {
        return 2 * x / (x + Math.sqrt(a2 + (1 - a2) * x * x));
    }

    /** Shadow ray with transmission through glass and cutouts. Result transmittance in c.trans. */
    private boolean visible(double ox, double oy, double oz, double dx, double dy, double dz, double maxT, Ctx c) {
        c.trans[0] = c.trans[1] = c.trans[2] = 1;
        RenderScene.Hit h = c.shadow;
        double remaining = maxT;
        for (int i = 0; i < 12; i++) {
            if (!sc.intersect((float) ox, (float) oy, (float) oz, (float) dx, (float) dy, (float) dz, 1e-4f, (float) Math.min(remaining, 1e30), h))
                return true;
            int tri = h.tri;
            SurfaceMaterial m = sc.mats[sc.tm[tri]];
            float[] mc = mColor[sc.tm[tri]];
            if (m.glass) {
                c.trans[0] *= 0.85 * (0.3 + 0.7 * mc[0]);
                c.trans[1] *= 0.85 * (0.3 + 0.7 * mc[1]);
                c.trans[2] *= 0.85 * (0.3 + 0.7 * mc[2]);
            } else {
                double op = m.opacity;
                if (m.albedo != null && op > 0) {
                    float u = h.u, v = h.v, w0 = 1 - u - v;
                    int o6 = tri * 6;
                    double tu = w0 * sc.tuv[o6] + u * sc.tuv[o6 + 2] + v * sc.tuv[o6 + 4];
                    double tv = w0 * sc.tuv[o6 + 1] + u * sc.tuv[o6 + 3] + v * sc.tuv[o6 + 5];
                    m.albedo.sample(tu, tv, c.tex);
                    op *= c.tex[3];
                }
                if (op >= 0.999) return false;
                c.trans[0] *= 1 - op;
                c.trans[1] *= 1 - op;
                c.trans[2] *= 1 - op;
            }
            if (c.trans[0] + c.trans[1] + c.trans[2] < 0.01) return false;
            ox += dx * (h.t + 1e-4);
            oy += dy * (h.t + 1e-4);
            oz += dz * (h.t + 1e-4);
            remaining -= h.t + 1e-4;
            if (remaining <= 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ output

    /** Tone-mapped ARGB image of the current state. */
    public int[] image(boolean denoise) {
        int n = width * height;
        int p = Math.max(1, passes);
        float inv = 1f / p;
        float[] col = new float[n * 3], alb = new float[n * 3], nrm = new float[n * 3], dep = new float[n], var = new float[n];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 3; k++) {
                col[i * 3 + k] = acc[i * 3 + k] * inv;
                alb[i * 3 + k] = gAlb[i * 3 + k] * inv;
                nrm[i * 3 + k] = gNrm[i * 3 + k] * inv;
            }
            dep[i] = gDep[i] * inv;
            double l = 0.2126 * col[i * 3] + 0.7152 * col[i * 3 + 1] + 0.0722 * col[i * 3 + 2];
            var[i] = (float) Math.max(0, (accSq[i] * inv - l * l) / p);
        }
        if (denoise && passes > 0) col = Denoiser.denoise(col, alb, nrm, dep, var, width, height);
        // auto exposure (log average luminance)
        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < n; i += 3) {
            double l = 0.2126 * col[i * 3] + 0.7152 * col[i * 3 + 1] + 0.0722 * col[i * 3 + 2];
            sum += Math.log(1e-4 + l);
            cnt++;
        }
        double avg = Math.exp(sum / Math.max(1, cnt));
        double ev = Math.max(0.05, Math.min(60, 0.24 / Math.max(1e-4, avg))) * exposure;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int r = tone(col[i * 3] * ev), g = tone(col[i * 3 + 1] * ev), b = tone(col[i * 3 + 2] * ev);
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    private static int tone(double x) {
        x = Math.max(0, x);
        double y = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
        y = Math.max(0, Math.min(1, y));
        y = y <= 0.0031308 ? 12.92 * y : 1.055 * Math.pow(y, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, y)) * 255);
    }
}
