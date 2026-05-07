package fi.dy.masa.hotkeywheel.gui;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner.ComboGroup;

/**
 * Top: scan button. Table: enable wheel, hotkey, count, details.
 */
public class HotkeyWheelConfigScreen extends Screen
{
    private final Screen parent;
    private List<ComboGroup> data = List.of();
    private int scroll;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 64;
    private static final int HEADER_Y = 44;
    private static final int MARGIN = 10;
    private static final int COL_ENABLE_W = 30;
    private static final int COL_HOTKEY_W = 90;
    private static final int COL_COUNT_W = 60;
    private static final int COL_GAP = 6;
    private int colEnableX() { return MARGIN; }
    private int colHotkeyX() { return colEnableX() + COL_ENABLE_W + COL_GAP; }
    private int colCountX() { return colHotkeyX() + COL_HOTKEY_W + COL_GAP; }
    private int colActionX() { return colCountX() + COL_COUNT_W + COL_GAP; }
    private int listBottom() { return this.height - 74; }
    private int listHeight() { return Math.max(60, listBottom() - LIST_TOP); }

    public HotkeyWheelConfigScreen(Screen parent)
    {
        super(Text.translatable("hotkeywheel.gui.title.configs"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        this.refreshFromGame();
        int cx = this.width / 2 - 100;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("hotkeywheel.gui.button.scan_refresh"), b -> {
            this.refreshFromGame();
        }).dimensions(cx, 18, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.client.setScreen(this.parent))
                .dimensions(cx, this.height - 28, 200, 20).build());
    }

    private void refreshFromGame()
    {
        if (this.client == null) return;
        this.data = KeyBindingComboScanner.scan(this.client);
    }

    private int maxScroll()
    {
        return Math.max(0, this.data.size() * ROW_H - this.listHeight());
    }

