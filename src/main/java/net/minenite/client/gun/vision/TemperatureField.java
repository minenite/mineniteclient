package net.minenite.client.gun.vision;

import net.minenite.client.gun.FxStore;
import net.minenite.client.gun.LaserBeamStore;
import net.minenite.client.gun.LaserWire;
import net.minenite.client.gun.NvgVision;
import net.minenite.client.gun.ThermalVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client temperature field: entities, contact heat, barrel glow, footprints, afterglow.
 */
public final class TemperatureField {
	private static final TemperatureField INSTANCE = new TemperatureField();
	private static final int MAX_FOOTPRINTS = 320;
	private static final int MAX_CONTACTS = 280;
	private static final int MAX_TRANSIENTS = 256;
	private static final int MAX_AFTERGLOW = 280;
	private static final int MAX_BARRELS = 48;
	private static final int MAX_LASER_SPOTS = 96;
	/** Laser impact marks linger ~3 seconds. */
	private static final float LASER_SPOT_LIFE_SEC = 3.0f;
	private static final double LASER_SPOT_MERGE = 0.18;

	public record Afterglow(double x, double y, double z, float halfW, float halfH, float halfD, float temp, float life) {
	}

	public record BarrelGlow(double x, double y, double z, float temp, float life) {
	}

	/** Tiny ground/wall burn from a laser tip (screen-painted as ~2px). */
	public record LaserSpot(double x, double y, double z, float temp, float ageSec) {
	}

	private final Map<UUID, Float> entityTemp = new HashMap<>();
	private final Map<UUID, Float> barrelHeat = new HashMap<>();
	private final Map<UUID, Vec3> lastEntityPos = new HashMap<>();
	private final Map<UUID, Vec3> lastLaserTip = new HashMap<>();
	private final Map<BlockPos, Float> footprints = new HashMap<>();
	private final Map<BlockPos, Float> contacts = new HashMap<>();
	private final Map<BlockPos, Float> transients = new HashMap<>();
	private final List<Afterglow> afterglow = new ArrayList<>();
	private final List<BarrelGlow> barrels = new ArrayList<>();
	private final List<LaserSpot> laserSpots = new ArrayList<>();
	/** Cached fire/lava/magma positions for FLIR (incremental strip scan). */
	private static final int MAX_FIRE_CACHE = 40;
	private final List<BlockPos> fireCache = new ArrayList<>();
	private final Set<BlockPos> fireSet = new HashSet<>();
	private int fireScanDx = -96;
	private ThermalPalette palette = ThermalPalette.IRONBOW;
	private int tick;

	private TemperatureField() {
	}

	public static TemperatureField get() {
		return INSTANCE;
	}

	public void reset() {
		entityTemp.clear();
		barrelHeat.clear();
		lastEntityPos.clear();
		footprints.clear();
		contacts.clear();
		transients.clear();
		afterglow.clear();
		barrels.clear();
		laserSpots.clear();
		fireCache.clear();
		fireSet.clear();
		fireScanDx = -96;
		lastLaserTip.clear();
		tick = 0;
		ThermalAgc.get().reset();
	}




	public ThermalPalette palette() {
		return palette;
	}

	public void setPalette(ThermalPalette palette) {
		this.palette = palette == null ? ThermalPalette.IRONBOW : palette;
	}

	public ThermalPalette cyclePalette() {
		palette = palette.next();
		return palette;
	}

