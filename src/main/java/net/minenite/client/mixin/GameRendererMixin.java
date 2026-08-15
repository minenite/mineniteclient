package net.minenite.client.mixin;

import net.minenite.client.gun.LaserBeamRenderer;
import net.minenite.client.gun.NvgVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void minenite$closeLaserBuffers(CallbackInfo ci) {
		LaserBeamRenderer.close();
	}

	@Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
	private static void minenite$nvgNightVision(LivingEntity camera, float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (camera instanceof LocalPlayer player && NvgVision.isWearing(player)) {
			cir.setReturnValue(NvgVision.tubeVisionScale(Minecraft.getInstance(), player, partialTicks));
		}
	}
}
