package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide offhand guns from the hand — they render on the back instead. */
@Mixin(ItemInHandLayer.class)
public class HideOffhandGunMixin<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<S>> {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void minenite$hideOffhandGun(
			S state,
			ItemStackRenderState item,
			ItemStack itemStack,
			HumanoidArm arm,
			com.mojang.blaze3d.vertex.PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		if (arm == avatar.mainArm) {
			return;
		}
		if (GunItemPose.isGun(itemStack) || GunItemPose.isGun(arm == HumanoidArm.RIGHT
				? avatar.rightHandItemStack : avatar.leftHandItemStack)) {
			ci.cancel();
		}
	}
}