	public void tick(Minecraft mc, LocalPlayer player, float dt) {
		if (mc.level == null || player == null) {
			return;
		}
		tick++;
		dt = Mth.clamp(dt, 0.001f, 0.1f);
		float day = 1f - NvgVision.skyDarken01(mc);

		AABB box = player.getBoundingBox().inflate(52.0);
		for (Entity e : mc.level.getEntities(player, box, ent -> ent instanceof LivingEntity && ent.isAlive())) {
			LivingEntity living = (LivingEntity) e;
			float target = bodyTarget(living);
			float cur = entityTemp.getOrDefault(living.getUUID(), target);
			float mass = living instanceof Player ? 0.55f : 0.4f;
			float k = 2.2f / Math.max(0.2f, mass);
			cur += (target - cur) * (1f - (float) Math.exp(-k * dt));
			entityTemp.put(living.getUUID(), Mth.clamp(cur, 0f, 1f));

			Vec3 now = living.getPosition(1f);
			Vec3 prev = lastEntityPos.put(living.getUUID(), now);
			if (prev != null) {
				double moved = prev.distanceToSqr(now);
				if (moved > 0.004) {
					AABB bb = living.getBoundingBox();
					float smear = Mth.clamp(cur * 0.72f, 0.35f, 0.85f);
					Vec3 mid = prev.add(now).scale(0.5);
					spawnAfterglow(mid, bb, smear * 0.85f, 1f);
					spawnAfterglow(prev.add(0, bb.getYsize() * 0.5, 0), bb, smear * 0.65f, 0.85f);
				}
			}

			applyContactHeat(mc, living, cur);
		}

		decayMap(footprints, dt, 0.38f);
		decayMap(contacts, dt, 0.26f); // ~3s wall/floor prints
		decayMap(transients, dt, 0.32f);
		decayBarrelHeat(dt);
		decayAfterglow(dt);
		decayBarrels(dt);
		sampleLaserImpacts();
		decayLaserSpots(dt);

		// Muzzle / tracer heat. Under FLIR: barrel heat on the shooter + tight tip glow only
		// (no big injectHeat blobs that read as floating white squares).
		boolean flir = ThermalVision.isWearing();
		for (FxStore.ActiveFx fx : FxStore.get().snapshot()) {
			var p = fx.payload;
			if (p.fxType() == FxStore.FX_MUZZLE) {
				float suppress = p.suppressed() ? 0.35f : 1f;
				float heat = 0.78f * (0.55f + 0.45f * fx.life()) * suppress;
				barrelHeat.merge(p.shooter(), heat, Math::max);
				if (flir) {
					// Warm muzzle tip only — collectBarrels paints small markers
					spawnBarrel(p.x0(), p.y0(), p.z0(), 0.55f * suppress, 0.75f);
				} else {
					spawnBarrel(p.x0(), p.y0(), p.z0(), 0.72f * suppress, 0.9f);
					injectHeat(new Vec3(p.x0(), p.y0(), p.z0()), heat * 0.65f);
					Vec3 tip = new Vec3(p.x1(), p.y1(), p.z1());
					if (tip.lengthSqr() > 1.0E-6) {
						spawnBarrel(p.x1(), p.y1(), p.z1(), 0.6f * suppress, 0.7f);
					}
				}
			} else if (!flir) {
				float heat = 0.55f * (0.45f + 0.55f * fx.life());
				injectHeat(new Vec3(p.x0(), p.y0(), p.z0()), heat);
				if (p.fxType() == FxStore.FX_TRACER) {
					injectHeat(new Vec3(p.x1(), p.y1(), p.z1()), heat * 0.7f);
				}
			}
		}

		if (tick % 10 == 0) {
			scanNearbyHeatSources(mc, player, day);
		}
		if (ThermalVision.isWearing()) {
			// Every tick: scan a few X columns so the cache fills without hitching
			scanFireStrip(mc, player);
			if (tick % 20 == 0) {
				pruneFireCache(player);
			}
		} else if (tick % 40 == 0) {
			fireCache.clear();
			fireSet.clear();
			fireScanDx = -96;
		}
	}

	/** Remember a fire cell discovered by the renderer (ray grid). */
	public void noteFire(BlockPos pos) {
		if (pos == null) {
			return;
		}
		BlockPos imm = pos.immutable();
		if (fireSet.contains(imm)) {
			transients.merge(imm, 0.97f, Math::max);
			return;
		}
		if (fireCache.size() >= MAX_FIRE_CACHE) {
			// FIFO eviction — lava lakes must not unbounded-grow the cache.
			BlockPos drop = fireCache.remove(0);
			fireSet.remove(drop);
		}
		if (fireSet.add(imm)) {
			fireCache.add(imm);
		}
		transients.merge(imm, 0.97f, Math::max);
	}

