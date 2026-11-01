package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Fast Wavefront OBJ + MTL reader. */
final class ObjLoader {
    private float[] v = new float[3 * 4096], vt = new float[2 * 4096], vn = new float[3 * 4096];
    private int nv, nvt, nvn;
    private final Map<String, RawPart> parts = new LinkedHashMap<>();
    private final Map<String, LongIntMap> maps = new HashMap<>();
    private final Map<String, String[]> mtl = new HashMap<>(); // Kd, map_Kd, d, Ns, Ks, Tr, Pm, Pr
    private RawPart cur;
    private LongIntMap curMap;
    private final Path dir;

    private ObjLoader(Path file) {
        dir = file.toAbsolutePath().getParent();
    }

    static List<RawPart> load(Path file, Consumer<String> progress) throws IOException {
        ObjLoader l = new ObjLoader(file);
        l.parse(file, progress);
        l.parts.values().forEach(l::applyMtl);
        return new ArrayList<>(l.parts.values());
    }

    private void parse(Path file, Consumer<String> progress) throws IOException {
        long size = Files.size(file), read = 0, lastReport = 0;
        use("default");
        int[] fv = new int[64], ft = new int[64], fn = new int[64];
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 20];
            byte[] line = new byte[4096];
            int ll = 0, n;
            while ((n = in.read(buf)) > 0) {
                read += n;
                for (int i = 0; i < n; i++) {
                    byte c = buf[i];
                    if (c == '\n' || c == '\r') {
                        if (ll > 0) handle(line, ll, fv, ft, fn);
                        ll = 0;
                    } else {
                        if (ll == line.length) line = Arrays.copyOf(line, ll * 2);
                        line[ll++] = c;
                    }
                }
                if (progress != null && read - lastReport > 8_000_000) {
                    lastReport = read;
                    progress.accept(I18n.text("OBJ beolvasása: ") + (read * 100 / Math.max(1, size)) + "%");
                }
            }
            if (ll > 0) handle(line, ll, fv, ft, fn);
        }
        parts.values().removeIf(p -> p.ic == 0);
    }

    private int pos;

    private void handle(byte[] s, int len, int[] fv, int[] ft, int[] fn) {
        pos = 0;
        while (pos < len && (s[pos] == ' ' || s[pos] == '\t')) pos++;
        if (pos >= len) return;
        byte c0 = s[pos], c1 = pos + 1 < len ? s[pos + 1] : 0;
        if (c0 == 'v' && (c1 == ' ' || c1 == '\t')) {
            pos += 2;
            if ((nv + 1) * 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[nv * 3] = num(s, len);
            v[nv * 3 + 1] = num(s, len);
            v[nv * 3 + 2] = num(s, len);
            nv++;
        } else if (c0 == 'v' && c1 == 't') {
            pos += 2;
            if ((nvt + 1) * 2 > vt.length) vt = Arrays.copyOf(vt, vt.length * 2);
            vt[nvt * 2] = num(s, len);
            vt[nvt * 2 + 1] = num(s, len);
            nvt++;
        } else if (c0 == 'v' && c1 == 'n') {
            pos += 2;
            if ((nvn + 1) * 3 > vn.length) vn = Arrays.copyOf(vn, vn.length * 2);
            vn[nvn * 3] = num(s, len);
            vn[nvn * 3 + 1] = num(s, len);
            vn[nvn * 3 + 2] = num(s, len);
            nvn++;
        } else if (c0 == 'f' && (c1 == ' ' || c1 == '\t')) {
            pos += 2;
            int k = 0;
            while (true) {
                skipWs(s, len);
                if (pos >= len || k >= fv.length) break;
                fv[k] = idx(s, len, nv);
                ft[k] = 0;
                fn[k] = 0;
                if (pos < len && s[pos] == '/') {
                    pos++;
                    if (pos < len && s[pos] != '/') ft[k] = idx(s, len, nvt);
                    if (pos < len && s[pos] == '/') {
                        pos++;
                        fn[k] = idx(s, len, nvn);
                    }
                }
                while (pos < len && s[pos] != ' ' && s[pos] != '\t') pos++;
                k++;
            }
            if (k < 3) return;
            int[] vi = new int[k];
            for (int i = 0; i < k; i++) vi[i] = corner(fv[i], ft[i], fn[i]);
            for (int i = 1; i + 1 < k; i++) if (vi[0] >= 0 && vi[i] >= 0 && vi[i + 1] >= 0) cur.tri(vi[0], vi[i], vi[i + 1]);
        } else if (startsWith(s, len, "usemtl")) {
            use(new String(s, pos + 6, len - pos - 6, StandardCharsets.UTF_8).trim());
        } else if (startsWith(s, len, "mtllib")) {
            String name = new String(s, pos + 6, len - pos - 6, StandardCharsets.UTF_8).trim();
            loadMtl(name);
        }
    }

    private boolean startsWith(byte[] s, int len, String w) {
        if (len - pos < w.length()) return false;
        for (int i = 0; i < w.length(); i++) if (s[pos + i] != w.charAt(i)) return false;
        return true;
    }

    private void use(String name) {
        cur = parts.computeIfAbsent(name, k -> {
            RawPart p = new RawPart();
            p.material = k;
            return p;
        });
        curMap = maps.computeIfAbsent(name, k -> new LongIntMap());
    }

    private int corner(int vi, int ti, int ni) {
        if (vi < 0 || vi >= nv) return -1;
        long k1 = vi + 1L, k2 = ((long) (ti + 1) << 32) | ((ni + 1) & 0xffffffffL);
        int found = curMap.get(k1, k2);
        if (found >= 0) return found;
        float u = 0, w = 0, nx = 0, ny = 0, nz = 0;
        if (ti >= 0 && ti < nvt) {
            u = vt[ti * 2];
            w = 1 - vt[ti * 2 + 1];
        } else cur.hasUV = false;
        if (ni >= 0 && ni < nvn) {
            nx = vn[ni * 3];
            ny = vn[ni * 3 + 1];
            nz = vn[ni * 3 + 2];
        } else cur.hasNormals = false;
        int id = cur.vertex(v[vi * 3], v[vi * 3 + 1], v[vi * 3 + 2], nx, ny, nz, u, w);
        curMap.put(k1, k2, id);
        return id;
    }

    private void skipWs(byte[] s, int len) {
        while (pos < len && (s[pos] == ' ' || s[pos] == '\t')) pos++;
    }

    /** Parses a 1-based (or negative relative) index, returns 0-based. */
    private int idx(byte[] s, int len, int count) {
        boolean neg = false;
        if (pos < len && s[pos] == '-') {
            neg = true;
            pos++;
        }
        int r = 0;
        while (pos < len && s[pos] >= '0' && s[pos] <= '9') r = r * 10 + (s[pos++] - '0');
        return neg ? count - r : r - 1;
    }

    private float num(byte[] s, int len) {
        skipWs(s, len);
        int start = pos;
        boolean neg = false;
        if (pos < len && (s[pos] == '-' || s[pos] == '+')) {
            neg = s[pos] == '-';
            pos++;
        }
        long m = 0;
        int exp = 0, digits = 0;
        while (pos < len && s[pos] >= '0' && s[pos] <= '9') {
            if (digits < 18) { m = m * 10 + (s[pos] - '0'); digits++; } else exp++;
            pos++;
        }
        if (pos < len && s[pos] == '.') {
            pos++;
            while (pos < len && s[pos] >= '0' && s[pos] <= '9') {
                if (digits < 18) { m = m * 10 + (s[pos] - '0'); digits++; exp--; }
                pos++;
            }
        }
        if (pos < len && (s[pos] == 'e' || s[pos] == 'E')) {
            pos++;
            boolean en = false;
            if (pos < len && (s[pos] == '-' || s[pos] == '+')) { en = s[pos] == '-'; pos++; }
            int e = 0;
            while (pos < len && s[pos] >= '0' && s[pos] <= '9') e = e * 10 + (s[pos++] - '0');
            exp += en ? -e : e;
        }
        if (pos == start) return 0;
        double r = m * Math.pow(10, exp);
        while (pos < len && s[pos] != ' ' && s[pos] != '\t') pos++;
        return (float) (neg ? -r : r);
    }

    private void loadMtl(String name) {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) {
            Path alt = dir.resolve(name.replace('\\', '/'));
            if (!Files.exists(alt)) return;
            p = alt;
        }
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.ISO_8859_1)) {
            String line;
            String[] m = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl")) {
                    m = new String[8];
                    mtl.put(line.substring(6).trim(), m);
                } else if (m != null) {
                    if (line.startsWith("Kd ")) m[0] = line.substring(3).trim();
                    else if (line.startsWith("map_Kd")) m[1] = mapName(line.substring(6).trim());
                    else if (line.startsWith("d ")) m[2] = line.substring(2).trim();
                    else if (line.startsWith("Ns ")) m[3] = line.substring(3).trim();
                    else if (line.startsWith("Ks ")) m[4] = line.substring(3).trim();
                    else if (line.startsWith("Tr ")) m[5] = line.substring(3).trim();
                    else if (line.startsWith("Pm ")) m[6] = line.substring(3).trim();
                    else if (line.startsWith("Pr ")) m[7] = line.substring(3).trim();
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** map_Kd may contain options like "-s 1 1 1 file.jpg". */
    private static String mapName(String s) {
        String[] t = s.split("\\s+");
        int i = 0;
        while (i < t.length - 1 && t[i].startsWith("-")) {
            String o = t[i];
            i += (o.equals("-s") || o.equals("-o") || o.equals("-t")) ? 4 : (o.equals("-mm") ? 3 : 2);
        }
        if (i >= t.length) i = t.length - 1;
        return String.join(" ", Arrays.copyOfRange(t, i, t.length));
    }

    void applyMtl(RawPart part) {
        String[] m = mtl.get(part.material);
        if (m == null) return;
        if (m[0] != null) {
            String[] c = m[0].split("\\s+");
            try {
                part.color = new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])};
                part.colorGiven = true;
            } catch (Exception ignored) {
            }
        }
        try {
            if (m[2] != null) part.opacity = Double.parseDouble(m[2]);
            else if (m[5] != null) part.opacity = 1 - Double.parseDouble(m[5]);
            if (m[3] != null) part.shininess = Double.parseDouble(m[3]);
            if (m[4] != null) part.specular = Double.parseDouble(m[4].split("\\s+")[0]);
            if (m[6] != null) part.metal = Double.parseDouble(m[6]) >= 0.5;
            if (m[7] != null) part.roughness = Double.parseDouble(m[7]);
        } catch (Exception ignored) {
        }
        if (m[1] != null) part.texture = TextureResolver.load(dir, m[1]);
    }

    /** Open addressing (long,long) -> int hash map; k1 must be non-zero. */
    static final class LongIntMap {
        long[] k1s = new long[1024], k2s = new long[1024];
        int[] vals = new int[1024];
        int size;

        int get(long a, long b) {
            int mask = k1s.length - 1, i = (int) (mix(a * 31 + b) & mask);
            while (true) {
                long kk = k1s[i];
                if (kk == 0) return -1;
                if (kk == a && k2s[i] == b) return vals[i];
                i = (i + 1) & mask;
            }
        }

        void put(long a, long b, int v) {
            if (size * 2 >= k1s.length) grow();
            int mask = k1s.length - 1, i = (int) (mix(a * 31 + b) & mask);
            while (k1s[i] != 0 && !(k1s[i] == a && k2s[i] == b)) i = (i + 1) & mask;
            if (k1s[i] == 0) size++;
            k1s[i] = a;
            k2s[i] = b;
            vals[i] = v;
        }

        private void grow() {
            long[] o1 = k1s, o2 = k2s;
            int[] ov = vals;
            k1s = new long[o1.length * 2];
            k2s = new long[o1.length * 2];
            vals = new int[o1.length * 2];
            size = 0;
            for (int i = 0; i < o1.length; i++) if (o1[i] != 0) put(o1[i], o2[i], ov[i]);
        }

        private static long mix(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            return k;
        }
    }
}