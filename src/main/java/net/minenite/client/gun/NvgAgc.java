package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Gen-III AGC: lamps raise local bloom; close bright sources duck gain. */
public final class NvgAgc {
	private static final NvgAgc INSTANCE = new NvgAgc();

	private float gain = 1f;
	private float localBloom;
	private float globalBloom;
	private float input;
	private float proximity;
	private float hotX;
	private float hotY;
	private float hotZ;
	private boolean hasHotspot;

	private NvgAgc() {
	}

	public static NvgAgc get() {
		return INSTANCE;
	}

	public void reset() {
		gain = 1f;
		localBloom = 0f;
		globalBloom = 0f;
		input = 0f;
		proximity = 0f;
		hasHotspot = false;
	}

	public void tickWorld(Minecraft mc, LocalPlayer player, float dt) {
		if (mc.level == null || player == null) {
			return;
		}
		LightLookSampler.HotSpot hot = LightLookSampler.sample(mc, player, 1f);
		tick(hot.intensity(), hot.proximity(), hot.x(), hot.y(), hot.z(), dt);
		tickDayFlood(NvgVision.dayFlood(mc, player, 1f), dt);
	}

	public void tick(float intensity, float prox, float x, float y, float z, float dt) {
		dt = Mth.clamp(dt, 0.001f, 0.1f);
		float target = Mth.clamp(intensity, 0f, 1.8f);
		float follow = target > input ? 18f : 6f;
		input += (target - input) * (1f - (float) Math.exp(-follow * dt));
		proximity += (prox - proximity) * (1f - (float) Math.exp(-12f * dt));
		proximity = Mth.clamp(proximity, 0f, 1f);
		if (intensity > 0.08f) {
			hotX = x;
			hotY = y;
			hotZ = z;
			hasHotspot = true;
		} else if (proximity < 0.02f && input < 0.08f) {
			hasHotspot = false;
		}
		float localKnee = 0.16f;
		float localExcess = Math.max(0f, input - localKnee);
		localBloom += localExcess * localExcess * 5.2f * dt;
		localBloom *= (float) Math.exp(-2.1 * dt);
		localBloom = Mth.clamp(localBloom, 0f, 1.25f);
		float globalDrive = input * (0.28f + proximity * 0.95f);
		if (globalDrive > 0.08f) {
			float gExcess = globalDrive - 0.08f;
			globalBloom += gExcess * gExcess * 8.5f * dt;
		}
		if (proximity > 0.4f && input > 0.65f) {
			float slam = Mth.clamp((proximity - 0.4f) / 0.6f * (input - 0.65f) / 0.35f, 0f, 1f);
			globalBloom = Math.max(globalBloom, slam * 1.2f);
		}
		globalBloom *= (float) Math.exp(-2.2 * dt);
		globalBloom = Mth.clamp(globalBloom, 0f, 1.45f);
		float load = globalBloom * 1.65f + localBloom * 0.28f;
		float desired = Mth.clamp(1f / (1f + load), 0.1f, 1f);
		float agcFollow = desired < gain ? 14f : 2.4f;
		gain += (desired - gain) * (1f - (float) Math.exp(-agcFollow * dt));
		gain = Mth.clamp(gain, 0.1f, 1f);
	}

	public void tickDayFlood(float dayFlood, float dt) {
		dt = Mth.clamp(dt, 0.001f, 0.1f);
		float f = Mth.clamp(dayFlood, 0f, 1f);
		if (f < 0.05f) {
			return;
		}
		float drive = f * f;
		input = Math.max(input, Mth.lerp(drive, input, 1.55f));
		proximity = Math.max(proximity, Mth.lerp(drive, proximity, 0.92f));
		localBloom = Math.max(localBloom, drive * 0.55f);
		globalBloom = Math.max(globalBloom, drive * 1.15f);
		float load = globalBloom * 1.35f + localBloom * 0.25f + drive * 0.9f;
		float desired = Mth.clamp(1f / (1f + load), 0.28f, 1f);
		gain += (desired - gain) * (1f - (float) Math.exp(-12f * dt));
		gain = Mth.clamp(gain, 0.28f, 1f);
	}

	public float gain() {
		return gain;
	}

	public boolean hasHotspot() {
		return hasHotspot;
	}

	public float hotX() {
		return hotX;
	}

	public float hotY() {
		return hotY;
	}

	public float hotZ() {
		return hotZ;
	}

	public float proximity() {
		return proximity;
	}

	public float displayLocalBloom() {
		return Mth.clamp((float) Math.pow(localBloom / 1.15f, 0.85), 0f, 1f);
	}

	public float displayGlobalBloom() {
		return Mth.clamp((float) Math.pow(globalBloom / 1.35f, 0.9) * proximity, 0f, 1f);
	}
}
