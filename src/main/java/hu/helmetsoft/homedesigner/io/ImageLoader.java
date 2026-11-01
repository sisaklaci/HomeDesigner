package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.util.Texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ImageLoader {
    private ImageLoader() {
    }

    public static Texture load(Path p) throws IOException {
        BufferedImage img = ImageIO.read(p.toFile());
        if (img == null) throw new IOException(I18n.text("Nem támogatott képformátum: ") + p.getFileName());
        return fromImage(img);
    }

    public static Texture load(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IOException(I18n.text("Nem támogatott beágyazott kép"));
        return fromImage(img);
    }

    public static Texture fromImage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        if (!img.getColorModel().hasAlpha()) for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        return new Texture(w, h, px);
    }

    public static BufferedImage toImage(Texture t) {
        BufferedImage img = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, t.w, t.h, t.argb, 0, t.w);
        return img;
    }

    /** Radiance .hdr (RGBE) loader. Returns {width, height} in dims and linear float RGB data. */
    public static float[] loadHdr(Path p, int[] dims) throws IOException {
        try (InputStream raw = Files.newInputStream(p); DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            String line;
            int w = 0, h = 0;
            while ((line = readLine(in)) != null) {
                if (line.startsWith("-Y") || line.startsWith("+Y")) {
                    String[] t = line.trim().split("\\s+");
                    h = Integer.parseInt(t[1]);
                    w = Integer.parseInt(t[3]);
                    break;
                }
            }
            if (w == 0) throw new IOException(I18n.text("Hibás HDR fejléc"));
            float[] out = new float[w * h * 3];
            byte[] scan = new byte[w * 4];
            for (int y = 0; y < h; y++) {
                int a = in.read(), b = in.read(), c = in.read(), d = in.read();
                if (a == 2 && b == 2 && ((c << 8) | d) == w && w >= 8 && w < 32768) {
                    for (int ch = 0; ch < 4; ch++) {
                        int x = 0;
                        while (x < w) {
                            int cnt = in.readUnsignedByte();
                            if (cnt > 128) {
                                cnt -= 128;
                                byte v = in.readByte();
                                for (int i = 0; i < cnt; i++) scan[(x++) * 4 + ch] = v;
                            } else {
                                for (int i = 0; i < cnt; i++) scan[(x++) * 4 + ch] = in.readByte();
                            }
                        }
                    }
                } else {
                    scan[0] = (byte) a; scan[1] = (byte) b; scan[2] = (byte) c; scan[3] = (byte) d;
                    in.readFully(scan, 4, w * 4 - 4);
                }
                for (int x = 0; x < w; x++) {
                    int e = scan[x * 4 + 3] & 255;
                    float f = e == 0 ? 0 : (float) Math.scalb(1.0, e - 136);
                    int o = (y * w + x) * 3;
                    out[o] = (scan[x * 4] & 255) * f;
                    out[o + 1] = (scan[x * 4 + 1] & 255) * f;
                    out[o + 2] = (scan[x * 4 + 2] & 255) * f;
                }
            }
            dims[0] = w;
            dims[1] = h;
            return out;
        }
    }

    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') sb.append((char) c);
        return c == -1 && sb.isEmpty() ? null : sb.toString();
    }
}
