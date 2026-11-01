package hu.helmetsoft.homedesigner.ui;

import hu.helmetsoft.homedesigner.io.BuiltinModels;
import hu.helmetsoft.homedesigner.io.HomeModels;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.text.Collator;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CatalogLocalizationTest {
    @Test void allModelsSortGloballyByLocalizedDisplayNameInBothLanguages() {
        List<Path> paths = new ArrayList<>();
        for (var asset : HomeModels.ASSETS)
            paths.add(Path.of("objects", HomeModels.FOLDER, asset.category().name(), asset.name()+".obj"));
        for (String name : BuiltinModels.NAMES)
            paths.add(Path.of("objects", BuiltinModels.FOLDER, name+".obj"));
        for (Locale locale : List.of(Locale.forLanguageTag("hu"), Locale.ENGLISH)) {
            var sorted = new ArrayList<>(paths);
            sorted.sort(CatalogPanel.displayOrder(locale));
            var collator = Collator.getInstance(locale);
            for (int i=1; i<sorted.size(); i++)
                assertTrue(collator.compare(CatalogPanel.displayName(sorted.get(i-1), locale),
                        CatalogPanel.displayName(sorted.get(i), locale)) <= 0);
            assertEquals(new HashSet<>(paths), new HashSet<>(sorted), "Sorting must preserve original paths");
        }
        Path solar = Path.of("objects", HomeModels.FOLDER, "SOLAR", "Napelem_tetore.obj");
        assertEquals("Roof-mounted solar panel", CatalogPanel.displayName(solar, Locale.ENGLISH));
        assertEquals("Tetőre szerelhető napelem", CatalogPanel.displayName(solar, Locale.forLanguageTag("hu")));
        assertTrue(CatalogPanel.matchesSearch(solar, "roof mounted", Locale.ENGLISH));
        assertTrue(CatalogPanel.matchesSearch(solar, "solar", Locale.ENGLISH));
        assertTrue(CatalogPanel.matchesSearch(solar, "tetore", Locale.forLanguageTag("hu")));
        assertTrue(CatalogPanel.matchesSearch(solar, "Napelem_tetore", Locale.ENGLISH));
        Path custom = Path.of("C:\\User models", "My_Custom_Kanape.obj");
        assertEquals("My Custom Kanape", CatalogPanel.displayName(custom, Locale.ENGLISH));
    }
}
