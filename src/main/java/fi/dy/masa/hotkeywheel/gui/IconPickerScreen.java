package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.util.HotkeyWheelCustomIconUtil;

/**
 * Simple icon picker: Vanilla items + custom PNGs from config/hotkeywheel/icons.
 */
public class IconPickerScreen extends Screen
{
    private final Screen parent;
    private final String actionId;
    private boolean tabCustom;
    private TextFieldWidget search;
    private List<Item> filteredItems = List.of();
    private List<String> customFiles = List.of();
    private int scroll;

    private static final int GRID = 18;
    private static final int TOP = 44;

    public IconPickerScreen(Screen parent, String actionId)
    {
        super(Text.translatable("hotkeywheel.gui.title.icon_picker"));
        this.parent = parent;
        this.actionId = actionId;
    }

    @Override
    protected void init()
    {
        int cx = this.width / 2;
        this.search = new TextFieldWidget(this.textRenderer, cx - 100, 18, 200, 18, Text.translatable("hotkeywheel.gui.icon.search"));
        this.search.setChangedListener(s -> this.refresh());
        this.addSelectableChild(this.search);
        this.setInitialFocus(this.search);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("hotkeywheel.gui.icon.tab_vanilla"), b -> {
            this.tabCustom = false;
            this.scroll = 0;
            this.refresh();
        }).dimensions(cx - 100, this.height - 28, 96, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("hotkeywheel.gui.icon.tab_custom"), b -> {
            this.tabCustom = true;
            this.scroll = 0;
            this.refresh();
        }).dimensions(cx + 4, this.height - 28, 96, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.client.setScreen(this.parent))
                .dimensions(cx - 100, this.height - 52, 200, 20).build());

        this.refresh();
    }

    private void refresh()
    {
        if (this.client == null) return;
        String q = this.search != null ? this.search.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (this.tabCustom)
        {
            List<String> all = HotkeyWheelCustomIconUtil.listPngFiles();
            if (q.isEmpty()) this.customFiles = all;
            else
            {
                List<String> out = new ArrayList<>();
                for (String s : all) if (s != null && s.toLowerCase(Locale.ROOT).contains(q)) out.add(s);
                this.customFiles = out;
            }
        }
        else
        {
            List<Item> out = new ArrayList<>(Registries.ITEM.size());
            for (Item it : Registries.ITEM)
            {
                if (it == null) continue;
                Identifier id = Registries.ITEM.getId(it);
                if (id == null) continue;
                String s = id.toString();
                if (q.isEmpty() || s.contains(q)) out.add(it);
            }
            this.filteredItems = out;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        this.scroll = Math.max(0, this.scroll - (int) (amount * GRID * 2));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.search != null && this.search.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int left = 16;
        int right = this.width - 16;
        int cols = Math.max(1, (right - left) / GRID);
        int x0 = (int) mouseX - left;
        int y0 = (int) mouseY - TOP + this.scroll;
        if (x0 < 0 || y0 < 0) return super.mouseClicked(mouseX, mouseY, button);
        int col = x0 / GRID;
        int row = y0 / GRID;
        int idx = row * cols + col;

        if (this.tabCustom)
        {
            if (idx < 0 || idx >= this.customFiles.size()) return super.mouseClicked(mouseX, mouseY, button);
            String fn = this.customFiles.get(idx);
            HotkeyWheelConfigStore.INSTANCE.setIconIdForAction(this.actionId, HotkeyWheelCustomIconUtil.toIconId(fn));
            HotkeyWheelConfigStore.INSTANCE.save();
            this.client.setScreen(this.parent);
            return true;
        }
        else
        {
            if (idx < 0 || idx >= this.filteredItems.size()) return super.mouseClicked(mouseX, mouseY, button);
            Item it = this.filteredItems.get(idx);
            Identifier id = Registries.ITEM.getId(it);
            if (id == null) return super.mouseClicked(mouseX, mouseY, button);
            HotkeyWheelConfigStore.INSTANCE.setIconIdForAction(this.actionId, id.toString());
            HotkeyWheelConfigStore.INSTANCE.save();
            this.client.setScreen(this.parent);
            return true;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFA0);
        if (this.search != null) this.search.render(context, mouseX, mouseY, delta);

        int left = 16;
        int right = this.width - 16;
        int cols = Math.max(1, (right - left) / GRID);
        context.enableScissor(0, TOP, this.width, this.height - 60);
        if (this.tabCustom)
        {
            for (int i = 0; i < this.customFiles.size(); i++)
            {
                int x = left + (i % cols) * GRID;
                int y = TOP + (i / cols) * GRID - this.scroll;
                if (y + GRID < TOP - 2 || y > this.height - 60) continue;
                String fn = this.customFiles.get(i);
                Identifier tex = HotkeyWheelCustomIconUtil.textureIdForIconId(HotkeyWheelCustomIconUtil.toIconId(fn));
                if (tex != null)
                {
                    context.drawTexture(tex, x, y, 0, 0, 16, 16, 16, 16);
                }
                else
                {
                    context.fill(x + 3, y + 3, x + 13, y + 13, 0x80FFFFFF);
                }
            }
        }
        else
        {
            for (int i = 0; i < this.filteredItems.size(); i++)
            {
                int x = left + (i % cols) * GRID;
                int y = TOP + (i / cols) * GRID - this.scroll;
                if (y + GRID < TOP - 2 || y > this.height - 60) continue;
                context.drawItem(new ItemStack(this.filteredItems.get(i)), x, y);
            }
        }
        context.disableScissor();
        super.render(context, mouseX, mouseY, delta);
    }
}

