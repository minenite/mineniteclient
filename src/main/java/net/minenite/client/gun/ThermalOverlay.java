package net.minenite.client.gun;

import net.minenite.client.gun.vision.OpticalMaterial;
import net.minenite.client.gun.vision.TemperatureField;
import net.minenite.client.gun.vision.ThermalAgc;
import net.minenite.client.gun.vision.ThermalMaterial;
import net.minenite.client.gun.vision.ThermalPalette;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Iris-safe thermal compositor (HUD).
 * Translucent wash + depth-sampled heat markers, AGC, afterglow, soft occlusion.
 */
public final class ThermalOverlay {
	private static final Matrix4f MVP = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static final float[] SCR = new float[2];

	private static long frame;
	private static String toast;
	private static int toastTicks;

	private record Marker(double dist, float temp, float x0, float y0, float x1, float y1,
						  float transmit, boolean living, boolean bloom) {
	}

	private ThermalOverlay() {
	}

	public static void toast(String msg) {
		if (msg == null || msg.isBlank()) {
			return;
		}
		toast = msg;
		toastTicks = 40;
	}

	public static void tickToast() {
		if (toastTicks > 0) {
			toastTicks--;
		}
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !ThermalVision.isWearing(player)) {
			return;
		}

		float pt = 1f;
		float dt = 1f / 20f;
		try {
			var delta = mc.getDeltaTracker();
			pt = delta.getGameTimeDeltaPartialTick(false);
			dt = Math.max(0.001f, delta.getGameTimeDeltaTicks() / 20f);
		} catch (Throwable ignored) {
		}
		frame++;
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		TemperatureField field = TemperatureField.get();
		ThermalPalette palette = field.palette();
		ThermalAgc agc = ThermalAgc.get();

		Camera camera = mc.gameRenderer.mainCamera();
		camera.getViewRotationProjectionMatrix(MVP);
		Vec3 camPos = camera.position();
		boolean mono = isMonoPalette(palette);

		// White/Black Hot: mid-grey cool plate (not pure black/white) so silhouettes stay readable.
		if (palette == ThermalPalette.WHITE_HOT) {
			graphics.fill(0, 0, w, h, palette.color(0.30f, 0.78f));
			graphics.fill(0, 0, w, h, palette.color(0.36f, 0.28f));
		} else if (palette == ThermalPalette.BLACK_HOT) {
			// Inverted: mid grey ≈ t 0.40–0.48 (not t≈0.1 which blows to white)
			graphics.fill(0, 0, w, h, palette.color(0.42f, 0.78f));
			graphics.fill(0, 0, w, h, palette.color(0.48f, 0.28f));
		} else {
			graphics.fill(0, 0, w, h, palette.color(0.12f, 0.38f));
			graphics.fill(0, 0, w, h, palette.color(0.2f, 0.16f));
		}

		List<Marker> markers = new ArrayList<>(400);
		collectBlocks(mc, player, field, markers, camPos, agc, mono);
		collectFire(mc, player, field, markers, camPos, agc);
		collectContacts(mc, player, field, markers, camPos, agc);
		collectTransientHeats(mc, player, field, markers, camPos, agc);
		collectAfterglow(mc, player, field, markers, camPos, agc, pt);
		// Tight muzzle/barrel tips only (dist cull + no bloom) — heat also warms entity silhouettes
		collectBarrels(mc, player, field, markers, camPos, agc, pt);
		collectEntities(mc, player, field, markers, pt, camPos, agc);
		agc.endFrame(dt);
		markers.sort(Comparator.comparingDouble((Marker m) -> -m.dist));

		for (Marker m : markers) {
			float temp = agc.map(m.temp);
			// Living heat must stay in the warm band — distant AGC used to map bodies to black squares
			if (m.living) {
				temp = Mth.clamp(Math.max(temp, 0.58f) * 0.35f + Math.max(m.temp, 0.5f) * 0.65f, 0.55f, 1f);
			} else if (mono && !m.living && temp < 0.45f) {
				// Cool solids → mid greys with enough spread to read block edges
				if (palette == ThermalPalette.WHITE_HOT) {
					temp = Mth.clamp(0.24f + temp * 0.38f, 0.22f, 0.50f);
				} else {
					temp = Mth.clamp(0.34f + temp * 0.30f, 0.32f, 0.58f);
				}
			}
			float a;
			if (m.living) {
				a = Mth.clamp(0.48f + temp * 0.48f, 0.45f, 0.96f) * Math.max(0.55f, m.transmit);
			} else if (mono && temp < 0.55f) {
				a = Mth.clamp(0.38f + temp * 0.35f, 0.34f, 0.72f) * m.transmit;
			} else if (temp < 0.35f) {
				a = Mth.clamp(0.16f + temp * 0.32f, 0.12f, 0.36f) * m.transmit;
			} else {
				a = Mth.clamp(0.32f + temp * 0.55f, 0.28f, 0.88f) * m.transmit;
			}
			float wash = 0f;
			if (wash > 0.02f) {
				float far = Mth.clamp((float) (m.dist / 48.0), 0f, 1f);
				temp = Mth.lerp(wash * (0.45f + far * 0.55f), temp, 0.42f);
				a *= Mth.lerp(wash * far, 1f, 0.35f);
			}
			paintMarker(graphics, palette, m, temp, a);
		}

		// Laser = impact dots only (~2px), no beam squares/filaments
		drawLaserImpactDots(graphics, palette, field, camPos, player.getEyePosition(pt));

