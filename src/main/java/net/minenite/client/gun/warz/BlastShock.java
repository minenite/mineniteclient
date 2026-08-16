package net.minenite.client.gun.warz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * What a blast does to the person caught in it: the screen kicks, the world
 * whites out for a moment and the ears ring.
 *
 * The server has been sending this on pvpgunminus:blast all along - pressure,
 * flash, tinnitus, muffle, a knockback direction and four durations - and the
 * client dropped it, so explosions had no effect beyond damage.
 */
public final class BlastShock {

    private BlastShock() {
    }

    private static float elapsed(WarzFxStores.Blast blast) {
        return (System.currentTimeMillis() - blast.startedAtMs()) / 50f;
    }

    /** 0..1 how far through a phase we are, or 1 when it is over. */
    private static float phase(WarzFxStores.Blast blast, int ticks) {
        if (ticks <= 0) {
            return 1f;
        }
        return Mth.clamp(elapsed(blast) / ticks, 0f, 1f);
    }

    /** Camera kick, applied to the view each frame while the shake lasts. */
    public static float shake() {
        WarzFxStores.Blast blast = WarzFxStores.blast();
        if (blast == null) {
            return 0f;
        }
        float t = phase(blast, blast.shakeTicks());
        if (t >= 1f) {
            return 0f;
        }
        // Hard kick that decays away rather than a constant wobble.
        return blast.pressure() * (1f - t) * (1f - t);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        WarzFxStores.Blast blast = WarzFxStores.blast();
        if (blast == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        float flash = 1f - phase(blast, blast.flashTicks());
        if (flash > 0f && blast.flash() > 0.01f) {
            int a = Mth.clamp((int) (blast.flash() * flash * 235), 0, 240);
            graphics.fill(0, 0, w, h, ARGB.color(a, 255, 250, 235));
        }

        // Tinnitus reads as a tightening grey vignette; the sound side of it is
        // the muffle the server also sends.
        float ring = 1f - phase(blast, blast.tinnitusTicks());
        if (ring > 0f && blast.tinnitus() > 0.01f) {
            float v = blast.tinnitus() * ring;
            int inset = Math.max(8, (int) (Math.min(w, h) * 0.05f * (1f + v)));
            int a = Mth.clamp((int) (v * 120), 0, 150);
            graphics.fill(0, 0, w, inset, ARGB.color(a, 12, 12, 14));
            graphics.fill(0, h - inset, w, h, ARGB.color(a, 12, 12, 14));
            graphics.fill(0, inset, inset, h - inset, ARGB.color(a, 12, 12, 14));
            graphics.fill(w - inset, inset, w, h - inset, ARGB.color(a, 12, 12, 14));
        }
    }

    /** True while the blast still muffles the world, for the sound side. */
    public static float muffle() {
        WarzFxStores.Blast blast = WarzFxStores.blast();
        if (blast == null) {
            return 0f;
        }
        float t = phase(blast, blast.muffleTicks());
        return t >= 1f ? 0f : blast.muffle() * (1f - t);
    }
}
