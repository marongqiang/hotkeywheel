package fi.dy.masa.hotkeywheel.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * Renders a radial (pie) wheel: ring segments, borders, item icons, short labels, mod source line, tooltip.
 */
public final class HotkeyWheelRadialRenderer
{
    /** Amber glass (idle slice). Keep it visible over dark vignette. */
    private static final int SLICE_BASE = 0xD0B07025;
    /** Magenta / rose — full wedge tint when selected (solid, no radial banding). */
    private static final int SLICE_SEL_SCALE = 0xF0E858C0;
    /** Outer ring plate color (alpha scaled by config). */
    private static final int OUTER_PLATE_RGB = 0x00110E0C;
    /** Inner ring plate color (alpha scaled by config; slightly lighter). */
    private static final int INNER_PLATE_RGB = 0x001A1612;
    /** Gap ring fill (hard separation). */
    private static final int GAP_PLATE = 0xE0000000;
    /** Bright flash on confirm. */
    private static final int SLICE_FLASH = 0xFFFFFFFF;
    private static final int LINE_COLOR = 0xE8C89850;
    private static final int LINE_DIV = 0xC8FFFFFF;
    private static final int LINE_SEL = 0xF0FFFFE8;
    private static final int LINE_SEL_BOLD = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFFFFF0D0;
    private static final int TEXT_SEL = 0xFFFFFFFF;
    private static final int HOVER_SCALE_MS = 150;
    private static final int LABEL_FADE_MS = 150;
    private static final int TOOLTIP_DELAY_MS = 300;
    private static final float SELECT_INNER = 0.95f;
    private static final float SELECT_OUTER = 1.06f;

    private HotkeyWheelRadialRenderer() { }

    private static String MOD_VERSION;
    private static String modVersion()
    {
        if (MOD_VERSION != null) return MOD_VERSION;
        MOD_VERSION = FabricLoader.getInstance()
                .getModContainer("hotkeywheel")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        return MOD_VERSION;
    }

    public static void render(
            DrawContext context,
            HotkeyWheelRadialView view,
            TextRenderer tr,
            int feedbackSliceIndex,
            long feedbackEndMs)
    {
        if (view == null || tr == null || view.n < 1) return;
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try
        {
            doRender(context, view, tr, feedbackSliceIndex, feedbackEndMs);
        }
        finally
        {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
        }
    }

