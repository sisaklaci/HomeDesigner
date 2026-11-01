package hu.helmetsoft.homedesigner.io;

import java.nio.file.Files;
import java.nio.file.Path;

/** Well-known application folders. */
public final class AppPaths {
    private AppPaths() {
    }

    /** Folder of the running jar / exe installation. */
    public static Path appDir() {
        try {
            Path p = Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path d = Files.isDirectory(p) ? p : p.getParent();
            // jpackage layout: <app>\app\homedesigner.jar
            if (d.getFileName() != null && d.getFileName().toString().equalsIgnoreCase("app") && d.getParent() != null) return d.getParent();
            return d;
        } catch (Exception e) {
            return Path.of("").toAbsolutePath();
        }
    }

    /** Bundled sample model folder ("objects"), searched near the app and the working directory. */
    public static Path objectsDir() {
        Path[] cands = {appDir().resolve("objects"), appDir().resolve("..").resolve("objects").normalize(),
                appDir().resolve("../..").resolve("objects").normalize(), Path.of("objects").toAbsolutePath()};
        for (Path c : cands) if (Files.isDirectory(c)) return c;
        return null;
    }
}
