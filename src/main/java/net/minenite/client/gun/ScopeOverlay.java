package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Rail-optic ADS HUD: irons / RDS / EOTech / ACOG / magnifying scope.
 */
public final class ScopeOverlay {
	private static final float DEFAULT_FALL = 0.014f;
	private static final float HORIZ_DRAG = 0.988f;
	private static final int[] MIL_RANGES = {100, 200, 300, 400};
	public static final byte HUD_IRONS = 0;
	public static final byte HUD_RDS = 1;
	public static final byte HUD_EOTECH = 2;
	public static final byte HUD_HOLO_CIRCLE = 3;
	public static final byte HUD_ACOG = 4;
	public static final byte HUD_SCOPE = 5;

	private static boolean active;
	private static int zeroYards = 100;
	private static String gunId = "";
	private static String opticId = "irons";
	private static String gripId = "";
	private static byte hudKind = HUD_IRONS;
	private static int reticleRgb = 0xFF2828;
	private static float packetFov = 0.92f;
	private static boolean prone;
	private static boolean rested;
	private static boolean serverBreath;
	private static int breathPct = 100;
	private static float bulletSpeed = 4.5f;
	private static float fallSpeed = DEFAULT_FALL;
	private static float open;
	private static float smoothFovMult = 1f;
	private static float swayPhase;
	private static float swayYaw;
	private static float swayPitch;
	private static int rangeYards = -1;
	private static int rangeTick;
	private static long lastServerScopeMs;

	private ScopeOverlay() {
	}

	public static void markServerPacket() {
		lastServerScopeMs = System.currentTimeMillis();
	}

	public static void accept(boolean on, int zero, String gun, byte flags, int breath,
			float speed, float fall, byte hud, String optic, int rgb, String grip, float fov) {
		boolean was = active;
		active = on;
		zeroYards = zero <= 0 ? 100 : zero;
		gunId = gun == null ? "" : gun;
		opticId = optic == null || optic.isBlank() ? "irons" : optic;
		gripId = grip == null ? "" : grip;
		hudKind = hud;
		reticleRgb = rgb == 0 ? 0xFF2828 : rgb;
		packetFov = fov > 0.05f ? fov : 0.92f;
		prone = (flags & 1) != 0;
		rested = (flags & 2) != 0;
		serverBreath = (flags & 4) != 0;
		breathPct = Mth.clamp(breath, 0, 100);
		bulletSpeed = speed > 0.5f ? speed : 4.5f;
		fallSpeed = fall > 0f ? fall : DEFAULT_FALL;
		if (active && !was) {
			open = 1f;
			smoothFovMult = packetFov;
		}
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		inferLocalAds(mc);
		if (active) {
			open = 1f;
			smoothFovMult += (packetFov - smoothFovMult) * 0.65f;
			if (Math.abs(smoothFovMult - packetFov) < 0.004f) {
				smoothFovMult = packetFov;
			}
			tickSway(mc);
			if (++rangeTick >= 4) {
				rangeTick = 0;
				rangeYards = measureRange(mc);
			}
			return;
		}
		open *= 0.55f;
		if (open < 0.04f) {
			open = 0f;
		}
		smoothFovMult += (1f - smoothFovMult) * 0.55f;
		if (smoothFovMult > 0.995f) {
			smoothFovMult = 1f;
		}
		swayYaw *= 0.7f;
		swayPitch *= 0.7f;
		rangeYards = -1;
	}

	public static void clear() {
		active = false;
		open = 0f;
		smoothFovMult = 1f;
		zeroYards = 100;
		gunId = "";
		opticId = "irons";
		gripId = "";
		hudKind = HUD_IRONS;
		prone = false;
		rested = false;
		serverBreath = false;
		breathPct = 100;
		swayYaw = 0f;
		swayPitch = 0f;
		rangeYards = -1;
		lastServerScopeMs = 0L;
	}

	public static boolean adsActive() {
		return active && open > 0.05f;
	}

