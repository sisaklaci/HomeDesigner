package hu.helmetsoft.homedesigner;

import atlantafx.base.theme.PrimerLight;
import hu.helmetsoft.homedesigner.edit.DemoProject;
import hu.helmetsoft.homedesigner.model.Project;
import hu.helmetsoft.homedesigner.ui.MainWindow;
import hu.helmetsoft.homedesigner.ui.StartupSplash;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** JavaFX application entry. */
public class App extends Application {
    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        var icon = App.class.getResourceAsStream("/hu/helmetsoft/homedesigner/icon.png");
        if (icon != null) stage.getIcons().add(new Image(icon));
        javafx.application.Platform.setImplicitExit(false);
        StartupSplash.show(() -> openMain(stage));
        String shot = System.getProperty("hd.shot");
        if (shot != null) debugShots(shot);
    }

    private void openMain(Stage stage) {
        Project p;
        try {
            p = DemoProject.create();
        } catch (Exception e) {
            javafx.scene.control.Alert error=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            error.setTitle(hu.helmetsoft.homedesigner.util.I18n.text("Hiba"));
            error.setHeaderText(hu.helmetsoft.homedesigner.util.I18n.text("A mintaterv nem tölthető be"));
            error.setContentText(e.getMessage());
            error.show();
            p = new Project();
        }
        MainWindow mw = new MainWindow(stage, p);
        mw.show();
        javafx.application.Platform.setImplicitExit(true);
        if (Boolean.getBoolean("hd.test")) mw.debugScenario();
    }

    /** Developer aid: periodically saves snapshots of all open windows (file_seq_window.png). */
    private static void debugShots(String file) {
        int[] seq = {0};
        var tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), e -> {
            int i = 0;
            seq[0]++;
            for (var win : javafx.stage.Window.getWindows()) {
                if (win.getScene() == null) continue;
                String f = file.replace(".png", "_" + seq[0] + "_" + i + ".png");
                i++;
                try {
                    var img = win.getScene().snapshot(null);
                    int w = (int) img.getWidth(), h = (int) img.getHeight();
                    var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var pr = img.getPixelReader();
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
                    javax.imageio.ImageIO.write(bi, "png", new java.io.File(f));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }));
        tl.setCycleCount(Integer.getInteger("hd.shots", 12));
        tl.play();
    }

    @Override
    public void stop() {
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(App.class, args);
    }
}
