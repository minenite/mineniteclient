package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person: don't draw a slung offhand gun in the left hand. */
@Mixin(ItemInHandRenderer.class)
public class HideOffhandGunFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void minenite$hideOffhandGunFp(
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
		if (hand == InteractionHand.OFF_HAND && GunItemPose.isGun(itemStack)) {
			ci.cancel();
		}
	}
}
