package fi.dy.masa.hotkeywheel.util;

/**
 * Short labels for the radial wheel (strips long config keys, toggles, etc.).
 */
public final class LabelShortener
{
    private LabelShortener() {}

    public static String shorten(String full, int maxChars)
    {
        if (full == null) return "";
        String s = full.trim();
        if (s.isEmpty()) return "";
        int paren = s.indexOf(" (");
        if (paren > 0) s = s.substring(0, paren).trim();
        String t = lastMeaningfulSegment(s);
        t = stripPrefix(t, "toggle");
        t = stripPrefix(t, "open");
        t = stripPrefix(t, "openGui");
        t = t.replace("Schemati", "Schem").replace("schematic", "schem");
        if (t.length() <= maxChars) return t;
        return t.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private static String stripPrefix(String t, String p)
    {
        if (t.length() <= p.length() + 1) return t;
        if (t.regionMatches(true, 0, p, 0, p.length()) && (t.length() == p.length() || Character.isUpperCase(t.charAt(p.length()))))
        {
            return t.substring(p.length());
        }
        return t;
    }

    private static String lastMeaningfulSegment(String s)
    {
        int d = s.lastIndexOf('.');
        if (d >= 0 && d < s.length() - 1) return s.substring(d + 1);
        d = s.lastIndexOf(' ');
        if (d >= 0 && d < s.length() - 1) return s.substring(d + 1);
        d = s.lastIndexOf('|');
        if (d >= 0 && d < s.length() - 1) return s.substring(d + 1).trim();
        return s;
    }

    public static String modTagForDisplay(String modNameOrId)
    {
        if (modNameOrId == null || modNameOrId.isEmpty()) return "";
        return "[" + modNameOrId + "]";
    }
}
