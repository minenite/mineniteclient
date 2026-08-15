package net.minenite.client.gun;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minenite.client.gun.vision.NvgPalette;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Tube chrome: phosphor wash, grain, vignette, localized AGC bloom. */
public final class NvgOverlay {
	private static final Matrix4f MVP = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static long frame;

	private NvgOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || ThermalVision.isWearing(player) || !NvgVision.isWearing(player)) {
			return;
		}
		frame++;
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		float night = NvgVision.nightAmount(mc, 1f);
		float dayFlood = NvgVision.dayFlood(mc, player, 1f);
		float photons = NvgVision.tubePhotons(mc, player, 1f);
		float starve = Mth.clamp(1f - photons, 0f, 1f);
		NvgAgc agc = NvgAgc.get();
		float gain = agc.gain() * Mth.lerp(starve, 1f, 0.42f) * Mth.lerp(dayFlood, 1f, 0.35f);
		float local = Math.max(agc.displayLocalBloom(), dayFlood * 0.55f);
		float global = Math.max(agc.displayGlobalBloom(), dayFlood * 0.95f);
		NvgPalette palette = NvgVision.palette();
		int[] rgb = palette.washRgb();
		int[] rgb2 = palette.washRgb2();
		float washMul = palette.washStrength();

		int washCap = palette == NvgPalette.GREEN || palette.trueColor() ? 28 : 52;
		int washA = Mth.clamp((int) ((10 + night * 8) * (0.55f + gain * 0.35f) * washMul
				* Mth.lerp(starve, 1f, 0.7f)), 4, washCap);
		graphics.fill(0, 0, w, h, ARGB.color(washA, rgb[0], rgb[1], rgb[2]));
		if (!palette.trueColor()) {
			graphics.fill(0, 0, w, h, ARGB.color(Mth.clamp(washA / 3, 3, washCap / 3), rgb2[0], rgb2[1], rgb2[2]));
			float punch = palette.punchStrength();
			if (punch > 0.01f) {
				int punchA = Mth.clamp((int) (washA * punch * 0.45f), 4, 36);
				graphics.fill(0, 0, w, h, ARGB.color(punchA, rgb[0], rgb[1], rgb[2]));
			}
		}

		if (dayFlood > 0.08f) {
			float blow = Mth.clamp((dayFlood - 0.08f) / 0.92f, 0f, 1f);
			int dayA = Mth.clamp((int) (blow * blow * (palette.trueColor() ? 140 : 175)), 0, 200);
			int wr = Mth.clamp((int) (rgb[0] * 0.45f + 180 * blow), 0, 255);
			int wg = Mth.clamp((int) (rgb[1] * 0.35f + 230 * blow), 0, 255);
			int wb = Mth.clamp((int) (rgb[2] * 0.45f + 190 * blow), 0, 255);
			graphics.fill(0, 0, w, h, ARGB.color(dayA, wr, wg, wb));
			if (blow > 0.35f) {
				int coreA = Mth.clamp((int) ((blow - 0.35f) / 0.65f * 90), 0, 90);
				graphics.fill(0, 0, w, h, ARGB.color(coreA, 230, 255, 235));
			}
		}

		drawTubeVignette(graphics, w, h, night, global, palette);

		drawGrain(graphics, player, w, h, gain, starve, night, palette);
		if (local > 0.02f || global > 0.015f) {
			drawHotspotBloom(graphics, mc, w, h, local, global, gain, palette);
		}
		drawTubeSaturate(graphics, w, h, local, global, agc.proximity(), palette);
	}

	private static void drawTubeVignette(GuiGraphicsExtractor graphics, int w, int h, float night, float global,
			NvgPalette palette) {
		int edge = Math.max(10, Math.min(w, h) / 6);
		int layers = 3;
		int[] edgeRgb = palette.trueColor() ? new int[]{0, 0, 0} : new int[]{0, 6, 0};
		if (palette == NvgPalette.RED) {
			edgeRgb = new int[]{8, 0, 0};
		} else if (palette == NvgPalette.BLUE) {
			edgeRgb = new int[]{0, 2, 10};
		} else if (palette == NvgPalette.AMBER) {
			edgeRgb = new int[]{8, 4, 0};
		} else if (palette == NvgPalette.WHITE) {
			edgeRgb = new int[]{2, 2, 4};
		}
		for (int i = 0; i < layers; i++) {
			float t = (i + 1f) / layers;
			int a = Mth.clamp((int) ((10 + night * 14 + global * 10) * t), 4, 52);
			int inset = (int) (edge * t);
			graphics.fill(0, 0, w, inset, ARGB.color(a, edgeRgb[0], edgeRgb[1], edgeRgb[2]));
			graphics.fill(0, h - inset, w, h, ARGB.color(a, edgeRgb[0], edgeRgb[1], edgeRgb[2]));
			graphics.fill(0, 0, inset, h, ARGB.color(a - 4, edgeRgb[0], edgeRgb[1], edgeRgb[2]));
			graphics.fill(w - inset, 0, w, h, ARGB.color(a - 4, edgeRgb[0], edgeRgb[1], edgeRgb[2]));
		}
	}

	private static void drawGrain(GuiGraphicsExtractor graphics, LocalPlayer player, int w, int h,
			float gain, float starve, float night, NvgPalette palette) {
		NvgGrain.ensure();
		int[] rgb = palette.washRgb();
		int grainA = Mth.clamp((int) ((38 + starve * 50 + night * 12) * palette.grainMul()
				* Mth.lerp(gain, 1.15f, 0.85f)), 18, 110);
		int ox = (int) ((frame * 5) & 255);
		int oy = (int) ((frame * 11) & 255);
		int tint = ARGB.color(grainA, rgb[0], rgb[1], rgb[2]);
		if (palette.trueColor()) {
			tint = ARGB.color(grainA, 200, 200, 200);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, NvgGrain.ID, 0, 0, ox, oy, w, h, 256, 256, tint);

		int sparks = Mth.clamp((int) (18 + starve * 36 + night * 10), 12, 48);
		long seed = frame * 0x9E3779B97F4A7C15L ^ (player.tickCount * 0xC2B2AE3DL);
		int sparkA = Mth.clamp((int) (20 + starve * 28), 14, 64);
		for (int i = 0; i < sparks; i++) {
			seed = splitMix(seed);
			int x = (int) ((seed >>> 33) % Math.max(1, w));
			seed = splitMix(seed);
			int y = (int) ((seed >>> 33) % Math.max(1, h));
			seed = splitMix(seed);
			int sz = ((seed >>> 61) & 3) == 0 ? 2 : 1;
			int a = sparkA + (int) ((seed >>> 57) & 15);
			if (palette.trueColor()) {
				int v = 160 + (int) ((seed >>> 52) & 0x5F);
				graphics.fill(x, y, Math.min(w, x + sz), Math.min(h, y + sz), ARGB.color(a, v, v, v));
			} else {
				int g = Mth.clamp(rgb[1] - 10 + (int) ((seed >>> 52) & 0x3F), 80, 255);
				graphics.fill(x, y, Math.min(w, x + sz), Math.min(h, y + sz),
						ARGB.color(a, rgb[0], g, rgb[2] / 2));
			}
		}
	}

	private static void drawHotspotBloom(GuiGraphicsExtractor graphics, Minecraft mc,
			int w, int h, float local, float global, float gain, NvgPalette palette) {
		NvgAgc agc = NvgAgc.get();
		float cx = w * 0.5f;
		float cy = h * 0.5f;
		boolean projected = false;
		if (agc.hasHotspot()) {
			try {
				Camera camera = mc.gameRenderer.mainCamera();
				camera.getViewRotationProjectionMatrix(MVP);
				var cam = camera.position();
				CLIP.set(agc.hotX() - (float) cam.x, agc.hotY() - (float) cam.y, agc.hotZ() - (float) cam.z, 1f);
				MVP.transform(CLIP);
				if (CLIP.w > 0.05f) {
					float nx = CLIP.x / CLIP.w;
					float ny = CLIP.y / CLIP.w;
					if (Math.abs(nx) < 1.4f && Math.abs(ny) < 1.4f) {
						cx = (nx * 0.5f + 0.5f) * w;
						cy = (1f - (ny * 0.5f + 0.5f)) * h;
						projected = true;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		if (!projected) {
			local *= 0.55f;
		}

		float prox = agc.proximity();
		float radius = Mth.lerp(prox, Math.min(w, h) * 0.1f, Math.min(w, h) * 0.42f);
		radius *= Mth.lerp(local, 0.75f, 1.55f);
		float sat = Mth.clamp(1.15f - gain * 0.4f, 0.35f, 1f);
		int[] rgb = palette.washRgb();
		int layers = 5;
		for (int i = layers - 1; i >= 0; i--) {
			float t = i / (float) (layers - 1);
			float falloff = (float) Math.exp(-t * t * 3.1);
			int alpha = Mth.clamp((int) (local * falloff * (70 + sat * 70) * (palette.trueColor() ? 0.7f : 1f)), 0, 160);
			if (alpha < 2) {
				continue;
			}
			float r = radius * (0.2f + t * 1.25f);
			int x0 = Math.round(cx - r);
			int y0 = Math.round(cy - r);
			int x1 = Math.round(cx + r);
			int y1 = Math.round(cy + r);
			int red = Mth.clamp((int) (rgb[0] + sat * 70), 0, 255);
			int green = Mth.clamp((int) (rgb[1] + sat * 40), 0, 255);
			int blue = Mth.clamp((int) (rgb[2] + sat * 50), 0, 255);
			graphics.fill(x0, y0, x1, y1, ARGB.color(alpha, red, green, blue));
		}
		if (local > 0.12f) {
			float coreR = radius * Mth.lerp(prox, 0.32f, 0.55f);
			int a = Mth.clamp((int) (local * local * 160 + prox * 50), 0, 200);
			graphics.fill(
					Math.round(cx - coreR), Math.round(cy - coreR),
					Math.round(cx + coreR), Math.round(cy + coreR),
					ARGB.color(a, 235, 255, 240));
		}
		if (global > 0.1f) {
			float g = Mth.clamp((global - 0.1f) / 0.9f, 0f, 1f);
			int aWash = Mth.clamp((int) (g * g * 110 * palette.washStrength()), 0, 140);
			graphics.fill(0, 0, w, h, ARGB.color(aWash, rgb[0], rgb[1], rgb[2]));
		}
	}

	/** Close lamps / day flood: tubes white-out like a real photocathode. */
	private static void drawTubeSaturate(GuiGraphicsExtractor graphics, int w, int h,
			float local, float global, float proximity, NvgPalette palette) {
		float blind = Mth.clamp(global * 1.05f + local * proximity * 0.7f, 0f, 1f);
		if (blind < 0.06f) {
			return;
		}
		int[] rgb = palette.washRgb();
		int phA = Mth.clamp((int) (blind * 130), 0, 170);
		graphics.fill(0, 0, w, h, ARGB.color(phA,
				Mth.clamp(rgb[0] + 40, 0, 255),
				Mth.clamp(rgb[1] + 20, 0, 255),
				Mth.clamp(rgb[2] + 30, 0, 255)));
		float white = Mth.clamp((blind - 0.28f) / 0.72f, 0f, 1f);
		white = white * white;
		if (white > 0.02f) {
			int wA = Mth.clamp((int) (white * 230), 0, 230);
			int inset = (int) (Math.min(w, h) * Mth.lerp(white, 0.22f, 0.02f));
			graphics.fill(inset, inset, w - inset, h - inset, ARGB.color(wA, 255, 255, 250));
			if (white > 0.55f) {
				int full = Mth.clamp((int) ((white - 0.55f) / 0.45f * 210), 0, 210);
				graphics.fill(0, 0, w, h, ARGB.color(full, 255, 255, 255));
			}
		}
	}

	private static long splitMix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
