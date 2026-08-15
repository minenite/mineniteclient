package net.minenite.client.gun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Fullscreen dazzle when a laser is aimed into the eyes, or flashbang whiteout. */
public final class LaserEyeFlash {
	private static float intensity;
	private static float r = 1f;
	private static float g = 0.2f;
	private static float b = 0.2f;
	private static boolean refreshedThisTick;
	private static long lastAcceptMs;
	private static int whiteoutTicks;

	private LaserEyeFlash() {
	}

	public static void accept(int rgb, float nextIntensity) {
		long now = System.currentTimeMillis();
		float next = Mth.clamp(nextIntensity, 0f, 1f);
		float pr = ((rgb >> 16) & 0xFF) / 255f;
		float pg = ((rgb >> 8) & 0xFF) / 255f;
		float pb = (rgb & 0xFF) / 255f;
		boolean whiteout = pr > 0.9f && pg > 0.9f && pb > 0.9f && next >= 0.9f;
		if (!whiteout && ThermalVision.isWearing()) {
			return;
		}
		if (whiteout) {
			whiteoutTicks = Math.max(whiteoutTicks, 3);
			intensity = 1f;
			r = 1f;
			g = 1f;
			b = 1f;
			refreshedThisTick = true;
			lastAcceptMs = now;
			return;
		}
		boolean ir = pg > 0.55f && pg > pr * 1.6f && pg > pb * 1.4f && pr < 0.45f;
		if (ir) {
			next *= 0.28f;
		}
		if (now - lastAcceptMs < 80L && intensity > 0.35f) {
			intensity = Math.max(intensity, next * 0.9f);
			refreshedThisTick = true;
			return;
		}
		lastAcceptMs = now;
		intensity = Math.max(intensity * 0.85f, next);
		r = pr;
		g = pg;
		b = pb;
		refreshedThisTick = true;
	}

	public static void tick() {
		if (whiteoutTicks > 0) {
			whiteoutTicks--;
			intensity = 1f;
			r = 1f;
			g = 1f;
			b = 1f;
			refreshedThisTick = true;
		}
		if (!refreshedThisTick) {
			intensity = Math.max(0f, intensity - 0.12f);
		}
		refreshedThisTick = false;
	}

	public static void clear() {
		intensity = 0f;
		whiteoutTicks = 0;
	}

	public static void render(GuiGraphicsExtractor graphics) {
		boolean whiteout = whiteoutTicks > 0 || (r > 0.9f && g > 0.9f && b > 0.9f && intensity > 0.55f);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		if (whiteout) {
			float i = whiteoutTicks > 0 ? 1f : Mth.clamp(intensity, 0f, 1f);
			int a = Mth.clamp((int) (i * 255f), 0, 255);
			graphics.fill(0, 0, w, h, ARGB.color(a, 255, 255, 255));
			return;
		}
		if (intensity <= 0.01f || ThermalVision.isWearing()) {
			return;
		}
		float i = Mth.clamp(intensity, 0f, 1f);
		boolean ir = g > 0.55f && g > r * 1.6f && g > b * 1.4f && r < 0.45f;
		float washScale = ir ? 0.32f : 1f;
		int aWash = Mth.clamp((int) (i * 140 * washScale), 0, ir ? 48 : 200);
		graphics.fill(0, 0, w, h, ARGB.color(aWash,
				Mth.clamp((int) (r * 255), 0, 255),
				Mth.clamp((int) (g * 255), 0, 255),
				Mth.clamp((int) (b * 255), 0, 255)));
		int aCore = Mth.clamp((int) (i * i * 210 * washScale), 0, ir ? 70 : 230);
		int cr = Mth.clamp((int) (r * 180 + 75), 0, 255);
		int cg = Mth.clamp((int) (g * 180 + 75), 0, 255);
		int cb = Mth.clamp((int) (b * 180 + 75), 0, 255);
		int insetX = (int) (w * (ir ? 0.28f : 0.18f) - i * (ir ? 0.06f : 0.12f));
		int insetY = (int) (h * (ir ? 0.28f : 0.18f) - i * (ir ? 0.06f : 0.12f));
		graphics.fill(insetX, insetY, w - insetX, h - insetY, ARGB.color(aCore, cr, cg, cb));
		if (!ir && i > 0.55f) {
			int aWhite = Mth.clamp((int) ((i - 0.55f) / 0.45f * 180), 0, 180);
			int ix = (int) (w * 0.32f);
			int iy = (int) (h * 0.32f);
			graphics.fill(ix, iy, w - ix, h - iy, ARGB.color(aWhite, 255, 255, 255));
		}
	}
}
