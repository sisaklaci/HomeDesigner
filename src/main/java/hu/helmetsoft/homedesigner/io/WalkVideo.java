package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Rational;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

/** Bounded-memory, constant-rate silent MP4 recording; encoding never runs on the UI thread. */
public final class WalkVideo {
    public static final int FPS=15;
    private record Frame(BufferedImage image,long index) {}
    private final ArrayBlockingQueue<Frame> frames=new ArrayBlockingQueue<>(2);
    private final CompletableFuture<Path> result=new CompletableFuture<>();
    private final Path destination,temporary;
    private final int width,height;
    private volatile boolean stopping;
    private volatile long endIndex;
    private volatile IOException failure;
    private long lastIndex=-1;

    public WalkVideo(Path destination,int width,int height) throws IOException {
        if(width<2 || height<2 || width%2!=0 || height%2!=0)
            throw new IllegalArgumentException(I18n.text("A videóméret pozitív, páros pixelszám legyen."));
        this.destination=destination.toAbsolutePath();
        if(Files.isDirectory(this.destination)) throw new IOException(I18n.text("A videó célja nem lehet mappa: ")+destination);
        this.width=width; this.height=height;
        temporary=Files.createTempFile(this.destination.getParent(),".homedesigner-video-",".mp4");
        Thread worker=new Thread(this::encode,"walk-video");
        worker.setDaemon(true); worker.start();
    }

    public boolean offer(BufferedImage image,long elapsedNanos) {
        if(image==null || elapsedNanos<0) throw new IllegalArgumentException(I18n.text("Érvénytelen videóképkocka."));
        long index=elapsedNanos*FPS/1_000_000_000L;
        if(stopping || result.isDone() || index<=lastIndex) return false;
        if(!frames.offer(new Frame(image,index))) return false;
        lastIndex=index; return true;
    }

    public CompletableFuture<Path> stop(long elapsedNanos) {
        if(elapsedNanos<0) throw new IllegalArgumentException(I18n.text("Negatív felvételi idő."));
        if(!stopping) {
            endIndex=Math.max(lastIndex+1,Math.max(1,(long)Math.ceil(elapsedNanos/1e9*FPS)));
            stopping=true;
        }
        return result;
    }

    public CompletableFuture<Path> completion() { return result; }
    public boolean canAccept() { return !stopping && !result.isDone() && frames.remainingCapacity()>0; }
    public void abort(IOException cause) { failure=cause; stopping=true; }

    private void encode() {
        try {
            try(var channel=NIOUtils.writableChannel(temporary.toFile())) {
                AWTSequenceEncoder encoder=new AWTSequenceEncoder(channel,Rational.R(FPS,1));
                BufferedImage last=null; long written=0;
                while(!stopping || !frames.isEmpty()) {
                    if(failure!=null) throw failure;
                    Frame frame=frames.poll(100,TimeUnit.MILLISECONDS);
                    if(frame==null) continue;
                    BufferedImage image=resize(frame.image);
                    if(last==null) last=image;
                    while(written<frame.index) { encoder.encodeImage(last); written++; }
                    encoder.encodeImage(image); written++; last=image;
                }
                if(failure!=null) throw failure;
                if(last==null) throw new IOException(I18n.text("A felvétel nem tartalmaz képkockát."));
                while(written<endIndex) { encoder.encodeImage(last); written++; }
                encoder.finish();
            }
            if(Files.isDirectory(destination)) throw new IOException(I18n.text("A videó célja időközben mappa lett: ")+destination);
            Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
            result.complete(destination);
        } catch(Exception ex) {
            try { Files.deleteIfExists(temporary); } catch(IOException cleanup) { ex.addSuppressed(cleanup); }
            result.completeExceptionally(ex);
        } finally { frames.clear(); }
    }

    private BufferedImage resize(BufferedImage source) {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g=image.createGraphics();
        try {
            g.setColor(Color.BLACK); g.fillRect(0,0,width,height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale=Math.min((double)width/source.getWidth(),(double)height/source.getHeight());
            int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));
            g.drawImage(source,(width-w)/2,(height-h)/2,w,h,null);
        } finally { g.dispose(); }
        return image;
    }
}
