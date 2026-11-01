package hu.helmetsoft.homedesigner.io;

import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkVideoTest {
    @TempDir Path temporary;
    private static BufferedImage image(Color color) {
        BufferedImage image=new BufferedImage(80,60,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try { g.setColor(color); g.fillRect(0,0,80,60); } finally { g.dispose(); }
        return image;
    }
    @Test void mp4DecodesAtExactSizeFrameRateAndDurationEvenWithSkippedCaptures() throws Exception {
        Path path=temporary.resolve("walk.mp4");
        WalkVideo video=new WalkVideo(path,160,90);
        assertTrue(video.offer(image(Color.RED),0));
        assertTrue(video.offer(image(Color.BLUE),500_000_000));
        assertFalse(video.offer(image(Color.GREEN),500_000_000));
        assertEquals(path,video.stop(1_000_000_000).get(30,TimeUnit.SECONDS));
        try(var channel=NIOUtils.readableChannel(path.toFile())) {
            var grab=AWTFrameGrab.createAWTFrameGrab(channel);
            var meta=grab.getVideoTrack().getMeta();
            assertEquals(15,meta.getTotalFrames());
            assertEquals(1,meta.getTotalDuration(),1e-6);
            assertEquals(160,meta.getVideoCodecMeta().getSize().getWidth());
            assertEquals(90,meta.getVideoCodecMeta().getSize().getHeight());
            for(int i=0;i<15;i++) {
                BufferedImage frame=grab.getFrame(); assertNotNull(frame);
                Color center=new Color(frame.getRGB(80,45));
                if(i<7) assertTrue(center.getRed()>center.getBlue()+100);
                else assertTrue(center.getBlue()>center.getRed()+100);
                Color border=new Color(frame.getRGB(0,45));
                assertTrue(border.getRed()<20 && border.getBlue()<20,"Aspect ratio must be preserved with black borders");
            }
            assertNull(grab.getFrame());
        }
    }
    @Test void emptyAbortedAndFailedRecordingsDoNotReplaceTheDestinationOrLeaveTemporaryFiles() throws Exception {
        Path path=temporary.resolve("keep.mp4"); Files.writeString(path,"original");
        WalkVideo empty=new WalkVideo(path,80,60);
        assertThrows(ExecutionException.class,() -> empty.stop(0).get(30,TimeUnit.SECONDS));
        assertEquals("original",Files.readString(path));
        WalkVideo aborted=new WalkVideo(path,80,60);
        aborted.offer(image(Color.RED),0);
        aborted.abort(new java.io.IOException("capture failure"));
        assertThrows(ExecutionException.class,() -> aborted.completion().get(30,TimeUnit.SECONDS));
        assertEquals("original",Files.readString(path));
        Path directory=temporary.resolve("directory.mp4");
        WalkVideo failed=new WalkVideo(directory,80,60);
        Files.createDirectory(directory);
        failed.offer(image(Color.BLUE),0);
        assertThrows(ExecutionException.class,() -> failed.stop(1_000_000_000).get(30,TimeUnit.SECONDS));
        assertTrue(Files.isDirectory(directory));
        try(var files=Files.list(temporary)) { assertEquals(2,files.count()); }
    }
    @Test void rejectsInvalidDimensionsAndTimes() throws Exception {
        assertThrows(IllegalArgumentException.class,() -> new WalkVideo(temporary.resolve("bad.mp4"),81,60));
        WalkVideo video=new WalkVideo(temporary.resolve("valid.mp4"),80,60);
        assertThrows(IllegalArgumentException.class,() -> video.offer(image(Color.RED),-1));
        assertThrows(IllegalArgumentException.class,() -> video.stop(-1));
        video.offer(image(Color.RED),0);
        video.stop(1).get(30,TimeUnit.SECONDS);
    }
}
