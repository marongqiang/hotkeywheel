package fi.dy.masa.hotkeywheel.util;

import javax.annotation.Nullable;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;

/**
 * Resolves the short line for the wheel: user override, then {@link LabelShortener} on
 * {@link WheelAction#getFullLabel()}.
 */
public final class WheelActionLabels
{
    private WheelActionLabels() { }

    public static String mainLineShort(WheelAction a, HotkeyWheelConfigStore cfg)
    {
        if (a == null || cfg == null) return "";
        @Nullable String o = cfg.getLabelOverride(a.getActionId());
        if (o != null && o.trim().isEmpty() == false) return o.trim();
        return LabelShortener.shorten(a.getFullLabel(), cfg.getShortLabelMaxChars());
    }

    /** Full tooltip line: remove technical prefixes like "actionId | ". */
    public static String tooltipFullLine(WheelAction a, HotkeyWheelConfigStore cfg)
    {
        if (a == null) return "";
        String s = a.getFullLabel();
        if (s == null) s = "";
        // Common format in lists: "actionId | Translated name (Key)"
        int bar = s.indexOf(" | ");
        if (bar >= 0 && bar + 3 < s.length())
        {
            s = s.substring(bar + 3);
        }
        // If user has a custom label override, prefer it (still keep any "(Key)" suffix from original).
        if (cfg != null)
        {
            @Nullable String o = cfg.getLabelOverride(a.getActionId());
            if (o != null && o.trim().isEmpty() == false)
            {
                String ov = o.trim();
                int par = s.lastIndexOf(" (");
                if (par > 0 && s.endsWith(")"))
                {
                    return ov + s.substring(par);
                }
                return ov;
            }
        }
        return s;
    }
}
