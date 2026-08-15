package net.minenite.client.mixin;

import net.minenite.client.gun.NvgVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Complementary / Iris read vanilla night-vision. NODS are not a potion, so pretend
 * the local wearer has full NV while tubes are on.
 */
@Mixin(LivingEntity.class)
public class LivingEntityNvgEffectMixin {
	@Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
	private void minenite$nvgHasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect != MobEffects.NIGHT_VISION) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof LocalPlayer player && NvgVision.isWearing(player)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
	private void minenite$nvgBlend(Holder<MobEffect> effect, float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (effect != MobEffects.NIGHT_VISION) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof LocalPlayer player && NvgVision.isWearing(player)) {
			cir.setReturnValue(NvgVision.tubeVisionScale(Minecraft.getInstance(), player, partialTicks));
		}
	}
}
