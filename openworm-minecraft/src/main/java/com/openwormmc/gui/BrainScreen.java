package com.openwormmc.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Brain viewer (key K). Shows REAL data received from the server snapshot: membrane potential of all 302 neurons
 * as a 20x16 grid (colour = potential), counts, per-segment dorsal/ventral muscle activation, sensory channels.
 * Hover a cell for neuron name and potential. Cells are drawn once per frame (302 rects); connections are NOT drawn per frame.
 * UNCOMPILED in the build environment.
 */
public class BrainScreen extends Screen {
    public static volatile ClientSnapshot SNAP;
    public static final class ClientSnapshot {
        public double[] v; public float[] dorsal, ventral, stimulus; public int active, connections; public double timeMs, hz, uptime;
    }
    private final String[] names;
    public BrainScreen(String[] neuronNames) { super(Text.literal("C. elegans brain (c302 C1-style)")); this.names = neuronNames; }

    static int color(double mv) { // -65..+20 mV : dark blue -> cyan -> yellow -> red
        double t = Math.max(0, Math.min(1, (mv + 65) / 85.0)); int r = (int) (255 * Math.min(1, t * 2)), g = (int) (255 * (t < 0.5 ? t * 2 : 2 - 2 * t)), b = (int) (255 * Math.max(0, 1 - t * 2));
        return 0xFF000000 | (r << 16) | (g << 8) | Math.max(b, 60);
    }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx); ClientSnapshot s = SNAP;
        ctx.drawText(textRenderer, title, 10, 8, 0xFFFFFF, true);
        if (s == null || s.v == null) { ctx.drawText(textRenderer, "No worm within 48 blocks", 10, 24, 0xFFAAAA, false); super.render(ctx, mx, my, delta); return; }
        int cols = 20, cell = 12, x0 = 10, y0 = 24; String hover = null;
        for (int i = 0; i < s.v.length; i++) { int x = x0 + (i % cols) * cell, y = y0 + (i / cols) * cell; ctx.fill(x, y, x + cell - 1, y + cell - 1, color(s.v[i]));
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) hover = names[i] + "  " + String.format("%.1f mV", s.v[i]); }
        int ix = x0 + cols * cell + 20, y = 24;
        String[] info = { "neurons: " + s.v.length, "connections (chem+NMJ+gap): " + s.connections, "active (> -30 mV): " + s.active,
            String.format("sim time: %.0f ms   update: %.1f Hz", s.timeMs, s.hz), String.format("uptime: %.0f s", s.uptime),
            "sensory input HEAD/TAIL/BODY/ODOR L/R/FOOD:" };
        for (String l : info) { ctx.drawText(textRenderer, l, ix, y, 0xFFFFFF, false); y += 11; }
        StringBuilder st = new StringBuilder(); for (float f : s.stimulus) st.append(String.format("%.1f ", f)); ctx.drawText(textRenderer, st.toString(), ix, y, 0xFFFF88, false); y += 16;
        ctx.drawText(textRenderer, "body (head left): dorsal (top) / ventral (bottom) muscle activation", ix, y, 0xFFFFFF, false); y += 12;
        for (int i = 0; i < s.dorsal.length; i++) { int x = ix + i * 22; int hd = (int) (s.dorsal[i] * 30), hv = (int) (s.ventral[i] * 30);
            ctx.fill(x, y + 30 - hd, x + 18, y + 30, 0xFFFF9944); ctx.fill(x, y + 34, x + 18, y + 34 + hv, 0xFF44AAFF); }
        if (hover != null) ctx.drawTooltip(textRenderer, Text.literal(hover), mx, my);
        super.render(ctx, mx, my, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
