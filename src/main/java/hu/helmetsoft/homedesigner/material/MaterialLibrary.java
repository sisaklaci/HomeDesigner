package hu.helmetsoft.homedesigner.material;

import hu.helmetsoft.homedesigner.util.I18n;

import hu.helmetsoft.homedesigner.io.ImageLoader;
import hu.helmetsoft.homedesigner.model.Finish;
import hu.helmetsoft.homedesigner.util.Noise;
import hu.helmetsoft.homedesigner.util.Texture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Catalogue of material presets (burkolatok) and resolver for {@link Finish} objects. */
public final class MaterialLibrary {
    public enum Category {
        FLOOR("Padló"), WALL("Fal"), ROOF("Tető"), OTHER("Egyéb");
        public final String label;

        Category(String l) {
            label = l;
        }
    }

    public static final class Preset {
        public final String id, name;
        public final Category category;
        final Supplier<Texture> gen;
        public final double tile, rough, spec;
        public final boolean tintable, metal, glass, bump;
        public final String defColor;

        Preset(String id, String name, Category c, Supplier<Texture> gen, double tile, double rough, double spec,
               boolean tintable, String defColor, boolean metal, boolean glass, boolean bump) {
            this.id = id;
            this.name = name;
            this.category = c;
            this.gen = gen;
            this.tile = tile;
            this.rough = rough;
            this.spec = spec;
            this.tintable = tintable;
            this.defColor = defColor;
            this.metal = metal;
            this.glass = glass;
            this.bump = bump;
        }

        @Override
        public String toString() {
            return I18n.display(name);
        }
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    private static final Map<String, Texture> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, SurfaceMaterial> MATERIALS = new ConcurrentHashMap<>();

    private static void add(String id, String name, Category c, Supplier<Texture> gen, double tile, double rough, double spec,
                            boolean tint, String col, boolean bump) {
        PRESETS.put(id, new Preset(id, name, c, gen, tile, rough, spec, tint, col, false, false, bump));
    }

    static {
        Category F = Category.FLOOR, W = Category.WALL, R = Category.ROOF, O = Category.OTHER;
        add("oak", "Tölgy parketta", F, ProceduralTextures::oak, 2.4, 0.35, 0.45, false, null, true);
        add("walnut", "Dió parketta", F, ProceduralTextures::walnut, 2.4, 0.3, 0.5, false, null, true);
        add("laminate", "Világos laminált", F, ProceduralTextures::lightLaminate, 2.5, 0.4, 0.4, false, null, true);
        add("greyoak", "Szürke tölgy", F, ProceduralTextures::greyOak, 2.4, 0.45, 0.35, false, null, true);
        add("basket", "Mozaik parketta", F, ProceduralTextures::basket, 1.6, 0.35, 0.45, false, null, true);
        add("tile_light", "Kerámia lap 60×60 – világos", F, () -> ProceduralTextures.ceramic(new double[]{0.92, 0.9, 0.86}), 1.2, 0.2, 0.55, true, "#FFFFFF", true);
        add("tile_grey", "Kerámia lap 60×60 – szürke", F, () -> ProceduralTextures.ceramic(new double[]{0.55, 0.55, 0.54}), 1.2, 0.25, 0.5, true, "#FFFFFF", true);
        add("marble", "Márvány lap", F, ProceduralTextures::marbleTiles, 1.2, 0.08, 0.7, false, null, true);
        add("slate", "Pala lap – sötét", F, ProceduralTextures::slate, 1.2, 0.5, 0.35, false, null, true);
        add("checker", "Sakktábla csempe", F, ProceduralTextures::checker, 1.8, 0.12, 0.6, false, null, true);
        add("carpet", "Szőnyegpadló", F, ProceduralTextures::carpet, 0.8, 0.95, 0.02, true, "#9C8F80", false);
        add("concrete", "Csiszolt beton", F, ProceduralTextures::concrete, 3.0, 0.45, 0.35, false, null, false);
        add("plaster", "Festett fal (vakolat)", W, ProceduralTextures::plaster, 1.5, 0.85, 0.08, true, "#F3EFE7", false);
        add("stripes", "Csíkos tapéta", W, ProceduralTextures::stripes, 1.0, 0.8, 0.1, true, "#D8E3E8", false);
        add("damask", "Mintás tapéta", W, ProceduralTextures::damask, 1.0, 0.75, 0.12, true, "#E8DCC6", false);
        add("brick", "Tégla", W, ProceduralTextures::brickRed, 1.04, 0.85, 0.1, false, null, true);
        add("brick_white", "Festett tégla – fehér", W, ProceduralTextures::brickWhite, 1.04, 0.8, 0.1, false, null, true);
        add("subway", "Metró csempe", W, ProceduralTextures::subway, 0.9, 0.1, 0.7, true, "#FFFFFF", true);
        add("wood_panel", "Fa lambéria", W, ProceduralTextures::woodPanel, 1.2, 0.45, 0.35, false, null, true);
        add("stone", "Kőburkolat", W, ProceduralTextures::stone, 1.6, 0.85, 0.1, false, null, true);
        add("concrete_wall", "Látszóbeton", W, ProceduralTextures::concrete, 3.0, 0.7, 0.15, false, null, false);
        add("roof_red", "Cserép – piros", R, ProceduralTextures::roofRed, 1.4, 0.6, 0.25, false, null, true);
        add("roof_anthracite", "Cserép – antracit", R, ProceduralTextures::roofAnthracite, 1.4, 0.45, 0.35, false, null, true);
        add("shingle", "Bitumenes zsindely", R, ProceduralTextures::shingles, 1.6, 0.8, 0.1, false, null, true);
        add("metal_roof", "Állókorcos fémlemez", R, ProceduralTextures::metalRoof, 2.4, 0.35, 0.5, true, "#5A6068", true);
        add("grass", "Fű", O, ProceduralTextures::grass, 4.0, 0.9, 0.05, false, null, false);
        add("soil", "Termőföld", O, ProceduralTextures::soil, 1.5, 0.98, 0.02, false, null, true);
        add("fabric", "Szövet", O, ProceduralTextures::fabric, 0.4, 0.95, 0.03, true, "#8A9AA8", false);
        add("leather", "Bőr", O, ProceduralTextures::leather, 0.5, 0.45, 0.35, true, "#6B4430", false);
        add("oak_wood", "Fa (tölgy)", O, ProceduralTextures::woodGeneric, 1.0, 0.4, 0.4, false, null, false);
        add("white_plastic", "Fehér műanyag", O, null, 1, 0.3, 0.4, true, "#F4F4F2", false);
        add("paint_gloss", "Fényes festék", O, null, 1, 0.15, 0.6, true, "#E0E0E0", false);
        PRESETS.put("chrome", new Preset("chrome", "Króm", O, null, 1, 0.05, 1, true, "#E8E8EA", true, false, false));
        PRESETS.put("steel", new Preset("steel", "Matt acél", O, null, 1, 0.4, 1, true, "#B8BABD", true, false, false));
        PRESETS.put("glass", new Preset("glass", "Üveg", O, null, 1, 0, 1, true, "#E8F4F4", false, true, false));
        add("custom", "Saját textúra (kép)…", O, null, 1.0, 0.5, 0.3, true, "#FFFFFF", false);
    }

