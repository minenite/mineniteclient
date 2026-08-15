package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Blood-loss vignette + heartbeat pulse from WarzPlugin medical. */
public final class BloodOverlay {
    private static float severity;
    private static float pulse;

    private BloodOverlay() {
    }

    public static void accept(float sev, boolean beat) {
        severity = Mth.clamp(sev, 0f, 1f);
        if (beat) {
            pulse = Math.max(pulse, 0.55f + severity * 0.35f);
        }
    }

    public static void tick() {
        if (pulse > 0f) {
            pulse = Math.max(0f, pulse - 0.08f);
        }
    }

    public static void clear() {
        severity = 0f;
        pulse = 0f;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (severity <= 0.01f && pulse <= 0.01f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float v = Mth.clamp(severity, 0f, 1f);
        int rings = 2 + (int) (v * 4f);
        int edge = Math.max(14, (int) (Math.min(w, h) * (0.10f + v * 0.28f)));
        for (int i = 0; i < rings; i++) {
            float t = (i + 1f) / rings;
            int a = Mth.clamp((int) ((28 + v * 110) * t), 10, 180);
            int inset = (int) (edge * t);
            int r = Mth.clamp((int) (40 + v * 90), 20, 140);
            graphics.fill(0, 0, w, inset, ARGB.color(a, r, 0, 0));
            graphics.fill(0, h - inset, w, h, ARGB.color(a, r, 0, 0));
            graphics.fill(0, inset, inset, h - inset, ARGB.color(a, r, 0, 0));
            graphics.fill(w - inset, inset, w, h - inset, ARGB.color(a, r, 0, 0));
        }
        if (v >= 0.45f) {
            int aCenter = Mth.clamp((int) ((v - 0.45f) / 0.55f * 140), 0, 160);
            graphics.fill(0, 0, w, h, ARGB.color(aCenter, 8, 0, 0));
        }
        if (v >= 0.85f) {
            int aBlack = Mth.clamp((int) ((v - 0.85f) / 0.15f * 200), 40, 220);
            graphics.fill(0, 0, w, h, ARGB.color(aBlack, 0, 0, 0));
        }
        if (pulse > 0.01f) {
            int aPulse = Mth.clamp((int) (pulse * 90), 0, 110);
            graphics.fill(0, 0, w, h, ARGB.color(aPulse, 160, 10, 10));
        }
    }
}