	/** Any fitted sight hides the first-person gun so the 2D optic HUD is visible. */
	public static boolean hidesHeldGun() {
		return adsActive() && hudKind != HUD_IRONS;
	}

	public static float fovMult() {
		if (smoothFovMult >= 0.999f) {
			return 1f;
		}
		return Mth.clamp(smoothFovMult, 0.12f, 1f);
	}

	public static boolean isScoped() {
		return open > 0.05f;
	}

	public static float swayYaw() {
		return isScoped() ? swayYaw : 0f;
	}

	public static float swayPitch() {
		return isScoped() ? swayPitch : 0f;
	}

	public static boolean isHoldingBreathLocal() {
		if (!isScoped()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) {
			return serverBreath;
		}
		long win = mc.getWindow().handle();
		boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		return shift || serverBreath;
	}

	/**
	 * Drive the optic HUD from the held gun when server scope packets are not
	 * arriving (companion hello never linked). Packet path wins when it is live.
	 */
	private static void inferLocalAds(Minecraft mc) {
		if (System.currentTimeMillis() - lastServerScopeMs < 1500L) {
			return;
		}
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		net.minecraft.world.item.ItemStack hand = p.getMainHandItem();
		boolean ads = GunItemPose.isGun(hand) && (p.entityTags().contains("pgm_aim") || adsSlowness(p));
		if (!ads) {
			if (active) {
				active = false;
			}
			return;
		}
		String optic = GunAttachmentVisuals.opticId(hand);
		String grip = GunAttachmentVisuals.gripId(hand);
		String gun = GunAttachmentVisuals.gunId(hand);
		int zero = GunAttachmentVisuals.zeroYards(hand);
		byte hud = hudForOptic(optic);
		float fov = fovForOptic(optic, zero);
		byte flags = 0;
		if (p.isCrouching()) {
			flags |= 1;
		}
		accept(true, zero, gun, flags, 100, 4.5f, DEFAULT_FALL, hud, optic,
				GunAttachmentVisuals.reticleRgb(hand), grip, fov);
	}

	private static boolean adsSlowness(LocalPlayer p) {
		for (var effect : p.getActiveEffects()) {
			if (effect.getAmplifier() < 4) {
				continue;
			}
			String id = String.valueOf(effect.getEffect()).toLowerCase();
			if (id.contains("slowness") || id.contains("slowdown") || id.contains("moveslow")) {
				return true;
			}
		}
		return false;
	}

	private static byte hudForOptic(String id) {
		if (id == null) {
			return HUD_IRONS;
		}
		return switch (id.toLowerCase()) {
			case "rds" -> HUD_RDS;
			case "eotech" -> HUD_EOTECH;
			case "holo_circle" -> HUD_HOLO_CIRCLE;
			case "acog" -> HUD_ACOG;
			case "scope_6x", "scope_8x", "scope_barrett" -> HUD_SCOPE;
			default -> HUD_IRONS;
		};
	}

	private static float fovForOptic(String id, int zero) {
		if (id == null) {
			return 0.92f;
		}
		return switch (id.toLowerCase()) {
			case "rds" -> 0.85f;
			case "eotech", "holo_circle" -> 0.82f;
			case "acog" -> zero >= 250 ? 0.48f : zero >= 150 ? 0.50f : 0.55f;
			case "scope_6x" -> zero >= 250 ? 0.22f : zero >= 150 ? 0.30f : 0.42f;
			case "scope_8x", "scope_barrett" -> zero >= 250 ? 0.18f : zero >= 150 ? 0.28f : 0.42f;
			default -> 0.92f;
		};
	}

