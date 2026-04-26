package fi.dy.masa.hotkeywheel.gui;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;

/**
 * Per-combo: participate (not excluded), mod name, function name. Rows may be vanilla
 * {@link net.minecraft.client.option.KeyBinding} or Masa (MaLiLib) hotkey lines.
 */
public class ComboFunctionDetailScreen extends Screen
{
    private final Screen parent;
    private final String comboIdUpper;
    private final List<KeyBindingComboScanner.ScanRow> rows;
    private int scroll;

    private static final int ROW_H = 18;
    private static final int LIST_TOP = 44;
    private static final int HEADER_Y = 28;

    public ComboFunctionDetailScreen(Screen parent, String comboIdUpper, List<KeyBindingComboScanner.ScanRow> rows)
    {
        super(Text.translatable("hotkeywheel.gui.title.function_list"));
        this.parent = parent;
        this.comboIdUpper = comboIdUpper;
        this.rows = rows;
    }

    @Override
    protected void init()
    {
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        this.scroll = MathHelper.clamp(this.scroll - (int) (amount * ROW_H), 0, this.maxScroll());
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private int maxScroll()
    {
        return Math.max(0, this.rows.size() * ROW_H - (this.height - LIST_TOP - 36));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseY >= LIST_TOP && mouseY < this.height - 32)
        {
            int y0 = (int) mouseY - LIST_TOP + this.scroll;
            int idx = y0 / ROW_H;
            if (idx >= 0 && idx < this.rows.size())
            {
                if (mouseX >= 12 && mouseX < 12 + 18)
                {
                    KeyBindingComboScanner.ScanRow row = this.rows.get(idx);
                    HotkeyWheelConfigStore.INSTANCE.toggleActionExcludedForWheel(this.comboIdUpper, row.exclusionKey());
                    HotkeyWheelConfigStore.INSTANCE.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        String comboDisp = fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil.comboIdToDisplayString(this.comboIdUpper);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFA0);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(comboDisp), this.width / 2, 22, 0xA0A0A0A0);
        int xPart = 14;
        int xMod = 40;
        int xFn = this.width / 2 - 20;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.participate"), xPart, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.mod"), xMod, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.function"), xFn, HEADER_Y, 0xA0A0A0A0);

        context.enableScissor(0, LIST_TOP, this.width, this.height - 36);
        for (int i = 0; i < this.rows.size(); i++)
        {
            int y = LIST_TOP - this.scroll + i * ROW_H;
            if (y + ROW_H < LIST_TOP - 1 || y > this.height) continue;
            KeyBindingComboScanner.ScanRow row = this.rows.get(i);
            boolean on = HotkeyWheelConfigStore.INSTANCE.isActionExcludedFromWheel(this.comboIdUpper, row.exclusionKey()) == false;
            String ch = on ? "☑" : "☐";
            context.drawTextWithShadow(this.textRenderer, ch, xPart, y + 4, 0xFFFFFF);
            String mod = row.modName();
            if (this.textRenderer.getWidth(mod) > xFn - xMod - 8) mod = this.textRenderer.trimToWidth(mod, xFn - xMod - 8) + "…";
            context.drawTextWithShadow(this.textRenderer, mod, xMod, y + 4, 0xE0E0E0E0);
            String fn = row.functionNameI18n();
            int fnMax = this.width - xFn - 10;
            if (this.textRenderer.getWidth(fn) > fnMax) fn = this.textRenderer.trimToWidth(fn, fnMax) + "…";
            context.drawTextWithShadow(this.textRenderer, fn, xFn, y + 4, 0xE0E0E0E0);
        }
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }
}
