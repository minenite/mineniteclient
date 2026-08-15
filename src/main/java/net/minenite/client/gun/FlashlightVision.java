package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Handheld / gun-mounted flashlight from WarZ item PDC. */
public final class FlashlightVision {
	private static float strobe;

	private FlashlightVision() {
	}

	public static boolean isOn() {
		return isOn(Minecraft.getInstance().player);
	}

	public static boolean isOn(LocalPlayer player) {
		return strength(player) > 0.04f;
	}

	/** Iris held-light 0–15 for this stack (Simply Upscaled-style cone source). */
	public static int emission(ItemStack stack) {
		Mode mode = mode(stack);
		if (mode == Mode.OFF) {
			return 0;
		}
		if (mode == Mode.STROBE) {
			float pulse = 0.55f + 0.45f * Mth.sin(strobe);
			return pulse > 0.62f ? 15 : 2;
		}
		return 15;
	}

	/** 0–1, strobe flickers. */
	public static float strength() {
		return strength(Minecraft.getInstance().player);
	}

	public static float strength(LocalPlayer player) {
		if (player == null) {
			return 0f;
		}
		Mode mode = mode(player.getMainHandItem());
		if (mode == Mode.OFF) {
			mode = mode(player.getOffhandItem());
		}
		if (mode == Mode.OFF) {
			return 0f;
		}
		if (mode == Mode.STROBE) {
			strobe += 0.35f;
			float pulse = 0.55f + 0.45f * Mth.sin(strobe);
			return pulse > 0.62f ? pulse : 0.08f;
		}
		return 1f;
	}

	private static Mode mode(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Mode.OFF;
		}
		String optic = GunAttachmentVisuals.pdcStringRaw(stack, "optic_mode");
		if (optic != null) {
			String id = optic.toLowerCase();
			if (id.contains("strobe")) {
				return Mode.STROBE;
			}
			if (id.contains("flash")) {
				return Mode.FLASH;
			}
		}
		boolean on = GunAttachmentVisuals.pdcFlag(stack, "flashlight_on");
		if (!on) {
			return Mode.OFF;
		}
		if (GunAttachmentVisuals.pdcFlag(stack, "flashlight")
				|| GunAttachmentVisuals.pdcFlag(stack, "flashlight_mod")) {
			return Mode.FLASH;
		}
		return Mode.OFF;
	}

	private enum Mode {
		OFF, FLASH, STROBE
	}
}