	private static void tickSway(Minecraft mc) {
		boolean breath = isHoldingBreathLocal();
		float amp = 0.55f;
		if (hudKind == HUD_IRONS) {
			amp *= 0.35f;
		} else if (hudKind == HUD_RDS) {
			amp *= 0.55f;
		}
		if (prone) {
			amp *= 0.42f;
		}
		if (rested) {
			amp *= 0.48f;
			if ("bipod".equals(gripId)) {
				amp *= 0.45f;
			}
		}
		float lungs = breathPct / 100f;
		if (breath && lungs > 0.08f) {
			amp *= 0.12f + 0.25f * (1f - lungs);
		} else if (breath) {
			amp *= 1.35f;
		}
		LocalPlayer p = mc.player;
		if (p != null && p.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
			amp *= 1.55f;
		}
		int lived = p != null ? p.tickCount : 0;
		swayPhase = lived * 0.07f + amp * 0.04f * (lived % 200);
		float targetYaw = (float) Math.sin(swayPhase) * amp
				+ (float) Math.sin(swayPhase * 0.37f) * amp * 0.35f;
		float targetPitch = (float) Math.cos(swayPhase * 0.81f) * amp * 0.72f
				+ (float) Math.sin(swayPhase * 1.7f) * amp * 0.18f;
		swayYaw += (targetYaw - swayYaw) * 0.28f;
		swayPitch += (targetPitch - swayPitch) * 0.28f;
	}

	public static Vec3 swayedLook(LocalPlayer player) {
		Vec3 look = player.getViewVector(1f);
		if (!isScoped() || (swayYaw == 0f && swayPitch == 0f)) {
			return look;
		}
		return look.yRot((float) Math.toRadians(-swayYaw))
				.xRot((float) Math.toRadians(-swayPitch));
	}

