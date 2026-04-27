package fi.dy.masa.hotkeywheel.gui;

/**
 * Radial index & positions (inspired by common radial-menu math, e.g. segment sweep from top).
 */
public final class RadialWheelMath
{
    private RadialWheelMath() {}

    public static int selectedSegmentIndex(double dx, double dy, float innerR, int n)
    {
        if (n < 1) return -1;
        if (n == 1) return 0;
        double d2 = dx * dx + dy * dy;
        if (d2 < (double) innerR * (double) innerR) return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx));
        a = (a + 450.0) % 360.0;
        int idx = (int) (a / (360.0 / n));
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return idx;
    }

    public static double midAngleRad(int seg, int n)
    {
        if (n <= 0) return 0.0;
        double segAngle = 2.0 * Math.PI / n;
        return seg * segAngle - Math.PI / 2.0 + segAngle / 2.0;
    }

    public static double startAngleRad(int seg, int n)
    {
        if (n <= 0) return 0.0;
        return seg * (2.0 * Math.PI / n) - Math.PI / 2.0;
    }

    public static double endAngleRad(int seg, int n)
    {
        if (n <= 0) return 0.0;
        return (seg + 1) * (2.0 * Math.PI / n) - Math.PI / 2.0;
    }
}
