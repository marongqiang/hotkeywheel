package fi.dy.masa.hotkeywheel.compat.malilib;

/**
 * Stable per-action id for config exclusion lines (no IHotkey on classpath outside compat).
 */
public final class MalilibIds
{
    private MalilibIds() {}

    public static String exclusionId(String modName, String hotkeyConfigName)
    {
        if (modName == null) modName = "";
        if (hotkeyConfigName == null) hotkeyConfigName = "";
        return "mli:" + modName.replace(":", "_") + ":" + hotkeyConfigName.replace(":", "_");
    }
}
