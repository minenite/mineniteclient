package net.minenite.client.mixin;

import net.minenite.client.gun.GunAttachmentVisuals;
import net.minenite.client.gun.GunItemPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person rail optics / cans / grips while the hand matrix is still active. */
@Mixin(ItemInHandRenderer.class)
public class GunOpticFirstPersonMixin {
	@Inject(method = "renderItem", at = @At("RETURN"))
	private void minenite$attachmentsFp(
			LivingEntity mob,
			ItemStack itemStack,
			ItemDisplayContext type,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		if (type != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				&& type != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
			return;
		}
		if (!GunItemPose.isGun(itemStack)) {
			return;
		}
		GunAttachmentVisuals.submit(poseStack, submitNodeCollector, lightCoords, itemStack, mob, true);
	}
}
