package hu.helmetsoft.homedesigner.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextureTest {
    @Test void sparsePreviewSamplesMatchRendererWithoutAllocatingTheLinearImage() throws Exception {
        Texture sparse=new Texture(2,2,new int[]{0x80FF0000,0x4000FF00,0xFF0000FF,0x00FFFFFF});
        Texture render=new Texture(2,2,sparse.argb.clone());
        float[] expected=new float[4],actual=new float[4];
        var linear=Texture.class.getDeclaredField("lin"); linear.setAccessible(true);
        for(double u:new double[]{-1.3,0,0.25,0.37,0.75,1,2.8})
            for(double v:new double[]{-0.5,0,0.41,0.75,1,1.8}) {
                render.sample(u,v,expected); sparse.sampleUncached(u,v,actual);
                assertArrayEquals(expected,actual);
                assertNull(linear.get(sparse));
            }
        sparse.sample(0.37,0.41,expected);
        assertNotNull(linear.get(sparse));
        sparse.sampleUncached(0.37,0.41,actual);
        assertArrayEquals(expected,actual);
    }
}
