package net.minenite.client.gun.vision;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Thermal false-color palettes. {@code t} is normalized temperature 0…1.
 */
public enum ThermalPalette {
	WHITE_HOT("White Hot"),
	BLACK_HOT("Black Hot"),
	IRONBOW("Ironbow"),
	RAINBOW("Rainbow"),
	FUSION("Fusion");

	private final String label;

	ThermalPalette(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public ThermalPalette next() {
		ThermalPalette[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	/** ARGB color for temperature 0…1. */
	public int color(float t, float alpha01) {
		t = Mth.clamp(t, 0f, 1f);
		int a = Mth.clamp((int) (alpha01 * 255f), 0, 255);
		int r, g, b;
		switch (this) {
			case WHITE_HOT -> {
				int v = Mth.clamp((int) (t * 255f), 0, 255);
				r = g = b = v;
			}
			case BLACK_HOT -> {
				int v = Mth.clamp((int) ((1f - t) * 255f), 0, 255);
				r = g = b = v;
			}
			case IRONBOW -> {
				// black → blue → magenta → orange → white
				if (t < 0.25f) {
					float u = t / 0.25f;
					r = 0;
					g = 0;
					b = Mth.clamp((int) (u * 180), 0, 180);
				} else if (t < 0.5f) {
					float u = (t - 0.25f) / 0.25f;
					r = Mth.clamp((int) (u * 200), 0, 200);
					g = 0;
					b = Mth.clamp((int) (180 + u * 40), 0, 255);
				} else if (t < 0.75f) {
					float u = (t - 0.5f) / 0.25f;
					r = 255;
					g = Mth.clamp((int) (u * 160), 0, 160);
					b = Mth.clamp((int) ((1f - u) * 80), 0, 80);
				} else {
					float u = (t - 0.75f) / 0.25f;
					r = 255;
					g = Mth.clamp((int) (160 + u * 95), 0, 255);
					b = Mth.clamp((int) (u * 220), 0, 255);
				}
			}
			case RAINBOW -> {
				float h = t * 0.85f; // stop before wrapping to red-red
				float[] rgb = hsv(h, 1f, Mth.clamp(0.35f + t * 0.65f, 0f, 1f));
				r = (int) (rgb[0] * 255);
				g = (int) (rgb[1] * 255);
				b = (int) (rgb[2] * 255);
			}
			case FUSION -> {
				// cool teal ambient + ironbow hot overlay bias
				if (t < 0.4f) {
					float u = t / 0.4f;
					r = Mth.clamp((int) (10 + u * 20), 0, 40);
					g = Mth.clamp((int) (40 + u * 80), 0, 140);
					b = Mth.clamp((int) (50 + u * 90), 0, 160);
				} else {
					float u = (t - 0.4f) / 0.6f;
					r = Mth.clamp((int) (40 + u * 215), 0, 255);
					g = Mth.clamp((int) (60 + u * 140), 0, 255);
					b = Mth.clamp((int) ((1f - u) * 100), 0, 100);
				}
			}
			default -> r = g = b = 128;
		}
		return ARGB.color(a, r, g, b);
	}

	private static float[] hsv(float h, float s, float v) {
		float i = (float) Math.floor(h * 6f);
		float f = h * 6f - i;
		float p = v * (1f - s);
		float q = v * (1f - f * s);
		float t = v * (1f - (1f - f) * s);
		return switch (((int) i) % 6) {
			case 0 -> new float[]{v, t, p};
			case 1 -> new float[]{q, v, p};
			case 2 -> new float[]{p, v, t};
			case 3 -> new float[]{p, q, v};
			case 4 -> new float[]{t, p, v};
			default -> new float[]{v, p, q};
		};
	}
}
