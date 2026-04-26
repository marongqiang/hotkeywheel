package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Renders a radial (pie) wheel: ring segments, borders, item icons, short labels, mod source line, tooltip.
 */
public final class HotkeyWheelRadialRenderer
{
    private static final int SLICE_BASE = 0x90402055;
    private static final int SLICE_SEL = 0xD0C060A0;
    private static final int SLICE_FLASH = 0xE0FFE090;
    private static final int LINE_COLOR = 0x88EECCEE;
    private static final int LINE_SEL = 0xE8FFFFFF;
    private static final int CENTER_DOT = 0xF0E0D0E0;
    private static final int TEXT_OFF = 0xFFBB99CC;
    private static final int TEXT_SEL = 0xFFFFFFB0;
    private static final int TEXT_MOD = 0xA0888899;
    private static final int WHEEL_SCALE_MS = 130;

    private HotkeyWheelRadialRenderer() { }

    public static void render(
            DrawContext context,
            HotkeyWheelRadialView view,
            TextRenderer tr,
            int feedbackSliceIndex,
            long feedbackEndMs)
    {
        if (view == null || tr == null || view.n < 1) return;
        int w = view.width;
        int h = view.height;
        int n = view.n;
        int selectedIndex = view.selectedIndex;
        long now = System.currentTimeMillis();
        float innerR = HotkeyWheelRadialLayout.innerR(w, h, n);
        float outerR = HotkeyWheelRadialLayout.outerR(w, h, n);
        float fPulse = 0f;
        if (feedbackSliceIndex >= 0 && now < feedbackEndMs)
        {
            fPulse = (feedbackEndMs - now) / (float) WHEEL_SCALE_MS;
            if (fPulse > 1f) fPulse = 1f;
        }
        float wheelScale = 1f + 0.07f * fPulse;

        int cx = w / 2;
        int cy = h / 2;
        int steps = 10;

        context.fill(0, 0, w, h, 0xD0101820);

        if (fPulse > 0.001f)
        {
            context.getMatrices().push();
            context.getMatrices().translate(cx, cy, 0f);
            context.getMatrices().scale(wheelScale, wheelScale, 1f);
            context.getMatrices().translate(-cx, -cy, 0f);
        }

        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        for (int i = 0; i < n; i++)
        {
            boolean sel = i == selectedIndex && selectedIndex >= 0;
            boolean flash = (feedbackSliceIndex == i) && (now < feedbackEndMs);
            int argb = flash ? SLICE_FLASH : (sel ? SLICE_SEL : SLICE_BASE);
            drawRingWedge(mat, cx, cy, i, n, innerR, outerR, argb, steps);
        }
        for (int i = 0; i < n; i++)
        {
            boolean highlight = (i == selectedIndex && selectedIndex >= 0);
            int borderColor = highlight ? LINE_SEL : LINE_COLOR;
            drawWedgeBorders(mat, cx, cy, i, n, innerR, outerR, borderColor, steps);
        }
        context.fill(cx - 2, cy - 2, cx + 2, cy + 2, CENTER_DOT);

        if (fPulse > 0.001f)
        {
            context.getMatrices().pop();
        }

        boolean iconsOnly = n > 12;
        for (int i = 0; i < n; i++)
        {
            double ang = RadialWheelMath.midAngleRad(i, n);
            float lr = (innerR + outerR) * 0.5f;
            int lx = cx + (int) (Math.cos(ang) * lr);
            int ly = cy + (int) (Math.sin(ang) * lr);
            ItemStack stack = (i < view.iconStacks.size() ? view.iconStacks.get(i) : ItemStack.EMPTY);
            String main = i < view.mainLines.size() ? view.mainLines.get(i) : "";
            String mod = i < view.modTagLines.size() ? view.modTagLines.get(i) : "";
            boolean showIcon = stack.isEmpty() == false;
            if (iconsOnly && showIcon) main = "";
            if (main.isEmpty() == false) main = tr.trimToWidth(main, iconsOnly ? 40 : 88);
            int col = (i == selectedIndex && selectedIndex >= 0) ? TEXT_SEL : TEXT_OFF;
            int th = 9;
            if (showIcon)
            {
                int ix = lx - 8;
                int iy = ly - 8;
                if (main.isEmpty() == false)
                {
                    int tw = tr.getWidth(main);
                    int total = 18 + tw;
                    int startX = lx - total / 2;
                    ix = startX;
                    int tx = startX + 18;
                    context.drawItem(stack, ix, iy);
                    context.drawTextWithShadow(tr, main, tx, ly - 4, col);
                }
                else
                {
                    context.drawItem(stack, ix, iy);
                }
            }
            else
            {
                if (main.isEmpty() == false)
                {
                    int tw = tr.getWidth(main);
                    int bg = 0xAA260040;
                    int padX = 3;
                    int py = 2;
                    int bx0 = lx - tw / 2 - padX;
                    int by0 = ly - th / 2 - py;
                    int bx1 = bx0 + tw + padX * 2;
                    int by1 = by0 + th + py * 2;
                    if (mod.isEmpty()) context.fill(bx0, by0, bx1, by1, bg);
                }
                if (main.isEmpty() == false) context.drawTextWithShadow(tr, main, lx - tr.getWidth(main) / 2, ly - 4, col);
            }
            if (mod.isEmpty() == false)
            {
                int yMod = showIcon ? (ly + 7) : (ly + 4);
                int mw = tr.getWidth(mod);
                context.drawTextWithShadow(tr, mod, lx - mw / 2, yMod, TEXT_MOD);
            }
        }

        if (selectedIndex >= 0 && selectedIndex < view.fullLabelLines.size())
        {
            String full = view.fullLabelLines.get(selectedIndex);
            if (full == null) full = "";
            String preview = (selectedIndex < view.previewLines.size() ? view.previewLines.get(selectedIndex) : "");
            List<Text> tool = new ArrayList<>();
            if (full.isEmpty() == false) tool.add(Text.literal(full));
            if (preview != null && preview.isEmpty() == false)
            {
                tool.add(Text.literal(preview).formatted(Formatting.GRAY));
            }
            if (tool.isEmpty() == false) context.drawTooltip(tr, tool, Optional.empty(), view.mouseX, view.mouseY);
        }
    }

