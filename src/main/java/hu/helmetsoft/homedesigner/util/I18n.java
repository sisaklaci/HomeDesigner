package hu.helmetsoft.homedesigner.util;

import java.text.Collator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/** UI language and resource lookup. Model identifiers, user names and file paths are never translated. */
public final class I18n {
    private static final String BUNDLE = "hu.helmetsoft.homedesigner.i18n.Messages";
    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);
    private static volatile Locale currentLocale = Locale.forLanguageTag(initialLanguage());
    private static volatile ResourceBundle messages = bundle(currentLocale);

    private I18n() {}

    private static String initialLanguage() {
        String override = System.getProperty("hd.language");
        if ("en".equals(override) || "hu".equals(override)) return override;
        try { return "en".equals(PREFS.get("language", "hu")) ? "en" : "hu"; }
        catch (SecurityException ex) { return "hu"; }
    }

    public static Locale locale() { return currentLocale; }

    public static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    public static String text(String key, Object... arguments) {
        String value = messages.getString(key);
        return arguments.length == 0 ? value : String.format(currentLocale, value, arguments);
    }

    /** For explicitly known application labels only; never pass project/custom names here. */
    public static String display(Object label) {
        if (label == null) return "";
        String value = label.toString();
        return messages.containsKey(value) ? messages.getString(value) : value;
    }

    public static String assetName(String storedName) {
        String key = "asset." + storedName;
        return messages.containsKey(key) ? messages.getString(key) : storedName.replace('_', ' ');
    }

    public static Collator collator() { return Collator.getInstance(currentLocale); }

    /** Persists the choice without touching the open project. */
    public static void persistLanguage(String language) throws java.util.prefs.BackingStoreException {
        if (!"hu".equals(language) && !"en".equals(language)) throw new IllegalArgumentException(language);
        PREFS.put("language", language);
        PREFS.flush();
    }

    /** Changes presentation resources only. Call on the FX thread before rebuilding localized controls. */
    public static void setLanguage(String language) {
        if (!"hu".equals(language) && !"en".equals(language)) throw new IllegalArgumentException(language);
        Locale locale = Locale.forLanguageTag(language);
        ResourceBundle next = bundle(locale);
        messages = next;
        currentLocale = locale;
    }
}
