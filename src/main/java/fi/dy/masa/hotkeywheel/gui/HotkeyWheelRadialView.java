package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelCustomIconUtil;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelItemIconUtil;
import fi.dy.masa.hotkeywheel.util.LabelShortener;
import fi.dy.masa.hotkeywheel.util.WheelActionLabels;

/**
 * Precomputed per-slice data for {@link HotkeyWheelRadialRenderer}.
 */
public final class HotkeyWheelRadialView
{
    public final int width;
    public final int height;
    public final int n;
    public final int selectedIndex;
    public final int mouseX;
    public final int mouseY;
    /** Elapsed ms on the current {@link #selectedIndex} without changing (for preview), or 0 if none. */
    public final long stableHoverOnSelectionMs;
    public final List<String> mainLines;
    public final List<String> modTagLines;
    public final List<ItemStack> iconStacks;
    /** Optional custom textures (null if slice uses an {@link ItemStack} icon). */
    public final List<Identifier> iconTextures;
    public final List<String> fullLabelLines;
    public final List<String> previewLines;
    /** True when the pointer is inside the inner dead zone (no slice selected; cancel). */
    public final boolean mouseInDeadZone;
    /** True when entries are split into two rings for rendering/selection. */
    public final boolean dualRing;
    public final int innerRingCount;
    public final int outerRingCount;
    /** -1 none, 0 inner, 1 outer. */
    public final int selectedRing;

    public HotkeyWheelRadialView(
            int width,
            int height,
            int n,
            int selectedIndex,
            int mouseX,
            int mouseY,
            long stableHoverOnSelectionMs,
            List<String> mainLines,
            List<String> modTagLines,
            List<ItemStack> iconStacks,
            List<Identifier> iconTextures,
            List<String> fullLabelLines,
            List<String> previewLines,
            boolean mouseInDeadZone,
            boolean dualRing,
            int innerRingCount,
            int outerRingCount,
            int selectedRing)
    {
        this.width = width;
        this.height = height;
        this.n = n;
        this.selectedIndex = selectedIndex;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.stableHoverOnSelectionMs = stableHoverOnSelectionMs;
        this.mainLines = mainLines;
        this.modTagLines = modTagLines;
        this.iconStacks = iconStacks;
        this.iconTextures = iconTextures;
        this.fullLabelLines = fullLabelLines;
        this.previewLines = previewLines;
        this.mouseInDeadZone = mouseInDeadZone;
        this.dualRing = dualRing;
        this.innerRingCount = innerRingCount;
        this.outerRingCount = outerRingCount;
        this.selectedRing = selectedRing;
    }

    public static HotkeyWheelRadialView build(
            int width,
            int height,
            int selectedIndex,
            int mouseX,
            int mouseY,
            long stableHoverOnSelectionMs,
            List<WheelAction> entries,
            HotkeyWheelConfigStore cfg,
            boolean closingAfterFeedback)
    {
        int n = entries == null ? 0 : entries.size();
        float cancelR = n > 0 ? HotkeyWheelRadialLayout.cancelR(width, height) : 0f;
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        boolean inDead = n > 0 && (dx * dx + dy * dy) < (double) cancelR * (double) cancelR;
        boolean dual = n > 8;
        int inCount = dual ? (int) Math.floor(n * 0.40) : n;
        if (dual)
        {
            if (inCount < 1) inCount = 1;
            if (inCount > n - 1) inCount = n - 1;
        }
        int outCount = dual ? (n - inCount) : 0;
        int selRing = selectedIndex < 0 ? -1 : (dual && selectedIndex >= inCount ? 1 : 0);
        List<String> mains = new ArrayList<>(n);
        List<String> mods = new ArrayList<>(n);
        List<ItemStack> icons = new ArrayList<>(n);
        List<Identifier> textures = new ArrayList<>(n);
        List<String> fulls = new ArrayList<>(n);
        List<String> previews = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
        {
            WheelAction a = entries.get(i);
            mains.add(a == null ? "" : WheelActionLabels.mainLineShort(a, cfg));
            if (a != null)
            {
                mods.add(LabelShortener.modTagForDisplay(a.getSourceModName()));
            }
            else
            {
                mods.add("");
            }
            if (a != null)
            {
                String itemId = cfg.getIconItemIdForAction(a.getActionId());
                Identifier tex = HotkeyWheelCustomIconUtil.textureIdForIconId(itemId);
                if (tex != null)
                {
                    textures.add(tex);
                    icons.add(ItemStack.EMPTY);
                }
                else
                {
                    textures.add(null);
                    icons.add(HotkeyWheelItemIconUtil.stackForItemId(itemId));
                }
            }
            else
            {
                icons.add(ItemStack.EMPTY);
                textures.add(null);
            }
            fulls.add(a == null ? "" : a.getFullLabel());
            String p = null;
            if (a != null
                    && closingAfterFeedback == false
                    && stableHoverOnSelectionMs >= 500L
                    && i == selectedIndex)
            {
                p = a.getPreviewText();
            }
            previews.add(p != null && p.isEmpty() == false ? p : "");
        }
        return new HotkeyWheelRadialView(
                width, height, n, selectedIndex, mouseX, mouseY, stableHoverOnSelectionMs,
                mains, mods, icons, textures, fulls, previews, inDead, dual, inCount, outCount, selRing);
    }
}