    private static void drawRingWedge(
            Matrix4f m,
            int cx, int cy,
            int i, int n,
            float innerR, float outerR,
            int argb,
            int arcSteps)
    {
        double a0 = RadialWheelMath.startAngleRad(i, n);
        double a1 = RadialWheelMath.endAngleRad(i, n);
        for (int s = 0; s < arcSteps; s++)
        {
            double t0 = a0 + (a1 - a0) * s / arcSteps;
            double t1 = a0 + (a1 - a0) * (s + 1) / arcSteps;
            float i0x = cx + (float) (Math.cos(t0) * innerR);
            float i0y = cy + (float) (Math.sin(t0) * innerR);
            float i1x = cx + (float) (Math.cos(t1) * innerR);
            float i1y = cy + (float) (Math.sin(t1) * innerR);
            float o0x = cx + (float) (Math.cos(t0) * outerR);
            float o0y = cy + (float) (Math.sin(t0) * outerR);
            float o1x = cx + (float) (Math.cos(t1) * outerR);
            float o1y = cy + (float) (Math.sin(t1) * outerR);
            drawQuadColor(m, i0x, i0y, o0x, o0y, o1x, o1y, i1x, i1y, argb);
        }
    }

    private static void drawWedgeBorders(
            Matrix4f m,
            int cx, int cy,
            int i, int n,
            float innerR, float outerR,
            int argb,
            int arcSteps)
    {
        double a0 = RadialWheelMath.startAngleRad(i, n);
        double a1 = RadialWheelMath.endAngleRad(i, n);
        for (int s = 0; s < arcSteps; s++)
        {
            double t0 = a0 + (a1 - a0) * s / arcSteps;
            double t1 = a0 + (a1 - a0) * (s + 1) / arcSteps;
            float o0x = cx + (float) (Math.cos(t0) * outerR);
            float o0y = cy + (float) (Math.sin(t0) * outerR);
            float o1x = cx + (float) (Math.cos(t1) * outerR);
            float o1y = cy + (float) (Math.sin(t1) * outerR);
            addLine2d(m, o0x, o0y, o1x, o1y, argb);
        }
        float oSX = cx + (float) (Math.cos(a0) * outerR);
        float oSY = cy + (float) (Math.sin(a0) * outerR);
        float oEX = cx + (float) (Math.cos(a1) * outerR);
        float oEY = cy + (float) (Math.sin(a1) * outerR);
        float iSX = cx + (float) (Math.cos(a0) * innerR);
        float iSY = cy + (float) (Math.sin(a0) * innerR);
        float iEX = cx + (float) (Math.cos(a1) * innerR);
        float iEY = cy + (float) (Math.sin(a1) * innerR);
        addLine2d(m, iSX, iSY, oSX, oSY, argb);
        addLine2d(m, iEX, iEY, oEX, oEY, argb);
    }

    private static void addLine2d(Matrix4f m, float x0, float y0, float x1, float y1, int argb)
    {
        float a = (argb >> 24 & 255) / 255f;
        float r = (argb >> 16 & 255) / 255f;
        float g = (argb >> 8 & 255) / 255f;
        float b = (argb & 255) / 255f;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        buffer.vertex(m, x0, y0, 0f).color(r, g, b, a).next();
        buffer.vertex(m, x1, y1, 0f).color(r, g, b, a).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawQuadColor(Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb)
    {
        float a = (argb >> 24 & 255) / 255f;
        float r = (argb >> 16 & 255) / 255f;
        float g = (argb >> 8 & 255) / 255f;
        float b = (argb & 255) / 255f;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(m, x0, y0, 0f).color(r, g, b, a).next();
        buffer.vertex(m, x1, y1, 0f).color(r, g, b, a).next();
        buffer.vertex(m, x2, y2, 0f).color(r, g, b, a).next();
        buffer.vertex(m, x3, y3, 0f).color(r, g, b, a).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
