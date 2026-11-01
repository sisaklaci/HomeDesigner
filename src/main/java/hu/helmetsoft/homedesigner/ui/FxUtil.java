package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.geom.Mesh;
import hu.helmetsoft.homedesigner.material.SurfaceMaterial;
import hu.helmetsoft.homedesigner.util.Texture;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** JavaFX conversion helpers with caches. */
public final class FxUtil {
    private FxUtil() {
    }

    private static final Map<Mesh, TriangleMesh> MESHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, PhongMaterial> MATERIALS = new java.util.HashMap<>();

    public static Image image(Texture t) {
        if (t == null) return null;
        Object o = t.getFxImage();
        if (o instanceof Image i) return i;
        WritableImage wi = new WritableImage(t.w, t.h);
        wi.getPixelWriter().setPixels(0, 0, t.w, t.h, PixelFormat.getIntArgbInstance(), t.argb, 0, t.w);
        t.setFxImage(wi);
        return wi;
    }

    public static Color color(double[] c, double a) {
        return Color.color(clamp(c[0]), clamp(c[1]), clamp(c[2]), clamp(a));
    }

    static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    public static String hex(Color c) {
        return String.format(I18n.locale(), "#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    public static Color parse(String hex, Color def) {
        if (hex == null) return def;
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return def;
        }
    }

    public static PhongMaterial material(SurfaceMaterial m) {
        PhongMaterial pm = MATERIALS.get(m.key);
        if (pm != null) return pm;
        pm = new PhongMaterial();
        if (m.emission != null) {
            double mx = Math.max(m.emission[0], Math.max(m.emission[1], m.emission[2]));
            Color c = Color.color(clamp(m.emission[0] / mx), clamp(m.emission[1] / mx), clamp(m.emission[2] / mx));
            pm.setDiffuseColor(c);
            WritableImage wi = new WritableImage(1, 1);
            wi.getPixelWriter().setColor(0, 0, c);
            pm.setSelfIlluminationMap(wi);
        } else if (m.glass) {
            pm.setDiffuseColor(Color.WHITE);
            // Prism's 3D pipeline uses texture alpha, not diffuseColor/Node opacity.
            WritableImage glass = new WritableImage(1,1);
            glass.getPixelWriter().setColor(0,0,color(m.color,Math.min(0.35,m.opacity)));
            pm.setDiffuseMap(glass);
            pm.setSpecularColor(Color.gray(0.9));
            pm.setSpecularPower(120);
        } else {
            pm.setDiffuseColor(color(m.color, m.opacity));
            if (m.albedo != null) pm.setDiffuseMap(image(m.albedo));
            double s = (m.metal ? 0.7 : m.specular * 0.55) * (1 - m.roughness * 0.85);
            pm.setSpecularColor(Color.gray(clamp(s)));
            pm.setSpecularPower(Math.max(4, 2 / Math.max(0.01, m.roughness * m.roughness)));
            if (m.bump != null) pm.setBumpMap(image(m.bump));
        }
        MATERIALS.put(m.key, pm);
        return pm;
    }

    public static void clearMaterialCache() {
        MATERIALS.clear();
    }

    public static TriangleMesh mesh(Mesh m) {
        TriangleMesh tm = MESHES.get(m);
        if (tm != null) return tm;
        tm = mesh(m, null);
        MESHES.put(m, tm);
        return tm;
    }

    /** Builds a TriangleMesh; uvOverride replaces the mesh UVs. */
    public static TriangleMesh mesh(Mesh m, float[] uvOverride) {
        TriangleMesh tm = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        int n = m.vertexCount();
        tm.getPoints().setAll(m.pos, 0, n * 3);
        float[] nrm = m.nrm;
        if (nrm == null) {
            m.computeNormals();
            nrm = m.nrm;
        }
        tm.getNormals().setAll(nrm, 0, n * 3);
        float[] uv = uvOverride != null ? uvOverride : m.uv;
        if (uv == null) uv = new float[n * 2];
        tm.getTexCoords().setAll(uv, 0, n * 2);
        int[] f = new int[m.idx.length * 3];
        for (int i = 0; i < m.idx.length; i++) {
            int v = m.idx[i];
            f[i * 3] = v;
            f[i * 3 + 1] = v;
            f[i * 3 + 2] = v;
        }
        tm.getFaces().setAll(f);
        return tm;
    }
}
