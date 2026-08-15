package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minenite.client.gun.vision.NvgPalette;

/** Helmet CMD/PDC wear checks + night factor. H cycles multi phosphor or toggles fixed tubes. */
public final class NvgVision {
	public static final float CMD_MULTI = 2001f;
	public static final float CMD_GREEN = 2010f;
	public static final float CMD_WHITE = 2011f;
	public static final float CMD_AMBER = 2012f;
	public static final float CMD_BLUE = 2013f;
	public static final float CMD_RED = 2014f;
	public static final float CMD_TRUE = 2015f;

	private static boolean powered = true;
	private static int lastCmd = -1;
	private static NvgPalette palette = NvgPalette.GREEN;
	private static String toast;
	private static int toastTicks;

	private NvgVision() {
	}

	public static boolean isWearing() {
		return isWearing(Minecraft.getInstance().player);
	}

	public static boolean isWearing(LocalPlayer player) {
		sync(player);
		return hasHelmet(player) && powered;
	}

	public static boolean hasHelmet() {
		return hasHelmet(Minecraft.getInstance().player);
	}

	public static boolean hasHelmet(LocalPlayer player) {
		return helmetCmd(player) >= 0;
	}

	public static boolean powered() {
		return powered;
	}

	public static NvgPalette palette() {
		LocalPlayer player = Minecraft.getInstance().player;
		NvgPalette locked = lockedPalette(player);
		return locked != null ? locked : palette;
	}

	public static NvgPalette lockedPalette(LocalPlayer player) {
		int cmd = helmetCmd(player);
		if (cmd < 0 || Math.abs(cmd - CMD_MULTI) < 0.5f) {
			return null;
		}
		if (Math.abs(cmd - CMD_GREEN) < 0.5f) {
			return NvgPalette.GREEN;
		}
		if (Math.abs(cmd - CMD_WHITE) < 0.5f) {
			return NvgPalette.WHITE;
		}
		if (Math.abs(cmd - CMD_AMBER) < 0.5f) {
			return NvgPalette.AMBER;
		}
		if (Math.abs(cmd - CMD_BLUE) < 0.5f) {
			return NvgPalette.BLUE;
		}
		if (Math.abs(cmd - CMD_RED) < 0.5f) {
			return NvgPalette.RED;
		}
		if (Math.abs(cmd - CMD_TRUE) < 0.5f) {
			return NvgPalette.TRUE_COLOR;
		}
		return null;
	}

	public static String handleH(LocalPlayer player) {
		sync(player);
		if (!hasHelmet(player)) {
			return null;
		}
		NvgPalette locked = lockedPalette(player);
		if (locked == null) {
			palette = palette.next();
			toast("NVG: " + palette.label());
			overlay(toast);
			return toast;
		}
		powered = !powered;
		toast(powered ? ("NVG ON: " + locked.label()) : ("NVG OFF: " + locked.label()));
		overlay(toast);
		return toast;
	}

	public static String toastText() {
		return toastTicks > 0 ? toast : null;
	}

	public static void tickToast() {
		if (toastTicks > 0) {
			toastTicks--;
		}
	}

	private static void toast(String msg) {
		toast = msg;
		toastTicks = 40;
	}

	private static void overlay(String msg) {
		try {
			Minecraft.getInstance().gui.hud.setOverlayMessage(
					net.minecraft.network.chat.Component.literal(msg), false);
		} catch (Throwable ignored) {
		}
	}

	private static void sync(LocalPlayer player) {
		int cmd = helmetCmd(player);
		if (cmd < 0) {
			if (lastCmd >= 0) {
				powered = true;
				lastCmd = -1;
			}
			return;
		}
		if (cmd != lastCmd) {
			lastCmd = cmd;
			powered = true;
		}
	}

