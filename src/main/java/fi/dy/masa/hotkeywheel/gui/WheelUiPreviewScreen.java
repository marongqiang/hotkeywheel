package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.HotkeyWheelClient;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelCustomIconUtil;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelItemIconUtil;

/**
 * Visual wheel preview + drag reorder for one key combo.
 * Order is persisted via {@link HotkeyWheelConfigStore#setWheelActionSortOrder(List)}.
 */
public class WheelUiPreviewScreen extends Screen
{
    private final Screen parent;
    private final String comboIdUpper;
    private final List<KeyBindingComboScanner.ScanRow> rows;
    private final List<PreviewAction> preview = new ArrayList<>();

    private int hoverIndex = -1;
    private int dragIndex = -1;
    private long hoverSinceMs = 0L;
    private int lastHover = Integer.MIN_VALUE;
    private long nextDebugLogMs = 0L;
    private long nextDebugIdxLogMs = 0L;

    public WheelUiPreviewScreen(Screen parent, String comboIdUpper, List<KeyBindingComboScanner.ScanRow> rows)
    {
        super(Text.translatable("hotkeywheel.gui.title.ui_preview"));
        this.parent = parent;
        this.comboIdUpper = comboIdUpper;
        this.rows = rows;
    }

    @Override
    protected void init()
    {
        this.rebuildPreviewFromRows();
        int cx = this.width / 2 - 100;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> {
            this.client.setScreen(this.parent);
        }).dimensions(cx, this.height - 28, 200, 20).build());
    }

    private void rebuildPreviewFromRows()
    {
        this.preview.clear();
        if (this.rows == null) return;
        List<String> order = HotkeyWheelConfigStore.INSTANCE.getWheelActionSortOrder();
        Set<String> used = new HashSet<>();
        for (String id : order)
        {
            if (id == null) continue;
            for (KeyBindingComboScanner.ScanRow r : this.rows)
            {
                if (r == null) continue;
                if (id.equals(r.exclusionKey()) && used.add(id))
                {
                    this.preview.add(PreviewAction.from(r, this.comboIdUpper));
                    break;
                }
            }
        }
        for (KeyBindingComboScanner.ScanRow r : this.rows)
        {
            if (r == null) continue;
            String id = r.exclusionKey();
            if (id == null || id.isEmpty()) continue;
            if (used.add(id)) this.preview.add(PreviewAction.from(r, this.comboIdUpper));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            int idx = this.indexFromMouseInWheel(mouseX, mouseY);
            if (idx >= 0 && idx < this.preview.size())
            {
                this.dragIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0 && this.dragIndex >= 0)
        {
            int dst = this.indexFromMouseInWheel(mouseX, mouseY);
            if (dst >= 0 && dst < this.preview.size() && dst != this.dragIndex)
            {
                PreviewAction a = this.preview.remove(this.dragIndex);
                this.preview.add(dst, a);
                this.persistPreviewOrder();
            }
            this.dragIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void persistPreviewOrder()
    {
        List<String> old = new ArrayList<>(HotkeyWheelConfigStore.INSTANCE.getWheelActionSortOrder());
        List<String> out = new ArrayList<>(this.preview.size() + old.size());
        Set<String> seen = new HashSet<>();
        for (PreviewAction a : this.preview)
        {
            if (a == null || a.actionId == null || a.actionId.isEmpty()) continue;
            if (seen.add(a.actionId)) out.add(a.actionId);
        }
        for (String s : old) if (s != null && s.isEmpty() == false && seen.add(s)) out.add(s);
        HotkeyWheelConfigStore.INSTANCE.setWheelActionSortOrder(out);
        HotkeyWheelConfigStore.INSTANCE.save();
    }

    private int wheelX() { return 10; }
    private int wheelY() { return 56; }
    private int wheelW() { return this.width - 20; }
    // Leave room for hint bar + Done button so text never overlaps the wheel.
    private int wheelH() { return this.height - 128; }

    private int indexFromMouseInWheel(double mouseX, double mouseY)
    {
        int n = this.preview.size();
        if (n < 1) return -1;
        // Important: don't treat clicks outside the wheel area as wheel interaction.
        // Otherwise large radii (small n) can "steal" clicks from UI controls (eg. Done button).
        if (mouseX < this.wheelX()
                || mouseX >= (this.wheelX() + this.wheelW())
                || mouseY < this.wheelY()
                || mouseY >= (this.wheelY() + this.wheelH()))
        {
            return -1;
        }
        int cx = this.wheelX() + this.wheelW() / 2;
        int cy = this.wheelY() + this.wheelH() / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        float innerR = HotkeyWheelRadialLayout.innerR(this.wheelW(), this.wheelH(), n);
        double d2 = dx * dx + dy * dy;
        if (d2 < (double) innerR * (double) innerR) return -1;
        if (n > 8)
        {
            float outerR = HotkeyWheelRadialLayout.outerR(this.wheelW(), this.wheelH(), n);
            float innerOut = HotkeyWheelRadialLayout.innerRingOuterR(this.wheelW(), this.wheelH());
            float outerIn = HotkeyWheelRadialLayout.outerRingInnerR(this.wheelW(), this.wheelH());
            if (d2 > (double) outerR * (double) outerR) return -1;
            if (d2 > (double) innerOut * (double) innerOut && d2 < (double) outerIn * (double) outerIn) return -1;
            int nIn = (int) Math.floor(n * 0.40);
            if (nIn < 1) nIn = 1;
            if (nIn > n - 1) nIn = n - 1;
            int nOut = n - nIn;
            if (d2 < (double) innerOut * (double) innerOut)
            {
                this.debugPick("inner", n, nIn, nOut, dx, dy, d2, innerR, innerOut, outerIn, outerR, 0.0);
                int seg = RadialWheelMath.selectedSegmentIndex(dx, dy, innerR, nIn);
                int idx = seg;
                this.debugPickIndex("inner", seg, idx);
                return idx;
            }
            else
            {
                if (nOut <= 0) return -1;
                double off = Math.PI / (double) nOut;
                double rdx = dx * Math.cos(off) - dy * Math.sin(off);
                double rdy = dx * Math.sin(off) + dy * Math.cos(off);
                this.debugPick("outer", n, nIn, nOut, dx, dy, d2, innerR, innerOut, outerIn, outerR, off);
                int seg = RadialWheelMath.selectedSegmentIndex(rdx, rdy, innerR, nOut);
                int idx = seg < 0 ? -1 : (nIn + seg);
                this.debugPickIndex("outer", seg, idx);
                return idx;
            }
        }
        this.debugPick("single", n, n, 0, dx, dy, d2, innerR, 0f, 0f, HotkeyWheelRadialLayout.outerR(this.wheelW(), this.wheelH(), n), 0.0);
        int seg = RadialWheelMath.selectedSegmentIndex(dx, dy, innerR, n);
        int idx = seg;
        this.debugPickIndex("single", seg, idx);
        return idx;
    }

    private void debugPickIndex(String ring, int seg, int idx)
    {
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() == false) return;
        long now = System.currentTimeMillis();
        if (now < this.nextDebugIdxLogMs) return;
        this.nextDebugIdxLogMs = now + 200L;
        String action = (idx >= 0 && idx < this.preview.size() && this.preview.get(idx) != null)
                ? this.preview.get(idx).actionId
                : "?";
        HotkeyWheelClient.LOGGER.info(String.format(
                java.util.Locale.ROOT,
                "WheelPickPreviewIndex ring=%s seg=%d idx=%d action=%s",
                ring, seg, idx, action));
    }

    private void debugPick(
            String ring,
            int nTotal,
            int nIn,
            int nOut,
            double dx,
            double dy,
            double d2,
            float cancelR,
            float innerOut,
            float outerIn,
            float outerR,
            double offRad)
    {
        if (HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging() == false) return;
        long now = System.currentTimeMillis();
        if (now < this.nextDebugLogMs) return;
        this.nextDebugLogMs = now + 200L;
        double deg = (Math.toDegrees(Math.atan2(dy, dx)) + 450.0) % 360.0;
        HotkeyWheelClient.LOGGER.info(String.format(
                java.util.Locale.ROOT,
                "WheelPickPreview ring=%s n=%d in/out=%d/%d dx=%.1f dy=%.1f d2=%.1f deg=%.1f cancelR=%.1f innerOut=%.1f outerIn=%.1f outerR=%.1f offRad=%.4f",
                ring, nTotal, nIn, nOut,
                dx, dy, d2, deg,
                cancelR, innerOut, outerIn, outerR, offRad));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        String comboDisp = fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil.comboIdToDisplayString(this.comboIdUpper);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFE8E8E8);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(comboDisp), this.width / 2, 18, 0xFF909090);
        // (no debug overlay; keep UI clean)

        int idx = this.indexFromMouseInWheel(mouseX, mouseY);
        if (idx != this.lastHover)
        {
            this.lastHover = idx;
            this.hoverSinceMs = System.currentTimeMillis();
        }
        this.hoverIndex = idx;
        long stable = idx >= 0 ? (System.currentTimeMillis() - this.hoverSinceMs) : 0L;

        HotkeyWheelRadialView view = PreviewAction.toView(
                this.wheelW(), this.wheelH(), this.hoverIndex,
                (int) mouseX - this.wheelX(), (int) mouseY - this.wheelY(),
                stable, this.preview);

        context.getMatrices().push();
        context.getMatrices().translate(this.wheelX(), this.wheelY(), 0f);
        HotkeyWheelRadialRenderer.render(context, view, this.textRenderer, -1, 0L);
        context.getMatrices().pop();

        // Hint bar below the wheel, above the Done button.
        int hy = this.height - 70;
        context.fill(10, hy - 6, this.width - 10, hy + 18, 0xA0101010);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.hint.drag_reorder"), this.width / 2, hy, 0xFFC8C8C8);
        super.render(context, mouseX, mouseY, delta);
    }

    private static final class PreviewAction
    {
        public final String actionId;
        public final String label;
        public final boolean enabled;
        public final ItemStack stack;
        public final Identifier tex;

        private PreviewAction(String actionId, String label, boolean enabled, ItemStack stack, Identifier tex)
        {
            this.actionId = actionId;
            this.label = label;
            this.enabled = enabled;
            this.stack = stack;
            this.tex = tex;
        }

        public static PreviewAction from(KeyBindingComboScanner.ScanRow row, String comboIdUpper)
        {
            String id = row.exclusionKey();
            boolean on = HotkeyWheelConfigStore.INSTANCE.isActionExcludedFromWheel(comboIdUpper, id) == false;
            String iconId = HotkeyWheelConfigStore.INSTANCE.getIconItemIdForAction(id);
            Identifier tex = HotkeyWheelCustomIconUtil.textureIdForIconId(iconId);
            ItemStack st = tex != null ? ItemStack.EMPTY : HotkeyWheelItemIconUtil.stackForItemId(iconId);
            return new PreviewAction(id, row.functionNameI18n(), on, st, tex);
        }

        public static HotkeyWheelRadialView toView(
                int w, int h,
                int selectedIndex,
                int mouseX, int mouseY,
                long stableHoverOnSelectionMs,
                List<PreviewAction> actions)
        {
            int n = actions != null ? actions.size() : 0;
            int cx = w / 2;
            int cy = h / 2;
            float innerR = n > 0 ? HotkeyWheelRadialLayout.innerR(w, h, n) : 0f;
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            boolean inDead = n > 0 && (dx * dx + dy * dy) < (double) innerR * (double) innerR;
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
                PreviewAction a = actions.get(i);
                mains.add(a != null ? a.label : "");
                mods.add("");
                icons.add(a != null ? a.stack : ItemStack.EMPTY);
                textures.add(a != null ? a.tex : null);
                fulls.add(a != null ? a.label : "");
                previews.add("");
            }
            return new HotkeyWheelRadialView(
                    w, h, n, selectedIndex, mouseX, mouseY, stableHoverOnSelectionMs,
                    mains, mods, icons, textures, fulls, previews, inDead, dual, inCount, outCount, selRing);
        }
    }
}

