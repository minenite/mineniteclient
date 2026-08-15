package net.minenite.client.gun;

import net.minenite.client.gun.vision.TemperatureField;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Short-lived muzzle flashes, tracers, and thermal blast cues from {@code pvpgunminus:fx}. */
public final class FxStore {
	public static final byte FX_MUZZLE = 1;
	public static final byte FX_TRACER = 2;
	public static final byte FX_THERMAL_BLAST = 3;
	public static final byte FX_THERMAL_SMOKE = 4;
	public static final int FX_FLAG_SUPPRESSED = 1;

	private static final FxStore INSTANCE = new FxStore();
	private static final int MAX_ACTIVE = 96;
	private final List<ActiveFx> active = new CopyOnWriteArrayList<>();

	private FxStore() {
	}

	public static FxStore get() {
		return INSTANCE;
	}

	public void accept(Payload payload) {
		if (payload == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (payload.fxType() == FX_THERMAL_BLAST) {
			float r = Math.max(0.5f, payload.scaleOrWidth());
			TemperatureField.get().explode(payload.x0(), payload.y0(), payload.z0(), r);
			if (mc.level != null) {
				mc.level.addParticle(ParticleTypes.EXPLOSION, payload.x0(), payload.y0(), payload.z0(), 0, 0, 0);
				int n = Mth.clamp((int) (r * 8), 6, 28);
				for (int i = 0; i < n; i++) {
					double ox = (Math.random() - 0.5) * r;
					double oy = Math.random() * r * 0.4;
					double oz = (Math.random() - 0.5) * r;
					mc.level.addParticle(ParticleTypes.SMOKE,
							payload.x0() + ox, payload.y0() + oy, payload.z0() + oz, 0, 0.04, 0);
				}
			}
			return;
		}
		if (payload.fxType() == FX_THERMAL_SMOKE) {
			float h = Math.max(4f, payload.scaleOrWidth());
			TemperatureField.get().smokePlume(payload.x0(), payload.y0(), payload.z0(), h);
			if (mc.level != null) {
				int n = Mth.clamp((int) (h * 2), 8, 40);
				for (int i = 0; i < n; i++) {
					double oy = (i / (double) n) * h;
					mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
							payload.x0(), payload.y0() + oy, payload.z0(), 0, 0.08, 0);
				}
			}
			return;
		}
		while (active.size() >= MAX_ACTIVE) {
			active.remove(0);
		}
		active.add(new ActiveFx(payload, Math.max(1, Math.min(12, payload.ttlTicks()))));
		spawnParticles(mc, payload);
	}

	private static void spawnParticles(Minecraft mc, Payload p) {
		if (mc.level == null) {
			return;
		}
		int argb = 0xFF000000 | (p.rgb() & 0xFFFFFF);
		if (p.fxType() == FX_MUZZLE) {
			float s = Math.max(0.2f, p.scaleOrWidth()) * (p.suppressed() ? 0.35f : 1f);
			DustParticleOptions dust = new DustParticleOptions(argb, s);
			for (int i = 0; i < 6; i++) {
				mc.level.addParticle(dust, p.x0(), p.y0(), p.z0(),
						p.x1() * 0.08, p.y1() * 0.08, p.z1() * 0.08);
			}
			if (!p.suppressed()) {
				mc.level.addParticle(ParticleTypes.FLAME, p.x0(), p.y0(), p.z0(),
						p.x1() * 0.02, p.y1() * 0.02, p.z1() * 0.02);
			}
			return;
		}
		if (p.fxType() == FX_TRACER) {
			Vec3 from = new Vec3(p.x0(), p.y0(), p.z0());
			Vec3 to = new Vec3(p.x1(), p.y1(), p.z1());
			double len = from.distanceTo(to);
			int n = Math.max(4, (int) (len * 2.2));
			DustParticleOptions core = new DustParticleOptions(argb, Math.max(0.35f, p.scaleOrWidth() * 6f));
			for (int i = 0; i <= n; i++) {
				double t = i / (double) n;
				mc.level.addParticle(core,
						from.x + (to.x - from.x) * t,
						from.y + (to.y - from.y) * t,
						from.z + (to.z - from.z) * t,
						0, 0, 0);
			}
		}
	}

	public void clearAll() {
		active.clear();
	}

	public void tick() {
		List<ActiveFx> doomed = new ArrayList<>();
		for (ActiveFx fx : active) {
			fx.ttl--;
			if (fx.ttl <= 0) {
				doomed.add(fx);
			}
		}
		active.removeAll(doomed);
	}

	public List<ActiveFx> snapshot() {
		return new ArrayList<>(active);
	}

	public record Payload(byte fxType, UUID shooter, int rgb,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float scaleOrWidth, int ttlTicks, boolean suppressed) {
	}

	public static final class ActiveFx {
		public final Payload payload;
		private int ttl;
		public final int maxTtl;

		private ActiveFx(Payload payload, int ttl) {
			this.payload = payload;
			this.ttl = ttl;
			this.maxTtl = ttl;
		}

		public float life() {
			return maxTtl <= 0 ? 0f : Math.max(0f, (float) ttl / (float) maxTtl);
		}
	}
}
