package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Converts .blend / .max files via installed Blender / 3ds Max, or finds an exported sibling file. */
final class ExternalConverters {
    private ExternalConverters() {
    }

    static final List<String> SIBLING_EXT = List.of("obj", "fbx", "3ds", "dae", "glb", "gltf");

    static Path cacheDir() {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null) base = System.getProperty("user.home");
        Path p = Path.of(base, "HomeDesigner", "cache");
        try {
            Files.createDirectories(p);
        } catch (IOException ignored) {
        }
        return p;
    }

    private static Path outDir(Path src) throws IOException {
        String id = Integer.toHexString(src.toAbsolutePath().toString().toLowerCase().hashCode()) + "_"
                + Long.toHexString(Files.getLastModifiedTime(src).toMillis()) + "_" + Long.toHexString(Files.size(src));
        Path d = cacheDir().resolve(id);
        Files.createDirectories(d);
        return d;
    }

    // ---------------------------------------------------------------- Blender

    static Path findBlender() {
        String env = System.getenv("BLENDER_PATH");
        if (env != null && Files.isRegularFile(Path.of(env))) return Path.of(env);
        List<Path> candidates = new ArrayList<>();
        for (String root : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA") == null ? null : System.getenv("LOCALAPPDATA") + "\\Programs"}) {
            if (root == null) continue;
            Path bf = Path.of(root, "Blender Foundation");
            if (Files.isDirectory(bf)) {
                try (Stream<Path> s = Files.list(bf)) {
                    s.map(d -> d.resolve("blender.exe")).filter(Files::isRegularFile).forEach(candidates::add);
                } catch (IOException ignored) {
                }
            }
            Path steam = Path.of(root, "Steam", "steamapps", "common", "Blender", "blender.exe");
            if (Files.isRegularFile(steam)) candidates.add(steam);
        }
        String path = System.getenv("PATH");
        if (path != null) for (String d : path.split(";")) {
            try {
                Path b = Path.of(d.trim(), "blender.exe");
                if (Files.isRegularFile(b)) candidates.add(b);
            } catch (Exception ignored) {
            }
        }
        candidates.sort(Comparator.comparing(Path::toString).reversed()); // newest version folder first
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static Path blendToObj(Path blend, Consumer<String> progress) throws IOException {
        Path blender = findBlender();
        if (blender == null) return null;
        Path dir = outDir(blend);
        Path out = dir.resolve("model.obj");
        if (Files.isRegularFile(out) && Files.size(out) > 0) return out;
        Path script = dir.resolve("export.py");
        Files.writeString(script, String.join("\n",
                "import bpy, sys",
                "out = sys.argv[sys.argv.index('--') + 1]",
                "for o in bpy.data.objects:",
                "    try:",
                "        o.hide_viewport = False; o.hide_set(False)",
                "    except Exception: pass",
                "try:",
                "    bpy.ops.wm.obj_export(filepath=out, export_selected_objects=False, export_materials=True, path_mode='COPY', forward_axis='NEGATIVE_Z', up_axis='Y', apply_modifiers=True)",
                "except Exception:",
                "    bpy.ops.export_scene.obj(filepath=out, use_selection=False, use_materials=True, path_mode='COPY', axis_forward='-Z', axis_up='Y')",
                ""), StandardCharsets.UTF_8);
        if (progress != null) progress.accept(I18n.text("Konvertálás Blenderrel..."));
        run(List.of(blender.toString(), "-b", blend.toAbsolutePath().toString(), "--python", script.toString(), "--", out.toString()), 600);
        return Files.isRegularFile(out) && Files.size(out) > 0 ? out : null;
    }

    // ---------------------------------------------------------------- 3ds Max

    static Path findMaxBatch() {
        for (String root : new String[]{System.getenv("ProgramFiles"), "C:\\Program Files"}) {
            if (root == null) continue;
            Path ad = Path.of(root, "Autodesk");
            if (!Files.isDirectory(ad)) continue;
            try (Stream<Path> s = Files.list(ad)) {
                Optional<Path> p = s.filter(d -> d.getFileName().toString().startsWith("3ds Max"))
                        .sorted(Comparator.comparing(Path::toString).reversed())
                        .map(d -> d.resolve("3dsmaxbatch.exe")).filter(Files::isRegularFile).findFirst();
                if (p.isPresent()) return p.get();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    static Path maxToFbx(Path max, Consumer<String> progress) throws IOException {
        Path batch = findMaxBatch();
        if (batch == null) return null;
        Path dir = outDir(max);
        Path out = dir.resolve("model.fbx");
        if (Files.isRegularFile(out) && Files.size(out) > 0) return out;
        Path script = dir.resolve("export.ms");
        String src = max.toAbsolutePath().toString().replace("\\", "\\\\");
        String dst = out.toString().replace("\\", "\\\\");
        Files.writeString(script, "loadMaxFile \"" + src + "\" quiet:true useFileUnits:true\n"
                + "exportFile \"" + dst + "\" #noPrompt using:FBXEXP\n", StandardCharsets.UTF_8);
        if (progress != null) progress.accept(I18n.text("Konvertálás 3ds Max-szal..."));
        run(List.of(batch.toString(), script.toString()), 900);
        return Files.isRegularFile(out) && Files.size(out) > 0 ? out : null;
    }

    // ---------------------------------------------------------------- siblings

    /** Same-named exported file next to the original, or the single other model in that folder. */
    static Path sibling(Path file) {
        Path dir = file.toAbsolutePath().getParent();
        String fn = file.getFileName().toString();
        String base = fn.substring(0, fn.lastIndexOf('.'));
        for (String ext : SIBLING_EXT) {
            Path p = dir.resolve(base + "." + ext);
            if (Files.isRegularFile(p)) return p;
        }
        List<Path> others = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                int i = n.lastIndexOf('.');
                return i > 0 && SIBLING_EXT.contains(n.substring(i + 1));
            }).forEach(others::add);
        } catch (IOException ignored) {
        }
        others.sort(Comparator.comparingInt(p -> SIBLING_EXT.indexOf(ext(p))));
        return others.isEmpty() ? null : others.get(0);
    }

    static String ext(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1);
    }

    private static void run(List<String> cmd, int timeoutSec) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        try {
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(I18n.text("Az átalakítás túllépte az időkorlátot"));
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(I18n.text("Megszakítva"));
        }
    }
}
