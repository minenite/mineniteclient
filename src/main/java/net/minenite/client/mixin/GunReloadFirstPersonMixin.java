package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minenite.client.gun.GunReloadAnimator;
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

/** First-person reload dip / mag cycle on the held gun. */
@Mixin(ItemInHandRenderer.class)
public class GunReloadFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void minenite$reloadFp(
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
		if (hand != InteractionHand.MAIN_HAND || !GunItemPose.isGun(itemStack)) {
			return;
		}
		GunReloadAnimator.applyFirstPerson(poseStack, player);
	}
}
