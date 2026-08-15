package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 can swallow left-clicks (attack cooldown / missTime) before a swing
 * packet is sent. Guns need that swing for ADS.
 */
@Mixin(Minecraft.class)
public class GunAttackMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	protected int missTime;

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void minenite$gunAdsNoMissDelay(CallbackInfoReturnable<Boolean> cir) {
		if (this.player != null && GunItemPose.isGun(this.player.getMainHandItem())) {
			this.missTime = 0;
		}
	}

	@Redirect(
			method = "startAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;cannotAttackWithItem(Lnet/minecraft/world/item/ItemStack;I)Z"
			)
	)
	private boolean minenite$gunsAlwaysSwing(LocalPlayer instance, ItemStack stack, int extra) {
		if (GunItemPose.isGun(stack)) {
			return false;
		}
		return instance.cannotAttackWithItem(stack, extra);
	}
}
