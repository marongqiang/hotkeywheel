package fi.dy.masa.hotkeywheel.hotkeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.gui.HotkeyWheelGameScreen;
import fi.dy.masa.hotkeywheel.gui.HotkeyWheelRadialLayout;
import fi.dy.masa.hotkeywheel.gui.HotkeyWheelRadialRenderer;
import fi.dy.masa.hotkeywheel.gui.HotkeyWheelRadialView;
import fi.dy.masa.hotkeywheel.gui.RadialWheelMath;
import fi.dy.masa.hotkeywheel.util.HotkeyKeyCodes;

/**
 * Global radial key wheel: whitelisted key combo; 2+ actions (vanilla and/or Masa) share the same bind.
 */
public final class HotkeyWheelManager
{
    public static final HotkeyWheelManager INSTANCE = new HotkeyWheelManager();
    private static final int FEEDBACK_MS = 130;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean open;
    private int selectedIndex = -1;
    private int activeKeyCode = -1;
    private final List<WheelAction> activeEntries = new ArrayList<>();
    private HotkeyWheelGameScreen screen;
    private net.minecraft.client.gui.screen.Screen parentScreen;
    /**
     * When true, wheel is draw-only (no {@link net.minecraft.client.gui.screen.Screen}): avoids Fabric
     * setScreen(null) + ScreenEvents NPE.
     */
    private boolean wheelOverlayOnly;
    private int lastHoverForStable = Integer.MIN_VALUE;
    private long hoverSinceMs = 0L;
    private int feedbackSlice = -1;
    private long feedbackEndMs = 0L;
    private boolean closingAfterFeedback;

    private HotkeyWheelManager() { }

    public int getFeedbackSliceIndex()
    {
        return this.feedbackSlice;
    }

    public long getFeedbackEndMs()
    {
        return this.feedbackEndMs;
    }

    public void tick()
    {
        if (this.closingAfterFeedback)
        {
            if (this.feedbackEndMs > 0L && System.currentTimeMillis() >= this.feedbackEndMs)
            {
                this.closingAfterFeedback = false;
                this.feedbackSlice = -1;
                this.feedbackEndMs = 0L;
                this.close();
            }
            return;
        }
        if (this.mc.player == null || this.mc.world == null)
        {
            if (this.wheelOverlayOnly && this.open)
            {
                this.mc.mouse.lockCursor();
                this.wheelOverlayOnly = false;
            }
            this.open = false;
            return;
        }
        if (this.open && this.screen != null)
        {
            this.selectedIndex = this.screen.getSelectedIndex();
        }
        else if (this.open && this.wheelOverlayOnly)
        {
            this.selectedIndex = this.computeIndexFromMouseForOverlay();
        }
        this.updateHoverStability();
    }

    private void updateHoverStability()
    {
        if (this.open == false) return;
        if (this.selectedIndex != this.lastHoverForStable)
        {
            this.lastHoverForStable = this.selectedIndex;
            this.hoverSinceMs = System.currentTimeMillis();
        }
    }

    public boolean onKeyboardKey(int key, int scancode, int action, int modifiers)
    {
        if (key == HotkeyKeyCodes.KEY_NONE) return false;
        if (this.wheelOverlayOnly)
        {
            if (this.mc.currentScreen != null) return false;
        }
        else if (this.mc.currentScreen != null && this.mc.currentScreen != this.screen) return false;
        boolean isPress = (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT);
        boolean isRelease = (action == GLFW.GLFW_RELEASE);
        if (this.closingAfterFeedback) return this.open;
        if (this.open)
        {
            if (isRelease && key == this.activeKeyCode)
            {
                this.triggerSelected();
                if (this.selectedIndex < 0) this.close();
                else
                {
                    this.feedbackSlice = this.selectedIndex;
                    this.feedbackEndMs = System.currentTimeMillis() + (long) FEEDBACK_MS;
                    this.closingAfterFeedback = true;
                }
                return true;
            }
            return true;
        }
        if (isPress)
        {
            if (this.isWheelBlockedByWhitelistForKeyPress(key, modifiers)) return false;
            List<WheelAction> entries = this.collectWheelActionsForEvent(key, modifiers);
            if (entries.size() >= 2)
            {
                this.openWith(key, entries);
                return true;
            }
        }
        return false;
    }

