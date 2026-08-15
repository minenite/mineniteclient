package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Main-hand gun poses:
 * - ADS → full bow aim
 * - Hipfire → partial crossbow hold
 * - Idle carry → EMPTY (across-body applied in PlayerModel mixin)
 */
@Mixin(AvatarRenderer.class)
public class GunArmPoseMixin {
	@Inject(
			method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("RETURN"),
			cancellable = true
	)
	private static void minenite$gunArmPose(
			Avatar avatar, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<HumanoidModel.ArmPose> cir
	) {
		if (hand != InteractionHand.MAIN_HAND || stack.isEmpty() || !GunItemPose.isGun(stack)) {
			return;
		}
		if (avatar.swinging) {
			return;
		}
		if (GunItemPose.isAiming(avatar)) {
			cir.setReturnValue(HumanoidModel.ArmPose.BOW_AND_ARROW);
		} else if (GunItemPose.isHipfiring(avatar)) {
			cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		} else {
			cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
		}
	}
}
