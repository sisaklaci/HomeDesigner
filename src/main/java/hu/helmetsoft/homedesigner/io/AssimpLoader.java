package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.util.Texture;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.assimp.Assimp.*;

/** Imports FBX, 3DS, DAE, glTF, PLY, STL, (old) BLEND and other formats via Assimp. */
final class AssimpLoader {
    private AssimpLoader() {
    }

    static synchronized List<RawPart> load(Path file) throws IOException {
        int flags = aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices
                | aiProcess_PreTransformVertices | aiProcess_FlipUVs | aiProcess_SortByPType | aiProcess_FindDegenerates
                | aiProcess_RemoveRedundantMaterials | aiProcess_ValidateDataStructure;
        AIScene scene;
        if (Boolean.getBoolean("hd.debug")) System.err.println("assimp: " + file);
        try {
            scene = aiImportFile(file.toAbsolutePath().toString(), flags);
        } catch (Throwable t) {
            throw new IOException(I18n.text("Az Assimp könyvtár nem tölthető be: ") + t.getMessage(), t);
        }
        if (scene == null || scene.mRootNode() == null) {
            String err = aiGetErrorString();
            throw new IOException(I18n.text("A fájl nem olvasható (") + file.getFileName() + "): " + (err == null ? "ismeretlen hiba" : err));
        }
        try {
            Path dir = file.toAbsolutePath().getParent();
            Map<Integer, RawPart> template = new HashMap<>();
            int nm = scene.mNumMaterials();
            PointerBuffer mats = scene.mMaterials();
            for (int i = 0; i < nm; i++) template.put(i, material(AIMaterial.create(mats.get(i)), scene, dir, i));

            List<RawPart> out = new ArrayList<>();
            Map<Integer, RawPart> byMat = new HashMap<>();
            PointerBuffer meshes = scene.mMeshes();
            for (int m = 0; m < scene.mNumMeshes(); m++) {
                AIMesh mesh = AIMesh.create(meshes.get(m));
                if ((mesh.mPrimitiveTypes() & aiPrimitiveType_TRIANGLE) == 0) continue;
                int mi = mesh.mMaterialIndex();
                RawPart p = byMat.computeIfAbsent(mi, k -> {
                    RawPart t = template.get(k), r = new RawPart();
                    if (t != null) {
                        r.material = t.material;
                        r.color = t.color;
                        r.colorGiven = t.colorGiven;
                        r.texture = t.texture;
                        r.opacity = t.opacity;
                        r.shininess = t.shininess;
                        r.specular = t.specular;
                    }
                    out.add(r);
                    return r;
                });
                int base = p.vc;
                AIVector3D.Buffer v = mesh.mVertices();
                AIVector3D.Buffer n = mesh.mNormals();
                AIVector3D.Buffer uv = mesh.mTextureCoords(0);
                if (uv == null) p.hasUV = false;
                if (n == null) p.hasNormals = false;
                int nv = mesh.mNumVertices();
                for (int i = 0; i < nv; i++) {
                    AIVector3D a = v.get(i);
                    float nx = 0, ny = 0, nz = 0, u = 0, w = 0;
                    if (n != null) {
                        AIVector3D b = n.get(i);
                        nx = b.x();
                        ny = b.y();
                        nz = b.z();
                    }
                    if (uv != null) {
                        AIVector3D c = uv.get(i);
                        u = c.x();
                        w = c.y();
                    }
                    p.vertex(a.x(), a.y(), a.z(), nx, ny, nz, u, w);
                }
                AIFace.Buffer faces = mesh.mFaces();
                for (int f = 0; f < mesh.mNumFaces(); f++) {
                    AIFace face = faces.get(f);
                    if (face.mNumIndices() != 3) continue;
                    IntBuffer ib = face.mIndices();
                    p.tri(base + ib.get(0), base + ib.get(1), base + ib.get(2));
                }
            }
            out.removeIf(p -> p.ic == 0);
            return out;
        } finally {
            aiReleaseImport(scene);
        }
    }

    private static RawPart material(AIMaterial mat, AIScene scene, Path dir, int index) {
        RawPart r = new RawPart();
        try (MemoryStack st = MemoryStack.stackPush()) {
            AIString name = AIString.calloc(st);
            if (aiGetMaterialString(mat, AI_MATKEY_NAME, aiTextureType_NONE, 0, name) == aiReturn_SUCCESS)
                r.material = name.dataString();
            else r.material = "mat" + index;
            AIColor4D c = AIColor4D.calloc(st);
            if (aiGetMaterialColor(mat, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, c) == aiReturn_SUCCESS
                    || aiGetMaterialColor(mat, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, c) == aiReturn_SUCCESS) {
                r.color = new double[]{c.r(), c.g(), c.b()};
                r.colorGiven = true;
            }
            FloatBuffer fb = st.mallocFloat(1);
            IntBuffer max = st.ints(1);
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, fb, max) == aiReturn_SUCCESS) {
                float o = fb.get(0);
                if (o > 0.01f) r.opacity = o;
            }
            max.put(0, 1);
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_SHININESS, aiTextureType_NONE, 0, fb, max) == aiReturn_SUCCESS)
                r.shininess = fb.get(0);
            AIString path = AIString.calloc(st);
            for (int type : new int[]{aiTextureType_BASE_COLOR, aiTextureType_DIFFUSE}) {
                if (aiGetMaterialTexture(mat, type, 0, path, (IntBuffer) null, null, null, null, null, null) == aiReturn_SUCCESS) {
                    String ref = path.dataString();
                    Texture t = ref.startsWith("*") ? embedded(scene, ref) : null;
                    if (t == null) t = TextureResolver.load(dir, ref);
                    if (t != null) {
                        r.texture = t;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return r;
    }

    private static Texture embedded(AIScene scene, String ref) {
        try {
            int i = Integer.parseInt(ref.substring(1));
            if (i < 0 || i >= scene.mNumTextures()) return null;
            return decode(AITexture.create(scene.mTextures().get(i)));
        } catch (Exception e) {
            return null;
        }
    }

    private static Texture decode(AITexture t) {
        try {
            if (t.mHeight() == 0) {
                ByteBuffer bb = t.pcDataCompressed();
                byte[] data = new byte[bb.remaining()];
                bb.get(data);
                return ImageLoader.load(data).limit(2048);
            }
            int w = t.mWidth(), h = t.mHeight();
            AITexel.Buffer px = t.pcData();
            int[] argb = new int[w * h];
            for (int i = 0; i < argb.length; i++) {
                AITexel x = px.get(i);
                argb[i] = ((x.a() & 255) << 24) | ((x.r() & 255) << 16) | ((x.g() & 255) << 8) | (x.b() & 255);
            }
            return new Texture(w, h, argb).limit(2048);
        } catch (Exception e) {
            return null;
        }
    }
}