	public static int helmetCmd(LocalPlayer player) {
		if (player == null) {
			return -1;
		}
		ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
		if (helm.isEmpty() || !helm.is(Items.CARVED_PUMPKIN)) {
			return -1;
		}
		CustomModelData cmd = helm.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd != null) {
			for (float f : cmd.floats()) {
				int c = Math.round(f);
				if (isNvgCmd(c)) {
					return c;
				}
			}
			Float f0 = cmd.getFloat(0);
			if (f0 != null) {
				int c = Math.round(f0);
				if (isNvgCmd(c)) {
					return c;
				}
			}
			for (String s : cmd.strings()) {
				if (s == null) {
					continue;
				}
				String low = s.toLowerCase();
				if (low.equals("quad_nods") || low.equals("multi")) {
					return Math.round(CMD_MULTI);
				}
				if (low.contains("quad_nods_green") || low.equals("green")) {
					return Math.round(CMD_GREEN);
				}
				if (low.contains("quad_nods_white") || low.equals("white")) {
					return Math.round(CMD_WHITE);
				}
				if (low.contains("quad_nods_amber") || low.equals("amber")) {
					return Math.round(CMD_AMBER);
				}
				if (low.contains("quad_nods_blue") || low.equals("blue")) {
					return Math.round(CMD_BLUE);
				}
				if (low.contains("quad_nods_red") || low.equals("red")) {
					return Math.round(CMD_RED);
				}
				if (low.contains("quad_nods_true") || low.contains("true")) {
					return Math.round(CMD_TRUE);
				}
			}
		}
		CustomData custom = helm.get(DataComponents.CUSTOM_DATA);
		if (custom != null) {
			String nbt = custom.copyTag().toString().toLowerCase();
			if (nbt.contains("quad_nods_green")) {
				return Math.round(CMD_GREEN);
			}
			if (nbt.contains("quad_nods_white")) {
				return Math.round(CMD_WHITE);
			}
			if (nbt.contains("quad_nods_amber")) {
				return Math.round(CMD_AMBER);
			}
			if (nbt.contains("quad_nods_blue")) {
				return Math.round(CMD_BLUE);
			}
			if (nbt.contains("quad_nods_red")) {
				return Math.round(CMD_RED);
			}
			if (nbt.contains("quad_nods_true")) {
				return Math.round(CMD_TRUE);
			}
			if (nbt.contains("quad_nods") || nbt.contains("nvg_id")) {
				return Math.round(CMD_MULTI);
			}
		}
		return -1;
	}

	private static boolean isNvgCmd(int c) {
		return c == Math.round(CMD_MULTI)
				|| (c >= Math.round(CMD_GREEN) && c <= Math.round(CMD_TRUE));
	}

	public static float skyDarken01(Minecraft mc) {
		if (mc.level == null) {
			return 0f;
		}
		return Mth.clamp(mc.level.getSkyDarken() / 15f, 0f, 1f);
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	public static float nightAmount(Minecraft mc, float partialTicks) {
		if (mc.level == null) {
			return 0f;
		}
		try {
			long t = mc.level.getOverworldClockTime() % 24000L;
			if (t < 0L) {
				t += 24000L;
			}
			if (t < 12500L) {
				return 0f;
			}
		} catch (Throwable ignored) {
		}
		float night = smoothstep(0.72f, 0.92f, skyDarken01(mc));
		try {
			var attrs = mc.level.environmentAttributes();
			float sunDeg = attrs.getDimensionValue(EnvironmentAttributes.SUN_ANGLE);
			float sunRad = sunDeg * (float) (Math.PI / 180.0);
			float sunUp = Mth.clamp((float) Math.cos(sunRad) * 0.5f + 0.5f, 0f, 1f);
			if (sunUp < 0.2f) {
				night = Math.max(night, smoothstep(0.2f, 0.05f, sunUp));
			}
		} catch (Throwable ignored) {
		}
		return Mth.clamp(night, 0f, 1f);
	}

	public static float moonBrightness(Minecraft mc) {
		if (mc.level == null) {
			return 1f;
		}
		try {
			LocalPlayer player = mc.player;
			Vec3 at = player != null ? player.getEyePosition(1f) : Vec3.ZERO;
			MoonPhase phase = mc.level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, at);
			if (phase != null) {
				return Mth.clamp(DimensionType.MOON_BRIGHTNESS_PER_PHASE[phase.index()], 0f, 1f);
			}
		} catch (Throwable ignored) {
		}
		try {
			long day = mc.level.getOverworldClockTime() / 24000L;
			int idx = (int) Math.floorMod(day, 8L);
			return Mth.clamp(DimensionType.MOON_BRIGHTNESS_PER_PHASE[idx], 0f, 1f);
		} catch (Throwable ignored) {
			return 1f;
		}
	}

	public static float moonDarkness(Minecraft mc) {
		return 1f - moonBrightness(mc);
	}

	public static float nightAmbientLight(Minecraft mc, LocalPlayer player, float partialTicks) {
		float night = nightAmount(mc, partialTicks);
		if (night < 0.05f) {
			return 1f;
		}
		float open = skyOpen(mc, player, partialTicks);
		float moon = moonBrightness(mc);
		float stars = 0.045f;
		return Mth.clamp((moon * 0.55f + stars) * open * night, 0f, 1f);
	}

	public static float dayFlood(Minecraft mc, LocalPlayer player, float partialTicks) {
		if (mc.level == null || player == null) {
			return 0f;
		}
		float night = nightAmount(mc, partialTicks);
		float day = 1f - night;
		if (day < 0.08f) {
			return 0f;
		}
		float open = skyOpen(mc, player, partialTicks);
		float skyBright = 1f - skyDarken01(mc);
		float flood = day * Mth.clamp(open * 0.85f + skyBright * 0.35f, 0f, 1f) * skyBright;
		return Mth.clamp(flood, 0f, 1f);
	}

	public static float skyOpen(Minecraft mc, LocalPlayer player, float partialTicks) {
		if (mc.level == null || player == null) {
			return 0f;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition(partialTicks));
		float sky = mc.level.getBrightness(LightLayer.SKY, eye) / 15f;
		boolean canSee = mc.level.canSeeSky(eye);
		return Mth.clamp(Math.max(sky, canSee ? 0.85f : 0f), 0f, 1f);
	}

	/**
	 * Photons available to Gen-III tubes: moon, starlight, sky opening, indoor scatter, lamps.
	 * New moon under open sky is starved; full moon and interiors/lamps still feed the tubes.
	 */
	public static float tubePhotons(Minecraft mc, LocalPlayer player, float partialTicks) {
		if (mc.level == null || player == null) {
			return 1f;
		}
		float dayFlood = dayFlood(mc, player, partialTicks);
		if (dayFlood > 0.08f) {
			return Mth.clamp(0.9f + dayFlood * 0.45f, 0.9f, 1.4f);
		}
		float open = skyOpen(mc, player, partialTicks);
		float moon = moonBrightness(mc);
		float sky = (moon * 0.84f + 0.11f) * open;
		float scatter = (1f - open) * 0.48f;
		BlockPos eye = BlockPos.containing(player.getEyePosition(partialTicks));
		float lamps = mc.level.getBrightness(LightLayer.BLOCK, eye) / 15f;
		return Mth.clamp(sky * 0.92f + scatter + lamps * 0.55f, 0.08f, 1.15f);
	}

	/** Complementary / vanilla NV scale while wearing NODS. */
	public static float tubeVisionScale(Minecraft mc, LocalPlayer player, float partialTicks) {
		float photons = tubePhotons(mc, player, partialTicks);
		float nv = Mth.clamp(0.24f + photons * 0.76f, 0.24f, 1f);
		float bloom = NvgAgc.get().displayGlobalBloom();
		return nv * Mth.lerp(bloom, 1f, 0.84f);
	}
}
