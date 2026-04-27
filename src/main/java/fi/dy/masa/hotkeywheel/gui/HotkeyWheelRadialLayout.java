package fi.dy.masa.hotkeywheel.gui;

/**
 * Wheel layout radii.
 *
 * Target proportions (by diameter, i.e. radius fractions are half):
 * - Outer ring outer diameter: 100%  -> outer radius = 0.50 * wheelDiameter
 * - Inner ring outer diameter: ~62%  -> inner ring outer radius = 0.31 * wheelDiameter
 * - Center cancel diameter: 20%      -> cancel radius = 0.10 * wheelDiameter
 * - Gap between inner/outer rings: thin visual separation (a few % of diameter)
 *
 * We treat wheelDiameter as a fraction of the available min(screenWidth, screenHeight).
 */
public final class HotkeyWheelRadialLayout
{
    private HotkeyWheelRadialLayout() { }

    // Keep these in-sync with selection + rendering expectations.
    // Goal: clear separation without a large "dead" black ring.
    private static final float CANCEL_R_F = 0.20f;
    // Keep the gap as a thin separator line (not a wide dead ring).
    private static final float INNER_OUT_F = 0.648f;
    private static final float OUTER_IN_F = 0.652f;

    /** Legacy: cancel radius; prefer {@link #cancelR(int, int)}. */
    public static float innerR(int w, int h)
    {
        return cancelR(w, h);
    }

    /** Legacy: outer ring outer radius; prefer {@link #outerR(int, int)}. */
    public static float outerR(int w, int h)
    {
        return wheelOuterR(w, h);
    }

    /** Cancel zone radius (center circle). */
    public static float cancelR(int w, int h)
    {
        return wheelOuterR(w, h) * CANCEL_R_F;
    }

    /** Inner ring outer radius (only used in dual-ring mode). */
    public static float innerRingOuterR(int w, int h)
    {
        return wheelOuterR(w, h) * INNER_OUT_F;
    }

    /** Outer ring inner radius (only used in dual-ring mode). */
    public static float outerRingInnerR(int w, int h)
    {
        return wheelOuterR(w, h) * OUTER_IN_F;
    }

    /** Outer ring outer radius. */
    // (kept as the legacy method above)

    /** Single-ring inner radius (between cancel zone and outer ring). */
    public static float singleRingInnerR(int w, int h)
    {
        return cancelR(w, h);
    }

    /** Single-ring outer radius. */
    public static float singleRingOuterR(int w, int h)
    {
        return wheelOuterR(w, h);
    }

    /** Alias for older callsites: cancel radius. */
    public static float innerR(int w, int h, int n)
    {
        return cancelR(w, h);
    }

    /** Alias for older callsites: outer radius. */
    public static float outerR(int w, int h, int n)
    {
        return wheelOuterR(w, h);
    }

    private static float wheelOuterR(int w, int h)
    {
        float m = Math.min(w, h);
        // Keep margins so the wheel doesn't touch screen edges.
        return Math.max(60f, m * 0.42f);
    }
}
