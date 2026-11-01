package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.Texture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Finds and loads texture files referenced by models (absolute, relative, or anywhere below the model folder). */
public final class TextureResolver {
    private static final Map<String, Texture> CACHE = new ConcurrentHashMap<>();
    private static final Texture MISSING = new Texture(1, 1, new int[]{0});

    private TextureResolver() {
    }

    public static Texture load(Path dir, String ref) {
        if (ref == null || ref.isBlank()) return null;
        Path p = find(dir, ref.trim().replace('"', ' ').trim());
        if (p == null) return null;
        String key = p.toAbsolutePath().normalize().toString();
        Texture t = CACHE.computeIfAbsent(key, k -> {
            try {
                return ImageLoader.load(p).limit(2048);
            } catch (Exception e) {
                return MISSING;
            }
        });
        return t == MISSING ? null : t;
    }

    static Path find(Path dir, String ref) {
        String norm = ref.replace('\\', '/');
        try {
            Path direct = Path.of(ref);
            if (direct.isAbsolute() && Files.isRegularFile(direct)) return direct;
        } catch (Exception ignored) {
        }
        if (dir == null) return null;
        try {
            Path rel = dir.resolve(norm);
            if (Files.isRegularFile(rel)) return rel;
        } catch (Exception ignored) {
        }
        String name = norm.substring(norm.lastIndexOf('/') + 1);
        if (name.isEmpty()) return null;
        String base = stripExt(name);
        // search model folder and its parent (textures often live in a sibling "Texture(s)" folder)
        for (Path root : new Path[]{dir, dir.getParent()}) {
            if (root == null) continue;
            Path hit = search(root, name, base);
            if (hit != null) return hit;
        }
        return null;
    }

    private static Path search(Path root, String name, String base) {
        try (Stream<Path> s = Files.walk(root, 4)) {
            Path exact = null, sameBase = null;
            for (Path p : (Iterable<Path>) s::iterator) {
                String fn = p.getFileName().toString();
                if (fn.equalsIgnoreCase(name)) {
                    exact = p;
                    break;
                }
                if (sameBase == null && stripExt(fn).equalsIgnoreCase(base) && isImage(fn)) sameBase = p;
            }
            return exact != null ? exact : sameBase;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isImage(String fn) {
        String l = fn.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".bmp") || l.endsWith(".tif")
                || l.endsWith(".tiff") || l.endsWith(".gif");
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }
}
