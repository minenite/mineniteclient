package net.minenite.client.gun.vision;

import net.minecraft.util.Mth;

/**
 * Thermal auto-gain: keeps body heat readable in cold caves while lava doesn't
 * crush the whole palette to white.
 */
public final class ThermalAgc {
	private static final ThermalAgc INSTANCE = new ThermalAgc();

	private float black = 0.12f;
	private float white = 0.92f;
	private float sampleMin = 1f;
	private float sampleMax = 0f;
	private int samples;
	private float bodyHint;

	private ThermalAgc() {
	}

	public static ThermalAgc get() {
		return INSTANCE;
	}

	public void reset() {
		black = 0.12f;
		white = 0.92f;
		sampleMin = 1f;
		sampleMax = 0f;
		samples = 0;
		bodyHint = 0.55f;
	}

	/** Call while collecting scene temperatures each frame. */
	public void observe(float temp, boolean living) {
		temp = Mth.clamp(temp, 0f, 1f);
		sampleMin = Math.min(sampleMin, temp);
		sampleMax = Math.max(sampleMax, temp);
		samples++;
		if (living) {
			bodyHint += (temp - bodyHint) * 0.15f;
		}
	}

	/** Smooth black/white points toward the observed scene. */
	public void endFrame(float dt) {
		dt = Mth.clamp(dt, 0.001f, 0.1f);
		if (samples < 4) {
			sampleMin = 1f;
			sampleMax = 0f;
			samples = 0;
			return;
		}

		float lo = sampleMin;
		float hi = sampleMax;
		// Always reserve headroom so body heat sits in the upper-mid band
		float body = Mth.clamp(bodyHint, 0.4f, 0.7f);
		lo = Math.min(lo, body - 0.28f);
		hi = Math.max(hi, body + 0.22f);
		// Soft-knee: don't let a single lava pixel yank white to 1 instantly —
		// but don't ignore it either
		hi = Mth.clamp(hi, 0.55f, 1f);
		lo = Mth.clamp(lo, 0f, hi - 0.25f);

		float follow = 1f - (float) Math.exp(-4.5 * dt);
		black += (lo - black) * follow;
		white += (hi - white) * follow;
		if (white - black < 0.28f) {
			white = black + 0.28f;
		}

		sampleMin = 1f;
		sampleMax = 0f;
		samples = 0;
	}

	/** Remap raw temperature into display space. */
	public float map(float temp) {
		float span = Math.max(0.2f, white - black);
		float n = (temp - black) / span;
		// Soft shoulder so extreme heat blooms without wiping midtones
		if (n > 0.85f) {
			float u = (n - 0.85f) / 0.15f;
			n = 0.85f + u / (1f + u * 1.8f) * 0.15f;
		}
		return Mth.clamp(n, 0f, 1f);
	}

	public float blackPoint() {
		return black;
	}

	public float whitePoint() {
		return white;
	}
}
