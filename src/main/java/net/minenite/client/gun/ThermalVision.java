package net.minenite.client.gun;

import net.minenite.client.gun.vision.TemperatureField;
import net.minenite.client.gun.vision.ThermalPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** FLIR / thermal helmet wear checks (carved pumpkin CMD 2002 + fixed 2020–2024). */
public final class ThermalVision {
	public static final float CMD_MULTI = 2002f;
	public static final float CMD_WHITE_HOT = 2020f;
	public static final float CMD_BLACK_HOT = 2021f;
	public static final float CMD_IRONBOW = 2022f;
	public static final float CMD_RAINBOW = 2023f;
	public static final float CMD_FUSION = 2024f;
	/** @deprecated use {@link #CMD_MULTI} */
	public static final float CMD = CMD_MULTI;

	private static boolean powered = true;
	private static int lastCmd = -1;

	private ThermalVision() {
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

	public static boolean isMulti(LocalPlayer player) {
		int cmd = helmetCmd(player);
		return cmd >= 0 && Math.abs(cmd - CMD_MULTI) < 0.5f;
	}

	public static boolean powered() {
		return powered;
	}

	/** Locked palette for fixed-CMD helmets; null for multi. */
	public static ThermalPalette lockedPalette(LocalPlayer player) {
		int cmd = helmetCmd(player);
		if (cmd < 0 || Math.abs(cmd - CMD_MULTI) < 0.5f) {
			return null;
		}
		if (Math.abs(cmd - CMD_WHITE_HOT) < 0.5f) {
			return ThermalPalette.WHITE_HOT;
		}
		if (Math.abs(cmd - CMD_BLACK_HOT) < 0.5f) {
			return ThermalPalette.BLACK_HOT;
		}
		if (Math.abs(cmd - CMD_IRONBOW) < 0.5f) {
			return ThermalPalette.IRONBOW;
		}
		if (Math.abs(cmd - CMD_RAINBOW) < 0.5f) {
			return ThermalPalette.RAINBOW;
		}
		if (Math.abs(cmd - CMD_FUSION) < 0.5f) {
			return ThermalPalette.FUSION;
		}
		return null;
	}

	/**
	 * H while wearing FLIR: multi cycles palette; fixed toggles imager on/off.
	 * @return toast text, or null if nothing changed
	 */
	public static String handleH(LocalPlayer player) {
		sync(player);
		if (!hasHelmet(player)) {
			return null;
		}
		ThermalPalette locked = lockedPalette(player);
		if (locked == null) {
			ThermalPalette p = TemperatureField.get().cyclePalette();
			return "Thermal: " + p.label();
		}
		powered = !powered;
		TemperatureField.get().setPalette(locked);
		return powered ? ("Thermal ON: " + locked.label()) : ("Thermal OFF: " + locked.label());
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
		ThermalPalette locked = lockedPalette(player);
		if (locked != null) {
			TemperatureField.get().setPalette(locked);
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
				if (isThermalCmd(c)) {
					return c;
				}
			}
			Float f0 = cmd.getFloat(0);
			if (f0 != null) {
				int c = Math.round(f0);
				if (isThermalCmd(c)) {
					return c;
				}
			}
			for (String s : cmd.strings()) {
				if (s == null) {
					continue;
				}
				String low = s.toLowerCase();
				if (low.equals("thermal_flir") || low.equals("multi")) {
					return Math.round(CMD_MULTI);
				}
				if (low.contains("white_hot")) {
					return Math.round(CMD_WHITE_HOT);
				}
				if (low.contains("black_hot")) {
					return Math.round(CMD_BLACK_HOT);
				}
				if (low.contains("ironbow")) {
					return Math.round(CMD_IRONBOW);
				}
				if (low.contains("rainbow")) {
					return Math.round(CMD_RAINBOW);
				}
				if (low.contains("fusion")) {
					return Math.round(CMD_FUSION);
				}
				if (low.contains("thermal")) {
					return Math.round(CMD_MULTI);
				}
			}
		}
		CustomData custom = helm.get(DataComponents.CUSTOM_DATA);
		if (custom != null) {
			String nbt = custom.copyTag().toString().toLowerCase();
			if (nbt.contains("thermal_flir_white_hot") || nbt.contains("white_hot")) {
				return Math.round(CMD_WHITE_HOT);
			}
			if (nbt.contains("thermal_flir_black_hot") || nbt.contains("black_hot")) {
				return Math.round(CMD_BLACK_HOT);
			}
			if (nbt.contains("thermal_flir_ironbow") || nbt.contains("ironbow")) {
				return Math.round(CMD_IRONBOW);
			}
			if (nbt.contains("thermal_flir_rainbow") || nbt.contains("rainbow")) {
				return Math.round(CMD_RAINBOW);
			}
			if (nbt.contains("thermal_flir_fusion") || nbt.contains("fusion")) {
				return Math.round(CMD_FUSION);
			}
			if (nbt.contains("thermal_flir") || nbt.contains("thermal_id")) {
				return Math.round(CMD_MULTI);
			}
		}
		Component name = helm.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			String plain = name.getString().toLowerCase();
			if (plain.contains("white hot")) {
				return Math.round(CMD_WHITE_HOT);
			}
			if (plain.contains("black hot")) {
				return Math.round(CMD_BLACK_HOT);
			}
			if (plain.contains("ironbow")) {
				return Math.round(CMD_IRONBOW);
			}
			if (plain.contains("rainbow")) {
				return Math.round(CMD_RAINBOW);
			}
			if (plain.contains("fusion")) {
				return Math.round(CMD_FUSION);
			}
			if (plain.contains("flir") || plain.contains("thermal")) {
				return Math.round(CMD_MULTI);
			}
		}
		return -1;
	}

	private static boolean isThermalCmd(int c) {
		return c == Math.round(CMD_MULTI)
				|| (c >= Math.round(CMD_WHITE_HOT) && c <= Math.round(CMD_FUSION));
	}
}