    private static void doRender(
            DrawContext context,
            HotkeyWheelRadialView view,
            TextRenderer tr,
            int feedbackSliceIndex,
            long feedbackEndMs)
    {
        boolean previewUi = MinecraftClient.getInstance().currentScreen instanceof WheelUiPreviewScreen;
        // Per spec: MC dark frosted wheel style (preview uses the same style).
        boolean cleanLight = false;
        int w = view.width;
        int h = view.height;
        int n = view.n;
        int selectedIndex = view.selectedIndex;
        long now = System.currentTimeMillis();
        float innerR = HotkeyWheelRadialLayout.cancelR(w, h);
        float outerR = HotkeyWheelRadialLayout.outerR(w, h);
        float innerOutR = view.dualRing ? HotkeyWheelRadialLayout.innerRingOuterR(w, h) : outerR;
        float outerInR = view.dualRing ? HotkeyWheelRadialLayout.outerRingInnerR(w, h) : innerR;
        float innerRingAlpha = fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelInnerRingAlpha();
        float outerRingAlpha = fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelOuterRingAlpha();
        boolean showDividers = fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelShowDividers();
        if (previewUi)
        {
            // Preview must show structure regardless of user config.
            showDividers = true;
            innerRingAlpha = Math.max(innerRingAlpha, 0.90f);
            outerRingAlpha = Math.max(outerRingAlpha, 0.92f);
            // Strict ratios; no config-based gap.
        }

        int cx = w / 2;
        int cy = h / 2;
        // Reduce per-frame geometry a bit for smoother pointer/drag feel.
        int steps = 8;

        /* Dark frosted backdrop (low saturation). */
        context.fill(0, 0, w, h, 0x90101010);

        if (previewUi && fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
        {
            context.drawTextWithShadow(tr, "HotkeyWheel v" + modVersion(), 4, 4, 0xFFB0B0B0);
        }

        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        boolean flashActive = feedbackSliceIndex >= 0 && now < feedbackEndMs;
        boolean flashOn = false;
        if (flashActive)
        {
            long dt = now - (feedbackEndMs - 120L);
            long phase = (dt < 0L ? 0L : dt) % 120L;
            flashOn = phase < 60L;
        }

        float hoverT = hoverEaseT(view.stableHoverOnSelectionMs);
        /* 1) Ring plates (independent colors) so layers never visually mix. */
        List<Quad> quads = new ArrayList<>(n * steps * 2);
        for (int i = 0; i < n; i++)
        {
            float in = innerR;
            float out = outerR;
            int seg = i;
            int segN = n;
            double angOff = 0.0;
            if (view.dualRing)
            {
                int inCount = view.innerRingCount;
                if (i < inCount)
                {
                    segN = inCount;
                    seg = i;
                    in = innerR;
                    out = innerOutR;
                }
                else
                {
                    segN = view.outerRingCount;
                    seg = i - inCount;
                    in = outerInR;
                    out = outerR;
                    // Stagger outer ring by half a sector (clockwise).
                    if (segN > 0) angOff = -Math.PI / (double) segN;
                }
            }
            int plate = view.dualRing
                    ? (i < view.innerRingCount ? withAlpha(INNER_PLATE_RGB, innerRingAlpha) : withAlpha(OUTER_PLATE_RGB, outerRingAlpha))
                    : withAlpha(OUTER_PLATE_RGB, outerRingAlpha);
            if (view.selectedRing == 0 && i < view.innerRingCount) plate = mixTowardWhite(plate, 0.06f);
            if (view.selectedRing == 1 && i >= view.innerRingCount) plate = mixTowardWhite(plate, 0.06f);
            collectRingWedgeQuads(cx, cy, seg, segN, in, out, plate, steps, quads, false, angOff);
        }
        // legacy callsites
        // Gap between rings: keep it readable but not a black "hole".
        if (view.dualRing)
        {
            float gi = innerOutR;
            float go = outerInR;
            if (go > gi + 0.75f)
            {
                collectRingWedgeQuads(cx, cy, 0, 1, gi, go, 0x40101010, 48, quads, false, 0.0);
            }
        }
        /* 2) Colored slice overlay (only for hover/selected/flash). */
        for (int i = 0; i < n; i++)
        {
            boolean sel = i == selectedIndex && selectedIndex >= 0;
            boolean flash = flashActive && (feedbackSliceIndex == i) && flashOn;
            int base;
            if (previewUi)
            {
                base = sel ? mixTowardWhite(SLICE_SEL_SCALE, 0.25f) : 0x00000000;
            }
            else
            {
                // Default sector is blank; only selected highlight is drawn.
                base = sel ? mixTowardWhite(SLICE_SEL_SCALE, 0.20f) : 0x00000000;
            }
            int argb = flash ? SLICE_FLASH : base;
            float in = innerR;
            float out = outerR;
            int seg = i;
            int segN = n;
            double angOff = 0.0;
            if (view.dualRing)
            {
                int inCount = view.innerRingCount;
                if (i < inCount)
                {
                    segN = inCount;
                    seg = i;
                    in = innerR;
                    out = innerOutR;
                }
                else
                {
                    segN = view.outerRingCount;
                    seg = i - inCount;
                    in = outerInR;
                    out = outerR;
                    if (segN > 0) angOff = -Math.PI / (double) segN;
                }
            }
            if (sel)
            {
                float inTarget = cleanLight ? 0.92f : SELECT_INNER;
                float outTarget = cleanLight ? 1.10f : SELECT_OUTER;
                float sIn = lerp(1f, inTarget, hoverT);
                float sOut = lerp(1f, outTarget, hoverT);
                in = in * sIn;
                out = out * sOut;
            }
            boolean grad = previewUi == false && (sel == false) && (flash == false);
            collectRingWedgeQuads(cx, cy, seg, segN, in, out, argb, steps, quads, grad, angOff);
        }
        flushQuads(mat, quads);
        quads.clear();
        /* 3) Dividers: outer arc (gold) + radial boundaries (brighter) + optional mid ring line (dual). */
        List<Line> borderLines = new ArrayList<>(n * (steps + 8) + 128);
        if (showDividers)
        {
            for (int i = 0; i < n; i++)
            {
                boolean highlight = (i == selectedIndex && selectedIndex >= 0);
                int c = cleanLight ? (highlight ? 0xFF000000 : 0xFF202020) : (highlight ? LINE_SEL : LINE_COLOR);
                // More readable per-slice separators (radial lines)
                int cRad = cleanLight ? (highlight ? 0xFF000000 : 0xFFD0D0D0) : (highlight ? LINE_SEL_BOLD : 0xE0FFFFFF);
                float in = innerR;
                float out = outerR;
                int seg = i;
                int segN = n;
                double angOff = 0.0;
                if (view.dualRing)
                {
                    int inCount = view.innerRingCount;
                    if (i < inCount)
                    {
                        segN = inCount;
                        seg = i;
                        in = innerR;
                        out = innerOutR;
                    }
                    else
                    {
                        segN = view.outerRingCount;
                        seg = i - inCount;
                        in = outerInR;
                        out = outerR;
                        if (segN > 0) angOff = -Math.PI / (double) segN;
                    }
                }
                collectWedgeBorderLines(cx, cy, seg, segN, in, out, c, cRad, steps, borderLines, angOff);
                if (highlight)
                {
                    int bold = cleanLight ? 0xFF000000 : LINE_SEL_BOLD;
                    collectWedgeBorderLines(cx, cy, seg, segN, in * 0.965f, out * 1.05f, bold, bold, steps, borderLines, angOff);
                }
            }
            if (view.dualRing)
            {
                // Per user feedback: don't show two close rings in the gap; draw a single separator line.
                float sep = (innerOutR + outerInR) * 0.5f;
                collectCircleLineLoop(cx, cy, sep, 96, 0x50D0D0D0, borderLines);
            }
            collectCircleLineLoop(cx, cy, outerR, 96, 0x60D0D0D0, borderLines);
            collectCircleLineLoop(cx, cy, innerR, 72, 0x60D0D0D0, borderLines);
        }
        flushLines(mat, borderLines);
        // Center cancel zone only; no center text/icons.
        drawTacticalHub(context, tr, cx, cy, innerR, view.mouseInDeadZone, now, false, cleanLight);

        for (int i = 0; i < n; i++)
        {
            int seg = i;
            int segN = n;
            float in = innerR;
            float out = outerR;
            double angOff = 0.0;
            if (view.dualRing)
            {
                int inCount = view.innerRingCount;
                if (i < inCount)
                {
                    segN = inCount;
                    seg = i;
                    in = innerR;
                    out = innerOutR;
                }
                else
                {
                    segN = view.outerRingCount;
                    seg = i - inCount;
                    in = outerInR;
                    out = outerR;
                    if (segN > 0) angOff = -Math.PI / (double) segN;
                }
            }
            double ang = RadialWheelMath.midAngleRad(seg, segN) + angOff;
            float lr = (in + out) * 0.5f;
            int lx = cx + (int) (Math.cos(ang) * lr);
            int ly = cy + (int) (Math.sin(ang) * lr);
            ItemStack stack = (i < view.iconStacks.size() ? view.iconStacks.get(i) : ItemStack.EMPTY);
            Identifier iconTex = (i < view.iconTextures.size() ? view.iconTextures.get(i) : null);
            String main = i < view.mainLines.size() ? view.mainLines.get(i) : "";
            String mod = i < view.modTagLines.size() ? view.modTagLines.get(i) : "";
            boolean showIcon = stack.isEmpty() == false;
            boolean hover = (i == selectedIndex && selectedIndex >= 0);
            int col = hover ? TEXT_SEL : TEXT_OFF;
            float baseScale = (view.dualRing && i < view.innerRingCount)
                    ? fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelIconScaleInner()
                    : fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelIconScaleOuter();
            float iconScale = hover ? (baseScale * (1.0f + 0.08f * hoverT)) : baseScale;
            if (showIcon || iconTex != null)
            {
                drawIcon(context, lx, ly, stack, iconTex, iconScale, hover);
            }
            else
            {
                if (hover == false)
                {
                    int dot = 3;
                    context.fill(lx - dot, ly - dot, lx + dot + 1, ly + dot + 1, mixTowardWhite(SLICE_BASE, 0.10f));
                }
            }

            // Debug markers (preview only, when enabled): show mouse + computed icon center.
            if (previewUi && fi.dy.masa.hotkeywheel.config.HotkeyWheelConfigStore.INSTANCE.wheelDebugLogging())
            {
                if (hover)
                {
                    // Mouse marker (relative to wheel)
                    int mx = view.mouseX;
                    int my = view.mouseY;
                    context.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF40FF40);

                    // Icon center marker is often covered by the item icon itself; render it offset + bigger.
                    int tx = lx + 10;
                    int ty = ly - 10;
                    context.fill(tx - 2, ty - 2, tx + 3, ty + 3, 0xFFFF4040);
                    // a small cross
                    context.fill(tx - 6, ty, tx + 7, ty + 1, 0xFFFF4040);
                    context.fill(tx, ty - 6, tx + 1, ty + 7, 0xFFFF4040);
                    // and a line from mouse to target
                    Matrix4f dm = context.getMatrices().peek().getPositionMatrix();
                    List<Line> dbg = new ArrayList<>(1);
                    dbg.add(new Line(mx, my, tx, ty, 0xA0FF4040));
                    flushLines(dm, dbg);
                }
            }

            if (previewUi == false && hover && main.isEmpty() == false)
            {
                float a = clamp01(view.stableHoverOnSelectionMs / (float) LABEL_FADE_MS);
                String s = main;
                if (tr.getWidth(s) > 120) s = tr.trimToWidth(s, 120) + "…";
                double angMid = RadialWheelMath.midAngleRad(seg, segN) + angOff;
                float labelR = out * 1.15f;
                int tx = cx + (int) (Math.cos(angMid) * labelR);
                int ty = cy + (int) (Math.sin(angMid) * labelR);
                int tw = tr.getWidth(s);
                int argb = ((int) (a * 255f) << 24) | (col & 0x00FFFFFF);
                int bg = ((int) (a * 170f) << 24) | 0x00101010;
                int padX = 4;
                int padY = 2;
                if (mod != null && mod.isEmpty() == false)
                {
                    context.fill(tx - tw / 2 - padX, ty - 6 - padY, tx + tw / 2 + padX, ty + 6 + padY, bg);
                }
                context.drawTextWithShadow(tr, s, tx - tw / 2, ty - 4, argb);
            }
        }

        if (previewUi
                && selectedIndex >= 0
                && selectedIndex < view.mainLines.size()
                && view.mouseInDeadZone == false)
        {
            String s = view.mainLines.get(selectedIndex);
            if (s != null && s.isEmpty() == false)
            {
                String t = tr.trimToWidth(s, 160);
                if (t.length() < s.length()) t = t + "…";
                int tw = tr.getWidth(t);
                int x = Math.min(w - tw - 14, Math.max(6, view.mouseX + 12));
                int y = Math.min(h - 14, Math.max(6, view.mouseY + 12));
                context.fill(x - 6, y - 4, x + tw + 6, y + 10, 0xD0000000);
                context.drawTextWithShadow(tr, t, x, y, 0xFFFFFFFF);
            }
        }
        else
        {
            // Center text: hovered action name wins; otherwise title/cancel hint.
            if (selectedIndex >= 0 && selectedIndex < view.mainLines.size() && view.mouseInDeadZone == false)
            {
                String s = view.mainLines.get(selectedIndex);
                if (s != null && s.isEmpty() == false)
                {
                    String t = tr.trimToWidth(s, 140);
                    if (t.length() < s.length()) t = t + "…";
                    int tw = tr.getWidth(t);
                    int bx0 = cx - tw / 2 - 6;
                    int by0 = cy - 18;
                    int bx1 = cx + tw / 2 + 6;
                    int by1 = cy - 4;
                    context.fill(bx0, by0, bx1, by1, 0xB0000000);
                    context.drawTextWithShadow(tr, t, cx - tw / 2, cy - 16, 0xFFE8E8E8);
                }
            }
        }

        if (previewUi == false
                && view.stableHoverOnSelectionMs >= TOOLTIP_DELAY_MS
                && selectedIndex >= 0
                && selectedIndex < view.fullLabelLines.size()
                && (view.mouseInDeadZone == false))
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
            ItemStack hint = selectedIndex < view.iconStacks.size() ? view.iconStacks.get(selectedIndex) : ItemStack.EMPTY;
            if (hint.isEmpty() == false)
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null)
                {
                    TooltipContext tctx = mc.options.advancedItemTooltips
                            ? TooltipContext.Default.ADVANCED
                            : TooltipContext.Default.BASIC;
                    List<Text> fromItem = hint.getTooltip(mc.player, tctx);
                    int start = full.isEmpty() ? 0 : 1;
                    for (int i = start; i < fromItem.size(); i++)
                    {
                        tool.add(fromItem.get(i));
                    }
                }
            }
            if (tool.isEmpty() == false) context.drawTooltip(tr, tool, Optional.empty(), view.mouseX, view.mouseY);
        }
    }

    /** Center hub: title + optional cancel hint when pointer is in the dead zone. */
    private static void drawTacticalHub(
            DrawContext context,
            TextRenderer tr,
            int cx, int cy,
            float innerR,
            boolean inDead,
            long now)
    {
        drawTacticalHub(context, tr, cx, cy, innerR, inDead, now, true, false);
    }

    private static void drawTacticalHub(
            DrawContext context,
            TextRenderer tr,
            int cx, int cy,
            float innerR,
            boolean inDead,
            long now,
            boolean drawText,
            boolean cleanLight)
    {
        int hr = Math.max(10, (int) (innerR * (drawText ? 0.55f : (cleanLight ? 0.20f : 0.35f))));
        int base;
        if (cleanLight)
        {
            base = inDead ? 0xB0FFD8CC : 0x90FFFFFF;
        }
        else
        {
            base = inDead ? 0xE0A04030 : 0xE0180E0C;
        }
        float scale = 1.0f;
        if (inDead)
        {
            scale = 1.0f + 0.05f * (float) (0.5 + 0.5 * Math.sin(now * 0.012));
        }
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-cx, -cy, 0f);
        Matrix4f m = context.getMatrices().peek().getPositionMatrix();
        fillCircle(m, cx, cy, hr, base, 32);
        context.getMatrices().pop();
        if (drawText == false) return;
        String title = I18n.translate("hotkeywheel.ui.wheel_title");
        int tw = tr.getWidth(title);
        context.drawTextWithShadow(tr, title, cx - tw / 2, cy - 5, 0xFFFFF0D0);
        if (inDead)
        {
            String h = I18n.translate("hotkeywheel.ui.hub_cancel");
            int hw = tr.getWidth(h);
            context.drawTextWithShadow(tr, h, cx - hw / 2, cy + 6, 0xFFFFB0B0);
        }
    }

    private record Quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) { }
    private record Line(float x0, float y0, float x1, float y1, int argb) { }

    private static void collectRingWedgeQuads(
            int cx, int cy,
            int i, int n,
            float innerR, float outerR,
            int argb,
            int arcSteps,
            List<Quad> out,
            boolean radialGradient,
            double angleOffset)
    {
        if (n <= 0 || arcSteps <= 0) return;
        double a0 = RadialWheelMath.startAngleRad(i, n) + angleOffset;
        double a1 = RadialWheelMath.endAngleRad(i, n) + angleOffset;
        int cInner = radialGradient ? brighten(argb, -0.10f) : argb;
        int cOuter = radialGradient ? brighten(argb, 0.15f) : argb;
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
            out.add(new Quad(
                    i0x, i0y, cInner,
                    o0x, o0y, cOuter,
                    o1x, o1y, cOuter,
                    i1x, i1y, cInner));
        }
    }

    private static void collectWedgeBorderLines(
            int cx, int cy,
            int i, int n,
            float innerR, float outerR,
            int colorArc,
            int colorRadial,
            int arcSteps,
            List<Line> out,
            double angleOffset)
    {
        if (n <= 0 || arcSteps <= 0) return;
        double a0 = RadialWheelMath.startAngleRad(i, n) + angleOffset;
        double a1 = RadialWheelMath.endAngleRad(i, n) + angleOffset;
        for (int s = 0; s < arcSteps; s++)
        {
            double t0 = a0 + (a1 - a0) * s / arcSteps;
            double t1 = a0 + (a1 - a0) * (s + 1) / arcSteps;
            float o0x = cx + (float) (Math.cos(t0) * outerR);
            float o0y = cy + (float) (Math.sin(t0) * outerR);
            float o1x = cx + (float) (Math.cos(t1) * outerR);
            float o1y = cy + (float) (Math.sin(t1) * outerR);
            out.add(new Line(o0x, o0y, o1x, o1y, colorArc));
            // inner arc: subtle, helps read slice separation on dual rings
            int innerArc = (colorArc & 0x00FFFFFF) | 0x60000000;
            float i0x = cx + (float) (Math.cos(t0) * innerR);
            float i0y = cy + (float) (Math.sin(t0) * innerR);
            float i1x = cx + (float) (Math.cos(t1) * innerR);
            float i1y = cy + (float) (Math.sin(t1) * innerR);
            out.add(new Line(i0x, i0y, i1x, i1y, innerArc));
        }
        float oSX = cx + (float) (Math.cos(a0) * outerR);
        float oSY = cy + (float) (Math.sin(a0) * outerR);
        float oEX = cx + (float) (Math.cos(a1) * outerR);
        float oEY = cy + (float) (Math.sin(a1) * outerR);
        float iSX = cx + (float) (Math.cos(a0) * innerR);
        float iSY = cy + (float) (Math.sin(a0) * innerR);
        float iEX = cx + (float) (Math.cos(a1) * innerR);
        float iEY = cy + (float) (Math.sin(a1) * innerR);
        out.add(new Line(iSX, iSY, oSX, oSY, colorRadial));
        out.add(new Line(iEX, iEY, oEX, oEY, colorRadial));
        // pseudo-thickness: two slight angle offsets to make separators visible on all GUI scales
        double eps = 0.0035;
        float oSX2 = cx + (float) (Math.cos(a0 + eps) * outerR);
        float oSY2 = cy + (float) (Math.sin(a0 + eps) * outerR);
        float iSX2 = cx + (float) (Math.cos(a0 + eps) * innerR);
        float iSY2 = cy + (float) (Math.sin(a0 + eps) * innerR);
        float oEX2 = cx + (float) (Math.cos(a1 - eps) * outerR);
        float oEY2 = cy + (float) (Math.sin(a1 - eps) * outerR);
        float iEX2 = cx + (float) (Math.cos(a1 - eps) * innerR);
        float iEY2 = cy + (float) (Math.sin(a1 - eps) * innerR);
        out.add(new Line(iSX2, iSY2, oSX2, oSY2, colorRadial));
        out.add(new Line(iEX2, iEY2, oEX2, oEY2, colorRadial));
    }

    private static void collectCircleLineLoop(int cx, int cy, float r, int segs, int argb, List<Line> out)
    {
        if (r < 1f || segs < 3) return;
        double a0 = 0.0;
        for (int s = 0; s < segs; s++)
        {
            double a1 = 2.0 * Math.PI * (s + 1) / segs;
            float x0 = cx + (float) (Math.cos(a0) * r);
            float y0 = cy + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r);
            float y1 = cy + (float) (Math.sin(a1) * r);
            out.add(new Line(x0, y0, x1, y1, argb));
            a0 = a1;
        }
    }

    private static void flushQuads(Matrix4f m, List<Quad> quads)
    {
        if (quads.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Quad q : quads)
        {
            putVertex(buffer, m, q.x0, q.y0, q.c0);
            putVertex(buffer, m, q.x1, q.y1, q.c1);
            putVertex(buffer, m, q.x2, q.y2, q.c2);
            putVertex(buffer, m, q.x3, q.y3, q.c3);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void flushLines(Matrix4f m, List<Line> lines)
    {
        if (lines.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        for (Line ln : lines)
        {
            putVertex(buffer, m, ln.x0, ln.y0, ln.argb);
            putVertex(buffer, m, ln.x1, ln.y1, ln.argb);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void putVertex(BufferBuilder buffer, Matrix4f m, float x, float y, int argb)
    {
        float a = (argb >> 24 & 255) / 255f;
        float r = (argb >> 16 & 255) / 255f;
        float g = (argb >> 8 & 255) / 255f;
        float b = (argb & 255) / 255f;
        buffer.vertex(m, x, y, 0f).color(r, g, b, a).next();
    }

    private static void fillCircle(Matrix4f m, int cx, int cy, int r, int argb, int segs)
    {
        if (r < 1 || segs < 3) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segs; i++)
        {
            double a0 = 2.0 * Math.PI * i / segs;
            double a1 = 2.0 * Math.PI * (i + 1) / segs;
            float x0 = cx + (float) (Math.cos(a0) * r);
            float y0 = cy + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r);
            float y1 = cy + (float) (Math.sin(a1) * r);
            putVertex(buffer, m, cx, cy, argb);
            putVertex(buffer, m, x0, y0, argb);
            putVertex(buffer, m, x1, y1, argb);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static float hoverEaseT(long stableHoverMs)
    {
        if (stableHoverMs <= 0L) return 0f;
        float t = clamp01(stableHoverMs / (float) HOVER_SCALE_MS);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v)
    {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float lerp(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    private static int mixTowardWhite(int argb, float t)
    {
        t = clamp01(t);
        int a = (argb >> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int argb, float delta)
    {
        int a = (argb >> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        if (delta >= 0f)
        {
            float t = clamp01(delta);
            r = (int) (r + (255 - r) * t);
            g = (int) (g + (255 - g) * t);
            b = (int) (b + (255 - b) * t);
        }
        else
        {
            float t = clamp01(-delta);
            r = (int) (r * (1f - t));
            g = (int) (g * (1f - t));
            b = (int) (b * (1f - t));
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, float a)
    {
        int alpha = (int) (clamp01(a) * 255f);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static void drawIcon(DrawContext context, int cx, int cy, ItemStack stack, Identifier tex, float scale, boolean hover)
    {
        int sz = Math.max(10, (int) (16f * scale));
        int x0 = cx - sz / 2;
        int y0 = cy - sz / 2;
        int x1 = x0 + sz;
        int y1 = y0 + sz;
        // subtle shadow plate
        context.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0x40000000);
        if (hover)
        {
            context.fill(x0 - 2, y0 - 2, x1 + 2, y0 - 1, 0x90FFFFFF);
            context.fill(x0 - 2, y1 + 1, x1 + 2, y1 + 2, 0x90FFFFFF);
            context.fill(x0 - 2, y0 - 1, x0 - 1, y1 + 1, 0x90FFFFFF);
            context.fill(x1 + 1, y0 - 1, x1 + 2, y1 + 1, 0x90FFFFFF);
        }
        if (stack != null && stack.isEmpty() == false)
        {
            context.getMatrices().push();
            context.getMatrices().translate(cx, cy, 0f);
            context.getMatrices().scale(scale, scale, 1f);
            context.getMatrices().translate(-cx, -cy, 0f);
            context.drawItem(stack, cx - 8, cy - 8);
            context.getMatrices().pop();
        }
        else if (tex != null)
        {
            context.getMatrices().push();
            context.getMatrices().translate(cx, cy, 0f);
            context.getMatrices().scale(scale, scale, 1f);
            context.getMatrices().translate(-cx, -cy, 0f);
            // lightweight outline: 4 offset dark passes
            context.drawTexture(tex, cx - 9, cy - 8, 0, 0, 16, 16, 16, 16);
            context.drawTexture(tex, cx - 7, cy - 8, 0, 0, 16, 16, 16, 16);
            context.drawTexture(tex, cx - 8, cy - 9, 0, 0, 16, 16, 16, 16);
            context.drawTexture(tex, cx - 8, cy - 7, 0, 0, 16, 16, 16, 16);
            context.drawTexture(tex, cx - 8, cy - 8, 0, 0, 16, 16, 16, 16);
            context.getMatrices().pop();
        }
    }
}
