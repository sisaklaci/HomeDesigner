package hu.helmetsoft.homedesigner.io;

import hu.helmetsoft.homedesigner.geom.FurnitureTransform;
import hu.helmetsoft.homedesigner.geom.StairGeometry;
import hu.helmetsoft.homedesigner.geom.CabinetGeometry;
import hu.helmetsoft.homedesigner.model.Furniture;
import hu.helmetsoft.homedesigner.util.Texture;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Asynchronous, shared cache of imported models. */
public final class ModelLibrary {
    private static final Map<String, CompletableFuture<ModelData>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Texture> TOP = new ConcurrentHashMap<>();
    private static final Map<Furniture, StairModel> STAIRS = new java.util.WeakHashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final class StairModel {
        final String key;
        final ModelData model;
        Texture top;
        StairModel(String key, ModelData model) { this.key = key; this.model = model; }
    }
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(null, r, "model-loader", 256L << 20);
        t.setDaemon(true);
        return t;
    });

    private static final java.util.List<java.lang.ref.WeakReference<Runnable>> LOADED = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Called on a loader thread; the subscriber must retain its callback while it is needed. */
    public static void onLoaded(Runnable r) {
        LOADED.removeIf(ref -> ref.get()==null);
        LOADED.add(new java.lang.ref.WeakReference<>(r));
    }

    private ModelLibrary() {
    }

    public static String key(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString().toLowerCase();
    }

    public static CompletableFuture<ModelData> get(String path, Consumer<String> progress) {
        return CACHE.computeIfAbsent(key(path), k -> {
            CompletableFuture<ModelData> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return ModelLoader.load(Path.of(path), progress);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, POOL);
            f.thenRun(() -> {
                LOADED.removeIf(ref -> ref.get()==null);
                for(var ref:LOADED) {
                    Runnable listener=ref.get();
                    if(listener!=null) listener.run();
                }
            });
            return f;
        });
    }

    /** Model if already loaded, otherwise null (does not start loading). */
    public static ModelData now(String path) {
        if (path == null) return null;
        CompletableFuture<ModelData> f = CACHE.get(key(path));
        return f != null && f.isDone() && !f.isCompletedExceptionally() ? f.join() : null;
    }

    public static ModelData placed(Furniture f) {
        return f.stair != null || f.cabinet != null || f.solar != null ? stairModel(f).model : f.modelPath == null ? null : now(f.modelPath);
    }

    private static synchronized StairModel stairModel(Furniture f) {
        String key = f.width+"|"+f.depth+"|"+f.height+"|"+GSON.toJson(f.stair)+"|"+GSON.toJson(f.cabinet)+"|"+GSON.toJson(f.solar);
        StairModel cached = STAIRS.get(f);
        if (cached == null || !cached.key.equals(key)) {
            cached = new StairModel(key,f.solar != null ? hu.helmetsoft.homedesigner.geom.SolarGeometry.build(f)
                    : f.cabinet != null ? CabinetGeometry.build(f) : StairGeometry.build(f));
            STAIRS.put(f,cached);
        }
        return cached;
    }

    public static Texture placedTop(Furniture f) {
        if (f.stair == null && f.cabinet == null && f.solar == null) return f.modelPath == null ? null : topView(f.modelPath,f.upFix);
        StairModel model = stairModel(f);
        synchronized (model) {
            if (model.top == null) model.top = FurnitureTransform.topView(model.model,0,200);
            return model.top;
        }
    }

    public static String error(String path) {
        CompletableFuture<ModelData> f = CACHE.get(key(path));
        if (f == null || !f.isCompletedExceptionally()) return null;
        try {
            f.join();
        } catch (CompletionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return c.getMessage();
        }
        return null;
    }

    public static Texture topView(String path, int upFix) {
        ModelData md = now(path);
        if (md == null) return null;
        return TOP.computeIfAbsent(key(path) + "|" + upFix, k -> FurnitureTransform.topView(md, upFix, 160));
    }

    public static void forget(String path) {
        CACHE.remove(key(path));
    }
}
