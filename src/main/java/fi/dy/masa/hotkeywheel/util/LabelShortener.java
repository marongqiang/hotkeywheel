package fi.dy.masa.hotkeywheel.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Short labels for the radial wheel (strips long config keys, toggles, etc.).
 */
public final class LabelShortener
{
    private static final int TAIL_WORDS = 3;

    private LabelShortener() {}

    public static String shorten(String full, int maxChars)
    {
        if (full == null) return "";
        String s = full.trim();
        if (s.isEmpty()) return "";
        int paren = s.indexOf(" (");
        if (paren > 0) s = s.substring(0, paren).trim();
        String t = lastMeaningfulSegment(s);
        t = wordsFromCamelTail(t, TAIL_WORDS);
        t = stripPrefix(t, "toggle");
        t = stripPrefix(t, "open");
        t = stripPrefix(t, "openGui");
        t = t.replace("Schemati", "Schem").replace("schematic", "schem");
        t = t.trim().replaceAll("\\s+", " ");
        if (t.length() <= maxChars) return t;
        return t.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    /** Insert spaces at camel boundaries, then keep the last {@code maxTailWords} words. */
    private static String wordsFromCamelTail(String t, int maxTailWords)
    {
        if (t == null || t.isEmpty()) return "";
        if (maxTailWords < 1) return t;
        String spaced = insertSpacesAtCamelCase(t);
        String[] w = spaced.trim().split("\\s+");
        if (w.length <= maxTailWords) return String.join(" ", w);
        List<String> list = new ArrayList<>(maxTailWords);
        for (int i = w.length - maxTailWords; i < w.length; i++) list.add(w[i]);
        return String.join(" ", list);
    }

    private static String insertSpacesAtCamelCase(String t)
    {
        if (t == null || t.length() < 2) return t;
        StringBuilder b = new StringBuilder(t.length() + 8);
        char p = t.charAt(0);
        b.append(p);
        for (int i = 1; i < t.length(); i++)
        {
            char c = t.charAt(i);
            if (Character.isLowerCase(p) && Character.isUpperCase(c) || (Character.isDigit(p) == false && Character.isDigit(c)))
            {
                b.append(' ');
            }
            b.append(c);
            p = c;
        }
        return b.toString();
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
