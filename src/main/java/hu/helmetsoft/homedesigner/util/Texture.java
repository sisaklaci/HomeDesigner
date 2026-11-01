package hu.helmetsoft.homedesigner.util;

/** ARGB image usable by both the JavaFX viewer and the path tracer. Texture v=0 is the top row. */
public final class Texture {
    public final int w, h;
    public final int[] argb;
    private volatile float[] lin;
    private volatile Object fxImage;
    private static final float[] SRGB_TO_LIN = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            SRGB_TO_LIN[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
    }

    public Texture(int w, int h, int[] argb) {
        this.w = w;
        this.h = h;
        this.argb = argb;
    }

    public static float toLinear(int c8) {
        return SRGB_TO_LIN[c8 & 255];
    }

    public Object getFxImage() {
        return fxImage;
    }

    public void setFxImage(Object o) {
        fxImage = o;
    }

    public boolean hasAlpha() {
        for (int i = 0; i < argb.length; i += 7) if ((argb[i] >>> 24) < 250) return true;
        return false;
    }

    private float[] linear() {
        float[] l = lin;
        if (l == null) {
            synchronized (this) {
                if (lin == null) {
                    float[] t = new float[w * h * 4];
                    for (int i = 0; i < w * h; i++) {
                        int c = argb[i];
                        t[i * 4] = SRGB_TO_LIN[(c >> 16) & 255];
                        t[i * 4 + 1] = SRGB_TO_LIN[(c >> 8) & 255];
                        t[i * 4 + 2] = SRGB_TO_LIN[c & 255];
                        t[i * 4 + 3] = ((c >>> 24) & 255) / 255f;
                    }
                    lin = t;
                }
                l = lin;
            }
        }
        return l;
    }

    /** Bilinear repeat sampling, linear color into out[0..2], alpha in out[3]. */
    public void sample(double u, double v, float[] out) {
        sample(u,v,out,linear());
    }

    /** For sparse preview samples, avoid allocating a full linear RGBA copy of the source image. */
    public void sampleUncached(double u,double v,float[] out) {
        sample(u,v,out,lin);
    }

    private void sample(double u,double v,float[] out,float[] l) {
        double x = (u - Math.floor(u)) * w - 0.5, y = (v - Math.floor(v)) * h - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = (float) (x - x0), fy = (float) (y - y0);
        int x1 = x0 + 1, y1 = y0 + 1;
        x0 = Math.floorMod(x0, w);
        x1 = Math.floorMod(x1, w);
        y0 = Math.floorMod(y0, h);
        y1 = Math.floorMod(y1, h);
        int i00 = (y0 * w + x0) * 4, i10 = (y0 * w + x1) * 4, i01 = (y1 * w + x0) * 4, i11 = (y1 * w + x1) * 4;
        for (int k = 0; k < 4; k++) {
            float c00=channel(l,i00,k),c10=channel(l,i10,k),c01=channel(l,i01,k),c11=channel(l,i11,k);
            float a = c00 + (c10 - c00) * fx;
            float b = c01 + (c11 - c01) * fx;
            out[k] = a + (b - a) * fy;
        }
    }

    private float channel(float[] linear,int pixel,int channel) {
        if(linear!=null) return linear[pixel+channel];
        int value=argb[pixel/4];
        return channel==3 ? (value>>>24)/255f : SRGB_TO_LIN[(value>>(16-channel*8))&255];
    }

    /** Box-filter downscale so that the larger side is at most max. */
    public Texture limit(int max) {
        if (w <= max && h <= max) return this;
        int f = 1;
        while (w / f > max || h / f > max) f *= 2;
        int nw = Math.max(1, w / f), nh = Math.max(1, h / f);
        int[] o = new int[nw * nh];
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < f; j++)
                    for (int i = 0; i < f; i++) {
                        int c = argb[(y * f + j) * w + x * f + i];
                        a += c >>> 24;
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                    }
                int n = f * f;
                o[y * nw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        return new Texture(nw, nh, o);
    }
}
