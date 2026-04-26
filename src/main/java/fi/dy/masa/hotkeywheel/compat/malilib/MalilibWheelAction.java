package fi.dy.masa.hotkeywheel.compat.malilib;

import net.minecraft.client.resource.language.I18n;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;

/**
 * Masa IHotkey wheel slice (activate via togglable or keybind callback).
 */
public final class MalilibWheelAction implements WheelAction
{
    private final IHotkey hotkey;
    private final String modName;

    public MalilibWheelAction(IHotkey hotkey, String modName)
    {
        this.hotkey = hotkey;
        this.modName = modName != null ? modName : "";
    }

    @Override
    public String getActionId()
    {
        if (this.hotkey == null) return "";
        return MalilibIds.exclusionId(this.modName, this.hotkey.getName());
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
        IKeybind kb = this.hotkey.getKeybind();
        String a = (kb != null) ? kb.getKeysDisplayString() : "";
        String name = I18n.translate(this.hotkey.getName());
        return a.isEmpty() ? name : (name + " (" + a + ")");
    }

    @Override
    public String getSourceModName()
    {
        return this.modName;
    }

    @Override
    public void activate()
    {
        if (this.hotkey == null) return;
        if (this.hotkey instanceof IHotkeyTogglable t)
        {
            t.toggleBooleanValue();
            return;
        }
        IKeybind kb = this.hotkey.getKeybind();
        if (kb instanceof KeybindMulti km)
        {
            IHotkeyCallback cb = km.getCallback();
            if (cb != null)
            {
                cb.onKeyAction(KeyAction.PRESS, kb);
                cb.onKeyAction(KeyAction.RELEASE, kb);
            }
        }
    }
}
