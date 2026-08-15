package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

/** Soft outdoor-night HUD veil so unlit night reads dark without NODS. */
public final class NightWorldWash {
	private NightWorldWash() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || NvgVision.isWearing(player) || ThermalVision.isWearing(player)) {
			return;
		}
		float night = NvgVision.nightAmount(mc, 1f);
		float skyOpen = NvgVision.skyOpen(mc, player, 1f);
		float outdoor = night * skyOpen;
		float cave = Mth.clamp((1f - skyOpen) * (1f - skyOpen), 0f, 1f);
		float caveDark = cave > 0.35f ? Mth.clamp((cave - 0.35f) / 0.65f, 0f, 1f) : 0f;
		float moonDark = NvgVision.moonDarkness(mc);
		BlockPos eye = BlockPos.containing(player.getEyePosition(1f));
		float block = mc.level.getBrightness(LightLayer.BLOCK, eye) / 15f;
		float lampClear = Mth.clamp(1f - block * 1.35f, 0.12f, 1f);
		boolean shaders = ShaderHooks.packInUse();
		float a = 0f;
		if (outdoor >= 0.08f) {
			float fullA = Mth.lerp(Mth.clamp(outdoor, 0f, 1f), shaders ? 0.38f : 0.18f, shaders ? 0.78f : 0.42f);
			float newA = Mth.lerp(Mth.clamp(outdoor, 0f, 1f), shaders ? 0.52f : 0.32f, shaders ? 0.90f : 0.72f);
			a = Math.max(a, Mth.lerp(moonDark, fullA, newA));
		}
		if (caveDark > 0.05f) {
			a = Math.max(a, Mth.lerp(caveDark, shaders ? 0.14f : 0.08f, shaders ? 0.42f : 0.28f));
		}
		a *= lampClear;
		if (a < 0.04f) {
			return;
		}
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		int maxA = Mth.clamp((int) Mth.lerp(moonDark, shaders ? 160f : 120f, shaders ? 230f : 175f),
				shaders ? 160 : 120, shaders ? 230 : 175);
		int baseA = Mth.clamp((int) (a * 255f), 0, maxA);
		if (FlashlightVision.strength(player) > 0.05f) {
			baseA = Mth.clamp((int) (baseA * 0.40f), 0, maxA);
		}
		graphics.fill(0, 0, w, h, ARGB.color(baseA, 0, 0, 0));
	}
}
