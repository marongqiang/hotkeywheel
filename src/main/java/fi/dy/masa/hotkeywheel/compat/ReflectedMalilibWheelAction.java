package fi.dy.masa.hotkeywheel.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.resource.language.I18n;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.compat.malilib.MalilibIds;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
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
    private static Method mToggle;
    private static Method mGetKeybind;
    private static Method mGetCallback;
    private static Method mOnKeyAction;
    private static Class<?> cIConfigBoolean;
    private static Method mGetBooleanValue;

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
            mOnKeyAction = cIHotkeyCallback.getMethod("onKeyAction", cKeyAction, cIKeybind);
        }
        catch (Throwable t)
        {
            cIHotkeyTogglable = null;
        }
        try
        {
            cIConfigBoolean = Class.forName("fi.dy.masa.malilib.config.IConfigBoolean");
            mGetBooleanValue = cIConfigBoolean.getMethod("getBooleanValue");
        }
        catch (Throwable t)
        {
            cIConfigBoolean = null;
            mGetBooleanValue = null;
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
        final boolean dbg = HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging();
        try
        {
            if (cIHotkeyTogglable.isInstance(this.hotkey))
            {
                Boolean before = dbg ? this.malilibBooleanSnap() : null;
                if (dbg)
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel malilib: toggle ENTER mod={} nameKey={} actionId={} bindDisplay={} booleanBefore={}",
                            this.modName,
                            this.hotkeyName,
                            this.getActionId(),
                            this.keysDisplayForLog(),
                            before);
                }
                mToggle.invoke(this.hotkey);
                if (dbg)
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel malilib: toggle EXIT  mod={} nameKey={} actionId={} booleanAfter={}",
                            this.modName,
                            this.hotkeyName,
                            this.getActionId(),
                            this.malilibBooleanSnap());
                }
                return;
            }
            // Tweakeroo (and similar): some boolean toggles are not assignable to IHotkeyTogglable on the hotkey instance
            // but still expose toggleBooleanValue(); instant PRESS+RELEASE breaks them — prefer toggle when present.
            Method reflectToggle = findDeclaredToggleBooleanMethod(this.hotkey);
            if (reflectToggle != null)
            {
                Boolean before = dbg ? this.malilibBooleanSnap() : null;
                if (dbg)
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel malilib: toggleReflect ENTER mod={} nameKey={} actionId={} bindDisplay={} booleanBefore={}",
                            this.modName,
                            this.hotkeyName,
                            this.getActionId(),
                            this.keysDisplayForLog(),
                            before);
                }
                reflectToggle.invoke(this.hotkey);
                if (dbg)
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel malilib: toggleReflect EXIT  mod={} nameKey={} actionId={} booleanAfter={}",
                            this.modName,
                            this.hotkeyName,
                            this.getActionId(),
                            this.malilibBooleanSnap());
                }
                return;
            }
            Object nestedToggle = findNestedMalilibToggleTarget(this.hotkey, this.modName, this.hotkeyName);
            if (nestedToggle != null)
            {
                if (cIHotkeyTogglable.isInstance(nestedToggle))
                {
                    Boolean before = dbg ? malilibBooleanSnapFor(nestedToggle) : null;
                    if (dbg)
                    {
                        HotkeyWheelClient.LOGGER.info(
                                "HotkeyWheel malilib: toggleNested ENTER mod={} nameKey={} actionId={} bindDisplay={} nestedClass={} booleanBefore={}",
                                this.modName,
                                this.hotkeyName,
                                this.getActionId(),
                                this.keysDisplayForLog(),
                                nestedToggle.getClass().getSimpleName(),
                                before);
                    }
                    mToggle.invoke(nestedToggle);
                    if (dbg)
                    {
                        HotkeyWheelClient.LOGGER.info(
                                "HotkeyWheel malilib: toggleNested EXIT  mod={} nameKey={} actionId={} booleanAfter={}",
                                this.modName,
                                this.hotkeyName,
                                this.getActionId(),
                                malilibBooleanSnapFor(nestedToggle));
                    }
                    return;
                }
                Method nestedReflect = findDeclaredToggleBooleanMethod(nestedToggle);
                if (nestedReflect != null)
                {
                    Boolean before = dbg ? malilibBooleanSnapFor(nestedToggle) : null;
                    if (dbg)
                    {
                        HotkeyWheelClient.LOGGER.info(
                                "HotkeyWheel malilib: toggleNestedReflect ENTER mod={} nameKey={} actionId={} nestedClass={} booleanBefore={}",
                                this.modName,
                                this.hotkeyName,
                                this.getActionId(),
                                nestedToggle.getClass().getSimpleName(),
                                before);
                    }
                    nestedReflect.invoke(nestedToggle);
                    if (dbg)
                    {
                        HotkeyWheelClient.LOGGER.info(
                                "HotkeyWheel malilib: toggleNestedReflect EXIT  mod={} nameKey={} actionId={} booleanAfter={}",
                                this.modName,
                                this.hotkeyName,
                                this.getActionId(),
                                malilibBooleanSnapFor(nestedToggle));
                    }
                    return;
                }
            }
            Object kb = mGetKeybind.invoke(this.hotkey);
            if (kb != null && cKeybindMulti.isInstance(kb))
            {
                if (dbg)
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel malilib: callback PRESS only (no synthetic RELEASE) mod={} nameKey={} actionId={} bindDisplay={}",
                            this.modName,
                            this.hotkeyName,
                            this.getActionId(),
                            this.keysDisplayForLog());
                }
                Object cb = mGetCallback.invoke(kb);
                if (cb != null)
                {
                    // Synthetic PRESS+RELEASE makes many MaLiLib callbacks open on PRESS and close on RELEASE
                    // ("flash" one tick). Wheel selection is a discrete click: PRESS only.
                    mOnKeyAction.invoke(cb, keyActionPress, kb);
                }
            }
        }
        catch (Throwable t)
        {
            if (dbg)
            {
                HotkeyWheelClient.LOGGER.warn(
                        "HotkeyWheel malilib: activate FAILED mod={} nameKey={} actionId={}",
                        this.modName,
                        this.hotkeyName,
                        this.getActionId(),
                        t);
            }
        }
    }

    private String keysDisplayForLog()
    {
        try
        {
            Object kb = mGetKeybind.invoke(this.hotkey);
            if (kb == null) return "";
            Object s = kb.getClass().getMethod("getKeysDisplayString").invoke(kb);
            return s != null ? s.toString() : "";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private Boolean malilibBooleanSnap()
    {
        return malilibBooleanSnapFor(this.hotkey);
    }

    private static Boolean malilibBooleanSnapFor(Object o)
    {
        if (o == null || cIConfigBoolean == null || mGetBooleanValue == null) return null;
        try
        {
            if (cIConfigBoolean.isInstance(o))
            {
                return (Boolean) mGetBooleanValue.invoke(o);
            }
        }
        catch (Exception ignored) { }
        return null;
    }

    /**
     * Tweakeroo free-camera related hotkeys often wrap the real {@code IHotkeyTogglable} / boolean config in a field.
     */
    private static Object findNestedMalilibToggleTarget(Object hotkey, String modName, String hotkeyName)
    {
        if (hotkey == null || hotkeyName == null) return null;
        if ("Tweakeroo".equals(modName) == false) return null;
        if (hotkeyName.contains("freeCamera") == false) return null;
        for (Class<?> c = hotkey.getClass(); c != null; c = c.getSuperclass())
        {
            for (Field f : c.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v;
                try
                {
                    v = f.get(hotkey);
                }
                catch (IllegalAccessException e)
                {
                    continue;
                }
                if (v == null || v == hotkey) continue;
                if (cIHotkeyTogglable != null && cIHotkeyTogglable.isInstance(v)) return v;
                if (findDeclaredToggleBooleanMethod(v) != null) return v;
            }
        }
        return null;
    }

    /**
     * Find a non-static no-arg {@code toggleBooleanValue()} declared on {@code hotkey}'s class or superclasses.
     * Used when the runtime type is not an {@code IHotkeyTogglable} for {@code instanceof} but still toggles a boolean.
     */
    private static Method findDeclaredToggleBooleanMethod(Object hotkey)
    {
        if (hotkey == null) return null;
        for (Class<?> c = hotkey.getClass(); c != null; c = c.getSuperclass())
        {
            try
            {
                Method m = c.getDeclaredMethod("toggleBooleanValue");
                if (m.getParameterCount() != 0) return null;
                if (Modifier.isStatic(m.getModifiers())) return null;
                m.setAccessible(true);
                return m;
            }
            catch (NoSuchMethodException ignored)
            {
                // try superclass
            }
        }
        return null;
    }
}
