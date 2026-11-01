package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.geom.Mesh;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;

import java.util.ArrayList;
import java.util.List;

/** Imported, normalized 3D model: meters, centered on X/Z, bottom at Y=0. */
public final class ModelData {
    public static final class Part {
        public final Mesh mesh;
        public final SurfaceMaterial material;
        public Mesh preview;

        public Part(Mesh mesh, SurfaceMaterial material) {
            this.mesh = mesh;
            this.material = material;
        }

        public Mesh viewMesh() {
            return preview != null ? preview : mesh;
        }
    }

    public final String source;
    public final List<Part> parts = new ArrayList<>();
    /** Bounding box after normalization. */
    public float minX, minY, minZ, maxX, maxY, maxZ;
    public String note;
    private String noteKey;
    private Object[] noteArguments;

    public void setNote(String key, Object... arguments) {
        noteKey = key;
        noteArguments = arguments.clone();
        note = hu.helmetsoft.homedesigner.util.I18n.text(key, arguments);
    }

    public String displayNote() {
        return noteKey == null ? note : hu.helmetsoft.homedesigner.util.I18n.text(noteKey, noteArguments);
    }

    public ModelData(String source) {
        this.source = source;
    }

    public int triangleCount() {
        int n = 0;
        for (Part p : parts) n += p.mesh.triangleCount();
        return n;
    }

    public void computeBounds() {
        minX = minY = minZ = Float.MAX_VALUE;
        maxX = maxY = maxZ = -Float.MAX_VALUE;
        for (Part p : parts) {
            float[] v = p.mesh.pos;
            for (int i = 0; i < v.length; i += 3) {
                minX = Math.min(minX, v[i]);
                maxX = Math.max(maxX, v[i]);
                minY = Math.min(minY, v[i + 1]);
                maxY = Math.max(maxY, v[i + 1]);
                minZ = Math.min(minZ, v[i + 2]);
                maxZ = Math.max(maxZ, v[i + 2]);
            }
        }
        if (minX > maxX) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 1;
        }
    }
}