    private void triggerSelected()
    {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.activeEntries.size()) return;
        WheelAction a = this.activeEntries.get(this.selectedIndex);
        if (a == null) return;
        try
        {
            a.activate();
        }
        catch (Throwable t)
        {
            HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: action failed; ignoring.", t);
        }
    }

    public void renderOverlay(DrawContext drawContext)
    {
        if (!this.open || this.activeEntries.isEmpty()) return;
        if (this.wheelOverlayOnly == false) return;
        if (this.mc.currentScreen != null) return;
        if (this.mc.getWindow() == null) return;
        int w = this.mc.getWindow().getScaledWidth();
        int h = this.mc.getWindow().getScaledHeight();
        int mx = (int) (this.mc.mouse.getX() * (double) w / (double) this.mc.getWindow().getWidth());
        int my = (int) (this.mc.mouse.getY() * (double) h / (double) this.mc.getWindow().getHeight());
        long stable = 0L;
        if (this.selectedIndex >= 0) stable = System.currentTimeMillis() - this.hoverSinceMs;
        HotkeyWheelRadialView view = HotkeyWheelRadialView.build(
                w, h, this.selectedIndex, mx, my, stable, this.activeEntries, HotkeyWheelConfigStore.INSTANCE);
        HotkeyWheelRadialRenderer.render(
                drawContext, view, this.mc.textRenderer, this.getFeedbackSliceIndex(), this.getFeedbackEndMs());
    }

    public static String labelForKeyBinding(KeyBinding k)
    {
        String t = I18n.translate(k.getTranslationKey());
        var lt = k.getBoundKeyLocalizedText();
        String a = lt != null ? lt.getString() : "";
        return t + (a.isEmpty() ? "" : " (" + a + ")");
    }

    private void openWith(int keyCode, List<WheelAction> entries)
    {
        this.activeKeyCode = keyCode;
        this.activeEntries.clear();
        this.activeEntries.addAll(entries);
        this.open = true;
        this.selectedIndex = -1;
        this.closingAfterFeedback = false;
        this.feedbackSlice = -1;
        this.feedbackEndMs = 0L;
        this.lastHoverForStable = Integer.MIN_VALUE;
        this.hoverSinceMs = System.currentTimeMillis();
        this.parentScreen = this.mc.currentScreen;
        if (this.parentScreen == null)
        {
            this.wheelOverlayOnly = true;
            this.screen = null;
            this.mc.mouse.unlockCursor();
        }
        else
        {
            this.wheelOverlayOnly = false;
            this.screen = new HotkeyWheelGameScreen(this.parentScreen, this.activeEntries);
            this.mc.setScreen(this.screen);
        }
    }

    private void close()
    {
        if (this.wheelOverlayOnly)
        {
            this.mc.mouse.lockCursor();
        }
        else if (this.screen != null && this.mc.currentScreen == this.screen)
        {
            this.mc.setScreen(this.parentScreen);
        }
        this.open = false;
        this.wheelOverlayOnly = false;
        this.activeKeyCode = -1;
        this.activeEntries.clear();
        this.selectedIndex = -1;
        this.screen = null;
        this.parentScreen = null;
        this.closingAfterFeedback = false;
        this.feedbackSlice = -1;
        this.feedbackEndMs = 0L;
    }

    /** Aligned with {@link HotkeyWheelRadialLayout} inner dead zone. */
    private int computeIndexFromMouseForOverlay()
    {
        if (this.activeEntries == null || this.activeEntries.isEmpty() || this.mc.getWindow() == null) return -1;
        int n = this.activeEntries.size();
        if (n == 1) return 0;
        int w = this.mc.getWindow().getScaledWidth();
        int h = this.mc.getWindow().getScaledHeight();
        if (w < 1 || h < 1) return -1;
        int mx = (int) (this.mc.mouse.getX() * (double) w / (double) this.mc.getWindow().getWidth());
        int my = (int) (this.mc.mouse.getY() * (double) h / (double) this.mc.getWindow().getHeight());
        int cx = w / 2;
        int cy = h / 2;
        double dx = mx - cx;
        double dy = my - cy;
        float innerR = HotkeyWheelRadialLayout.innerR(w, h, n);
        return RadialWheelMath.selectedSegmentIndex(dx, dy, innerR, n);
    }

    private boolean isWheelBlockedByWhitelistForKeyPress(int keyCode, int modifiers)
    {
        var enabled = HotkeyWheelConfigStore.INSTANCE.getEnabledCombos();
        if (enabled.isEmpty()) return true;
        String comboId = HotkeyWheelKeyComboUtil.buildComboIdFromEvent(keyCode, modifiers);
        if (comboId.isEmpty()) return true;
        return enabled.contains(comboId) == false;
    }

    private static void filterAndSortForWheel(List<WheelAction> out)
    {
        if (out == null || out.isEmpty()) return;
        out.removeIf(a -> a == null || HotkeyWheelConfigStore.INSTANCE.isHiddenFromWheel(a.getActionId()));
        HotkeyWheelConfigStore.INSTANCE.sortActions(out);
    }

    private List<WheelAction> collectWheelActionsForEvent(int keyCode, int modifiers)
    {
        if (HotkeyWheelConfigStore.INSTANCE.getEnabledCombos().isEmpty()) return List.of();
        String comboId = HotkeyWheelKeyComboUtil.buildComboIdFromEvent(keyCode, modifiers);
        if (comboId.isEmpty()) return List.of();
        if (HotkeyWheelConfigStore.INSTANCE.isComboWheelEnabled(comboId) == false) return List.of();
        String comboU = comboId.toUpperCase(Locale.ROOT);
        Set<String> disabled = new LinkedHashSet<>();
        for (String s : HotkeyWheelConfigStore.INSTANCE.getDisabledOnCombo())
        {
            if (s != null && s.trim().isEmpty() == false) disabled.add(s.trim());
        }
        KeyBinding ourOpen = HotkeyWheelClient.OPEN_CONFIG_KEY;
        Set<KeyBinding> kseen = new LinkedHashSet<>();
        List<WheelAction> out = new ArrayList<>();
        GameOptions o = this.mc.options;
        if (o == null) return List.of();
        for (KeyBinding kb : o.allKeys)
        {
            if (kb == null) continue;
            if (ourOpen != null && kb == ourOpen) continue;
            if (kb.isUnbound()) continue;
            for (String cid : HotkeyWheelKeyComboUtil.getComboIdsForKeyBinding(kb))
            {
                if (cid == null) continue;
                if (cid.equalsIgnoreCase(comboId) == false) continue;
                String disabledId = comboU + "|" + kb.getTranslationKey();
                if (disabled.contains(disabledId)) continue;
                if (kseen.add(kb)) out.add(new VanillaWheelAction(kb));
                break;
            }
        }
        if (FabricLoader.getInstance().isModLoaded("malilib"))
        {
            try
            {
                fi.dy.masa.hotkeywheel.compat.malilib.MalilibWheelCollection.appendForCombo(
                        comboId, comboU, disabled, out);
            }
            catch (Throwable t)
            {
                HotkeyWheelClient.LOGGER.warn("Hotkey Wheel: optional MaLiLib collection failed; vanilla or partial list only for this key.", t);
            }
        }
        filterAndSortForWheel(out);
        return out;
    }
}