    @Override
    public void removed()
    {
        super.removed();
        HotkeyWheelConfigStore.INSTANCE.load();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        this.scroll = MathHelper.clamp(this.scroll - (int) (amount * ROW_H), 0, this.maxScroll());
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseY >= LIST_TOP && mouseY < this.listBottom() && mouseX > 4 && mouseX < this.width - 4)
        {
            int y0 = (int) mouseY - LIST_TOP + this.scroll;
            int idx = y0 / ROW_H;
            if (idx < 0 || idx >= this.data.size()) return super.mouseClicked(mouseX, mouseY, button);
            ComboGroup g = this.data.get(idx);
            int xEnable = this.colEnableX();
            int xHotkey = this.colHotkeyX();
            int xAction = this.colActionX();
            if (mouseX >= xEnable && mouseX < xHotkey)
            {
                boolean on = HotkeyWheelConfigStore.INSTANCE.isComboWheelEnabled(g.comboId());
                HotkeyWheelConfigStore.INSTANCE.setComboWheelEnabled(g.comboId(), !on);
                HotkeyWheelConfigStore.INSTANCE.save();
                return true;
            }
            if (mouseX >= xAction && mouseX < this.width - 10)
            {
                this.client.setScreen(new ComboFunctionDetailScreen(this, g.comboId(), g.rows()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.title.configs"), this.width / 2, 6, 0xFFE8E8E8);

        int xEnable = this.colEnableX();
        int xHotkey = this.colHotkeyX();
        int xCount = this.colCountX();
        int xAction = this.colActionX();

        // Header background
        context.fill(MARGIN - 4, HEADER_Y - 4, this.width - MARGIN + 4, HEADER_Y + 12, 0xA0101010);
        String hEnable = this.textRenderer.trimToWidth(Text.translatable("hotkeywheel.gui.col.enable").getString(), COL_ENABLE_W).trim();
        context.drawTextWithShadow(this.textRenderer, hEnable, xEnable + 2, HEADER_Y, 0xE0E0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.hotkey"), xHotkey + 4, HEADER_Y, 0xE0E0E0E0);
        String cntHead = Text.translatable("hotkeywheel.gui.col.count").getString();
        context.drawTextWithShadow(this.textRenderer, cntHead, xCount + (COL_COUNT_W / 2) - this.textRenderer.getWidth(cntHead) / 2, HEADER_Y, 0xE0E0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.details"), xAction + 4, HEADER_Y, 0xE0E0E0E0);

        context.enableScissor(0, LIST_TOP, this.width, this.listBottom());
        for (int i = 0; i < this.data.size(); i++)
        {
            int y = LIST_TOP - this.scroll + i * ROW_H;
            if (y + ROW_H < LIST_TOP - 1) continue;
            if (y > this.listBottom()) break;
            ComboGroup g = this.data.get(i);
            boolean on = HotkeyWheelConfigStore.INSTANCE.isComboWheelEnabled(g.comboId());

            // Row hover & separators
            boolean hover = mouseY >= y && mouseY < y + ROW_H && mouseX >= MARGIN - 4 && mouseX < this.width - MARGIN + 4;
            int rowBg = on ? 0x241A1A1A : 0x18101010;
            if (hover) rowBg = 0x40242424;
            context.fill(MARGIN - 4, y, this.width - MARGIN + 4, y + ROW_H, rowBg);
            context.fill(MARGIN - 4, y + ROW_H - 1, this.width - MARGIN + 4, y + ROW_H, 0x40A0A0A0);

            // Enable checkbox (pixel style)
            int cb = 14;
            int cbx = xEnable + (COL_ENABLE_W - cb) / 2;
            int cby = y + (ROW_H - cb) / 2;
            context.fill(cbx, cby, cbx + cb, cby + cb, hover ? 0xFFB0B0B0 : 0xFF808080);
            context.fill(cbx + 1, cby + 1, cbx + cb - 1, cby + cb - 1, 0xFF1A1A1A);
            context.fill(cbx + 2, cby + 2, cbx + cb - 2, cby + cb - 2, on ? 0xFF2A2A2A : 0xFF0C0C0C);
            if (on)
            {
                context.drawTextWithShadow(this.textRenderer, "✔", cbx + 4, cby + 2, 0xFFFFFFFF);
            }

            String hotkey = HotkeyWheelKeyComboUtil.comboIdToDisplayString(g.comboId());
            int hkMax = COL_HOTKEY_W - 8;
            if (this.textRenderer.getWidth(hotkey) > hkMax) hotkey = this.textRenderer.trimToWidth(hotkey, hkMax) + "…";
            int hotCol = on ? 0xFFE0E0E0 : 0xFF8A8A8A;
            context.drawTextWithShadow(this.textRenderer, hotkey, xHotkey + 4, y + 6, hotCol);
            String cnt = String.valueOf(g.entryCount());
            int cCol = g.entryCount() >= 2 ? 0xFFFFD060 : 0xFF7A7A7A;
            int cx = xCount + COL_COUNT_W / 2 - this.textRenderer.getWidth(cnt) / 2;
            context.drawTextWithShadow(this.textRenderer, cnt, cx, y + 6, cCol);

            // Action button-like label
            int bw = Math.min(120, this.width - xAction - MARGIN - 6);
            int bx0 = xAction + 4;
            int by0 = y + 3;
            context.fill(bx0, by0, bx0 + bw, by0 + 16, hover ? 0xFF3A3A3A : 0xFF2A2A2A);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.button.details"), bx0 + 8, y + 6, 0xFFE8E8E8);
        }
        context.disableScissor();

        // Bottom hint panel (does not overlap list)
        int hintTop = this.height - 66;
        context.fill(MARGIN - 4, hintTop, this.width - MARGIN + 4, this.height - 34, 0xA0101010);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.hint.keybind", Text.translatable("key.hotkeywheel.openconfig")), this.width / 2, hintTop + 6, 0xFFB0B0B0);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.scan.hint"), this.width / 2, hintTop + 18, 0xFF808080);

        // Scrollbar (simple MC-like)
        int contentH = this.data.size() * ROW_H;
        int viewH = this.listHeight();
        if (contentH > viewH)
        {
            int barX0 = this.width - MARGIN - 3;
            int barX1 = barX0 + 2;
            int barY0 = LIST_TOP;
            int barY1 = this.listBottom();
            context.fill(barX0, barY0, barX1, barY1, 0x40202020);
            float t = this.scroll / (float) (contentH - viewH);
            int thumbH = Math.max(12, (int) (viewH * (viewH / (float) contentH)));
            int thumbY = barY0 + (int) ((viewH - thumbH) * t);
            context.fill(barX0, thumbY, barX1, thumbY + thumbH, 0xA0B0B0B0);
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
