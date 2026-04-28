package fi.dy.masa.hotkeywheel.gui;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.scan.KeyBindingComboScanner;

/**
 * Per-combo: participate (not excluded), function name, icon.
 * The wheel layout preview is opened via a separate screen.
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
    private static final int MARGIN = 12;
    private static final int COL_PART_W = 30;
    private static final int COL_GAP = 8;
    private int colPartX() { return MARGIN; }
    private int colFnX() { return colPartX() + COL_PART_W + COL_GAP; }
    private int colIconX() { return this.width - 34; }
    private int colCustomX() { return this.width - 34 - 180; } // fixed-width "custom name" column
    private int listBottomY() { return this.height - 56; }
    private final java.util.Map<String, TextFieldWidget> customFields = new java.util.HashMap<>();

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
        this.customFields.clear();
        int cx = this.width / 2 - 100;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("hotkeywheel.gui.button.ui_preview"), b -> {
            this.client.setScreen(new WheelUiPreviewScreen(this, this.comboIdUpper, this.rows));
        }).dimensions(cx, this.height - 52, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> {
            this.client.setScreen(this.parent);
        }).dimensions(cx, this.height - 28, 200, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        this.scroll = MathHelper.clamp(this.scroll - (int) (amount * ROW_H), 0, this.maxScroll());
        this.layoutCustomFields();
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private int maxScroll()
    {
        return Math.max(0, this.rows.size() * ROW_H - (this.height - LIST_TOP - 58));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        // Let text fields handle click/focus first
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && mouseY >= LIST_TOP && mouseY < this.listBottomY())
        {
            int y0 = (int) mouseY - LIST_TOP + this.scroll;
            int idx = y0 / ROW_H;
            if (idx >= 0 && idx < this.rows.size())
            {
                KeyBindingComboScanner.ScanRow row = this.rows.get(idx);
                // Icon column
                if (mouseX >= this.width - 36 && mouseX < this.width - 16)
                {
                    this.client.setScreen(new IconPickerScreen(this, row.exclusionKey()));
                    return true;
                }
                // Participate checkbox
                int cb = 14;
                int cbx = this.colPartX() + (COL_PART_W - cb) / 2;
                if (mouseX >= cbx && mouseX < cbx + cb + 2)
                {
                    HotkeyWheelConfigStore.INSTANCE.toggleActionExcludedForWheel(this.comboIdUpper, row.exclusionKey());
                    HotkeyWheelConfigStore.INSTANCE.save();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        if (super.charTyped(chr, modifiers))
        {
            this.persistFocusedCustomNameIfAny();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (super.keyPressed(keyCode, scanCode, modifiers))
        {
            this.persistFocusedCustomNameIfAny();
            return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        String comboDisp = fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelKeyComboUtil.comboIdToDisplayString(this.comboIdUpper);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFE8E8E8);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(comboDisp), this.width / 2, 22, 0xA0A0A0A0);
        int xPart = this.colPartX();
        int xFn = this.colFnX();
        int xCustom = this.colCustomX();
        int xIcon = this.colIconX();
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.participate"), xPart, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.function"), xFn, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.custom_name"), xCustom, HEADER_Y, 0xA0A0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("hotkeywheel.gui.col.icon"), xIcon - 6, HEADER_Y, 0xA0A0A0A0);

        context.enableScissor(0, LIST_TOP, this.width, this.listBottomY());
        this.layoutCustomFields();
        for (int i = 0; i < this.rows.size(); i++)
        {
            int y = LIST_TOP - this.scroll + i * ROW_H;
            if (y + ROW_H < LIST_TOP - 1 || y > this.listBottomY()) continue;
            KeyBindingComboScanner.ScanRow row = this.rows.get(i);
            boolean on = HotkeyWheelConfigStore.INSTANCE.isActionExcludedFromWheel(this.comboIdUpper, row.exclusionKey()) == false;
            boolean hover = mouseX >= xPart && mouseX < xIcon + 24 && mouseY >= y && mouseY < y + ROW_H;
            int cb = 14;
            int cbx = xPart + (COL_PART_W - cb) / 2;
            int cby = y + (ROW_H - cb) / 2;
            context.fill(cbx, cby, cbx + cb, cby + cb, hover ? 0xFFB0B0B0 : 0xFF808080);
            context.fill(cbx + 1, cby + 1, cbx + cb - 1, cby + cb - 1, 0xFF1A1A1A);
            context.fill(cbx + 2, cby + 2, cbx + cb - 2, cby + cb - 2, on ? 0xFF2A2A2A : 0xFF0C0C0C);
            if (on)
            {
                context.drawTextWithShadow(this.textRenderer, "✔", cbx + 4, cby + 2, 0xFFFFFFFF);
                context.drawTextWithShadow(this.textRenderer, "✔", cbx + 4, cby + 2, 0xFFFFFFFF);
            }
            String fn = row.functionNameI18n();
            int fnMax = xCustom - xFn - 10;
            if (this.textRenderer.getWidth(fn) > fnMax) fn = this.textRenderer.trimToWidth(fn, fnMax) + "…";
            context.drawTextWithShadow(this.textRenderer, fn, xFn, y + 4, 0xE0E0E0E0);

            // Custom name text field (stored in Generic.labelOverrides; used by wheel label)
            String actionId = row.exclusionKey();
            TextFieldWidget tf = this.customFields.get(actionId);
            if (tf == null)
            {
                tf = new TextFieldWidget(this.textRenderer, xCustom, y + 2, 170, 14, Text.empty());
                tf.setMaxLength(64);
                String v = HotkeyWheelConfigStore.INSTANCE.getLabelOverride(actionId);
                tf.setText(v != null ? v : "");
                this.customFields.put(actionId, tf);
                this.addDrawableChild(tf);
            }

            String iconId = HotkeyWheelConfigStore.INSTANCE.getIconItemIdForAction(row.exclusionKey());
            net.minecraft.item.ItemStack stack = fi.dy.masa.hotkeywheel.util.HotkeyWheelItemIconUtil.stackForItemId(iconId);
            context.drawItem(stack, xIcon, y + 1);
        }
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }

    private void layoutCustomFields()
    {
        int x = this.colCustomX();
        int w = 170;
        for (int i = 0; i < this.rows.size(); i++)
        {
            int y = LIST_TOP - this.scroll + i * ROW_H;
            KeyBindingComboScanner.ScanRow row = this.rows.get(i);
            TextFieldWidget tf = this.customFields.get(row.exclusionKey());
            if (tf == null) continue;
            boolean visible = !(y + ROW_H < LIST_TOP - 1 || y > this.listBottomY());
            tf.setVisible(visible);
            if (visible)
            {
                tf.setX(x);
                tf.setY(y + 2);
                tf.setWidth(w);
            }
        }
    }

    private void persistFocusedCustomNameIfAny()
    {
        for (var e : this.customFields.entrySet())
        {
            TextFieldWidget tf = e.getValue();
            if (tf != null && tf.isFocused())
            {
                HotkeyWheelConfigStore.INSTANCE.setLabelOverride(e.getKey(), tf.getText());
                HotkeyWheelConfigStore.INSTANCE.save();
                return;
            }
        }
    }
}
