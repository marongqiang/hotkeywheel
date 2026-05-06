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
 * Tap the opener key (press then release) to open; left-click a slice to confirm; Esc / opener press again / center click to dismiss.
 */
public final class HotkeyWheelManager
{
    public static final HotkeyWheelManager INSTANCE = new HotkeyWheelManager();
    private static final int FEEDBACK_MS = 130;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean open;
    private int selectedIndex = -1;
    /** Locked after mouse confirms a slice while feedback plays; used for rendering only. */
    private int feedbackLockedIndex = -1;
    private int activeKeyCode = -1;
    private final List<WheelAction> activeEntries = new ArrayList<>();
    /** Track currently held keys so "M+G" can be distinguished from "M". */
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();
    private int lastHoverForStable = Integer.MIN_VALUE;
    private long hoverSinceMs = 0L;
    private int feedbackSlice = -1;
    private long feedbackEndMs = 0L;
    private boolean closingAfterFeedback;
    /** Vanilla key: release on the tick after PRESS. */
    private net.minecraft.client.util.InputUtil.Key vanillaKeyToRelease;
    /** Vanilla key binding: release (pressed=false) on next tick after a wheel-triggered press. */
    private KeyBinding vanillaBindingToRelease;
    /** Last hover index we logged for {@link #debugWheelPick}; reset in {@link #openWith} so first sample logs. */
    private int lastWheelPickDebugIndex = Integer.MIN_VALUE;
    /** Previous GLFW left-button state while wheel is open; used to detect click edges when vanilla Mouse is bypassed. */
    private int wheelGlfwLeftButtonPrev = GLFW.GLFW_RELEASE;
    /** After a qualifying PRESS, wait for this keysym's GLFW RELEASE to open the wheel ({@code -1} = none). */
    private int pendingWheelTapReleaseKey = -1;
    /**
     * After a same-keysym {@link VanillaWheelAction} defers {@code pressKeyBindingOnce}, stray GLFW {@code PRESS} /
     * {@code REPEAT} on the opener would otherwise start tap-arm. For {@link #blockOpenerTapArmForMillis} we swallow
     * those events; activation is always the deferred reflection path (other mods may eat the real key).
     */
    private int blockOpenerTapArmKey = -1;
    private long blockOpenerTapArmUntilMs;

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

    /**
     * GLFW keysym of the key that opened the wheel (same key used to dismiss with a second press while open).
     * Meaningful while the wheel is open or finishing feedback; otherwise {@code -1}.
     */
    public int getWheelOpenerKeyCode()
    {
        return this.activeKeyCode;
    }

    public void scheduleVanillaKeyReleaseForNextTick(net.minecraft.client.util.InputUtil.Key k)
    {
        this.vanillaKeyToRelease = k;
    }

    public void scheduleVanillaBindingReleaseForNextTick(KeyBinding kb)
    {
        this.vanillaBindingToRelease = kb;
    }

    /**
     * Call when the wheel just triggered a vanilla binding on the same physical key as the opener (defer path).
     * Prevents the next stray GLFW PRESS/REPEAT on that keysym from starting a new tap-open cycle.
     */
    public void blockOpenerTapArmForMillis(int glfwKeysym, long millis)
    {
        if (glfwKeysym < 0 || millis <= 0L) return;
        this.blockOpenerTapArmKey = glfwKeysym;
        this.blockOpenerTapArmUntilMs = System.currentTimeMillis() + millis;
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
        if (this.open && this.closingAfterFeedback == false)
        {
            this.pollWheelLeftClickFromGlfw();
        }
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
        // Drop keys we still think are held when GLFW says they are not (missed RELEASE after alt-tab, focus loss, etc.).
        this.reconcileHeldKeysWithGlfw();
        if (this.mc.currentScreen != null)
        {
            this.pendingWheelTapReleaseKey = -1;
            this.logDigit1KeyDiag(key, action, "screen_open", "", -1);
            return false;
        }
        boolean isPress = (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT);
        boolean isRelease = (action == GLFW.GLFW_RELEASE);

        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE && this.pendingWheelTapReleaseKey != -1)
        {
            if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
            {
                HotkeyWheelClient.LOGGER.info(
                        "HotkeyWheel keyboard: Esc — abort tap-arm (pending key {})",
                        this.pendingWheelTapReleaseKey);
            }
            this.pendingWheelTapReleaseKey = -1;
            return false;
        }

