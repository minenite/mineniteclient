package net.minenite.client.mixin;

import net.minenite.client.gun.ProneClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Force full crawl lean — swimAmount must be 1 for the body to lie flat. */
@Mixin(AvatarRenderer.class)
public class ProneRenderStateMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void minenite$forceProneSwim(
			Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci
	) {
		if (!ProneClient.shouldCrawlVisual(entity)) {
			return;
		}
		state.swimAmount = 1f;
		state.isVisuallySwimming = true;
	}
}
