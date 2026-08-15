package net.minenite.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minenite.client.gun.ScopeOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ADS with a sight/scope: hide the held gun so the 2D optic HUD is visible. */
@Mixin(ItemInHandRenderer.class)
public class HideAdsHandsMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void minenite$hideGunWhileScoped(
			AbstractClientPlayer player,
			float frameInterp,
			float xRot,
			InteractionHand hand,
			float attack,
			ItemStack itemStack,
			float inverseArmHeight,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		if (player instanceof LocalPlayer && ScopeOverlay.hidesHeldGun()) {
			ci.cancel();
		}
	}
}
