package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;
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
    public final List<String> fullLabelLines;
    public final List<String> previewLines;

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
            List<String> fullLabelLines,
            List<String> previewLines)
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
        this.fullLabelLines = fullLabelLines;
        this.previewLines = previewLines;
    }

    public static HotkeyWheelRadialView build(
            int width,
            int height,
            int selectedIndex,
            int mouseX,
            int mouseY,
            long stableHoverOnSelectionMs,
            List<WheelAction> entries,
            HotkeyWheelConfigStore cfg)
    {
        int n = entries == null ? 0 : entries.size();
        List<String> mains = new ArrayList<>(n);
        List<String> mods = new ArrayList<>(n);
        List<ItemStack> icons = new ArrayList<>(n);
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
                icons.add(HotkeyWheelItemIconUtil.stackForItemId(itemId));
            }
            else
            {
                icons.add(ItemStack.EMPTY);
            }
            fulls.add(a == null ? "" : a.getFullLabel());
            String p = null;
            if (a != null && stableHoverOnSelectionMs >= 500L && i == selectedIndex)
            {
                p = a.getPreviewText();
            }
            previews.add(p != null && p.isEmpty() == false ? p : "");
        }
        return new HotkeyWheelRadialView(
                width, height, n, selectedIndex, mouseX, mouseY, stableHoverOnSelectionMs,
                mains, mods, icons, fulls, previews);
    }
}
