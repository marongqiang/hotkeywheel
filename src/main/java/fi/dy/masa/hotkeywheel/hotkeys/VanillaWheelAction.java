package fi.dy.masa.hotkeywheel.hotkeys;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.MinecraftClient;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
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
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
        {
            HotkeyWheelClient.LOGGER.info(
                    "HotkeyWheel vanillaActivate: translationKey={}",
                    this.keyBinding.getTranslationKey());
        }
        // Special-case hotbar selection: when multiple actions share the same physical key,
        // KeyBinding.onKeyPressed(InputUtil.Key) cannot target a single binding reliably.
        String tk = this.keyBinding.getTranslationKey();
        if (tk != null && tk.startsWith("key.hotbar."))
        {
            try
            {
                int n = Integer.parseInt(tk.substring("key.hotbar.".length()));
                int slot = n - 1;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null && slot >= 0 && slot < 9)
                {
                    mc.player.getInventory().selectedSlot = slot;
                    return;
                }
            }
            catch (Exception ignored) { }
        }
        // For general KeyBindings, don't broadcast by physical key (it can trigger multiple bindings).
        // Instead, trigger only this KeyBinding instance via reflection.
        pressKeyBindingOnce(this.keyBinding);
    }

    private static void pressKeyBindingOnce(KeyBinding kb)
    {
        if (kb == null) return;
        boolean pressedSet = false;
        try
        {
            var m = KeyBinding.class.getDeclaredMethod("setPressed", boolean.class);
            m.setAccessible(true);
            m.invoke(kb, true);
            pressedSet = true;
        }
        catch (Throwable ignored) { }
        if (pressedSet == false)
        {
            try
            {
                var f = KeyBinding.class.getDeclaredField("pressed");
                f.setAccessible(true);
                f.setBoolean(kb, true);
                pressedSet = true;
            }
            catch (Throwable ignored) { }
        }
        try
        {
            var onPressed = KeyBinding.class.getDeclaredMethod("onPressed");
            onPressed.setAccessible(true);
            onPressed.invoke(kb);
        }
        catch (Throwable t)
        {
            try
            {
                var f = KeyBinding.class.getDeclaredField("timesPressed");
                f.setAccessible(true);
                int v = f.getInt(kb);
                f.setInt(kb, v + 1);
            }
            catch (Throwable ignored) { }
        }
        if (pressedSet) HotkeyWheelManager.INSTANCE.scheduleVanillaBindingReleaseForNextTick(kb);
    }
}
