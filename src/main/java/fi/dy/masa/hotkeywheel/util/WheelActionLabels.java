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
}