		if (!mono) {
			drawNoise(graphics, w, h, palette);
		} else {
			drawNoise(graphics, w, h, palette, 18, 0.03f); // faint sensor grain only
		}
		float stormWash = 0f;
		if (stormWash > 0.05f) {
			drawNoise(graphics, w, h, palette, (int) (30 + stormWash * 80), 0.04f + stormWash * 0.08f);
		}
		try {
			float smokeBlock = 0f;
			if (smokeBlock > 0.05f) {
				// Thermal sees through thinner smoke — only thick/thermal-obscuring blinds
				int milky = Mth.clamp((int) (smokeBlock * smokeBlock * 140), 0, 155);
				graphics.fill(0, 0, w, h, ARGB.color(milky, 120, 120, 125));
				drawNoise(graphics, w, h, palette, (int) (20 + smokeBlock * 100), 0.03f + smokeBlock * 0.1f);
			}
		} catch (Throwable ignored) {
			// ignore
		}
		drawVignette(graphics, w, h);
		drawModeHud(graphics, w, h, palette);
	}

	private static void paintMarker(GuiGraphicsExtractor graphics, ThermalPalette palette,
									Marker m, float temp, float a) {
		int x0 = Math.round(m.x0);
		int y0 = Math.round(m.y0);
		int x1 = Math.round(m.x1);
		int y1 = Math.round(m.y1);
		graphics.fill(x0, y0, x1, y1, palette.color(temp, a));

		if (m.living) {
			// Soft core — keep the marker's aspect (no square bloom rings)
			float pw = Math.max(0.5f, (m.x1 - m.x0) * 0.18f);
			float ph = Math.max(0.5f, (m.y1 - m.y0) * 0.14f);
			graphics.fill(
					Math.round(m.x0 + pw), Math.round(m.y0 + ph),
					Math.round(m.x1 - pw), Math.round(m.y1 - ph),
					palette.color(Math.min(1f, temp + 0.08f), a * 0.55f)
			);
			float padX = Math.max(0.6f, (m.x1 - m.x0) * 0.07f);
			float padY = Math.max(0.6f, (m.y1 - m.y0) * 0.07f);
			graphics.fill(
					Math.round(m.x0 - padX), Math.round(m.y0 - padY),
					Math.round(m.x1 + padX), Math.round(m.y1 + padY),
					palette.color(Math.min(1f, temp + 0.02f), a * 0.18f)
			);
		}

		if (m.bloom || temp > 0.9f) {
			float pad = Math.max(3f, Math.max(m.x1 - m.x0, m.y1 - m.y0) * 0.35f);
			graphics.fill(
					Math.round(m.x0 - pad), Math.round(m.y0 - pad),
					Math.round(m.x1 + pad), Math.round(m.y1 + pad),
					palette.color(Math.min(1f, temp + 0.04f), a * 0.18f)
			);
			pad *= 1.55f;
			graphics.fill(
					Math.round(m.x0 - pad), Math.round(m.y0 - pad),
					Math.round(m.x1 + pad), Math.round(m.y1 + pad),
					palette.color(1f, a * 0.08f)
			);
		} else if (!m.living && temp > 0.75f) {
			float pad = Math.max(2f, (m.x1 - m.x0) * 0.12f);
			graphics.fill(
					Math.round(m.x0 - pad), Math.round(m.y0 - pad),
					Math.round(m.x1 + pad), Math.round(m.y1 + pad),
					palette.color(Math.min(1f, temp + 0.05f), a * 0.35f)
			);
		}
	}

	private static boolean isMonoPalette(ThermalPalette palette) {
		return palette == ThermalPalette.WHITE_HOT || palette == ThermalPalette.BLACK_HOT;
	}

	private static void collectBlocks(Minecraft mc, LocalPlayer player, TemperatureField field, List<Marker> out,
									  Vec3 camPos, ThermalAgc agc, boolean mono) {
		BlockPos origin = player.blockPosition();
		Vec3 eye = player.getEyePosition(1f);
		int range = mono ? 32 : 28;
		for (int dx = -range; dx <= range; dx++) {
			for (int dy = -8; dy <= 10; dy++) {
				for (int dz = -range; dz <= range; dz++) {
					int dist2 = dx * dx + dy * dy + dz * dz;
					if (dist2 > range * range) {
						continue;
					}
					int stride = dist2 > 64 ? (dist2 > 196 ? 3 : 2) : 1;
					if (mono && dist2 <= 100) {
						stride = 1; // denser cool silhouettes up close
					}
					if ((dx % stride) != 0 || (dy % Math.max(1, stride - 1)) != 0 || (dz % stride) != 0) {
						continue;
					}
					BlockPos pos = origin.offset(dx, dy, dz);
					BlockState state = mc.level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					// Leaves/plants / laser LIGHT cubes: never stamp
					if (ThermalMaterial.isVegetation(state) || ThermalMaterial.isLightBlock(state)) {
						continue;
					}
					float temp = field.blockTemperature(mc, pos);
					ThermalMaterial.Props props = ThermalMaterial.of(state);
					Float fp = field.footprintsView().get(pos);
					if (fp != null) {
						temp = Math.max(temp, fp);
					}
					Float tr = field.transientsView().get(pos);
					if (tr != null) {
						temp = Math.max(temp, tr);
					}
					// Mono: keep cool solids so they stamp flat grey/black silhouettes
					if (!mono) {
						if (!props.heatSource() && temp < 0.18f && dist2 > 225) {
							continue;
						}
						if (!props.heatSource() && temp < 0.1f && dist2 > 81) {
							continue;
						}
					} else if (!props.heatSource() && temp < 0.08f && dist2 > 400) {
						continue;
					}
					// Fire/lava handled by collectFire (stride-free, longer range)
					if (ThermalMaterial.isFlame(state) || ThermalMaterial.isLava(state)
							|| state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
						continue;
					}
					Vec3 center = Vec3.atCenterOf(pos);
					float transmit = occlusionTransmit(mc, eye, center, player);
					if (transmit <= 0.05f) {
						continue;
					}
					float[] screen = projectAabb(new AABB(pos), camPos, false, 0);
					if (screen == null) {
						continue;
					}
					agc.observe(temp, false);
					boolean bloom = props.heatSource() && temp > 0.82f;
					out.add(new Marker(eye.distanceTo(center), temp, screen[0], screen[1], screen[2], screen[3],
							transmit, false, bloom));
				}
			}
		}
		for (Map.Entry<BlockPos, Float> e : field.footprintsView().entrySet()) {
			if (e.getValue() < 0.25f) {
				continue;
			}
			BlockPos pos = e.getKey();
			Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5);
			float transmit = occlusionTransmit(mc, eye, center, player);
			if (transmit <= 0.05f) {
				continue;
			}
			float[] screen = projectAabb(new AABB(pos.getX(), pos.getY() + 0.95, pos.getZ(),
					pos.getX() + 1, pos.getY() + 1.05, pos.getZ() + 1), camPos, false, 0);
			if (screen == null) {
				continue;
			}
			agc.observe(e.getValue(), false);
			out.add(new Marker(eye.distanceTo(center), e.getValue(), screen[0], screen[1], screen[2], screen[3],
					transmit * 0.85f, false, false));
		}
	}

	/**
	 * Fire / lava / magma: view ray-grid (to ~128m) + omnidirectional cache.
	 * Heat LOS is very forgiving — only solid walls fully hide a source.
	 */
	private static final int MAX_FIRE_PAINT = 18;

	private static void collectFire(Minecraft mc, LocalPlayer player, TemperatureField field,
									List<Marker> out, Vec3 camPos, ThermalAgc agc) {
		Vec3 eye = player.getEyePosition(1f);

		// 1) Sparse frustum discover (cheap) — fills the capped fire cache
		discoverFireAlongView(mc, player, field, eye);

		// 2) Paint nearest sources only — plume markers are expensive
		List<BlockPos> cache = field.fireCacheView();
		if (cache.isEmpty()) {
			return;
		}
		List<BlockPos> nearest = new ArrayList<>(cache);
		nearest.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(eye.x, eye.y, eye.z)));
		int painted = 0;
		for (BlockPos pos : nearest) {
			if (painted >= MAX_FIRE_PAINT) {
				break;
			}
			BlockState state = mc.level.getBlockState(pos);
			if (!isThermalFire(state)) {
				continue;
			}
			int before = out.size();
			paintFireAt(mc, player, field, out, camPos, agc, eye, pos, state);
			if (out.size() > before) {
				painted++;
			}
		}
	}

	/** Sparse ray grid through the FOV — finds fire/lava without melting FPS. */
	private static void discoverFireAlongView(Minecraft mc, LocalPlayer player, TemperatureField field, Vec3 eye) {
		Vec3 look = player.getViewVector(1f);
		Vec3 worldUp = new Vec3(0, 1, 0);
		Vec3 right = look.cross(worldUp);
		if (right.lengthSqr() < 1.0E-8) {
			right = new Vec3(1, 0, 0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();
		// ~9×7 rays × ~48 steps (was 25×17×128) — still finds lakes in view
		for (int iy = -3; iy <= 3; iy++) {
			for (int ix = -4; ix <= 4; ix++) {
				Vec3 dir = look.add(right.scale(ix * 0.07)).add(up.scale(iy * 0.065)).normalize();
				for (float t = 2f; t <= 96f; t += 2.0f) {
					BlockPos pos = BlockPos.containing(
							eye.x + dir.x * t,
							eye.y + dir.y * t,
							eye.z + dir.z * t
					);
					BlockState state = mc.level.getBlockState(pos);
					if (isThermalFire(state)) {
						field.noteFire(pos);
					} else if (!state.isAir() && state.canOcclude()) {
						break;
					}
				}
			}
		}
	}

	private static boolean isThermalFire(BlockState state) {
		return ThermalMaterial.isFlame(state) || ThermalMaterial.isLava(state)
				|| state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
	}

	private static void paintFireAt(Minecraft mc, LocalPlayer player, TemperatureField field,
									List<Marker> out, Vec3 camPos, ThermalAgc agc, Vec3 eye,
									BlockPos pos, BlockState state) {
		float temp = Math.max(0.94f, field.blockTemperature(mc, pos));
		Float tr = field.transientsView().get(pos);
		if (tr != null) {
			temp = Math.max(temp, tr);
		}
		if (ThermalMaterial.isLava(state) || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
			temp = 1f;
		}
		// Aim high so floor collider under the flame doesn't kill LOS
		Vec3 aim = new Vec3(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5);
		float transmit = heatTransmit(mc, eye, aim, player, pos);
		if (transmit <= 0.02f) {
			return;
		}
		double dist = eye.distanceTo(aim);
		if (dist > 140) {
			return;
		}
		// Stay hot at range — do not fade out by 20–30m
		float rangeKeep = dist > 100 ? Mth.clamp(1f - (float) ((dist - 100) / 50f) * 0.25f, 0.7f, 1f) : 1f;
		emitFirePlume(out, eye, camPos, agc, pos, state, temp * rangeKeep,
				Math.max(0.55f, transmit) * rangeKeep, frame, dist);
	}

	/**
	 * Soft LOS for heat. Only a solid wall well in front of the source hides it.
	 * Glass/leaves/lips near the flame still show a strong signature.
	 */
	private static float heatTransmit(Minecraft mc, Vec3 eye, Vec3 target, LocalPlayer player, BlockPos heatPos) {
		double full = eye.distanceTo(target);
		if (full < 1.0E-4) {
			return 1f;
		}
		// Primary aim
		float a = heatClip(mc, eye, target, player, heatPos);
		if (a >= 0.5f) {
			return a;
		}
		// Retry slightly above — cresting a hill / lava lake rim
		Vec3 high = target.add(0, 1.25, 0);
		float b = heatClip(mc, eye, high, player, heatPos);
		return Math.max(a, b);
	}

	private static float heatClip(Minecraft mc, Vec3 eye, Vec3 target, LocalPlayer player, BlockPos heatPos) {
		BlockHitResult hit = mc.level.clip(new ClipContext(
				eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return 1f;
		}
		BlockPos hitPos = hit.getBlockPos();
		if (hitPos.equals(heatPos) || hitPos.distManhattan(heatPos) <= 2) {
			return 1f;
		}
		BlockState state = mc.level.getBlockState(hitPos);
		if (isThermalFire(state) || ThermalMaterial.isLightBlock(state)) {
			return 1f;
		}
		String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
		OpticalMaterial.Props opt = OpticalMaterial.of(state);
		if (opt.glass() || id.contains("glass")) {
			return 0.85f;
		}
		if (ThermalMaterial.isVegetation(state) || id.contains("leaves") || id.contains("vine")) {
			return 0.7f;
		}
		double toHeat = hit.getLocation().distanceTo(target);
		if (toHeat < 3.0) {
			return 0.8f; // lip / pool edge
		}
		return 0f;
	}

	/**
	 * Multi-layer fire / lava plume: hot base, flickering tongues, soft bloom halo.
	 * LOD keeps tongues at range so distant fires stay detailed, not single pixels.
	 */
	private static void emitFirePlume(List<Marker> out, Vec3 eye, Vec3 camPos, ThermalAgc agc,
									  BlockPos pos, BlockState state, float baseTemp, float transmit,
									  long frame, double dist) {
		boolean lava = ThermalMaterial.isLava(state);
		boolean soul = state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
		boolean camp = state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
				|| state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
		boolean magma = state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + (lava ? 0.85 : (camp ? 0.45 : (magma ? 0.95 : 0.12)));
		double z = pos.getZ() + 0.5;
		int hash = pos.hashCode();
		float flicker = 0.82f + 0.18f * (float) Math.sin((frame + (hash & 255)) * 0.62);
		float flicker2 = 0.88f + 0.12f * (float) Math.cos((frame * 0.47) + (hash & 127));
		float core = Mth.clamp(Math.max(baseTemp, lava || magma ? 1f : 0.94f) * flicker, 0.78f, 1f);

		int lod = dist > 48 ? 2 : (dist > 24 ? 1 : 0);
		// Far heat must stay a readable hot blotch, not a sub-pixel spark
		float minPx = dist > 90 ? 12f : (dist > 55 ? 9f : (dist > 30 ? 6f : 3.5f));
		double rise = lava || magma ? 0.7 : (soul ? 1.15 : (camp ? 1.35 : 1.4));

		// Always: compact hot core (1 marker)
		addFirePart(out, eye, camPos, agc, transmit, core, true, minPx,
				x - (lava ? 0.5 : 0.34), y - 0.08, z - (lava ? 0.5 : 0.34),
				x + (lava ? 0.5 : 0.34), y + (lava ? 0.35 : 0.55), z + (lava ? 0.5 : 0.34));

		// Close only: a couple of tongue markers (was 7–10+ per fire)
		if (lod == 0) {
			int tongues = lava || magma ? 2 : 3;
			for (int i = 0; i < tongues; i++) {
				float t = (i + 0.5f) / tongues;
				double wobble = Math.sin((frame * 0.45) + hash * 0.01 + i * 1.7) * (lava ? 0.1 : 0.16);
				double tw = Math.cos((frame * 0.37) + hash * 0.02 + i) * (lava ? 0.1 : 0.14);
				double yy0 = y + t * rise * 0.45;
				double yy1 = y + t * rise + 0.22;
				float heat = Mth.clamp(core * (1.05f - t * 0.45f) * flicker2, 0.5f, 1f);
				double half = (lava || magma ? 0.28 : 0.13) * (1.15 - t * 0.6);
				addFirePart(out, eye, camPos, agc, transmit * (1f - t * 0.3f), heat, t < 0.4f, minPx,
						x + wobble - half, yy0, z + tw - half,
						x + wobble + half, yy1, z + tw + half);
			}
		} else if (lod == 1) {
			// Mid: one tall blotch
			addFirePart(out, eye, camPos, agc, transmit * 0.55f, core * 0.75f, true, minPx + 1f,
					x - 0.35, y + rise * 0.3, z - 0.35,
					x + 0.35, y + rise + 0.35, z + 0.35);
		}
	}

	private static void addFirePart(List<Marker> out, Vec3 eye, Vec3 camPos, ThermalAgc agc,
									float transmit, float temp, boolean bloom, float minPx,
									double x0, double y0, double z0, double x1, double y1, double z1) {
		if (transmit <= 0.04f) {
			return;
		}
		AABB bb = new AABB(
				Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
				Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)
		);
		float[] screen = projectAabb(bb, camPos, false, 0);
		if (screen == null) {
			return;
		}
		// Keep far fire readable — grow tiny projected footprints
		float min = Math.max(3f, minPx);
		if (screen[2] - screen[0] < min) {
			float cx = (screen[0] + screen[2]) * 0.5f;
			screen[0] = cx - min * 0.5f;
			screen[2] = cx + min * 0.5f;
		}
		if (screen[3] - screen[1] < min * 1.2f) {
			float cy = (screen[1] + screen[3]) * 0.5f;
			float half = min * 0.6f;
			screen[1] = cy - half;
			screen[3] = cy + half;
		}
		Vec3 c = bb.getCenter();
		agc.observe(temp, false);
		out.add(new Marker(eye.distanceTo(c), Mth.clamp(temp, 0f, 1f),
				screen[0], screen[1], screen[2], screen[3],
				Mth.clamp(transmit, 0f, 1f), false, bloom));
	}

	private static void collectContacts(Minecraft mc, LocalPlayer player, TemperatureField field,
										List<Marker> out, Vec3 camPos, ThermalAgc agc) {
		Vec3 eye = player.getEyePosition(1f);
		for (Map.Entry<BlockPos, Float> e : field.contactsView().entrySet()) {
			if (e.getValue() < 0.22f) {
				continue;
			}
			BlockPos pos = e.getKey();
			if (ThermalMaterial.isVegetation(mc.level.getBlockState(pos))) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(pos);
			float transmit = occlusionTransmit(mc, eye, center, player);
			if (transmit <= 0.05f) {
				continue;
			}
			float[] screen = projectAabb(new AABB(pos), camPos, false, 0);
			if (screen == null) {
				continue;
			}
			agc.observe(e.getValue(), false);
			out.add(new Marker(eye.distanceTo(center), e.getValue(), screen[0], screen[1], screen[2], screen[3],
					transmit * 0.75f, false, false));
		}
	}

	private static void collectBarrels(Minecraft mc, LocalPlayer player, TemperatureField field,
									   List<Marker> out, Vec3 camPos, ThermalAgc agc, float pt) {
		Vec3 eye = player.getEyePosition(pt);
		for (TemperatureField.BarrelGlow g : field.barrelsView()) {
			Vec3 center = new Vec3(g.x(), g.y(), g.z());
			double dist = eye.distanceTo(center);
			// Own muzzle sits on the lens — never paint a screen-filling heat bloom
			if (dist < 2.75) {
				continue;
			}
			float transmit = occlusionTransmit(mc, eye, center, player) * g.life();
			if (transmit <= 0.04f) {
				continue;
			}
			double s = 0.08 + 0.05 * g.life();
			float[] screen = projectAabb(new AABB(
					g.x() - s, g.y() - s, g.z() - s,
					g.x() + s, g.y() + s, g.z() + s
			), camPos, false, 0);
			if (screen == null) {
				continue;
			}
			// Warm tip only — no bloom whiteout
			float temp = Mth.clamp(0.62f + g.temp() * 0.22f * g.life(), 0.55f, 0.88f);
			agc.observe(temp, false);
			out.add(new Marker(dist, temp, screen[0], screen[1], screen[2], screen[3],
					transmit * 0.7f, false, false));
		}
	}

	private static void collectAfterglow(Minecraft mc, LocalPlayer player, TemperatureField field,
										 List<Marker> out, Vec3 camPos, ThermalAgc agc, float pt) {
		Vec3 eye = player.getEyePosition(pt);
		boolean drone = false;
		for (TemperatureField.Afterglow g : field.afterglowView()) {
			Vec3 center = new Vec3(g.x(), g.y(), g.z());
			double dist = eye.distanceTo(center);
			if (drone && dist > 820) {
				continue;
			}
			// Drone altitude: soft heat LOS (only solid walls hide); keep far blooms readable.
			float transmit = (drone
					? heatTransmit(mc, eye, center, player, BlockPos.containing(center))
					: occlusionTransmit(mc, eye, center, player)) * g.life();
			if (transmit <= 0.04f) {
				continue;
			}
			AABB bb = new AABB(
					g.x() - g.halfW(), g.y() - g.halfH(), g.z() - g.halfD(),
					g.x() + g.halfW(), g.y() + g.halfH(), g.z() + g.halfD()
			);
			float[] screen = projectAabb(bb, camPos, false, 0);
			if (screen == null) {
				continue;
			}
			if (drone || dist > 48) {
				float minPx = dist > 280 ? 16f : (dist > 160 ? 13f : (dist > 90 ? 10f : (dist > 48 ? 7f : 5f)));
				screen = ensureMinScreen(screen, minPx);
			}
			float temp = Math.max(0.45f, g.temp() * (0.55f + 0.45f * g.life()));
			agc.observe(temp, false);
			boolean bloom = temp > 0.72f;
			out.add(new Marker(dist + 0.08, temp, screen[0], screen[1], screen[2], screen[3],
					transmit * (drone ? 0.78f : 0.62f), false, bloom));
		}
	}

	/**
	 * Hot transient cells (explosion scorches) beyond the dense block scan — critical for MQ-9 FLIR.
	 */
	private static void collectTransientHeats(Minecraft mc, LocalPlayer player, TemperatureField field,
											 List<Marker> out, Vec3 camPos, ThermalAgc agc) {
		Vec3 eye = player.getEyePosition(1f);
		boolean drone = false;
		double maxDist = drone ? 780.0 : 120.0;
		double maxDist2 = maxDist * maxDist;
		int painted = 0;
		int budget = drone ? 220 : 80;
		for (Map.Entry<BlockPos, Float> e : field.transientsView().entrySet()) {
			float heat = e.getValue();
			if (heat < 0.38f) {
				continue;
			}
			BlockPos pos = e.getKey();
			Vec3 center = Vec3.atCenterOf(pos);
			double dist2 = eye.distanceToSqr(center);
			if (dist2 > maxDist2) {
				continue;
			}
			// Near field already covered by collectBlocks — skip duplicate close stamps on ground FLIR.
			if (!drone && dist2 < 36 * 36) {
				continue;
			}
			if (drone && dist2 < 28 * 28) {
				continue;
			}
			float transmit = drone
					? heatTransmit(mc, eye, center, player, pos)
					: occlusionTransmit(mc, eye, center, player);
			if (transmit <= 0.05f) {
				continue;
			}
			float[] screen = projectAabb(new AABB(pos), camPos, false, 0);
			if (screen == null) {
				continue;
			}
			double dist = Math.sqrt(dist2);
			if (drone || dist > 48) {
				float minPx = dist > 280 ? 14f : (dist > 160 ? 11f : (dist > 90 ? 8f : 5f));
				screen = ensureMinScreen(screen, minPx);
			}
			agc.observe(heat, false);
			out.add(new Marker(dist, heat, screen[0], screen[1], screen[2], screen[3],
					transmit * 0.7f, false, heat > 0.82f));
			if (++painted >= budget) {
				break;
			}
		}
	}

	private static float[] ensureMinScreen(float[] screen, float minPx) {
		float minX = screen[0], minY = screen[1], maxX = screen[2], maxY = screen[3];
		float aw = Math.max(0.5f, maxX - minX);
		float ah = Math.max(0.5f, maxY - minY);
		if (aw < minPx) {
			float cx = (minX + maxX) * 0.5f;
			minX = cx - minPx * 0.5f;
			maxX = cx + minPx * 0.5f;
		}
		if (ah < minPx) {
			float cy = (minY + maxY) * 0.5f;
			minY = cy - minPx * 0.5f;
			maxY = cy + minPx * 0.5f;
		}
		return new float[]{minX, minY, maxX, maxY};
	}

	private static void collectEntities(Minecraft mc, LocalPlayer player, TemperatureField field,
										List<Marker> out, float pt, Vec3 camPos, ThermalAgc agc) {
		Vec3 eye = player.getEyePosition(pt);
		// BigDrone altitude + zoom needs a much larger heat silhouette envelope.
		double inflate = 56.0;
		AABB box = player.getBoundingBox().inflate(inflate);
		for (Entity ent : mc.level.getEntities(player, box, e -> e instanceof LivingEntity && e.isAlive())) {
			LivingEntity living = (LivingEntity) ent;
			Vec3 aim = living.getEyePosition(pt);
			float transmit = occlusionTransmit(mc, eye, aim, player);
			if (transmit <= 0.05f) {
				continue;
			}
			float base = field.entityTemperature(living);
			double dist = eye.distanceTo(aim);
			agc.observe(base, true);
			emitLivingSilhouette(mc, player, living, base, transmit, dist, camPos, out, pt, eye);
		}
	}

	private static void emitLivingSilhouette(Minecraft mc, LocalPlayer player, LivingEntity living,
											 float base, float transmit, double dist, Vec3 camPos,
											 List<Marker> out, float pt, Vec3 eye) {
		AABB bb = living.getBoundingBox();
		double bw = bb.getXsize(), bh = bb.getYsize(), bd = bb.getZsize();
		if (bw < 1e-3 || bh < 1e-3 || bd < 1e-3) {
			return;
		}
		boolean biped = bh >= bw * 1.15 && bh >= bd * 1.15;
		boolean tiny = bh < 0.85;
		// Keep body heat in warm band so AGC/lava scenes don't paint black silhouettes
		float warm = Math.max(base, 0.62f);

		// Screen-space LOD (zoom makes distant mobs large — still use full shape)
		float[] footprint = projectAabb(bb, camPos, false, dist);
		float screenH = footprint == null ? 6f : Math.max(2f, footprint[3] - footprint[1]);
		float screenW = footprint == null ? 3f : Math.max(1.5f, footprint[2] - footprint[0]);

		// Yaw-oriented body mask samples → zombie/player reads as a figure, not a box
		emitOrientedBodySamples(mc, player, living, bb, warm, transmit, dist, camPos, out, pt, eye,
				biped, tiny, screenH, screenW);
	}

	/**
	 * Sample a biped/quad occupancy mask in the entity's facing frame and stamp small warm
	 * cells. Density follows on-screen height so zoomed/far targets keep a readable silhouette.
	 */
	private static void emitOrientedBodySamples(Minecraft mc, LocalPlayer player, LivingEntity living, AABB bb,
												float warm, float transmit, double dist, Vec3 camPos,
												List<Marker> out, float pt, Vec3 eye,
												boolean biped, boolean tiny, float screenH, float screenW) {
		float yaw = living.getYRot(pt) * ((float) Math.PI / 180f);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = fz;
		double rz = -fx;
		Vec3 center = bb.getCenter();
		double halfW = bb.getXsize() * 0.48;
		double halfH = bb.getYsize() * 0.5;
		double halfD = bb.getZsize() * 0.42;
		double bodyW = Math.max(bb.getXsize(), bb.getZsize()) * 0.5;
		halfW = bodyW * 0.95;
		halfD = bodyW * 0.72;

		int nv;
		int nu;
		int nw;
		if (tiny) {
			nv = screenH > 24 ? 5 : 4;
			nu = 3;
			nw = 2;
		} else if (screenH > 90) {
			nv = 12;
			nu = 7;
			nw = 5;
		} else if (screenH > 48) {
			nv = 10;
			nu = 6;
			nw = 4;
		} else if (screenH > 26) {
			nv = 9;
			nu = 5;
			nw = 3;
		} else if (screenH > 14) {
			nv = 8;
			nu = 4;
			nw = 3;
		} else if (screenH > 7) {
			nv = 7;
			nu = 4;
			nw = 2;
		} else {
			nv = 6;
			nu = 3;
			nw = 2;
		}

		boolean doOcclusion = dist < 22.0 && screenH > 18f;
		Vec3 toCam = eye.subtract(center);
		Vec3 view = toCam.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : toCam.normalize();

		// Cell size tracks on-screen figure so far silhouettes stay thin/tall, not fat squares
		double cellScale = Mth.clamp(Math.min(bb.getXsize(), bb.getZsize()) / (nu * 1.35), 0.04, 0.22);
		if (screenH < 12f) {
			cellScale *= 0.72;
		}

		for (int iy = 0; iy < nv; iy++) {
			float v = (iy + 0.5f) / nv;
			for (int ix = 0; ix < nu; ix++) {
				float u = (ix + 0.5f) / nu;
				for (int iz = 0; iz < nw; iz++) {
					float w = (iz + 0.5f) / nw;
					float occupy = tiny ? (0.55f + 0.45f * (1f - Math.abs(u - 0.5f) * 2f))
							: (biped ? bipedMask(u, v, w) : quadMask(u, v, w));
					if (occupy < 0.28f) {
						continue;
					}
					double lx = (u - 0.5) * 2.0 * halfW;
					double ly = (v - 0.5) * 2.0 * halfH;
					double lz = (w - 0.5) * 2.0 * halfD;
					double wx = center.x + rx * lx + fx * lz;
					double wy = center.y + ly;
					double wz = center.z + rz * lx + fz * lz;
					Vec3 sample = new Vec3(wx, wy, wz);
					if (sample.subtract(center).dot(view) < -0.22 * Math.max(halfW, halfD)) {
						continue;
					}
					float cellTx = 1f;
					if (doOcclusion) {
						cellTx = occlusionTransmit(mc, eye, sample, player);
						if (cellTx <= 0.06f) {
							continue;
						}
					}
					float part = partTempBias(v, biped);
					float t = Mth.clamp(Math.max(warm, 0.64f) * (0.82f + occupy * 0.2f) * part, 0.58f, 1f);
					// Stretch cells vertically for biped limbs so they don't read as pixels/squares
					double sx = cellScale * (biped && (v < 0.38f || v > 0.4f && v < 0.72f && (u < 0.25f || u > 0.75f))
							? 0.7 : 1.0);
					double sy = cellScale * (biped ? 1.45 : 1.1);
					double sz = cellScale * 0.85;
					float tx = Math.max(0.55f, transmit * cellTx * (0.75f + occupy * 0.25f));
					addLivingPart(out, dist + (1.0 - v) * 0.02, t, tx, camPos, false,
							wx - sx, wy - sy, wz - sz, wx + sx, wy + sy, wz + sz);
				}
			}
		}

		// Near/large: soft torso+head fill so the mask doesn't look sparse
		if (screenH > 22f && !tiny && biped) {
			emitSoftBipedFills(living, warm, transmit, dist, camPos, out, pt, bb);
		} else if (screenH > 22f && !tiny) {
			emitSoftQuadFills(living, warm, transmit, dist, camPos, out, pt, bb);
		}
	}

	private static void emitSoftBipedFills(LivingEntity living, float warm, float transmit, double dist,
										   Vec3 camPos, List<Marker> out, float pt, AABB bb) {
		float yaw = living.getYRot(pt) * ((float) Math.PI / 180f);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = fz;
		double rz = -fx;
		Vec3 c = bb.getCenter();
		double hw = Math.max(bb.getXsize(), bb.getZsize()) * 0.5;
		double hh = bb.getYsize() * 0.5;
		// torso
		stampOriented(out, dist, warm, transmit * 0.55f, camPos, c, rx, rz, fx, fz,
				0, -hh * 0.05, 0, hw * 0.42, hh * 0.28, hw * 0.28);
		// head
		stampOriented(out, dist - 0.02, Math.min(1f, warm + 0.14f), transmit * 0.6f, camPos, c, rx, rz, fx, fz,
				0, hh * 0.72, hw * 0.05, hw * 0.28, hh * 0.22, hw * 0.24);
		// legs
		stampOriented(out, dist + 0.02, warm * 0.9f, transmit * 0.5f, camPos, c, rx, rz, fx, fz,
				-hw * 0.22, -hh * 0.62, 0, hw * 0.16, hh * 0.32, hw * 0.16);
		stampOriented(out, dist + 0.02, warm * 0.9f, transmit * 0.5f, camPos, c, rx, rz, fx, fz,
				hw * 0.22, -hh * 0.62, 0, hw * 0.16, hh * 0.32, hw * 0.16);
		// arms
		stampOriented(out, dist + 0.01, warm * 0.94f, transmit * 0.48f, camPos, c, rx, rz, fx, fz,
				-hw * 0.62, hh * 0.05, 0, hw * 0.14, hh * 0.28, hw * 0.14);
		stampOriented(out, dist + 0.01, warm * 0.94f, transmit * 0.48f, camPos, c, rx, rz, fx, fz,
				hw * 0.62, hh * 0.05, 0, hw * 0.14, hh * 0.28, hw * 0.14);
	}

	private static void emitSoftQuadFills(LivingEntity living, float warm, float transmit, double dist,
										  Vec3 camPos, List<Marker> out, float pt, AABB bb) {
		float yaw = living.getYRot(pt) * ((float) Math.PI / 180f);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = fz;
		double rz = -fx;
		Vec3 c = bb.getCenter();
		double hw = Math.max(bb.getXsize(), bb.getZsize()) * 0.5;
		double hh = bb.getYsize() * 0.5;
		stampOriented(out, dist, warm, transmit * 0.55f, camPos, c, rx, rz, fx, fz,
				0, -hh * 0.1, 0, hw * 0.7, hh * 0.35, hw * 0.45);
		stampOriented(out, dist - 0.02, Math.min(1f, warm + 0.1f), transmit * 0.55f, camPos, c, rx, rz, fx, fz,
				0, hh * 0.35, hw * 0.35, hw * 0.32, hh * 0.28, hw * 0.28);
	}

	private static void stampOriented(List<Marker> out, double dist, float temp, float transmit, Vec3 camPos,
									  Vec3 center, double rx, double rz, double fx, double fz,
									  double lx, double ly, double lz, double sx, double sy, double sz) {
		double wx = center.x + rx * lx + fx * lz;
		double wy = center.y + ly;
		double wz = center.z + rz * lx + fz * lz;
		addLivingPart(out, dist, temp, transmit, camPos, false,
				wx - sx, wy - sy, wz - sz, wx + sx, wy + sy, wz + sz);
	}

	private static float bipedMask(float u, float v, float w) {
		float dx = u - 0.5f;
		float dz = w - 0.5f;
		float r2 = dx * dx + dz * dz;
		if (v > 0.72f) {
			return r2 < 0.085f ? 1f : (r2 < 0.13f ? 0.55f : 0f);
		}
		if (v > 0.38f) {
			float torso = r2 < 0.12f ? 1f : 0f;
			boolean armL = u < 0.22f && v < 0.72f && v > 0.4f && Math.abs(dz) < 0.28f;
			boolean armR = u > 0.78f && v < 0.72f && v > 0.4f && Math.abs(dz) < 0.28f;
			if (armL || armR) {
				return 0.85f;
			}
			return torso;
		}
		boolean legL = u > 0.12f && u < 0.42f && Math.abs(dz) < 0.28f;
		boolean legR = u > 0.58f && u < 0.88f && Math.abs(dz) < 0.28f;
		return (legL || legR) ? 0.75f : 0f;
	}

	private static float quadMask(float u, float v, float w) {
		float dx = u - 0.5f;
		float dz = w - 0.5f;
		float r2 = dx * dx * 0.7f + dz * dz;
		if (v < 0.25f) {
			return (Math.abs(dx) > 0.15f && Math.abs(dz) > 0.1f) ? 0.7f : 0f;
		}
		if (v > 0.7f) {
			return r2 < 0.2f ? 0.9f : 0f;
		}
		return r2 < 0.28f ? 1f : (r2 < 0.38f ? 0.5f : 0f);
	}

	private static float partTempBias(float v, boolean biped) {
		if (!biped) {
			return v > 0.7f ? 1.12f : (v < 0.25f ? 0.85f : 1.0f);
		}
		if (v > 0.78f) {
			return 1.18f;
		}
		if (v > 0.45f && v < 0.65f) {
			return 1.1f;
		}
		if (v < 0.35f) {
			return 0.88f;
		}
		return 1f;
	}

	private static void addLivingPart(List<Marker> out, double dist, float temp, float transmit, Vec3 camPos,
									  boolean bloom, double x0, double y0, double z0, double x1, double y1, double z1) {
		if (transmit <= 0.04f) {
			return;
		}
		float[] screen = projectAabb(new AABB(
				Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
				Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)
		), camPos, true, dist);
		if (screen == null) {
			return;
		}
		out.add(new Marker(dist, Mth.clamp(Math.max(temp, 0.52f), 0f, 1f), screen[0], screen[1], screen[2], screen[3],
				Mth.clamp(transmit, 0f, 1f), true, bloom));
	}

	/**
	 * Multi-hit occlusion: hard cut on stone/metal; soft leak through leaves/glass/scatter.
	 * Vegetation stacks attenuate gently (no blotchy hard holes in canopy).
	 */
	private static float occlusionTransmit(Minecraft mc, Vec3 eye, Vec3 target, LocalPlayer player) {
		double full = eye.distanceTo(target);
		if (full < 1.0E-4) {
			return 1f;
		}
		Vec3 dir = target.subtract(eye).normalize();
		Vec3 cursor = eye;
		float transmit = 1f;
		int foliageHits = 0;
		for (int step = 0; step < 8 && transmit > 0.04f; step++) {
			double remain = cursor.distanceTo(target);
			if (remain < 0.35) {
				return transmit;
			}
			BlockHitResult hit = mc.level.clip(new ClipContext(
					cursor, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
			));
			if (hit.getType() == HitResult.Type.MISS) {
				return transmit;
			}
			double hitDist = cursor.distanceTo(hit.getLocation());
			if (hitDist + 0.4 >= remain) {
				return transmit;
			}
			BlockState state = mc.level.getBlockState(hit.getBlockPos());
			OpticalMaterial.Props opt = OpticalMaterial.of(state);
			String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
			if (opt.glass() || id.contains("glass")) {
				transmit *= 0.84f;
			} else if (ThermalMaterial.isVegetation(state) || id.contains("leaves") || id.contains("vine")
					|| opt.scatter() > 0.4f) {
				foliageHits++;
				// First layers soft; deep canopy still readable (~25% floor)
				float layer = foliageHits <= 2 ? 0.78f : (foliageHits <= 4 ? 0.88f : 0.93f);
				transmit = Math.max(0.25f, transmit * layer);
			} else if (opt.transmission() > 0.2f || opt.water()) {
				transmit *= 0.58f;
			} else {
				return 0f; // hard cover
			}
			cursor = hit.getLocation().add(dir.scale(0.08));
		}
		return transmit;
	}

	private static float[] projectAabb(AABB bb, Vec3 camPos, boolean living, double dist) {
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
		int hits = 0;
		for (int c = 0; c < 8; c++) {
			double x = (c & 1) == 0 ? bb.minX : bb.maxX;
			double y = (c & 2) == 0 ? bb.minY : bb.maxY;
			double z = (c & 4) == 0 ? bb.minZ : bb.maxZ;
			if (!project(x, y, z, camPos, SCR)) {
				continue;
			}
			hits++;
			minX = Math.min(minX, SCR[0]);
			minY = Math.min(minY, SCR[1]);
			maxX = Math.max(maxX, SCR[0]);
			maxY = Math.max(maxY, SCR[1]);
		}
		if (hits < 1) {
			return null;
		}
		minX = Mth.clamp(minX, 0, w);
		minY = Mth.clamp(minY, 0, h);
		maxX = Mth.clamp(maxX, 0, w);
		maxY = Mth.clamp(maxY, 0, h);
		if (living) {
			// Preserve projected aspect — do NOT inflate into fat squares at range.
			float aw = Math.max(0.5f, maxX - minX);
			float ah = Math.max(0.5f, maxY - minY);
			float minW = ah > aw * 1.25f ? 1.0f : 1.4f;
			float minH = ah > aw * 1.25f ? 2.2f : 1.4f;
			if (aw < minW) {
				float cx = (minX + maxX) * 0.5f;
				minX = cx - minW * 0.5f;
				maxX = cx + minW * 0.5f;
			}
			if (ah < minH) {
				float cy = (minY + maxY) * 0.5f;
				minY = cy - minH * 0.5f;
				maxY = cy + minH * 0.5f;
			}
		} else {
			if (maxX - minX < 3f) {
				float cx = (minX + maxX) * 0.5f;
				minX = cx - 2f;
				maxX = cx + 2f;
			}
			if (maxY - minY < 3f) {
				float cy = (minY + maxY) * 0.5f;
				minY = cy - 2f;
				maxY = cy + 2f;
			}
			// Near-camera foreshortened blocks / light leftovers → giant HUD squares
			float maxW = w * 0.22f;
			float maxH = h * 0.22f;
			if (maxX - minX > maxW) {
				float cx = (minX + maxX) * 0.5f;
				minX = cx - maxW * 0.5f;
				maxX = cx + maxW * 0.5f;
			}
			if (maxY - minY > maxH) {
				float cy = (minY + maxY) * 0.5f;
				minY = cy - maxH * 0.5f;
				maxY = cy + maxH * 0.5f;
			}
		}
		return new float[]{minX, minY, maxX, maxY};
	}

	private static boolean project(double x, double y, double z, Vec3 camPos, float[] out) {
		Minecraft mc = Minecraft.getInstance();
		CLIP.set((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1f);
		MVP.transform(CLIP);
		if (CLIP.w <= 0.05f) {
			return false;
		}
		float nx = CLIP.x / CLIP.w;
		float ny = CLIP.y / CLIP.w;
		if (Math.abs(nx) > 1.5f || Math.abs(ny) > 1.5f) {
			return false;
		}
		int gw = mc.getWindow().getGuiScaledWidth();
		int gh = mc.getWindow().getGuiScaledHeight();
		out[0] = (nx * 0.5f + 0.5f) * gw;
		out[1] = (1f - (ny * 0.5f + 0.5f)) * gh;
		return true;
	}

	/** ~2×2 screen-pixel laser burn marks at the tip impact; linger via TemperatureField (~3s). */
	private static void drawLaserImpactDots(GuiGraphicsExtractor g, ThermalPalette palette,
											TemperatureField field, Vec3 camPos, Vec3 eye) {
		for (TemperatureField.LaserSpot spot : field.laserSpotsView()) {
			Vec3 world = new Vec3(spot.x(), spot.y(), spot.z());
			if (eye.distanceTo(world) < 1.25) {
				continue;
			}
			if (!project(spot.x(), spot.y(), spot.z(), camPos, SCR)) {
				continue;
			}
			int cx = Math.round(SCR[0]);
			int cy = Math.round(SCR[1]);
			float life = Mth.clamp(1f - spot.ageSec() / 3.0f, 0f, 1f);
			float temp = Mth.clamp(0.7f + spot.temp() * 0.2f, 0.65f, 0.95f);
			float a = Mth.clamp(0.55f + life * 0.4f, 0.35f, 0.95f);
			// Exactly 2×2 GUI pixels — no projected AABB squares
			g.fill(cx, cy, cx + 2, cy + 2, palette.color(temp, a));
			if (life > 0.55f) {
				g.fill(cx, cy, cx + 2, cy + 2, palette.color(Math.min(1f, temp + 0.08f), a * 0.55f));
			}
		}
	}

	private static void drawNoise(GuiGraphicsExtractor g, int w, int h, ThermalPalette palette) {
		drawNoise(g, w, h, palette, 40, 0.05f);
	}

	private static void drawNoise(GuiGraphicsExtractor g, int w, int h, ThermalPalette palette, int count, float alpha) {
		long seed = frame * 0x9E3779B97F4A7C15L;
		int step = Math.max(3, Math.min(w, h) / 56);
		for (int i = 0; i < count; i++) {
			seed = splitMix(seed);
			int x = (int) ((seed >>> 33) % Math.max(1, w));
			seed = splitMix(seed);
			int y = (int) ((seed >>> 33) % Math.max(1, h));
			seed = splitMix(seed);
			float n = ((seed >>> 40) & 0xFF) / 255f;
			g.fill(x, y, Math.min(w, x + step), Math.min(h, y + step),
					palette.color(0.1f + n * 0.05f, alpha * (0.6f + n * 0.4f)));
		}
	}

	private static void drawVignette(GuiGraphicsExtractor g, int w, int h) {
		int edge = Math.max(10, Math.min(w, h) / 9);
		for (int i = 0; i < 3; i++) {
			float t = (i + 1f) / 3f;
			int a = Mth.clamp((int) (36 * t), 8, 55);
			int inset = (int) (edge * t);
			g.fill(0, 0, w, inset, ARGB.color(a, 0, 0, 0));
			g.fill(0, h - inset, w, h, ARGB.color(a, 0, 0, 0));
			g.fill(0, 0, inset, h, ARGB.color(a, 0, 0, 0));
			g.fill(w - inset, 0, w, h, ARGB.color(a, 0, 0, 0));
		}
	}

	/** Shared H-key mode toast (thermal + NVG). */
	public static void drawModeToast(GuiGraphicsExtractor g, int w) {
		if (toastTicks > 0 && toast != null) {
			g.centeredText(Minecraft.getInstance().font, toast, w / 2, 12, 0xFFFFD080);
		}
	}

	private static void drawModeHud(GuiGraphicsExtractor g, int w, int h, ThermalPalette palette) {
		var font = Minecraft.getInstance().font;
		// While BigDrone OSD is up, skip this chip — it stacks on the hotbar strip.
		boolean mono = palette == ThermalPalette.WHITE_HOT || palette == ThermalPalette.BLACK_HOT;
		int col = mono
				? (palette == ThermalPalette.BLACK_HOT ? 0xFF101010 : 0xFFFFFFFF)
				: 0xFFCCAA66;
		g.text(font, palette.label() + "  [H]", 6, h - 14, col, false);
		drawModeToast(g, w);
	}

	private static long splitMix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
