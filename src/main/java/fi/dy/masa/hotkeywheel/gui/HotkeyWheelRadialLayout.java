package fi.dy.masa.hotkeywheel.gui;

/**
 * Inner/outer radii scale with slice count: fewer items → roomier wedges, many items →
 * a more compact ring (see {@code innerR(…, n)} / {@code outerR(…, n)}).
 */
public final class HotkeyWheelRadialLayout
{
    private HotkeyWheelRadialLayout() { }

    /** Legacy: default segment count; prefer {@link #innerR(int, int, int)}. */
    public static float innerR(int w, int h)
    {
        return innerR(w, h, 8);
    }

    /** Legacy. */
    public static float outerR(int w, int h)
    {
        return outerR(w, h, 8);
    }

    public static float innerR(int w, int h, int n)
    {
        if (n < 1) n = 1;
        float m = Math.min(w, h);
        if (n <= 6) return Math.max(22f, m * 0.09f);
        if (n <= 12) return Math.max(28f, m * 0.12f);
        return Math.max(34f, m * 0.15f);
    }

    public static float outerR(int w, int h, int n)
    {
        if (n < 1) n = 1;
        float m = Math.min(w, h);
        float in = innerR(w, h, n);
        if (n <= 6) return Math.max(in + 62f, m * 0.38f);
        if (n <= 12) return Math.max(in + 52f, m * 0.33f);
        return Math.max(in + 42f, m * 0.28f);
    }
}
