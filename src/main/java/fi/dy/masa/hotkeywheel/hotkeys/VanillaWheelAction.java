package fi.dy.masa.hotkeywheel.hotkeys;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import fi.dy.masa.hotkeywheel.util.KeyBindingModNames;

/**
 * KeyBinding-based wheel entry.
 */
public final class VanillaWheelAction implements WheelAction
{
    private final KeyBinding keyBinding;

    public VanillaWheelAction(KeyBinding keyBinding)
    {
        this.keyBinding = keyBinding;
    }

    public KeyBinding getKeyBinding()
    {
        return this.keyBinding;
    }

    @Override
    public String getActionId()
    {
        return this.keyBinding != null ? this.keyBinding.getTranslationKey() : "";
    }

    @Override
    public String getFullLabel()
    {
        return this.getLabel();
    }

    @Override
    public String getLabel()
    {
        return HotkeyWheelManager.labelForKeyBinding(this.keyBinding);
    }

    @Override
    public String getSourceModName()
    {
        if (this.keyBinding == null) return "";
        return KeyBindingModNames.getModDisplayName(KeyBindingModNames.getModId(this.keyBinding.getTranslationKey()));
    }


    @Override
    public void activate()
    {
        if (this.keyBinding == null) return;
        InputUtil.Key in = InputUtil.fromTranslationKey(this.keyBinding.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(in, true);
        KeyBinding.onKeyPressed(in);
        KeyBinding.setKeyPressed(in, false);
    }
}