	private static int measureRange(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return -1;
		}
		Vec3 eye = player.getEyePosition(1f);
		Vec3 look = swayedLook(player);
		double max = 400.0;
		Vec3 end = eye.add(look.scale(max));
		BlockHitResult block = mc.level.clip(new ClipContext(
				eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double best = max + 1;
		if (block.getType() != HitResult.Type.MISS) {
			best = eye.distanceTo(block.getLocation());
		}
		EntityHitResult entity = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
				player, eye, end,
				player.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0),
				e -> e.isPickable() && e != player,
				max);
		if (entity != null) {
			double d = eye.distanceTo(entity.getLocation());
			if (d < best) {
				best = d;
			}
		}
		if (best > max) {
			return -1;
		}
		return Mth.clamp((int) Math.round(best), 1, 999);
	}

	public static void render(GuiGraphicsExtractor graphics) {
		if (open <= 0.02f) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		int cx = (w / 2) - 1;
		int cy = h / 2;

		// Bare irons: no 2D overlay — vanilla crosshair + the gun model.
		if (hudKind == HUD_IRONS) {
			return;
		}
		switch (hudKind) {
			case HUD_RDS -> drawRds(graphics, cx, cy, open);
			case HUD_EOTECH -> drawEotech(graphics, cx, cy, open, false);
			case HUD_HOLO_CIRCLE -> drawEotech(graphics, cx, cy, open, true);
			case HUD_ACOG -> drawAcog(graphics, mc, w, h, cx, cy);
			default -> drawMagnifiedScope(graphics, mc, w, h, cx, cy);
		}
		drawStatusBelow(graphics, mc, cx, cy, hudKind == HUD_SCOPE
				|| hudKind == HUD_ACOG
				? Math.max(28, (int) (Math.min(w, h) * 0.40f)) : 40);
	}

	private static int reticleColor(int alpha) {
		int r = (reticleRgb >> 16) & 0xFF;
		int g = (reticleRgb >> 8) & 0xFF;
		int b = reticleRgb & 0xFF;
		return ARGB.color(Mth.clamp(alpha, 0, 255), r, g, b);
	}

	private static void drawIrons(GuiGraphicsExtractor g, int cx, int cy, float open) {
		int a = Mth.clamp((int) (200 * open), 0, 220);
		int col = ARGB.color(a, 30, 30, 32);
		// Rear notch
		fillSafe(g, cx - 14, cy + 8, cx - 4, cy + 10, col);
		fillSafe(g, cx + 5, cy + 8, cx + 15, cy + 10, col);
		fillSafe(g, cx - 2, cy + 2, cx + 3, cy + 12, col);
		// Front post
		fillSafe(g, cx, cy - 18, cx + 1, cy + 2, col);
		fillSafe(g, cx - 1, cy - 2, cx + 2, cy + 1, reticleColor(a));
	}

	private static void drawRds(GuiGraphicsExtractor g, int cx, int cy, float open) {
		int a = Mth.clamp((int) (180 * open), 0, 200);
		int ring = ARGB.color(Math.max(40, a / 3), 40, 40, 44);
		drawCircleRing(g, cx, cy, 22, ring);
		fillSafe(g, cx - 1, cy - 1, cx + 2, cy + 2, reticleColor(a));
	}

	private static void drawEotech(GuiGraphicsExtractor g, int cx, int cy, float open, boolean circle) {
		int a = Mth.clamp((int) (200 * open), 0, 220);
		int frame = ARGB.color(a / 2, 20, 22, 26);
		int half = 36;
		fillSafe(g, cx - half, cy - half, cx + half, cy - half + 2, frame);
		fillSafe(g, cx - half, cy + half - 2, cx + half, cy + half, frame);
		fillSafe(g, cx - half, cy - half, cx - half + 2, cy + half, frame);
		fillSafe(g, cx + half - 2, cy - half, cx + half, cy + half, frame);
		int ret = reticleColor(a);
		if (circle) {
			drawCircleRing(g, cx, cy, 11, ret);
			fillSafe(g, cx - 1, cy - 1, cx + 2, cy + 2, ret);
		} else {
			drawCircleRing(g, cx, cy, 15, ret);
			fillSafe(g, cx - 1, cy - 1, cx + 2, cy + 2, ret);
			fillSafe(g, cx - 6, cy + 8, cx + 7, cy + 9, ret);
			fillSafe(g, cx, cy + 2, cx + 1, cy + 12, ret);
		}
	}

	private static void drawCircleRing(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int i = 0; i < 48; i++) {
			double ang = i * (Math.PI * 2.0 / 48.0);
			int x = cx + (int) Math.round(Math.cos(ang) * r);
			int y = cy + (int) Math.round(Math.sin(ang) * r);
			fillSafe(g, x, y, x + 1, y + 1, color);
		}
	}

	private static void drawAcog(GuiGraphicsExtractor g, Minecraft mc, int w, int h, int cx, int cy) {
		int radius = Math.max(36, (int) (Math.min(w, h) * 0.28f));
		float house = 0.55f * open;
		drawCircularHousing(g, w, h, cx, cy, radius, house, false);
		drawScopeRing(g, w, h, cx, cy, radius, open);
		int a = Mth.clamp((int) (210 * open), 0, 230);
		int col = reticleColor(a);
		fillSafe(g, cx - 18, cy, cx - 3, cy + 1, col);
		fillSafe(g, cx + 4, cy, cx + 19, cy + 1, col);
		fillSafe(g, cx, cy - 14, cx + 1, cy - 3, col);
		// Chevron
		fillSafe(g, cx - 3, cy + 2, cx + 4, cy + 3, col);
		fillSafe(g, cx - 1, cy + 3, cx + 2, cy + 8, col);
		if (open > 0.4f && mc.font != null) {
			String label = opticId.toUpperCase();
			g.text(mc.font, label, cx - mc.font.width(label) / 2, cy + radius + 6,
					ARGB.color((int) (open * 180), 180, 200, 180), false);
		}
	}

	private static void drawMagnifiedScope(GuiGraphicsExtractor graphics, Minecraft mc,
										   int w, int h, int cx, int cy) {
		float aperture = 0.40f;
		int radius = Math.max(28, (int) (Math.min(w, h) * aperture));
		float house = housingStrength(zeroYards) * open;
		drawCircularHousing(graphics, w, h, cx, cy, radius, house, zeroYards < 150);
		drawFrostRing(graphics, w, h, cx, cy, radius, house);
		drawScopeRing(graphics, w, h, cx, cy, radius, open);
		drawScopeReticle(graphics, mc, cx, cy, radius, h, open);
		if (open > 0.4f) {
			String label = zeroYards + " yd";
			if (opticId.contains("barrett") || (gunId != null && gunId.toLowerCase().contains("barret"))) {
				label = "M82A1  ·  " + label;
			} else if (!opticId.isBlank()) {
				label = opticId.replace('_', ' ') + "  ·  " + label;
			}
			int tw = mc.font.width(label);
			graphics.text(mc.font, label, cx - tw / 2, cy + radius + 6,
					ARGB.color(Mth.clamp((int) (open * 200), 0, 220), 200, 210, 220), false);
		}
	}

	private static float housingStrength(int zero) {
		if (zero >= 250) {
			return 0.88f;
		}
		if (zero >= 150) {
			return 0.62f;
		}
		return 0.32f;
	}

	private static void drawStatusBelow(GuiGraphicsExtractor graphics, Minecraft mc,
										int cx, int cy, int radius) {
		if (open <= 0.4f) {
			return;
		}
		boolean showBreath = hudKind == HUD_SCOPE
				|| hudKind == HUD_ACOG
				|| hudKind == HUD_EOTECH
				|| hudKind == HUD_HOLO_CIRCLE;
		int y = cy + radius + 18;
		boolean breath = isHoldingBreathLocal();
		StringBuilder status = new StringBuilder();
		if (rested) {
			status.append("REST");
		} else if (prone) {
			status.append("PRONE");
		}
		if (showBreath && breath) {
			if (!status.isEmpty()) {
				status.append(" · ");
			}
			status.append(breathPct > 8 ? "HOLDING BREATH" : "BREATH OUT");
		}
		if (!status.isEmpty()) {
			String s = status.toString();
			int sw = mc.font.width(s);
			int sc = breath && breathPct > 8
					? ARGB.color(220, 90, 200, 255)
					: ARGB.color(200, 180, 190, 200);
			graphics.text(mc.font, s, cx - sw / 2, y, sc, false);
			y += 10;
		}
		if (showBreath) {
			int barW = Math.max(36, radius / 2);
			int bx0 = cx - barW / 2;
			graphics.fill(bx0, y, bx0 + barW, y + 3, ARGB.color(120, 20, 24, 30));
			int fill = Math.round(barW * (breathPct / 100f));
			int bc = breathPct > 25
					? ARGB.color(200, 70, 180, 255)
					: ARGB.color(220, 220, 80, 70);
			if (fill > 0) {
				graphics.fill(bx0, y, bx0 + fill, y + 3, bc);
			}
		}
	}

	private static void drawCircularHousing(GuiGraphicsExtractor graphics, int w, int h,
											int cx, int cy, int radius, float house, boolean lightMode) {
		int aMax = Mth.clamp((int) (255 * house), 0, 230);
		for (int y = 0; y < h; y++) {
			int dy = y - cy;
			int dy2 = dy * dy;
			int r2 = radius * radius;
			if (dy2 >= r2) {
				float dist = (float) Math.sqrt(dy2) - radius;
				float edgeFade = lightMode
						? Mth.clamp(0.35f + dist / Math.max(40f, h * 0.35f), 0.35f, 1f)
						: 1f;
				int a = Mth.clamp((int) (aMax * edgeFade), 0, 230);
				if (a > 4) {
					graphics.fill(0, y, w, y + 1, ARGB.color(a, 6, 6, 8));
				}
				continue;
			}
			int half = (int) Math.sqrt(r2 - dy2);
			int left = cx - half;
			int right = cx + half;
			if (left > 0) {
				fillVignetteRow(graphics, 0, left, y, cx, cy, radius, aMax, lightMode);
			}
			if (right < w) {
				fillVignetteRow(graphics, right, w, y, cx, cy, radius, aMax, lightMode);
			}
		}
	}

	private static void fillVignetteRow(GuiGraphicsExtractor graphics, int x0, int x1, int y,
										int cx, int cy, int radius, int aMax, boolean lightMode) {
		if (x1 <= x0 || aMax <= 4) {
			return;
		}
		if (!lightMode) {
			graphics.fill(x0, y, x1, y + 1, ARGB.color(aMax, 6, 6, 8));
			return;
		}
		int midX = (x0 + x1) / 2;
		float dist = (float) Math.hypot(midX - cx, y - cy) - radius;
		float edgeFade = Mth.clamp(0.30f + dist / Math.max(50f, 180f), 0.30f, 1f);
		int a = Mth.clamp((int) (aMax * edgeFade), 0, 200);
		if (a > 4) {
			graphics.fill(x0, y, x1, y + 1, ARGB.color(a, 8, 8, 10));
		}
	}

	private static void drawFrostRing(GuiGraphicsExtractor graphics, int w, int h,
									  int cx, int cy, int radius, float house) {
		for (int b = 1; b <= 4; b++) {
			int rOuter = radius + (int) (b * (5 + house * 8));
			int rInner = radius + (int) ((b - 1) * (5 + house * 8));
			int a = Mth.clamp((int) ((40 - b * 6) * house), 0, 70);
			if (a > 3) {
				fillAnnulus(graphics, w, h, cx, cy, rInner, rOuter, ARGB.color(a, 10, 12, 16));
			}
		}
	}

	private static void fillAnnulus(GuiGraphicsExtractor graphics, int w, int h,
									int cx, int cy, int rIn, int rOut, int color) {
		int rOut2 = rOut * rOut;
		int rIn2 = Math.max(0, rIn) * Math.max(0, rIn);
		int y0 = Math.max(0, cy - rOut);
		int y1 = h >= 9000 ? cy + rOut + 1 : Math.min(h, cy + rOut + 1);
		int xLimit = w >= 9000 ? Integer.MAX_VALUE : w;
		for (int y = y0; y < y1; y++) {
			int dy = y - cy;
			int dy2 = dy * dy;
			if (dy2 >= rOut2) {
				continue;
			}
			int halfOut = (int) Math.sqrt(rOut2 - dy2);
			int halfIn = dy2 >= rIn2 ? 0 : (int) Math.sqrt(rIn2 - dy2);
			int xa = Math.max(0, cx - halfOut);
			int xb = Math.min(xLimit, cx - halfIn);
			if (xa < xb) {
				graphics.fill(xa, y, xb, y + 1, color);
			}
			xa = Math.max(0, cx + halfIn);
			xb = Math.min(xLimit, cx + halfOut);
			if (xa < xb) {
				graphics.fill(xa, y, xb, y + 1, color);
			}
		}
	}

	private static void drawScopeRing(GuiGraphicsExtractor graphics, int w, int h,
									  int cx, int cy, int radius, float open) {
		int a = Mth.clamp((int) (200 * open), 0, 220);
		int thick = Math.max(2, radius / 30);
		fillAnnulus(graphics, w, h, cx, cy, radius - 1, radius + thick, ARGB.color(a, 28, 32, 38));
	}

	private static void drawScopeReticle(GuiGraphicsExtractor graphics, Minecraft mc,
										 int cx, int cy, int radius, int screenH, float open) {
		int a = Mth.clamp((int) (210 * open), 0, 230);
		int col = ARGB.color(a, 20, 20, 22);
		int accent = reticleColor(a);
		int arm = Math.max(8, radius * 2 / 5);
		int gap = Math.max(3, radius / 18);
		fillSafe(graphics, cx - arm, cy, cx - gap, cy + 1, col);
		fillSafe(graphics, cx + gap, cy, cx + arm, cy + 1, col);
		fillSafe(graphics, cx, cy - arm, cx + 1, cy - gap, col);
		fillSafe(graphics, cx, cy + gap, cx + 1, cy + arm, col);
		fillSafe(graphics, cx - 1, cy - 1, cx + 2, cy + 2, accent);

		float halfFovDeg = Math.max(4f, 70f * fovMult() * 0.5f);
		float pxPerDeg = (screenH * 0.5f) / halfFovDeg;
		double elev = elevationForZero(bulletSpeed, fallSpeed, zeroYards);
		int rngDotY = cy;
		for (int range : MIL_RANGES) {
			double ang = impactAngleBelowLook(bulletSpeed, fallSpeed, elev, range);
			int d = (int) Math.round(ang * pxPerDeg);
			if (Math.abs(d) < 4 || Math.abs(d) > radius - 6) {
				continue;
			}
			int y = cy + d;
			boolean isZero = range == zeroYards;
			int s = isZero ? 2 : 1;
			int mark = isZero ? accent : col;
			fillSafe(graphics, cx - 5, y, cx - 2, y + 1, mark);
			fillSafe(graphics, cx + 3, y, cx + 6, y + 1, mark);
			fillSafe(graphics, cx - s, y - s, cx + s + 1, y + s + 1, mark);
			if (open > 0.55f && mc.font != null) {
				graphics.text(mc.font, String.valueOf(range), cx + 8, y - 3,
						ARGB.color(Mth.clamp((int) (open * 160), 0, 180), 160, 165, 175), false);
			}
			if (rangeYards > 0 && Math.abs(range - rangeYards) < Math.abs(rngDotY == cy ? 999 : 0)) {
				rngDotY = y;
			}
		}
		if (rangeYards > 0 && open > 0.45f && mc.font != null) {
			double ang = impactAngleBelowLook(bulletSpeed, fallSpeed, elev, rangeYards);
			int liveY = cy + (int) Math.round(ang * pxPerDeg);
			if (Math.abs(liveY - cy) <= radius - 10) {
				rngDotY = liveY;
			}
			String rng = String.valueOf(rangeYards);
			int rw = mc.font.width(rng);
			int rx = cx - Math.max(22, radius / 5) - rw;
			int ry = Mth.clamp(rngDotY - 3, cy - radius + 8, cy + radius - 10);
			graphics.text(mc.font, rng, rx, ry,
					ARGB.color(Mth.clamp((int) (open * 220), 0, 235), 100, 230, 130), false);
			fillSafe(graphics, cx - 7, rngDotY, cx - 4, rngDotY + 1,
					ARGB.color(Mth.clamp((int) (open * 180), 0, 200), 80, 200, 110));
		}
	}

	private static double elevationForZero(double speed, double fall, double range) {
		if (range <= 1.0 || speed <= 0.1 || fall <= 0.0) {
			return (range - 100.0) * 0.012;
		}
		double lo = -2.0;
		double hi = 12.0;
		for (int i = 0; i < 28; i++) {
			double mid = (lo + hi) * 0.5;
			double y = heightAtRange(speed, fall, mid, range);
			if (y > 0.0) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return (lo + hi) * 0.5;
	}

	private static double impactAngleBelowLook(double speed, double fall, double elevDeg, double range) {
		if (range <= 1.0) {
			return 0.0;
		}
		double y = heightAtRange(speed, fall, elevDeg, range);
		return Math.toDegrees(Math.atan2(-y, range));
	}

	private static double heightAtRange(double speed, double fall, double elevDeg, double range) {
		double elev = Math.toRadians(elevDeg);
		double vx = speed * Math.cos(elev);
		double vy = speed * Math.sin(elev);
		double x = 0.0;
		double y = 0.0;
		int guard = 0;
		while (x < range && guard++ < 800) {
			x += vx;
			y += vy;
			vy -= fall;
			double terminal = Math.min(0.42, Math.max(0.045, fall * 8.5));
			if (vy < -terminal) {
				vy = -terminal;
			}
			vx *= HORIZ_DRAG;
			if (vx < 0.08) {
				break;
			}
		}
		return y;
	}

	private static void fillSafe(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col) {
		if (x1 > x0 && y1 > y0) {
			graphics.fill(x0, y0, x1, y1, col);
		}
	}
}