	/** Floor + wall contact imprints that cool over a few seconds. */
	private void applyContactHeat(Minecraft mc, LivingEntity living, float bodyTemp) {
		AABB bb = living.getBoundingBox();
		boolean moving = living.getDeltaMovement().horizontalDistanceSqr() > 0.0018;
		float floorHeat = bodyTemp * (living instanceof Player ? 0.55f : 0.48f);
		if (!moving) {
			floorHeat *= 0.78f; // standing still still warms the floor
		}

		if (living.onGround()) {
			BlockPos under = living.blockPosition().below();
			stampContact(mc, under, floorHeat);
			// Foot corners
			stampContact(mc, BlockPos.containing(bb.minX + 0.05, bb.minY - 0.05, bb.minZ + 0.05).below(), floorHeat * 0.85f);
			stampContact(mc, BlockPos.containing(bb.maxX - 0.05, bb.minY - 0.05, bb.maxZ - 0.05).below(), floorHeat * 0.85f);
			if (moving) {
				footprints.merge(under, floorHeat, Math::max);
				Vec3 vel = living.getDeltaMovement();
				BlockPos trail = BlockPos.containing(
						living.getX() - vel.x * 1.6,
						living.getY() - 0.1,
						living.getZ() - vel.z * 1.6
				).below();
				footprints.merge(trail, floorHeat * 0.7f, Math::max);
				if (footprints.size() > MAX_FOOTPRINTS) {
					trimOldest(footprints, MAX_FOOTPRINTS / 4);
				}
			}
		}

		// Wall contact — solid blocks touching the inflated AABB
		float wallHeat = bodyTemp * 0.44f;
		AABB probe = bb.inflate(0.12, -0.08, 0.12);
		int x0 = Mth.floor(probe.minX);
		int y0 = Mth.floor(probe.minY);
		int z0 = Mth.floor(probe.minZ);
		int x1 = Mth.floor(probe.maxX);
		int y1 = Mth.floor(probe.maxY);
		int z1 = Mth.floor(probe.maxZ);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					cursor.set(x, y, z);
					BlockState state = mc.level.getBlockState(cursor);
					if (state.isAir() || ThermalMaterial.isVegetation(state) || !state.canOcclude()) {
						continue;
					}
					// Prefer vertical surfaces (walls) over ceiling
					boolean side = x == x0 || x == x1 || z == z0 || z == z1;
					if (!side && y > bb.minY + 0.2) {
						continue;
					}
					stampContact(mc, cursor.immutable(), side ? wallHeat : wallHeat * 0.55f);
				}
			}
		}
	}

	private void stampContact(Minecraft mc, BlockPos pos, float heat) {
		if (pos == null || mc.level == null) {
			return;
		}
		BlockState state = mc.level.getBlockState(pos);
		if (state.isAir() || ThermalMaterial.isVegetation(state)) {
			return;
		}
		contacts.merge(pos.immutable(), Mth.clamp(heat, 0f, 1f), Math::max);
		if (contacts.size() > MAX_CONTACTS) {
			trimOldest(contacts, MAX_CONTACTS / 4);
		}
	}

	private void spawnBarrel(float x, float y, float z, float temp, float life) {
		if (barrels.size() >= MAX_BARRELS) {
			barrels.remove(0);
		}
		barrels.add(new BarrelGlow(x, y, z, Mth.clamp(temp, 0f, 1f), Mth.clamp(life, 0.2f, 1f)));
	}

	/** Only the laser tip impact — no beam body heat. */
	private void sampleLaserImpacts() {
		for (LaserWire.Beam beam : LaserBeamStore.get().snapshot()) {
			Vec3 tip = new Vec3(beam.tipX(), beam.tipY(), beam.tipZ());
			Vec3 prev = lastLaserTip.put(beam.shooter(), tip);
			if (prev != null && prev.distanceToSqr(tip) < LASER_SPOT_MERGE * LASER_SPOT_MERGE) {
				// Refresh existing nearby mark instead of stacking squares
				refreshNearbyLaserSpot(tip, 0.82f);
			} else {
				spawnLaserSpot(tip.x, tip.y, tip.z, 0.82f);
			}
		}
	}

	private void refreshNearbyLaserSpot(Vec3 tip, float temp) {
		for (int i = 0; i < laserSpots.size(); i++) {
			LaserSpot s = laserSpots.get(i);
			if (tip.distanceToSqr(new Vec3(s.x(), s.y(), s.z())) < LASER_SPOT_MERGE * LASER_SPOT_MERGE) {
				laserSpots.set(i, new LaserSpot(tip.x, tip.y, tip.z, Math.max(s.temp(), temp), 0f));
				return;
			}
		}
		spawnLaserSpot(tip.x, tip.y, tip.z, temp);
	}

	private void spawnLaserSpot(double x, double y, double z, float temp) {
		if (laserSpots.size() >= MAX_LASER_SPOTS) {
			laserSpots.remove(0);
		}
		laserSpots.add(new LaserSpot(x, y, z, Mth.clamp(temp, 0f, 1f), 0f));
	}

	private void decayLaserSpots(float dt) {
		List<LaserSpot> next = new ArrayList<>(laserSpots.size());
		for (LaserSpot s : laserSpots) {
			float age = s.ageSec() + dt;
			if (age < LASER_SPOT_LIFE_SEC) {
				float cool = 1f - (age / LASER_SPOT_LIFE_SEC);
				next.add(new LaserSpot(s.x(), s.y(), s.z(), s.temp() * (0.55f + 0.45f * cool), age));
			}
		}
		laserSpots.clear();
		laserSpots.addAll(next);
	}

	private void decayBarrelHeat(float dt) {
		float keep = (float) Math.exp(-0.45 * dt); // hot barrel ~4–5s
		Iterator<Map.Entry<UUID, Float>> it = barrelHeat.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Float> e = it.next();
			float v = e.getValue() * keep;
			if (v < 0.06f) {
				it.remove();
			} else {
				e.setValue(v);
			}
		}
	}

	private void decayBarrels(float dt) {
		float keep = (float) Math.exp(-0.5 * dt);
		List<BarrelGlow> next = new ArrayList<>(barrels.size());
		for (BarrelGlow g : barrels) {
			float life = g.life() * keep;
			float temp = g.temp() * (float) Math.exp(-0.4 * dt);
			if (life >= 0.08f && temp >= 0.15f) {
				next.add(new BarrelGlow(g.x(), g.y(), g.z(), temp, life));
			}
		}
		barrels.clear();
		barrels.addAll(next);
	}

	private void spawnAfterglow(Vec3 center, AABB bb, float temp, float life) {
		if (afterglow.size() >= MAX_AFTERGLOW) {
			afterglow.remove(0);
		}
		afterglow.add(new Afterglow(
				center.x, center.y, center.z,
				(float) (bb.getXsize() * 0.28),
				(float) (bb.getYsize() * 0.35),
				(float) (bb.getZsize() * 0.28),
				Mth.clamp(temp, 0f, 1f),
				Mth.clamp(life, 0.2f, 1f)
		));
	}

	private void decayAfterglow(float dt) {
		float keep = (float) Math.exp(-0.9 * dt);
		float tempKeep = (float) Math.exp(-0.55 * dt);
		List<Afterglow> next = new ArrayList<>(afterglow.size());
		for (Afterglow g : afterglow) {
			float life = g.life() * keep;
			float temp = g.temp() * tempKeep;
			if (life >= 0.08f && temp >= 0.12f) {
				next.add(new Afterglow(g.x(), g.y(), g.z(), g.halfW(), g.halfH(), g.halfD(), temp, life));
			}
		}
		afterglow.clear();
		afterglow.addAll(next);
	}

	private void scanNearbyHeatSources(Minecraft mc, LocalPlayer player, float day) {
		BlockPos origin = player.blockPosition();
		int r = 64;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dy = -10; dy <= 14; dy += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					int dist2 = dx * dx + dy * dy + dz * dz;
					if (dist2 > r * r) {
						continue;
					}
					BlockPos p = origin.offset(dx, dy, dz);
					BlockState state = mc.level.getBlockState(p);
					ThermalMaterial.Props props = ThermalMaterial.of(state);
					boolean flame = ThermalMaterial.isFlame(state) || ThermalMaterial.isLava(state)
							|| state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
					if (!props.heatSource() && !flame) {
						continue;
					}
					float t = sampleBlock(mc, p, state, day);
					if (flame) {
						t = Math.max(t, 0.94f);
					}
					transients.merge(p.immutable(), t, Math::max);
					if (flame) {
						double cx = p.getX() + 0.5;
						double cy = p.getY() + (ThermalMaterial.isLava(state) ? 1.0 : 0.6);
						double cz = p.getZ() + 0.5;
						float life = dist2 > 900 ? 0.9f : 0.75f;
						spawnAfterglow(new Vec3(cx, cy + 0.35, cz),
								new AABB(cx - 0.35, cy, cz - 0.35, cx + 0.35, cy + 1.1, cz + 0.35),
								Math.max(0.8f, t), life);
						spawnAfterglow(new Vec3(cx, cy + 0.95, cz),
								new AABB(cx - 0.22, cy + 0.55, cz - 0.22, cx + 0.22, cy + 1.45, cz + 0.22),
								Math.max(0.6f, t * 0.85f), life * 0.85f);
					}
				}
			}
		}
		if (transients.size() > MAX_TRANSIENTS) {
			trimOldest(transients, MAX_TRANSIENTS / 4);
		}
	}

	/** Sparse X-column scan — keeps lava lakes from melting the client. */
	private void scanFireStrip(Minecraft mc, LocalPlayer player) {
		if (mc.level == null || player == null) {
			return;
		}
		if (fireCache.size() >= MAX_FIRE_CACHE) {
			return;
		}
		BlockPos origin = player.blockPosition();
		int far = 72;
		int columns = 3;
		for (int c = 0; c < columns; c++) {
			int dx = fireScanDx;
			fireScanDx += 2;
			if (fireScanDx > far) {
				fireScanDx = -far;
			}
			for (int dz = -far; dz <= far; dz += 2) {
				if (dx * dx + dz * dz > far * far) {
					continue;
				}
				for (int dy = -12; dy <= 16; dy += 2) {
					BlockPos p = origin.offset(dx, dy, dz);
					BlockState state = mc.level.getBlockState(p);
					if (ThermalMaterial.isFlame(state) || ThermalMaterial.isLava(state)
							|| state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
						noteFire(p);
						if (fireCache.size() >= MAX_FIRE_CACHE) {
							return;
						}
					}
				}
			}
		}
	}

	private void pruneFireCache(LocalPlayer player) {
		if (player == null) {
			return;
		}
		BlockPos origin = player.blockPosition();
		double max2 = 140.0 * 140.0;
		fireCache.removeIf(p -> {
			double d2 = p.distToCenterSqr(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
			if (d2 > max2) {
				fireSet.remove(p);
				return true;
			}
			return false;
		});
	}

	public void injectHeat(BlockPos pos, float amount) {
		if (pos == null) {
			return;
		}
		transients.merge(pos.immutable(), Mth.clamp(amount, 0f, 1f), Math::max);
	}

	public void injectHeat(Vec3 pos, float amount) {
		if (pos == null) {
			return;
		}
		injectHeat(BlockPos.containing(pos), amount);
	}

	/**
	 * Persistent MQ-9 crater smoke for drone white-hot / black-hot — tall rising heat column
	 * visible from altitude (vanilla campfire particles are distance-culled).
	 */
	public void smokePlume(double x, double y, double z, float height) {
		float h = Mth.clamp(height, 4f, 56f);
		int layers = Mth.clamp(Mth.ceil(h / 1.05f), 8, 40);
		long salt = (long) (x * 31 + z * 17 + y);
		for (int i = 0; i < layers; i++) {
			float t = i / (float) Math.max(1, layers - 1);
			double yy = y + 0.25 + i * (h / layers);
			double wobble = Math.sin(i * 1.41 + (salt & 255) * 0.05) * (0.35 + t * 1.6);
			double tw = Math.cos(i * 1.17 + (salt & 127) * 0.07) * (0.35 + t * 1.6);
			float heat = Mth.clamp(0.95f - t * 0.58f, 0.32f, 0.96f);
			double half = 0.55 + t * 1.35;
			injectHeat(BlockPos.containing(x + wobble, yy, z + tw), heat);
			if (i % 2 == 0) {
				injectHeat(BlockPos.containing(x + wobble * 0.5, yy + 0.4, z + tw * 0.5), heat * 0.85f);
			}
			spawnAfterglow(new Vec3(x + wobble, yy, z + tw),
					new AABB(x + wobble - half, yy - half * 0.55, z + tw - half,
							x + wobble + half, yy + half * 1.1, z + tw + half),
					heat, 1f);
		}
		// Hot crater bowl under the column
		float bowl = Math.min(5f, 2.2f + h * 0.04f);
		int bi = Mth.ceil(bowl);
		BlockPos origin = BlockPos.containing(x, y, z);
		for (int dx = -bi; dx <= bi; dx++) {
			for (int dz = -bi; dz <= bi; dz++) {
				if (dx * dx + dz * dz > bowl * bowl) {
					continue;
				}
				float fall = 1f - (float) (Math.sqrt(dx * dx + dz * dz) / (bowl + 0.2));
				injectHeat(origin.offset(dx, 0, dz), Mth.clamp(0.55f + fall * 0.4f, 0.45f, 0.95f));
			}
		}
	}

	/**
	 * Thermal blast: white-hot core, cooling shell, rising plume + drifting embers.
	 */
	public void explode(double x, double y, double z, float radius) {
		float r = Mth.clamp(radius, 0.5f, 24f);
		int ri = Mth.ceil(r);
		BlockPos origin = BlockPos.containing(x, y, z);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dy = -ri; dy <= ri; dy++) {
				for (int dz = -ri; dz <= ri; dz++) {
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (dist > r + 0.35) {
						continue;
					}
					float falloff = 1f - (float) (dist / (r + 0.35));
					float heat = Mth.clamp(0.55f + falloff * 0.45f, 0.4f, 1f);
					if (dist < r * 0.35) {
						heat = 1f;
					} else if (dist < r * 0.65) {
						heat = Math.max(heat, 0.85f);
					}
					injectHeat(origin.offset(dx, dy, dz), heat);
				}
			}
		}
		// Flash core
		spawnAfterglow(new Vec3(x, y + 0.2, z),
				new AABB(x - r * 0.55, y - 0.15, z - r * 0.55, x + r * 0.55, y + r * 0.45, z + r * 0.55),
				1f, 1f);
		// Rising hot gas column (narrower tongues)
		int plumes = 8;
		for (int i = 0; i < plumes; i++) {
			double yy = y + 0.25 + i * (0.4 + r * 0.06);
			double wobble = Math.sin(i * 1.7) * r * 0.08;
			double tw = Math.cos(i * 1.3) * r * 0.08;
			float heat = Mth.clamp(0.98f - i * 0.08f, 0.4f, 1f);
			double half = Math.max(0.22, r * 0.18 * (1.0 - i / (double) plumes));
			spawnAfterglow(new Vec3(x + wobble, yy, z + tw),
					new AABB(x + wobble - half, yy - half * 0.7, z + tw - half,
							x + wobble + half, yy + half * 0.9, z + tw + half),
					heat, 1f);
		}
		// Embers / fragments
		for (int i = 0; i < 10; i++) {
			double ang = i * 2.399963; // golden angle
			double rad = r * (0.35 + (i % 3) * 0.18);
			double ex = x + Math.cos(ang) * rad;
			double ez = z + Math.sin(ang) * rad;
			double ey = y + 0.15 + (i % 4) * 0.35;
			spawnAfterglow(new Vec3(ex, ey, ez),
					new AABB(ex - 0.22, ey - 0.18, ez - 0.22, ex + 0.22, ey + 0.28, ez + 0.22),
					Mth.clamp(0.9f - i * 0.04f, 0.5f, 1f), 0.85f);
		}
	}

	public float entityTemperature(LivingEntity entity) {
		if (entity == null) {
			return 0.3f;
		}
		float body = entityTemp.getOrDefault(entity.getUUID(), bodyTarget(entity));
		float barrel = barrelHeat.getOrDefault(entity.getUUID(), 0f);
		// Hot barrel / recent muzzle flash warms the weapon-side silhouette under FLIR
		return Mth.clamp(body + barrel * 0.28f, 0f, 1f);
	}

	public float barrelHeat(UUID id) {
		return id == null ? 0f : barrelHeat.getOrDefault(id, 0f);
	}

	public float blockTemperature(Minecraft mc, BlockPos pos) {
		if (mc.level == null || pos == null) {
			return 0.3f;
		}
		float day = 1f - NvgVision.skyDarken01(mc);
		return sampleBlock(mc, pos, mc.level.getBlockState(pos), day);
	}

	private float sampleBlock(Minecraft mc, BlockPos pos, BlockState state, float day) {
		ThermalMaterial.Props props = ThermalMaterial.of(state);
		float t = props.baseTemp();

		float sky = mc.level.getBrightness(LightLayer.SKY, pos) / 15f;
		float block = mc.level.getBrightness(LightLayer.BLOCK, pos) / 15f;
		boolean canSeeSky = mc.level.canSeeSky(pos);

		if (canSeeSky) {
			float sun = ThermalMaterial.sunBias(day, sky);
			float recept = Mth.clamp(1.45f - props.thermalMass() * 0.5f, 0.35f, 1.4f);
			t += sun * recept;
		} else {
			t -= ThermalMaterial.shadeBias(false, sky);
			t -= day * 0.06f; // daytime shade reads clearly cooler
		}
		// Sunline / shade edge contrast against neighbors
		if (day > 0.15f) {
			t += sunShadeEdge(mc, pos, canSeeSky, day);
		}
		float rain = 0f;
		try {
			rain = mc.level.getRainLevel(1f);
		} catch (Throwable ignored) {
		}
		if (canSeeSky && rain > 0.05f) {
			t -= ThermalMaterial.rainBias(rain);
		}
		if (sky < 0.15f && pos.getY() < mc.level.getSeaLevel()) {
			t -= 0.1f;
		}
		if (!props.heatSource()) {
			t += block * 0.14f;
		} else {
			t = Math.max(t, props.baseTemp());
		}

		Float fp = footprints.get(pos);
		if (fp != null) {
			t = Math.max(t, fp);
		}
		Float ct = contacts.get(pos);
		if (ct != null) {
			t = Math.max(t, ct);
		}
		Float tr = transients.get(pos);
		if (tr != null) {
			t = Math.max(t, tr);
		}

		t = 0.08f + (t - 0.08f) * Mth.clamp(props.emissivity(), 0.18f, 1f);
		return Mth.clamp(t, 0f, 1f);
	}

	/** Boost contrast where sunlit ground meets shade (tree/building edge). */
	private static float sunShadeEdge(Minecraft mc, BlockPos pos, boolean canSeeSky, float day) {
		float edge = 0f;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = pos.relative(d);
			boolean nSky = mc.level.canSeeSky(n);
			if (canSeeSky && !nSky) {
				edge += 0.045f; // warm lip on sunny side
			} else if (!canSeeSky && nSky) {
				edge -= 0.04f; // cool lip in shade
			}
		}
		BlockPos above = pos.above();
		boolean aSky = mc.level.canSeeSky(above);
		if (canSeeSky && !aSky) {
			edge += 0.03f;
		} else if (!canSeeSky && aSky) {
			edge -= 0.025f;
		}
		return Mth.clamp(edge * day, -0.12f, 0.14f);
	}

	private static float bodyTarget(LivingEntity living) {
		if (living instanceof Blaze) {
			return 0.92f;
		}
		if (living instanceof Player) {
			return 0.58f;
		}
		if (living instanceof Animal) {
			return 0.54f;
		}
		String id = living.getType().builtInRegistryHolder().key().identifier().getPath();
		if (id.contains("blaze") || id.contains("magma") || id.contains("strider") || id.contains("wither")) {
			return 0.9f;
		}
		if (id.contains("snow") || id.contains("stray")) {
			return 0.2f;
		}
		if (id.contains("zombie") || id.contains("skeleton") || id.contains("phantom")) {
			return 0.4f;
		}
		if (living.isOnFire()) {
			return 0.88f;
		}
		return 0.52f;
	}

	private static void decayMap(Map<BlockPos, Float> map, float dt, float rate) {
		Iterator<Map.Entry<BlockPos, Float>> it = map.entrySet().iterator();
		float keep = (float) Math.exp(-rate * dt);
		while (it.hasNext()) {
			Map.Entry<BlockPos, Float> e = it.next();
			float v = e.getValue() * keep;
			if (v < 0.04f) {
				it.remove();
			} else {
				e.setValue(v);
			}
		}
	}

	private static void trimOldest(Map<BlockPos, Float> map, int remove) {
		Iterator<BlockPos> it = map.keySet().iterator();
		int n = 0;
		while (it.hasNext() && n < remove) {
			it.next();
			it.remove();
			n++;
		}
	}

	public Map<BlockPos, Float> footprintsView() {
		return footprints;
	}

	public Map<BlockPos, Float> contactsView() {
		return contacts;
	}

	public Map<BlockPos, Float> transientsView() {
		return transients;
	}

	public List<BlockPos> fireCacheView() {
		return fireCache;
	}

	public List<Afterglow> afterglowView() {
		return afterglow;
	}

	public List<BarrelGlow> barrelsView() {
		return barrels;
	}

	public List<LaserSpot> laserSpotsView() {
		return laserSpots;
	}
}