        long wallMs = System.currentTimeMillis();
        if (this.blockOpenerTapArmKey >= 0 && wallMs >= this.blockOpenerTapArmUntilMs)
        {
            this.blockOpenerTapArmKey = -1;
        }
        if (this.blockOpenerTapArmKey >= 0
                && key == this.blockOpenerTapArmKey
                && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && wallMs < this.blockOpenerTapArmUntilMs)
        {
            // Always swallow: letting GLFW through and skipping the deferred reflection caused silent failures when
            // another mod cancelled or reordered keyboard input; activation stays reflection-only.
            if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() && action == GLFW.GLFW_PRESS)
            {
                HotkeyWheelClient.LOGGER.info(
                        "HotkeyWheel keyboard: post-activate block swallow opener PRESS key={} untilMs={}",
                        key,
                        this.blockOpenerTapArmUntilMs);
            }
            return true;
        }

        if (isPress) this.heldKeys.add(key);

        if (this.closingAfterFeedback)
        {
            if (isRelease && key == this.activeKeyCode) return false;
            // Same as when the wheel is fully open: opener / Esc should dismiss, not get stuck here until FEEDBACK_MS.
            if (action == GLFW.GLFW_PRESS && (key == this.activeKeyCode || key == GLFW.GLFW_KEY_ESCAPE))
            {
                if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel keyboard: dismiss during slice feedback ({})",
                            key == GLFW.GLFW_KEY_ESCAPE ? "Esc" : "opener press");
                }
                this.close();
                return true;
            }
            return this.open;
        }
        if (this.open)
        {
            if (action == GLFW.GLFW_PRESS && (key == this.activeKeyCode || key == GLFW.GLFW_KEY_ESCAPE))
            {
                if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                {
                    HotkeyWheelClient.LOGGER.info(
                            "HotkeyWheel keyboard: dismiss wheel ({})",
                            key == GLFW.GLFW_KEY_ESCAPE ? "Esc" : "opener press");
                }
                this.close();
                return true;
            }
            return true;
        }

        if (this.pendingWheelTapReleaseKey == key && action == GLFW.GLFW_REPEAT)
        {
            return true;
        }

        if (isRelease && this.pendingWheelTapReleaseKey == key)
        {
            this.pendingWheelTapReleaseKey = -1;
            if (this.isWheelBlockedByWhitelistForKeyPress(key, modifiers))
            {
                this.logDigit1KeyDiag(
                        key,
                        action,
                        "tap_release_whitelist_blocked",
                        HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys),
                        -1);
                if (isRelease) this.heldKeys.remove(key);
                return false;
            }
            List<WheelAction> entries = this.collectWheelActionsForEvent(key, modifiers);
            if (entries.size() < 2)
            {
                this.logDigit1KeyDiag(
                        key,
                        action,
                        "tap_release_too_few_actions",
                        HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys),
                        entries.size());
                if (isRelease) this.heldKeys.remove(key);
                return false;
            }
            this.logDigit1KeyDiag(
                    key,
                    action,
                    "opening_wheel_tap_release",
                    HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys),
                    entries.size());
            this.openWith(key, entries);
            if (isRelease) this.heldKeys.remove(key);
            return true;
        }

        if (isRelease) this.heldKeys.remove(key);

        if (action == GLFW.GLFW_PRESS)
        {
            if (this.isWheelBlockedByWhitelistForKeyPress(key, modifiers))
            {
                this.logDigit1KeyDiag(key, action, "whitelist_blocked", HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys), -1);
                return false;
            }
            List<WheelAction> entries = this.collectWheelActionsForEvent(key, modifiers);
            if (entries.size() < 2)
            {
                this.logDigit1KeyDiag(key, action, "too_few_actions", HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys), entries.size());
                return false;
            }
            this.pendingWheelTapReleaseKey = key;
            if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
            {
                HotkeyWheelClient.LOGGER.info(
                        "HotkeyWheel keyboard: tap-arm PRESS key={} combo={} entries={} (release to open wheel)",
                        key,
                        HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(key, modifiers, this.heldKeys),
                        entries.size());
            }
            return true;
        }
        return false;
    }

    /**
     * Detect left mouse via GLFW (rising edge). Vanilla mouse button handling is cancelled
     * while the wheel is open, so other mods cannot steal the click before we see it.
     */
    private void pollWheelLeftClickFromGlfw()
    {
        if (this.mc.currentScreen != null) return;
        if (this.mc.getWindow() == null) return;
        long handle = this.mc.getWindow().getHandle();
        if (handle == 0L) return;
        int now = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (now == GLFW.GLFW_PRESS && this.wheelGlfwLeftButtonPrev != GLFW.GLFW_PRESS)
        {
            int idx = this.computeIndexFromMouseForOverlay();
            this.selectedIndex = idx;
            if (idx < 0)
            {
                if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                {
                    HotkeyWheelClient.LOGGER.info("HotkeyWheel mouse: left click in cancel zone — close wheel (GLFW edge)");
                }
                this.close();
            }
            else
            {
                if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
                {
                    HotkeyWheelClient.LOGGER.info("HotkeyWheel mouseConfirm: slice={} (GLFW left edge)", idx);
                }
                this.triggerSelected();
                this.feedbackLockedIndex = idx;
                this.feedbackSlice = idx;
                this.feedbackEndMs = System.currentTimeMillis() + (long) FEEDBACK_MS;
                this.closingAfterFeedback = true;
            }
        }
        this.wheelGlfwLeftButtonPrev = now;
    }

    private void reconcileHeldKeysWithGlfw()
    {
        if (this.heldKeys.isEmpty()) return;
        if (this.mc.getWindow() == null) return;
        long handle = this.mc.getWindow().getHandle();
        if (handle == 0L) return;
        this.heldKeys.removeIf(k -> {
            if (k == HotkeyKeyCodes.KEY_NONE) return true;
            return org.lwjgl.glfw.GLFW.glfwGetKey(handle, k) != GLFW.GLFW_PRESS;
        });
    }

    /** When {@code Generic.ui.debugLogging} is true, log one line for digit-1 / keypad-1 presses to diagnose combo id vs whitelist. */
    private void logDigit1KeyDiag(int key, int action, String phase, String comboId, int entryCount)
    {
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() == false) return;
        if (action != GLFW.GLFW_PRESS) return;
        if (key != GLFW.GLFW_KEY_1 && key != GLFW.GLFW_KEY_KP_1) return;
        if ("screen_open".equals(phase))
        {
            HotkeyWheelClient.LOGGER.info("HotkeyWheel digit1: phase=screen_open (currentScreen blocks wheel)");
            return;
        }
        if (entryCount < 0)
        {
            HotkeyWheelClient.LOGGER.info("HotkeyWheel digit1: phase={} comboId={}", phase, comboId);
        }
        else
        {
            HotkeyWheelClient.LOGGER.info("HotkeyWheel digit1: phase={} comboId={} entries={}", phase, comboId, entryCount);
        }
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
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
        {
            HotkeyWheelClient.LOGGER.info(
                    "HotkeyWheel triggerSelected: activeKeyCode={} slice={} actionClass={} actionId={} label={}",
                    this.activeKeyCode,
                    this.selectedIndex,
                    a.getClass().getSimpleName(),
                    a.getActionId(),
                    a.getLabel());
        }
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
        this.lastWheelPickDebugIndex = Integer.MIN_VALUE;
        long wh = this.mc.getWindow() != null ? this.mc.getWindow().getHandle() : 0L;
        this.wheelGlfwLeftButtonPrev = wh != 0L ? GLFW.glfwGetMouseButton(wh, GLFW.GLFW_MOUSE_BUTTON_LEFT) : GLFW.GLFW_RELEASE;
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
        this.wheelGlfwLeftButtonPrev = GLFW.GLFW_RELEASE;
        this.pendingWheelTapReleaseKey = -1;
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
                int seg = RadialWheelMath.selectedSegmentIndex(dx, dy, cancelR, nIn);
                this.debugWheelPick("inner", n, nIn, nOut, dx, dy, d2, cancelR, innerRingOuter, outerRingInner, outerR, 0.0, seg);
                return seg;
            }
            else
            {
                if (nOut <= 0) return -1;
                // Outer ring is staggered by half a sector.
                double off = Math.PI / (double) nOut;
                double rdx = dx * Math.cos(off) - dy * Math.sin(off);
                double rdy = dx * Math.sin(off) + dy * Math.cos(off);
                int seg = RadialWheelMath.selectedSegmentIndex(rdx, rdy, cancelR, nOut);
                int idx = seg < 0 ? -1 : (nIn + seg);
                this.debugWheelPick("outer", n, nIn, nOut, dx, dy, d2, cancelR, innerRingOuter, outerRingInner, outerR, off, idx);
                return idx;
            }
        }

        int seg = RadialWheelMath.selectedSegmentIndex(dx, dy, cancelR, n);
        this.debugWheelPick("single", n, n, 0, dx, dy, d2, cancelR, 0f, 0f, HotkeyWheelRadialLayout.outerR(w, h), 0.0, seg);
        return seg;
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
            double outerOffsetRad,
            int resolvedIndex)
    {
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() == false) return;
        if (resolvedIndex == this.lastWheelPickDebugIndex) return;
        this.lastWheelPickDebugIndex = resolvedIndex;
        double deg = (Math.toDegrees(Math.atan2(dy, dx)) + 450.0) % 360.0;
        HotkeyWheelClient.LOGGER.info(String.format(
                Locale.ROOT,
                "WheelPick ring=%s idx=%d n=%d in/out=%d/%d dx=%.1f dy=%.1f d2=%.1f deg=%.1f cancelR=%.1f innerOut=%.1f outerIn=%.1f outerR=%.1f offRad=%.4f",
                ring, resolvedIndex, nTotal, nIn, nOut,
                dx, dy, d2, deg,
                cancelR, innerRingOuter, outerRingInner, outerR, outerOffsetRad));
    }

    private boolean isWheelBlockedByWhitelistForKeyPress(int keyCode, int modifiers)
    {
        var enabled = HotkeyWheelConfigStore.INSTANCE.getEnabledCombos();
        if (enabled.isEmpty()) return true;
        String comboId = HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(keyCode, modifiers, this.heldKeys);
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
        String comboId = HotkeyWheelKeyComboUtil.buildComboIdFromEventWithHeldKeys(keyCode, modifiers, this.heldKeys);
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
