package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strip offhand guns from third-person hand item models (stack kept for {@link net.minenite.client.gun.GunBackLayer}).
 */
@Mixin(AvatarRenderer.class)
public class ClearOffhandGunStateMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void minenite$clearOffhandGunFromHands(
			Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci
	) {
		ItemStack off = state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack;
		if (!GunItemPose.isGun(off)) {
			return;
		}
		if (state.mainArm == HumanoidArm.RIGHT) {
			state.leftHandItemState.clear();
		} else {
			state.rightHandItemState.clear();
		}
	}
}
