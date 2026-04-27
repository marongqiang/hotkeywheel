package fi.dy.masa.hotkeywheel.compat;

import java.lang.reflect.Method;
import net.minecraft.client.resource.language.I18n;
import fi.dy.masa.hotkeywheel.compat.malilib.MalilibIds;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;

/**
 * One MaLiLib IHotkey slice; holds hotkey as {@link Object} and invokes via reflection.
 */
public final class ReflectedMalilibWheelAction implements WheelAction
{
    private static Class<?> cIHotkeyTogglable;
    private static Class<?> cKeybindMulti;
    private static Class<?> cKeyAction;
    private static Class<?> cIHotkeyCallback;
    private static Object keyActionPress;
    private static Object keyActionRelease;
    private static Method mToggle;
    private static Method mGetKeybind;
    private static Method mGetCallback;
    private static Method mOnKeyAction;

    static
    {
        try
        {
            cIHotkeyTogglable = Class.forName("fi.dy.masa.malilib.config.IHotkeyTogglable");
            cKeybindMulti = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindMulti");
            cKeyAction = Class.forName("fi.dy.masa.malilib.hotkeys.KeyAction");
            cIHotkeyCallback = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkeyCallback");
            mToggle = cIHotkeyTogglable.getMethod("toggleBooleanValue");
            Class<?> cIKeybind = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind");
            Class<?> cIHotkey = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey");
            mGetKeybind = cIHotkey.getMethod("getKeybind");
            mGetCallback = cKeybindMulti.getMethod("getCallback");
            keyActionPress = cKeyAction.getField("PRESS").get(null);
            keyActionRelease = cKeyAction.getField("RELEASE").get(null);
            mOnKeyAction = cIHotkeyCallback.getMethod("onKeyAction", cKeyAction, cIKeybind);
        }
        catch (Throwable t)
        {
            cIHotkeyTogglable = null;
        }
    }

    public static boolean reflectionReady()
    {
        return cIHotkeyTogglable != null;
    }

    private final Object hotkey;
    private final String modName;
    private final String hotkeyName;

    public ReflectedMalilibWheelAction(Object hotkey, String modName)
    {
        this.hotkey = hotkey;
        this.modName = modName != null ? modName : "";
        String n = null;
        try
        {
            n = (String) this.hotkey.getClass().getMethod("getName").invoke(this.hotkey);
        }
        catch (Exception ignored) { }
        this.hotkeyName = n != null ? n : "";
    }

    @Override
    public String getActionId()
    {
        return MalilibIds.exclusionId(this.modName, this.hotkeyName);
    }

    @Override
    public String getFullLabel()
    {
        return this.getLabel();
    }

    @Override
    public String getLabel()
    {
        if (this.hotkey == null) return "";
        String name = I18n.translate(this.hotkeyName);
        String a = "";
        try
        {
            Object kb = mGetKeybind.invoke(this.hotkey);
            if (kb != null) a = (String) kb.getClass().getMethod("getKeysDisplayString").invoke(kb);
        }
        catch (Exception ignored) { }
        return a == null || a.isEmpty() ? name : (name + " (" + a + ")");
    }

    @Override
    public String getSourceModName()
    {
        return this.modName;
    }

    @Override
    public void activate()
    {
        if (this.hotkey == null || cIHotkeyTogglable == null) return;
        try
        {
            if (cIHotkeyTogglable.isInstance(this.hotkey))
            {
                mToggle.invoke(this.hotkey);
                return;
            }
            Object kb = mGetKeybind.invoke(this.hotkey);
            if (kb != null && cKeybindMulti.isInstance(kb))
            {
                Object cb = mGetCallback.invoke(kb);
                if (cb != null)
                {
                    mOnKeyAction.invoke(cb, keyActionPress, kb);
                    mOnKeyAction.invoke(cb, keyActionRelease, kb);
                }
            }
        }
        catch (Throwable t)
        {
            // caller logs
        }
    }
}
