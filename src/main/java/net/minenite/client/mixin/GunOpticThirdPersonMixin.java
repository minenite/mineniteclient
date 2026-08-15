package net.minenite.client.mixin;

import net.minenite.client.gun.GunAttachmentVisuals;
import net.minenite.client.gun.GunItemPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person 3D attachments on bone-CMD guns (while hand pose is still pushed). */
@Mixin(ItemInHandLayer.class)
public class GunOpticThirdPersonMixin<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel<S>> {
	@Inject(
			method = "submitArmWithItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
					shift = At.Shift.AFTER
			)
	)
	private void minenite$attachmentsTp(
			S state,
			ItemStackRenderState item,
			ItemStack itemStack,
			HumanoidArm arm,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		if (arm != avatar.mainArm || !GunItemPose.isGun(itemStack)) {
			return;
		}
		LivingEntity living = GunItemPose.livingFromState(avatar);
		GunAttachmentVisuals.submit(poseStack, submitNodeCollector, lightCoords, itemStack, living, false);
	}
}
