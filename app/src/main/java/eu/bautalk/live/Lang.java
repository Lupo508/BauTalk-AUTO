package eu.bautalk.live;

import java.util.Locale;

public enum Lang {
    SERBIAN("Srpski", "sr-RS", new Locale("sr", "RS")),
    POLISH("Polski", "pl-PL", new Locale("pl", "PL")),
    GERMAN("Deutsch", "de-DE", Locale.GERMANY);

    public final String label;
    public final String speechTag;
    public final Locale locale;

    Lang(String label, String speechTag, Locale locale) {
        this.label = label;
        this.speechTag = speechTag;
        this.locale = locale;
    }

    public static Lang fromLanguageTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;
        String language = Locale.forLanguageTag(tag).getLanguage();
        if ("sr".equalsIgnoreCase(language) || "bs".equalsIgnoreCase(language) || "hr".equalsIgnoreCase(language)) return SERBIAN;
        if ("pl".equalsIgnoreCase(language)) return POLISH;
        if ("de".equalsIgnoreCase(language)) return GERMAN;
        return null;
    }

    @Override public String toString() { return label; }
}
