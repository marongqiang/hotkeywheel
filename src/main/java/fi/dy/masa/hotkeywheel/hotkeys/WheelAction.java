package fi.dy.masa.hotkeywheel.hotkeys;

import javax.annotation.Nullable;

/**
 * One slice on the in-game hotkey wheel (vanilla keybind or optional MaLiLib hotkey).
 */
public interface WheelAction
{
    /** Stable id: vanilla translation key, or {@code mli:Mod:HotkeyName}. */
    String getActionId();

    /** Long description for tooltips; usually same as {@link #getLabel()}. */
    String getFullLabel();

    /** Shown in wheel (may be i18n long); still used to derive short name when not overridden. */
    String getLabel();

    void activate();

    /**
     * Optional mini-label like {@code Litematica} (no brackets) for a faint sub-line. Empty if none.
     */
    default String getSourceModName()
    {
        return "";
    }

    /**
     * Optional state line for hover &gt; 0.5s (future / MaLiLib integration); default no preview.
     */
    @Nullable
    default String getPreviewText()
    {
        return null;
    }
}