    private MaterialLibrary() {
    }

    public static List<Preset> presets() {
        return new ArrayList<>(PRESETS.values());
    }

    public static List<Preset> presets(Category first) {
        List<Preset> l = new ArrayList<>();
        for (Preset p : PRESETS.values()) if (p.category == first) l.add(p);
        for (Preset p : PRESETS.values()) if (p.category != first) l.add(p);
        return l;
    }

    public static Preset preset(String id) {
        Preset p = PRESETS.get(id);
        return p != null ? p : PRESETS.get("plaster");
    }

    public static Texture texture(String presetId) {
        Preset p = preset(presetId);
        if (p.gen == null) return null;
        return TEXTURES.computeIfAbsent(p.id, k -> p.gen.get());
    }

    public static SurfaceMaterial resolve(Finish f) {
        if (f == null) f = new Finish("plaster", null);
        Finish fin = f;
        return MATERIALS.computeIfAbsent(f.key(), k -> build(fin));
    }

    private static SurfaceMaterial build(Finish f) {
        Preset p = preset(f.preset);
        SurfaceMaterial m = new SurfaceMaterial(f.key());
        m.roughness = p.rough;
        m.specular = p.spec;
        m.metal = p.metal;
        m.glass = p.glass;
        m.tileSize = p.tile * (f.scale > 0 ? f.scale : 1);
        String col = f.color != null ? f.color : p.defColor;
        if (col != null) m.color = Noise.hex(col, m.color);
        if ("custom".equals(p.id) && f.texture != null) {
            Texture t = TEXTURES.computeIfAbsent("file:" + f.texture, k -> {
                try {
                    return ImageLoader.load(Path.of(f.texture)).limit(2048);
                } catch (Exception e) {
                    return ProceduralTextures.plaster();
                }
            });
            m.albedo = t;
        } else if (p.gen != null) {
            m.albedo = texture(p.id);
            if (p.bump) m.bump = TEXTURES.computeIfAbsent(p.id + "#n", k -> ProceduralTextures.normalMap(texture(p.id), 6));
        }
        if (p.glass) m.opacity = 0.25;
        return m;
    }

    /** Material for lamp bulbs. */
    public static SurfaceMaterial emissive(double[] rgb, double power) {
        String key = "emit|" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "|" + power;
        return MATERIALS.computeIfAbsent(key, k -> {
            SurfaceMaterial m = new SurfaceMaterial(k);
            m.color = rgb;
            m.emission = new double[]{rgb[0] * power, rgb[1] * power, rgb[2] * power};
            m.roughness = 1;
            m.specular = 0;
            return m;
        });
    }

    /** Material derived from imported model data. */
    public static SurfaceMaterial imported(String key, double[] color, Texture tex, double rough, double spec, double opacity) {
        return MATERIALS.computeIfAbsent(key, k -> {
            SurfaceMaterial m = new SurfaceMaterial(k);
            m.color = color;
            m.albedo = tex;
            m.roughness = rough;
            m.specular = spec;
            m.opacity = opacity;
            m.glass = opacity < 0.6;
            m.tileSize = 1;
            return m;
        });
    }
}
