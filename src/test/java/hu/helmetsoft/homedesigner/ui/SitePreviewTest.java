package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.edit.ProjectOps;
import hu.helmetsoft.homedesigner.model.*;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SitePreviewTest {
    @Test void realEarthworksSceneHasAnEditableThreeDimensionalPreview() throws Exception {
        Path log=Path.of("target","site-preview-test.log");
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        String shots=System.getProperty("hd.testSnapshots","");
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Xmx2g","-Dhd.language=hu","-Dhd.testSnapshots="+shots,"-cp",cp,Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(120,TimeUnit.SECONDS)) {
            process.destroyForcibly(); fail("Landscaping preview timed out: "+Files.readString(log));
        }
        assertEquals(0,process.exitValue(),Files.readString(log));
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            CompletableFuture<Void> done=new CompletableFuture<>();
            Platform.startup(() -> {
                try { verify(); done.complete(null); }
                catch(Throwable ex) { done.completeExceptionally(ex); }
            });
            try { done.get(100,TimeUnit.SECONDS); }
            catch(Throwable ex) { ex.printStackTrace(); System.exit(1); }
            Platform.exit(); System.exit(0);
        }

        private static void verify() throws Exception {
            Project p=new Project();
            p.env.terrainSlope=10; p.env.terrainAzimuth=90;
            p.env.foundation=Environment.Foundation.BLOCK;
            p.env.autoEmbankment=true; p.env.embankmentWidth=2;
            Room room=ProjectOps.addRoomWithWalls(p,ProjectOps.rect(-3,-2.5,6,5),"Holiday house");
            room.hasCeiling=false; p.ensureLevels(); p.activeLevel().baseHeight=1.2;
            for(Wall wall:p.walls) wall.height=2.5;
            SiteFill f=new SiteFill(); f.outline.x=0; f.outline.y=4.5;
            f.outline.width=8; f.outline.depth=4; f.elevation=1.05; f.slopeWidth=2;
            p.env.siteFills.add(f);
            SiteFill bed=new SiteFill(); bed.outline.x=-5; bed.outline.y=2;
            bed.outline.shape=SlabOpening.Shape.ELLIPSE; bed.outline.width=2.5; bed.outline.depth=4;
            bed.elevation=1.4; bed.retaining=true; bed.finish=new Finish("soil",null);
            p.env.siteFills.add(bed);
            p.balconies.add(Balcony.terrace(-3,2.5,6,3,0));
            p.retainingWalls.addAll(hu.helmetsoft.homedesigner.geom.RetainingWallGeometry.aroundFill(bed,0.25));
            Stage stage=new Stage(); MainWindow window=new MainWindow(stage,p);
            Editor editor=field(window,"ed"); View3D view=field(window,"view");
            stage.show();
            try {
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                view.resetCamera(); view.rebuild(true);
                assertNotNull(view.snapshot());
                editor.select(f);
                assertSame(f,editor.selection());
                String path=System.getProperty("hd.testSnapshots","");
                if(!path.isBlank()) {
                    Path dir=Path.of(path); Files.createDirectories(dir);
                    save(stage.getScene().snapshot(null),dir.resolve("earthworks-preview.png"));
                    save(view.snapshot(),dir.resolve("earthworks-3d.png"));
                }
            } finally { stage.close(); }
        }

        private static void save(Image image,Path path) throws Exception {
            int w=(int)image.getWidth(),h=(int)image.getHeight();
            var output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var reader=image.getPixelReader();
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) output.setRGB(x,y,reader.getArgb(x,y));
            assertTrue(ImageIO.write(output,"png",path.toFile()));
        }

        @SuppressWarnings("unchecked")
        private static <T> T field(Object owner,String name) throws Exception {
            var f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return (T)f.get(owner);
        }
    }
}
