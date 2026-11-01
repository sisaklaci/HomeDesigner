package hu.helmetsoft.homedesigner.util;

import com.google.gson.Gson;
import hu.helmetsoft.homedesigner.io.BuiltinModels;
import hu.helmetsoft.homedesigner.io.HomeModels;
import hu.helmetsoft.homedesigner.io.ProjectIO;
import hu.helmetsoft.homedesigner.material.MaterialLibrary;
import hu.helmetsoft.homedesigner.model.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @Test void bundlesHaveIdenticalCompleteKeysAndNonemptyValues() {
        var hu = I18n.bundle(Locale.forLanguageTag("hu"));
        var en = I18n.bundle(Locale.ENGLISH);
        assertEquals(hu.keySet(), en.keySet());
        assertTrue(en.keySet().size() > 800);
        for (String key : hu.keySet()) {
            assertFalse(hu.getString(key).isBlank(), key);
            assertFalse(en.getString(key).isBlank(), key);
            Pattern format = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]");
            assertEquals(format.matcher(hu.getString(key)).results().map(m -> m.group()).sorted().toList(),
                    format.matcher(en.getString(key)).results().map(m -> m.group()).sorted().toList(), key);
        }
        assertEquals("Oak parquet", en.getString("Tölgy parketta"));
        assertEquals("Tölgy parketta", hu.getString("Tölgy parketta"));
        assertEquals("English", Locale.ENGLISH.getDisplayLanguage(Locale.ENGLISH));
    }

    @Test void everyLiteralLookupInProductionSourcesExistsInBothBundles() throws Exception {
        Pattern lookup = Pattern.compile("I18n\\.text\\(\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
        var gson = new Gson();
        int lookups = 0;
        try (var files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                var matcher = lookup.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (matcher.group(1).equals("\"\"")) continue; // Java text blocks are checked separately below.
                    String key = gson.fromJson(matcher.group(1), String.class);
                    for (String language : List.of("hu", "en"))
                        assertTrue(I18n.bundle(Locale.forLanguageTag(language)).containsKey(key), file + ": " + key);
                    lookups++;
                }
            }
        }
        assertTrue(lookups > 500);
        String source = Files.readString(Path.of("src", "main", "java", "hu", "helmetsoft", "homedesigner", "ui", "MainWindow.java"));
        var blocks = Pattern.compile("I18n\\.text\\(\"\"\"\\R([\\s\\S]*?)\"\"\"\\)").matcher(source);
        while (blocks.find()) {
            String key = blocks.group(1).stripIndent();
            assertTrue(I18n.bundle(Locale.ENGLISH).containsKey(key), "Missing guide text block");
            assertTrue(I18n.bundle(Locale.forLanguageTag("hu")).containsKey(key));
        }
    }

    @Test void everyBundledModelCategoryAndEnumHasAnExplicitTranslation() throws Exception {
        List<String> assets = new ArrayList<>(BuiltinModels.NAMES);
        HomeModels.ASSETS.forEach(a -> assets.add(a.name()));
        assertEquals(190, assets.size());
        for (String language : List.of("hu", "en")) {
            var bundle = I18n.bundle(Locale.forLanguageTag(language));
            for (String asset : assets) assertTrue(bundle.containsKey("asset." + asset), asset);
            for (HomeModels.Category category : HomeModels.Category.values()) assertTrue(bundle.containsKey(category.label), category.name());
            for (Roof.Type type : Roof.Type.values()) assertTrue(bundle.containsKey(type.label));
            for (Opening.Type type : Opening.Type.values()) assertTrue(bundle.containsKey(type.label));
            for (hu.helmetsoft.homedesigner.model.Wall.Fence type : hu.helmetsoft.homedesigner.model.Wall.Fence.values())
                assertTrue(bundle.containsKey(type.label));
            for (Opening.Top type : Opening.Top.values()) assertTrue(bundle.containsKey(type.label));
            for (RoofElement.Type type : RoofElement.Type.values()) assertTrue(bundle.containsKey(type.label));
            for (RoofElement.Cover type : RoofElement.Cover.values()) assertTrue(bundle.containsKey(labelKey(type)));
            for (Stair.Type type : Stair.Type.values()) assertTrue(bundle.containsKey(type.label));
            for (SlabOpening.Shape type : SlabOpening.Shape.values()) assertTrue(bundle.containsKey(type.label));
            for (Balcony.Railing type : Balcony.Railing.values()) assertTrue(bundle.containsKey(labelKey(type)));
            for (Room.Vault type : Room.Vault.values()) assertTrue(bundle.containsKey(type.label));
            for (Environment.Foundation type : Environment.Foundation.values()) assertTrue(bundle.containsKey(type.label));
            for (var category : MaterialLibrary.Category.values()) {
                assertTrue(bundle.containsKey(category.label));
                for (var preset : MaterialLibrary.presets(category)) assertTrue(bundle.containsKey(preset.name), preset.id);
            }
            for (var tool : hu.helmetsoft.homedesigner.ui.Editor.Tool.values()) {
                assertTrue(bundle.containsKey(tool.label), tool.name());
                assertTrue(bundle.containsKey(tool.hint), tool.name());
            }
        }
    }

    private static String labelKey(Enum<?> value) throws Exception {
        var field = value.getDeclaringClass().getDeclaredField("label");
        field.setAccessible(true);
        return (String)field.get(value);
    }

    @Test void newlyCreatedNamesAndDemoUseTheSelectedLanguageWithoutRenamingExistingData() {
        String original=I18n.locale().getLanguage();
        try {
            I18n.setLanguage("hu");
            Room savedRoom=new Room(); savedRoom.name="Saját szoba";
            I18n.setLanguage("en");
            assertEquals("Room",new Room().name);
            assertEquals("Light",new LightSource().name);
            Project demo=hu.helmetsoft.homedesigner.edit.DemoProject.create();
            assertEquals("Living room",demo.rooms.getFirst().name);
            assertEquals("Bedroom",demo.rooms.get(1).name);
            assertEquals("Bathroom",demo.rooms.get(2).name);
            assertEquals("Living room lamp",demo.lights.getFirst().name);
            assertEquals("Saját szoba",savedRoom.name);
        } finally { I18n.setLanguage(original); }
    }

    @Test void switchingResourcesIsImmediateWithoutChangingModelValuesOrCustomNames() {
        String original = I18n.locale().getLanguage();
        try {
            Project project = new Project();
            Furniture furniture = new Furniture();
            furniture.name = "Kanapé / My custom sofa";
            furniture.modelPath = "C:\\Models\\Kanape_2_szemelyes.obj";
            project.furniture.add(furniture);
            Roof roof = new Roof();
            project.roofs.add(roof);
            String saved = ProjectIO.toJson(project);
            I18n.setLanguage("hu");
            assertEquals("Nyeregtető", roof.type.toString());
            assertEquals("Tölgy parketta", MaterialLibrary.preset("oak").toString());
            I18n.setLanguage("en");
            assertEquals("Gable roof", roof.type.toString());
            assertEquals("Oak parquet", MaterialLibrary.preset("oak").toString());
            assertEquals("Roof-mounted solar panel", I18n.assetName("Napelem_tetore"));
            assertEquals(saved, ProjectIO.toJson(project));
            assertEquals("GABLE", roof.type.name());
            assertThrows(IllegalArgumentException.class, () -> I18n.setLanguage("xx"));
        } finally { I18n.setLanguage(original); }
    }
}
