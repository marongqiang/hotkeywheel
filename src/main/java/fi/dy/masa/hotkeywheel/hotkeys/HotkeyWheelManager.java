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
import fi.dy.masa.hotkeywheel.compat.MalilibAccessReflective;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
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
    /** Locked at key release while feedback plays; used for rendering only. */
    private int feedbackLockedIndex = -1;
    private int activeKeyCode = -1;
    private final List<WheelAction> activeEntries = new ArrayList<>();
    private int lastHoverForStable = Integer.MIN_VALUE;
    private long hoverSinceMs = 0L;
    private int feedbackSlice = -1;
    private long feedbackEndMs = 0L;
    private boolean closingAfterFeedback;
    /** Vanilla key: release on the tick after PRESS. */
    private net.minecraft.client.util.InputUtil.Key vanillaKeyToRelease;
    /** Vanilla key binding: release (pressed=false) on next tick after a wheel-triggered press. */
    private KeyBinding vanillaBindingToRelease;
    private long nextDebugLogMs = 0L;

    private HotkeyWheelManager() { }

    public boolean isOpen()
    {
        return this.open;
    }

    public int getFeedbackSliceIndex()
    {
        return this.feedbackSlice;
    }

    public long getFeedbackEndMs()
    {
        return this.feedbackEndMs;
    }

    public void scheduleVanillaKeyReleaseForNextTick(net.minecraft.client.util.InputUtil.Key k)
    {
        this.vanillaKeyToRelease = k;
    }

    public void scheduleVanillaBindingReleaseForNextTick(KeyBinding kb)
    {
        this.vanillaBindingToRelease = kb;
    }

    public void tick()
    {
        this.applyPendingVanillaKeyRelease();
        this.applyPendingVanillaBindingRelease();
        if (this.closingAfterFeedback)
        {
            if (this.feedbackEndMs > 0L && System.currentTimeMillis() >= this.feedbackEndMs)
            {
                this.closingAfterFeedback = false;
                this.feedbackSlice = -1;
                this.feedbackEndMs = 0L;
                this.feedbackLockedIndex = -1;
                this.close();
            }
            return;
        }
        if (this.mc.player == null || this.mc.world == null)
        {
            if (this.open) this.close();
            return;
        }
        if (this.open) this.selectedIndex = this.computeIndexFromMouseForOverlay();
        this.updateHoverStability();
    }

    private void applyPendingVanillaKeyRelease()
    {
        if (this.vanillaKeyToRelease == null) return;
        KeyBinding.setKeyPressed(this.vanillaKeyToRelease, false);
        this.vanillaKeyToRelease = null;
    }

    private void applyPendingVanillaBindingRelease()
    {
        if (this.vanillaBindingToRelease == null) return;
        try
        {
            // Try method first (when available in mappings), then fall back to field.
            var m = KeyBinding.class.getDeclaredMethod("setPressed", boolean.class);
            m.setAccessible(true);
            m.invoke(this.vanillaBindingToRelease, false);
        }
        catch (Throwable t)
        {
            try
            {
                var f = KeyBinding.class.getDeclaredField("pressed");
                f.setAccessible(true);
                f.setBoolean(this.vanillaBindingToRelease, false);
            }
            catch (Throwable ignored) { }
        }
        this.vanillaBindingToRelease = null;
    }

    private void runOnMainThreadClient(Runnable r)
    {
        if (this.mc == null) return;
        this.mc.execute(r);
    }

    private void updateHoverStability()
    {
        if (this.open == false) return;
        if (this.closingAfterFeedback) return;
        if (this.selectedIndex != this.lastHoverForStable)
        {
            this.lastHoverForStable = this.selectedIndex;
            this.hoverSinceMs = System.currentTimeMillis();
        }
    }

    public boolean onKeyboardKey(int key, int scancode, int action, int modifiers)
    {
        if (key == HotkeyKeyCodes.KEY_NONE) return false;
        if (this.mc.currentScreen != null) return false;
        boolean isPress = (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT);
        boolean isRelease = (action == GLFW.GLFW_RELEASE);
        if (this.closingAfterFeedback) return this.open;
        if (this.open)
        {
            if (isRelease && key == this.activeKeyCode)
            {
                this.refreshSelectionAtRelease();
                this.triggerSelected();
                if (this.selectedIndex < 0) this.close();
                else
                {
                    this.feedbackLockedIndex = this.selectedIndex;
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
            if (entries.size() < 2) return false;
            this.openWith(key, entries);
            return true;
        }
        return false;
    }

    /** Sync slice index to pointer position at the moment the bind is released. */
    private void refreshSelectionAtRelease()
    {
        if (this.open == false || this.activeEntries.isEmpty()) return;
        this.selectedIndex = this.computeIndexFromMouseForOverlay();
    }

    private int getDisplayIndexForView()
    {
        if (this.closingAfterFeedback) return this.feedbackLockedIndex;
        return this.selectedIndex;
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
        if (this.open == false || this.activeEntries.isEmpty()) return;
        if (this.mc.currentScreen != null) return;
        if (this.mc.getWindow() == null) return;
        int w = this.mc.getWindow().getScaledWidth();
        int h = this.mc.getWindow().getScaledHeight();
        int mx = (int) (this.mc.mouse.getX() * (double) w / (double) this.mc.getWindow().getWidth());
        int my = (int) (this.mc.mouse.getY() * (double) h / (double) this.mc.getWindow().getHeight());
        int disp = this.getDisplayIndexForView();
        long stable = 0L;
        if (disp >= 0) stable = System.currentTimeMillis() - this.hoverSinceMs;
        HotkeyWheelRadialView view = HotkeyWheelRadialView.build(
                w, h, disp, mx, my, stable, this.activeEntries, HotkeyWheelConfigStore.INSTANCE, this.closingAfterFeedback);
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
        this.feedbackLockedIndex = -1;
        this.lastHoverForStable = Integer.MIN_VALUE;
        this.hoverSinceMs = System.currentTimeMillis();
        this.runOnMainThreadClient(() -> this.mc.mouse.unlockCursor());
    }

    private void close()
    {
        this.runOnMainThreadClient(() -> {
            // If an action opened a GUI (eg. chat), don't forcibly lock the cursor here,
            // otherwise the screen can get interrupted/closed immediately.
            if (this.mc.mouse != null && this.mc.currentScreen == null) this.mc.mouse.lockCursor();
        });
        this.open = false;
        this.activeKeyCode = -1;
        this.activeEntries.clear();
        this.selectedIndex = -1;
        this.closingAfterFeedback = false;
        this.feedbackSlice = -1;
        this.feedbackEndMs = 0L;
        this.feedbackLockedIndex = -1;
    }

    private int computeIndexFromMouseForOverlay()
    {
        if (this.closingAfterFeedback) return this.feedbackLockedIndex;
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
        float cancelR = HotkeyWheelRadialLayout.cancelR(w, h);
        double d2 = dx * dx + dy * dy;
        if (d2 < (double) cancelR * (double) cancelR) return -1;

        if (n > 8)
        {
            float outerR = HotkeyWheelRadialLayout.outerR(w, h);
            float innerRingOuter = HotkeyWheelRadialLayout.innerRingOuterR(w, h);
            float outerRingInner = HotkeyWheelRadialLayout.outerRingInnerR(w, h);
            if (d2 > (double) outerR * (double) outerR) return -1;

            // strict gap between rings: no selection
            if (d2 > (double) innerRingOuter * (double) innerRingOuter && d2 < (double) outerRingInner * (double) outerRingInner) return -1;

            int nIn = (int) Math.floor(n * 0.40);
            if (nIn < 1) nIn = 1;
            if (nIn > n - 1) nIn = n - 1;
            int nOut = n - nIn;

            if (d2 < (double) innerRingOuter * (double) innerRingOuter)
            {
                this.debugWheelPick("inner", n, nIn, nOut, dx, dy, d2, cancelR, innerRingOuter, outerRingInner, outerR, 0.0);
                return RadialWheelMath.selectedSegmentIndex(dx, dy, cancelR, nIn);
            }
            else
            {
                if (nOut <= 0) return -1;
                // Outer ring is staggered by half a sector.
                double off = Math.PI / (double) nOut;
                double rdx = dx * Math.cos(off) - dy * Math.sin(off);
                double rdy = dx * Math.sin(off) + dy * Math.cos(off);
                this.debugWheelPick("outer", n, nIn, nOut, dx, dy, d2, cancelR, innerRingOuter, outerRingInner, outerR, off);
                int seg = RadialWheelMath.selectedSegmentIndex(rdx, rdy, cancelR, nOut);
                return seg < 0 ? -1 : (nIn + seg);
            }
        }

        this.debugWheelPick("single", n, n, 0, dx, dy, d2, cancelR, 0f, 0f, HotkeyWheelRadialLayout.outerR(w, h), 0.0);
        return RadialWheelMath.selectedSegmentIndex(dx, dy, cancelR, n);
    }

    private void debugWheelPick(
            String ring,
            int nTotal,
            int nIn,
            int nOut,
            double dx,
            double dy,
            double d2,
            float cancelR,
            float innerRingOuter,
            float outerRingInner,
            float outerR,
            double outerOffsetRad)
    {
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() == false) return;
        long now = System.currentTimeMillis();
        if (now < this.nextDebugLogMs) return;
        this.nextDebugLogMs = now + 200L;
        double deg = (Math.toDegrees(Math.atan2(dy, dx)) + 450.0) % 360.0;
        HotkeyWheelClient.LOGGER.info(String.format(
                Locale.ROOT,
                "WheelPick ring=%s n=%d in/out=%d/%d dx=%.1f dy=%.1f d2=%.1f deg=%.1f cancelR=%.1f innerOut=%.1f outerIn=%.1f outerR=%.1f offRad=%.4f",
                ring, nTotal, nIn, nOut,
                dx, dy, d2, deg,
                cancelR, innerRingOuter, outerRingInner, outerR, outerOffsetRad));
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
                MalilibAccessReflective.INSTANCE.appendForCombo(comboId, comboU, disabled, out);
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
