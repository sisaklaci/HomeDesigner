package hu.helmetsoft.homedesigner.io;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Runs a trial Assimp import in a separate process. Assimp's (unofficial) .blend reader can crash natively on
 * newer Blender files, which would otherwise take down the whole application.
 */
public final class AssimpProbe {
    public static final String ARG = "--assimp-probe";
    private static final Map<String, Boolean> RESULTS = new ConcurrentHashMap<>();

    private AssimpProbe() {
    }

    /** True if the file can be imported by Assimp without crashing. */
    public static boolean isSafe(Path file) {
        String key;
        try {
            key = file.toAbsolutePath() + "|" + Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return false;
        }
        return RESULTS.computeIfAbsent(key, k -> run(file));
    }

    private static boolean run(Path file) {
        try {
            List<String> cmd = new ArrayList<>();
            Path java = Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java");
            if (Files.isRegularFile(java)) {
                cmd.add(java.toString());
                cmd.add("-Xss64m");
                cmd.add("-XX:ErrorFile=" + Path.of(System.getProperty("java.io.tmpdir"), "hd_probe_crash_%p.log"));
                cmd.add("-cp");
                cmd.add(System.getProperty("java.class.path"));
                cmd.add("hu.helmetsoft.homedesigner.Launcher");
            } else {
                String self = ProcessHandle.current().info().command().orElse(null);
                if (self == null) return true;
                cmd.add(self);
            }
            cmd.add(ARG);
            cmd.add(file.toAbsolutePath().toString());
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return true; // could not test – fall back to trying in-process
        }
    }

    /** Child-process entry: exit code 0 = import succeeded. */
    public static void main(String file) {
        int code = 3;
        try {
            var scene = aiImportFile(file, aiProcess_Triangulate | aiProcess_PreTransformVertices | aiProcess_ValidateDataStructure);
            if (scene != null) {
                code = scene.mNumMeshes() > 0 ? 0 : 4;
                aiReleaseImport(scene);
            }
        } catch (Throwable t) {
            code = 5;
        }
        System.exit(code);
    }
}
