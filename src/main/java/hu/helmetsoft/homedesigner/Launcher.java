package hu.helmetsoft.homedesigner;

/** Plain main class (JavaFX on the classpath requires a non-Application launcher). */
public final class Launcher {
    public static void main(String[] args) {
        if (args.length == 2 && hu.helmetsoft.homedesigner.io.AssimpProbe.ARG.equals(args[0])) {
            hu.helmetsoft.homedesigner.io.AssimpProbe.main(args[1]);
            return;
        }
        // Load the Assimp native library (and its newer MSVC runtime) before JavaFX loads its own,
        // older msvcp140.dll – otherwise native model import crashes.
        try {
            org.lwjgl.assimp.Assimp.aiGetVersionMajor();
        } catch (Throwable t) {
            System.err.println(hu.helmetsoft.homedesigner.util.I18n.text("Assimp nem tölthető be: ") + t);
        }
        App.main(args);
    }
}
