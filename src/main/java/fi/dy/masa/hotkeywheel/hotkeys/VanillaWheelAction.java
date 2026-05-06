package fi.dy.masa.hotkeywheel.hotkeys;

import java.lang.reflect.Field;
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
        int opener = HotkeyWheelManager.INSTANCE.getWheelOpenerKeyCode();
        if (opener >= 0 && physicalKeysymMatches(this.keyBinding, opener))
        {
            // Opening / confirming the wheel uses the same physical key as this binding (e.g. B opens
            // wheel and a slice is Xaero "new waypoint" also on B). Activating synchronously inside the
            // cancelled keyboard RELEASE breaks many mods — defer one client task so GLFW / KeyBinding
            // state can settle after our mixin cancels the opener key release.
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null)
            {
                KeyBinding kb = this.keyBinding;
                if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel vanillaActivate: defer same-key press (wheelKey={} translationKey={})",
                            opener,
                            kb.getTranslationKey());
                }
                // Must cover slice-feedback (~130ms) plus stray GLFW ordering; blocks accidental tap-arm re-open.
                HotkeyWheelManager.INSTANCE.blockOpenerTapArmForMillis(opener, 550L);
                // Double-defer: one frame after wheel/GLFW settle, then press (more reliable with heavy modpacks).
                mc.execute(() -> {
                    if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                    {
                        HotkeyWheelClient.LOGGER.info(
                                "HotkeyWheel vanillaActivate: same-key defer stage-1 (outer execute) tk={}",
                                kb.getTranslationKey());
                    }
                    mc.execute(() -> {
                        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                        {
                            HotkeyWheelClient.LOGGER.info(
                                    "HotkeyWheel vanillaActivate: same-key defer stage-2 (pressKeyBindingOnce) tk={}",
                                    kb.getTranslationKey());
                        }
                        pressKeyBindingOnce(kb);
                    });
                });
            }
            return;
        }
        pressKeyBindingOnce(this.keyBinding);
    }

    private static boolean physicalKeysymMatches(KeyBinding kb, int glfwKeysym)
    {
        if (kb == null || glfwKeysym < 0 || kb.isUnbound()) return false;
        try
        {
            InputUtil.Key bound = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
            if (bound == null || bound.equals(InputUtil.UNKNOWN_KEY)) return false;
            if (bound.getCategory() != InputUtil.Type.KEYSYM) return false;
            return bound.getCode() == glfwKeysym;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static void pressKeyBindingOnce(KeyBinding kb)
    {
        if (kb == null) return;
        final boolean dbg = HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging();
        boolean setPressedOk = false;
        try
        {
            kb.setPressed(true);
            setPressedOk = true;
        }
        catch (Throwable t)
        {
            if (dbg) HotkeyWheelClient.LOGGER.warn("HotkeyWheel vanillaPress: setPressed(true) threw tk={}", kb.getTranslationKey(), t);
            try
            {
                Field f = firstDeclaredField(KeyBinding.class, "pressed", "field_1653");
                if (f != null)
                {
                    f.setAccessible(true);
                    f.setBoolean(kb, true);
                    setPressedOk = true;
                }
            }
            catch (Throwable t2)
            {
                if (dbg) HotkeyWheelClient.LOGGER.warn("HotkeyWheel vanillaPress: pressed field fallback failed tk={}", kb.getTranslationKey(), t2);
            }
        }
        // 1.20.1 has no instance onPressed(); mods use wasPressed() which reads timesPressed.
        boolean timesBump = bumpTimesPressedOnBinding(kb, dbg);
        if (timesBump == false && dbg)
        {
            HotkeyWheelClient.LOGGER.warn(
                    "HotkeyWheel vanillaPress: timesPressed bump failed tk={} (binding may not fire until fixed)",
                    kb.getTranslationKey());
        }
        if (setPressedOk) HotkeyWheelManager.INSTANCE.scheduleVanillaBindingReleaseForNextTick(kb);
        if (dbg)
        {
            HotkeyWheelClient.LOGGER.info(
                    "HotkeyWheel vanillaPress: tk={} setPressedOk={} timesPressedBump={} releaseScheduled={}",
                    kb.getTranslationKey(),
                    setPressedOk,
                    timesBump,
                    setPressedOk);
        }
    }

    /**
     * Increment {@code timesPressed} on this binding only (so {@link KeyBinding#wasPressed()} sees one press).
     * Tries Yarn name then 1.20.1 intermediary ({@code field_1661}) because some runtimes expose the raw intermediary class to reflection.
     * Do not use {@link KeyBinding#onKeyPressed(InputUtil.Key)} here: it increments every binding on that physical key, so the wrong mod can win.
     */
    private static boolean bumpTimesPressedOnBinding(KeyBinding kb, boolean dbg)
    {
        Field f = firstDeclaredField(KeyBinding.class, "timesPressed", "field_1661");
        if (f == null)
        {
            if (dbg)
            {
                HotkeyWheelClient.LOGGER.warn(
                        "HotkeyWheel vanillaPress: no timesPressed field (yarn/intermediary) on KeyBinding tk={}",
                        kb.getTranslationKey());
            }
            return false;
        }
        try
        {
            f.setAccessible(true);
            f.setInt(kb, f.getInt(kb) + 1);
            return true;
        }
        catch (Throwable t)
        {
            if (dbg) HotkeyWheelClient.LOGGER.warn("HotkeyWheel vanillaPress: timesPressed setInt failed tk={}", kb.getTranslationKey(), t);
            return false;
        }
    }

    /** First existing declared field on {@code clazz} matching one of {@code names} (Yarn then intermediary). */
    private static Field firstDeclaredField(Class<?> clazz, String... names)
    {
        for (String n : names)
        {
            try
            {
                return clazz.getDeclaredField(n);
            }
            catch (NoSuchFieldException ignored) { }
        }
        return null;
    }
}
