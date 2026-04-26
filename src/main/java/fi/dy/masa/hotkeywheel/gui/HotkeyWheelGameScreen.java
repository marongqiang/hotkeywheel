package fi.dy.masa.hotkeywheel.gui;

import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore;
import fi.dy.masa.hotkeywheel.hotkeys.HotkeyWheelManager;
import fi.dy.masa.hotkeywheel.hotkeys.WheelAction;

/**
 * In-game full-screen for radial selection (pointer picks slice).
 */
public class HotkeyWheelGameScreen extends Screen
{
    private final Screen parent;
    private final List<WheelAction> entries;
    private int lastSelected = -1;
    private int hoverForStable = Integer.MIN_VALUE;
    private long hoverSinceMs = 0L;

    public HotkeyWheelGameScreen(Screen parent, List<WheelAction> entries)
    {
        super(Text.literal("Hotkey Wheel"));
        this.parent = parent;
        this.entries = entries;
    }

    @Override
    protected void init()
    {
        super.init();
        if (this.client != null)
        {
            this.client.mouse.unlockCursor();
        }
    }

    @Override
    public void removed()
    {
        if (this.client != null)
        {
            this.client.mouse.lockCursor();
        }
    }

    public int getSelectedIndex()
    {
        return this.lastSelected;
    }

    @Override
    public void tick()
    {
        this.lastSelected = this.computeIndexFromMouse();
        if (this.lastSelected != this.hoverForStable)
        {
            this.hoverForStable = this.lastSelected;
            this.hoverSinceMs = System.currentTimeMillis();
        }
    }

    private int computeIndexFromMouse()
    {
        if (this.client == null || this.entries == null || this.entries.isEmpty()) return -1;
        int n = this.entries.size();
        if (n == 1) return 0;
        int mx = (int) (this.client.mouse.getX() * (double) this.width / (double) this.client.getWindow().getWidth());
        int my = (int) (this.client.mouse.getY() * (double) this.height / (double) this.client.getWindow().getHeight());
        int cx = this.width / 2;
        int cy = this.height / 2;
        double dx = mx - cx;
        double dy = my - cy;
        float innerR = HotkeyWheelRadialLayout.innerR(this.width, this.height, n);
        return RadialWheelMath.selectedSegmentIndex(dx, dy, innerR, n);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        this.renderBackground(context);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        int n = this.entries.size();
        if (n < 1) { super.render(context, mouseX, mouseY, delta); return; }
        long stable = this.lastSelected < 0 ? 0L : (System.currentTimeMillis() - this.hoverSinceMs);
        HotkeyWheelRadialView view = HotkeyWheelRadialView.build(
                this.width, this.height, this.lastSelected, mouseX, mouseY, stable, this.entries, HotkeyWheelConfigStore.INSTANCE);
        HotkeyWheelRadialRenderer.render(
                context, view, this.textRenderer, HotkeyWheelManager.INSTANCE.getFeedbackSliceIndex(), HotkeyWheelManager.INSTANCE.getFeedbackEndMs());
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause()
    {
        return false;
    }
}
