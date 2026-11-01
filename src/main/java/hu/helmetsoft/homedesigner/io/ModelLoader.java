package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.geom.Mesh;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.util.Texture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Entry point for importing 3D models in any supported format. */
public final class ModelLoader {
    public static final int PREVIEW_TRIANGLES = 250_000;
    public static final List<String> EXTENSIONS = List.of("obj", "fbx", "3ds", "blend", "max", "dae", "gltf", "glb", "ply", "stl", "lwo", "ase", "x", "off");

    private ModelLoader() {
    }

    public static boolean isModelFile(Path p) {
        return EXTENSIONS.contains(ExternalConverters.ext(p));
    }

    public static ModelData load(Path file, Consumer<String> progress) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException(I18n.text("A fájl nem található: ") + file);
        String ext = ExternalConverters.ext(file);
        List<RawPart> raw;
        String note = null;
        String noteKey = null;
        Object noteFile = null;
        switch (ext) {
            case "obj" -> raw = ObjLoader.load(file, progress);
            case "blend" -> {
                Path conv = null;
                try {
                    conv = ExternalConverters.blendToObj(file, progress);
                } catch (IOException ignored) {
                }
                if (conv != null) raw = ObjLoader.load(conv, progress);
                else {
                    raw = AssimpProbe.isSafe(file) ? tryAssimp(file) : null;
                    if (raw != null) zUpToYUp(raw);
                    if (raw == null) {
                        Path sib = ExternalConverters.sibling(file);
                        if (sib == null) throw new IOException(I18n.text("A .blend fájl megnyitásához telepített Blender szükséges ")
                                + I18n.text("(blender.org), vagy exportálja OBJ/FBX formátumba."));
                        raw = loadDirect(sib, progress);
                        note = I18n.text("Blender nem található – a mellette lévő ") + sib.getFileName() + I18n.text(" fájl lett betöltve.");
                        noteKey = "Blender nem található – a mellette lévő %s fájl lett betöltve.";
                        noteFile = sib.getFileName();
                    }
                }
            }
            case "max" -> {
                Path conv = null;
                try {
                    conv = ExternalConverters.maxToFbx(file, progress);
                } catch (IOException ignored) {
                }
                if (conv != null) raw = AssimpLoader.load(conv);
                else {
                    Path sib = ExternalConverters.sibling(file);
                    if (sib == null) throw new IOException(I18n.text("A .max formátum zárt (Autodesk). Telepített 3ds Max szükséges, ")
                            + I18n.text("vagy exportálja a modellt OBJ/FBX/3DS formátumba."));
                    raw = loadDirect(sib, progress);
                    note = I18n.text("A .max helyett a mellette lévő ") + sib.getFileName() + I18n.text(" fájl lett betöltve.");
                    noteKey = "A .max helyett a mellette lévő %s fájl lett betöltve.";
                    noteFile = sib.getFileName();
                }
            }
            default -> {
                if (progress != null) progress.accept(I18n.text("Modell beolvasása..."));
                raw = AssimpLoader.load(file);
            }
        }
        if (raw == null || raw.isEmpty()) throw new IOException(I18n.text("A fájl nem tartalmaz megjeleníthető geometriát."));
        if (progress != null) progress.accept(I18n.text("Feldolgozás..."));
        ModelData md = build(file.toAbsolutePath().toString(), raw);
        md.note = note;
        if (noteKey != null) md.setNote(noteKey, noteFile);
        return md;
    }

    private static List<RawPart> loadDirect(Path p, Consumer<String> progress) throws IOException {
        return "obj".equals(ExternalConverters.ext(p)) ? ObjLoader.load(p, progress) : AssimpLoader.load(p);
    }

    /** Blender files read directly are Z-up: (x, y, z) -> (x, z, -y). */
    private static void zUpToYUp(List<RawPart> raw) {
        for (RawPart p : raw) {
            for (int i = 0; i < p.vc * 3; i += 3) {
                float y = p.pos[i + 1];
                p.pos[i + 1] = p.pos[i + 2];
                p.pos[i + 2] = -y;
                float ny = p.nrm[i + 1];
                p.nrm[i + 1] = p.nrm[i + 2];
                p.nrm[i + 2] = -ny;
            }
        }
    }

    private static List<RawPart> tryAssimp(Path p) {
        try {
            List<RawPart> r = AssimpLoader.load(p);
            return r.isEmpty() ? null : r;
        } catch (Throwable e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ normalization

    static ModelData build(String source, List<RawPart> raw) {
        double minX = 1e30, minY = 1e30, minZ = 1e30, maxX = -1e30, maxY = -1e30, maxZ = -1e30;
        for (RawPart p : raw) {
            for (int i = 0; i < p.vc * 3; i += 3) {
                float x = p.pos[i], y = p.pos[i + 1], z = p.pos[i + 2];
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        double ext = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        double scale = ext > 300 ? 0.001 : ext > 8 ? 0.01 : 1;
        double cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        ModelData md = new ModelData(source);
        int idx = 0;
        for (RawPart p : raw) {
            float[] pos = new float[p.vc * 3];
            for (int i = 0; i < pos.length; i += 3) {
                pos[i] = (float) ((p.pos[i] - cx) * scale);
                pos[i + 1] = (float) ((p.pos[i + 1] - minY) * scale);
                pos[i + 2] = (float) ((p.pos[i + 2] - cz) * scale);
                if (!Float.isFinite(pos[i]) || !Float.isFinite(pos[i + 1]) || !Float.isFinite(pos[i + 2])) pos[i] = pos[i + 1] = pos[i + 2] = 0;
            }
            Mesh m = new Mesh(pos, Arrays.copyOf(p.nrm, p.vc * 3), Arrays.copyOf(p.uv, p.vc * 2), Arrays.copyOf(p.idx, p.ic));
            if (!p.hasNormals || zeroNormals(m)) m.computeNormals();
            else normalize(m.nrm);
            md.parts.add(new ModelData.Part(m, material(source + "#" + idx++ + ":" + p.material, p)));
        }
        md.computeBounds();
        int tris = md.triangleCount();
        if (tris > PREVIEW_TRIANGLES) decimate(md, tris);
        return md;
    }

    private static boolean zeroNormals(Mesh m) {
        int zeros = 0, n = m.nrm.length / 3;
        for (int i = 0; i < m.nrm.length; i += 3)
            if (m.nrm[i] == 0 && m.nrm[i + 1] == 0 && m.nrm[i + 2] == 0) zeros++;
        return zeros > n / 10;
    }

    private static void normalize(float[] n) {
        for (int i = 0; i < n.length; i += 3) {
            float l = (float) Math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]);
            if (l > 1e-8f) {
                n[i] /= l;
                n[i + 1] /= l;
                n[i + 2] /= l;
            } else n[i + 1] = 1;
        }
    }

    private static SurfaceMaterial material(String key, RawPart p) {
        double[] c = p.color.clone();
        double lum = c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11;
        Texture tex = p.texture;
        if (tex != null) {
            if (lum < 0.05) c = new double[]{1, 1, 1};
        } else if (!p.colorGiven || lum < 0.02) {
            c = new double[]{0.78, 0.78, 0.76};
        }
        double ns = Math.max(0, Math.min(1000, p.shininess));
        double rough = Math.max(0.12, Math.min(0.95, 1 - Math.sqrt(ns / 1000.0) * 1.1));
        if (Double.isFinite(p.roughness)) rough = Math.clamp(p.roughness,0,1);
        double spec = Math.max(0.04, Math.min(0.5, p.specular));
        SurfaceMaterial material = MaterialLibrary.imported(key, c, tex, rough, spec, Math.max(0.05, Math.min(1, p.opacity)));
        material.metal = p.metal;
        return material;
    }

    // ------------------------------------------------------------------ preview decimation (vertex clustering)

    private static void decimate(ModelData md, int tris) {
        double ext = Math.max(md.maxX - md.minX, Math.max(md.maxY - md.minY, md.maxZ - md.minZ));
        double cell = ext / 400;
        for (int iter = 0; iter < 8; iter++) {
            int total = 0;
            List<Mesh> res = new ArrayList<>();
            for (ModelData.Part p : md.parts) {
                Mesh m = cluster(p.mesh, cell, md);
                res.add(m);
                total += m.triangleCount();
            }
            if (total <= PREVIEW_TRIANGLES || iter == 7) {
                for (int i = 0; i < res.size(); i++) md.parts.get(i).preview = res.get(i);
                return;
            }
            cell *= 1.5;
        }
    }

    private static Mesh cluster(Mesh m, double cell, ModelData md) {
        int n = m.vertexCount();
        int[] remap = new int[n];
        HashMap<Long, Integer> cells = new HashMap<>();
        int count = 0;
        int[] rep = new int[n];
        for (int i = 0; i < n; i++) {
            long gx = (long) Math.floor((m.pos[i * 3] - md.minX) / cell);
            long gy = (long) Math.floor((m.pos[i * 3 + 1] - md.minY) / cell);
            long gz = (long) Math.floor((m.pos[i * 3 + 2] - md.minZ) / cell);
            // include normal octant so hard edges survive
            int no = (m.nrm[i * 3] > 0 ? 1 : 0) | (m.nrm[i * 3 + 1] > 0 ? 2 : 0) | (m.nrm[i * 3 + 2] > 0 ? 4 : 0);
            long key = (gx & 0xFFFFF) | ((gy & 0xFFFFF) << 20) | ((gz & 0xFFFFF) << 40) | ((long) no << 60);
            Integer c = cells.get(key);
            if (c == null) {
                c = count;
                rep[count++] = i;
                cells.put(key, c);
            }
            remap[i] = c;
        }
        float[] pos = new float[count * 3], nrm = new float[count * 3], uv = new float[count * 2];
        for (int c = 0; c < count; c++) {
            int i = rep[c];
            System.arraycopy(m.pos, i * 3, pos, c * 3, 3);
            System.arraycopy(m.nrm, i * 3, nrm, c * 3, 3);
            System.arraycopy(m.uv, i * 2, uv, c * 2, 2);
        }
        int[] idx = new int[m.idx.length];
        int k = 0;
        for (int t = 0; t < m.idx.length; t += 3) {
            int a = remap[m.idx[t]], b = remap[m.idx[t + 1]], c = remap[m.idx[t + 2]];
            if (a == b || b == c || a == c) continue;
            idx[k++] = a;
            idx[k++] = b;
            idx[k++] = c;
        }
        return new Mesh(pos, nrm, uv, Arrays.copyOf(idx, k));
    }
}
