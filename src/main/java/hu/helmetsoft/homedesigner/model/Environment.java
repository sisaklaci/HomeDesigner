package hu.helmetsoft.homedesigner.model;

/** Lighting / environment settings. */
public class Environment {
    public enum Foundation {
        NONE("Nincs alaptest"), BLOCK("Beton alaptest"), EMBEDDED("Dombba simuló / pince");
        public final String label;
        Foundation(String label) { this.label=label; }
        @Override public String toString() { return hu.helmetsoft.homedesigner.util.I18n.text(label); }
    }
    public double terrainSlope, terrainAzimuth, terrainHeight, terrainOriginX, terrainOriginY;
    public Foundation foundation=Foundation.NONE;
    public double foundationDepth=1;
    public Finish foundationFinish=new Finish("concrete",null);
    public java.util.List<SiteFill> siteFills=new java.util.ArrayList<>();
    public boolean autoEmbankment;
    public double embankmentWidth=3, embankmentTopOffset=-0.15;
    public Finish embankmentFinish=new Finish("grass",null);
    /** Sun direction: azimuth in degrees (0 = north/up on plan, clockwise), elevation above horizon. */
    public double sunAzimuth = 150;
    public double sunElevation = 42;
    public double sunIntensity = 1.0;
    public double skyIntensity = 1.0;
    public double exposure = 1.0;
    public String hdri;
    public boolean showGround = true;
    public Finish ground = new Finish("grass", null);
}
