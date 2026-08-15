package net.minenite.client.mixin;

import net.minenite.client.gun.FlashlightVision;
import net.minenite.client.gun.NvgAgc;
import net.minenite.client.gun.NvgVision;
import net.minenite.client.gun.ShaderHooks;
import net.minenite.client.gun.ThermalVision;
import net.minenite.client.gun.vision.NvgPalette;
import net.minenite.client.gun.vision.TemperatureField;
import net.minenite.client.gun.vision.ThermalPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Illumination feed for NODS + outdoor-night crush when tubes are off. */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private boolean needsUpdate;

	@Inject(method = "extract", at = @At("HEAD"))
	private void minenite$forceNvgLightmap(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (NvgVision.isWearing(this.minecraft.player) || ThermalVision.isWearing(this.minecraft.player)
				|| FlashlightVision.isOn(this.minecraft.player)) {
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void minenite$visionLightmap(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (!renderState.needsUpdate) {
			return;
		}
		Minecraft mc = this.minecraft;
		if (mc.level == null || mc.player == null) {
			return;
		}

		float night = NvgVision.nightAmount(mc, partialTicks);
		boolean nvg = NvgVision.isWearing(mc.player);
		boolean thermal = ThermalVision.isWearing(mc.player);
		if (thermal) {
			ThermalPalette palette = TemperatureField.get().palette();
			boolean mono = palette == ThermalPalette.WHITE_HOT || palette == ThermalPalette.BLACK_HOT;
			renderState.nightVisionEffectIntensity = 0f;
			renderState.darknessEffectScale = 0f;
			if (mono) {
				renderState.brightness = 0.18f;
				renderState.ambientColor = new Vector3f(0.06f, 0.06f, 0.07f);
				renderState.skyFactor = 0.18f;
				renderState.skyLightColor = new Vector3f(0.08f, 0.08f, 0.09f);
				renderState.blockFactor = 0.4f;
				renderState.blockLightTint = new Vector3f(0.28f, 0.28f, 0.3f);
			} else {
				renderState.brightness = Math.max(0.35f, renderState.brightness * 0.55f);
				renderState.ambientColor = new Vector3f(0.12f, 0.14f, 0.18f);
				renderState.skyFactor = Math.max(0.35f, renderState.skyFactor * 0.55f);
				renderState.skyLightColor = new Vector3f(0.2f, 0.24f, 0.3f);
				renderState.blockFactor = Math.max(0.85f, renderState.blockFactor * 0.9f);
				renderState.blockLightTint = new Vector3f(0.55f, 0.6f, 0.7f);
			}
			return;
		}
		boolean flashlight = FlashlightVision.isOn(mc.player);
		if (!nvg && ShaderHooks.packInUse() && !flashlight) {
			return;
		}
		if (flashlight && !nvg && !thermal) {
			if (ShaderHooks.packInUse()) {
				return;
			}
			this.needsUpdate = true;
			renderState.darknessEffectScale = 0f;
			renderState.brightness = Math.max(renderState.brightness, 0.42f);
			renderState.blockFactor = Math.max(renderState.blockFactor, 1.55f);
			renderState.blockLightTint = new Vector3f(
					Math.max(renderState.blockLightTint.x(), 1.15f),
					Math.max(renderState.blockLightTint.y(), 1.05f),
					Math.max(renderState.blockLightTint.z(), 0.85f));
			return;
		}
		BlockPos eye = BlockPos.containing(mc.player.getEyePosition(partialTicks));
		float eyeSky = mc.level.getBrightness(LightLayer.SKY, eye) / 15f;
		float skyOpen = NvgVision.skyOpen(mc, mc.player, partialTicks);
		float outdoorNight = Math.max(eyeSky * night, skyOpen * night);
		float moonDark = NvgVision.moonDarkness(mc);
		float moonBright = 1f - moonDark;

		if (!nvg) {
			float crush = 0f;
			if (night > 0.001f && (outdoorNight > 0.02f || night > 0.12f)) {
				crush = Math.max(crush, Mth.clamp(Math.max(outdoorNight, night * skyOpen), 0f, 1f));
			}
			float cave = Mth.clamp((1f - skyOpen) * (1f - eyeSky), 0f, 1f);
			if (cave > 0.3f) {
				crush = Math.max(crush, Mth.clamp((cave - 0.25f) * 0.85f, 0f, 0.75f));
			}
			if (crush < 0.04f) {
				return;
			}
			float outdoorBoost = skyOpen * night * moonDark;
			crush = Mth.clamp(crush * (1f + outdoorBoost * 0.55f) + outdoorBoost * 0.22f, 0f, 1f);
			renderState.nightVisionEffectIntensity = 0f;
			renderState.darknessEffectScale = Math.min(renderState.darknessEffectScale, 0.02f);
			float brightFloor = Mth.lerp(moonDark * skyOpen, 0.08f, 0.02f);
			renderState.brightness = Math.max(brightFloor,
					renderState.brightness * (1f - crush * Mth.lerp(moonDark, 0.72f, 0.92f))
							- crush * Mth.lerp(moonDark, 0.18f, 0.32f));
			float ambMul = Mth.lerp(moonDark, 0.06f, 0.02f);
			renderState.ambientColor = new Vector3f(
					renderState.ambientColor.x() * (1f - crush) * ambMul,
					renderState.ambientColor.y() * (1f - crush) * ambMul,
					renderState.ambientColor.z() * (1f - crush) * (ambMul + 0.01f));
			float skyMul = Mth.lerp(moonDark, 0.03f, 0.008f);
			renderState.skyFactor *= Mth.lerp(crush, 1f, skyMul);
			float moonSky = Mth.lerp(moonBright, 0.25f, 1f);
			renderState.skyLightColor = new Vector3f(
					renderState.skyLightColor.x() * Mth.lerp(crush, 1f, skyMul) * moonSky,
					renderState.skyLightColor.y() * Mth.lerp(crush, 1f, skyMul * 1.1f) * moonSky,
					renderState.skyLightColor.z() * Mth.lerp(crush, 1f, skyMul * 1.2f) * moonSky);
			float lampBoost = Mth.lerp(crush, 1f, 1.85f);
			renderState.blockFactor = Math.max(1.15f, renderState.blockFactor * lampBoost);
			renderState.blockLightTint = new Vector3f(
					Math.min(1.8f, 0.95f + crush * 0.55f),
					Math.min(1.7f, 0.88f + crush * 0.45f),
					Math.min(1.5f, 0.75f + crush * 0.35f));
			return;
		}

		NvgAgc agc = NvgAgc.get();
		float bloom = agc.displayGlobalBloom();
		float localBloom = agc.displayLocalBloom();
		float dayFlood = NvgVision.dayFlood(mc, mc.player, partialTicks);
		NvgPalette palette = NvgVision.palette();
		float[] prgb = palette.visionRgb();

		// Shader: color = max(ambient, nightVisionColor * intensity). Vanilla NV is ~1.0
		// so unlit cells still read. Tubes must match that or night crush stays black.
		float nv = NvgVision.tubeVisionScale(mc, mc.player, partialTicks);
		if (palette.trueColor()) {
			nv *= 0.88f;
		}
		renderState.nightVisionEffectIntensity = Mth.clamp(nv, 0.22f, 1f);
		renderState.nightVisionColor = new Vector3f(prgb[0], prgb[1], prgb[2]);
		renderState.darknessEffectScale = 0f;

		float photons = NvgVision.tubePhotons(mc, mc.player, partialTicks);
		float amb = Mth.lerp(dayFlood, 0.10f + photons * 0.18f, 0.85f) * Mth.lerp(bloom, 1f, 0.7f);
		renderState.ambientColor = new Vector3f(prgb[0] * amb * 0.45f, prgb[1] * amb * 0.55f, prgb[2] * amb * 0.45f);
		renderState.brightness = Math.max(renderState.brightness, Mth.lerp(dayFlood, 0.55f + photons * 0.35f, 1.0f));

		float moon = NvgVision.moonBrightness(mc);
		float skyGain = Mth.lerp(night, 1.05f, 1.55f) * Mth.lerp(moon, 0.55f, 1.25f);
		skyGain *= Mth.lerp(dayFlood, 1f, 1.6f);
		skyGain *= Mth.lerp(bloom, 1f, 0.75f);
		renderState.skyFactor = Math.max(renderState.skyFactor, 1.1f) * skyGain;
		float skyTint = Mth.lerp(dayFlood, 0.55f, 1.0f);
		renderState.skyLightColor = new Vector3f(
				Math.max(0.08f, prgb[0] * skyTint),
				Math.max(0.12f, prgb[1] * skyTint),
				Math.max(0.08f, prgb[2] * skyTint));

		float lamp = (1.35f + agc.gain() * 0.9f) * (1f + localBloom * 0.2f) * Mth.lerp(dayFlood, 1f, 0.5f);
		renderState.blockFactor = Math.max(renderState.blockFactor, 1.35f) * lamp;
		renderState.blockLightTint = new Vector3f(
				Math.min(1.6f, 0.35f + prgb[0] * 0.7f + dayFlood * 0.3f),
				Math.min(2.2f, 0.55f + prgb[1] * 1.1f + localBloom * 0.2f + dayFlood * 0.4f),
				Math.min(1.6f, 0.35f + prgb[2] * 0.7f + dayFlood * 0.3f));
	}
}
