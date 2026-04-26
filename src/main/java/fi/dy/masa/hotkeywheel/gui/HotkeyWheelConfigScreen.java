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
    private static final int LIST_TOP = 60;
    private static final int HEADER_Y = 42;
    /** Reserved width for 是否启用 / Enable column; hotkey text starts after this. */
    private static final int MARGIN = 8;
    private static final int X_CHECK = 12;
    private static final int X_HOTKEY = 100;
    private int colCountX() { return this.width - 118; }
    private int colDetailsX() { return this.width - 64; }

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
        return Math.max(0, this.data.size() * ROW_H - (this.height - LIST_TOP - 36));
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
        if (button == 0 && mouseY >= LIST_TOP && mouseY < this.height - 32 && mouseX > 4 && mouseX < this.width - 4)
        {
            int y0 = (int) mouseY - LIST_TOP + this.scroll;
            int idx = y0 / ROW_H;
            if (idx < 0 || idx >= this.data.size()) return super.mouseClicked(mouseX, mouseY, button);
            ComboGroup g = this.data.get(idx);
            if (mouseX >= MARGIN && mouseX < X_HOTKEY - 4)
            {
                boolean on = HotkeyWheelConfigStore.INSTANCE.isComboWheelEnabled(g.comboId());
                HotkeyWheelConfigStore.INSTANCE.setComboWheelEnabled(g.comboId(), !on);
                HotkeyWheelConfigStore.INSTANCE.save();
                return true;
            }
            if (mouseX >= this.colDetailsX() && mouseX < this.width - 10)
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFA0);
        int x2 = this.colCountX();
        int x3 = this.colDetailsX();
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.enable"), MARGIN, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.hotkey"), X_HOTKEY, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.count"), x2, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.details"), x3, HEADER_Y, 0xA0A0A0A0);
        context.enableScissor(0, LIST_TOP, this.width, this.height - 34);
        for (int i = 0; i < this.data.size(); i++)
        {
            int y = LIST_TOP - this.scroll + i * ROW_H;
            if (y + ROW_H < LIST_TOP - 1) continue;
            if (y > this.height) break;
            ComboGroup g = this.data.get(i);
            boolean on = HotkeyWheelConfigStore.INSTANCE.isComboWheelEnabled(g.comboId());
            context.drawTextWithShadow(this.textRenderer, on ? "☑" : "☐", X_CHECK, y + 5, 0xFFFFFF);
            String hotkey = HotkeyWheelKeyComboUtil.comboIdToDisplayString(g.comboId());
            int wMax = x2 - X_HOTKEY - 6;
            if (wMax < 20) wMax = 20;
            if (this.textRenderer.getWidth(hotkey) > wMax) hotkey = this.textRenderer.trimToWidth(hotkey, wMax) + "…";
            context.drawTextWithShadow(this.textRenderer, hotkey, X_HOTKEY, y + 5, 0xE0E0E0E0);
            String cnt = String.valueOf(g.entryCount());
            context.drawTextWithShadow(this.textRenderer, cnt, x2, y + 5, 0xE0E0E0E0);
            context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.button.details"), x3, y + 5, 0xFFAA88);
        }
        context.disableScissor();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.hint.keybind", Text.translatable("key.hotkeywheel.openconfig")), this.width / 2, this.height - 52, 0x808080);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.scan.hint"), this.width / 2, this.height - 40, 0xFF666666);
        super.render(context, mouseX, mouseY, delta);
    }
}
