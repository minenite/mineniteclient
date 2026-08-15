package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WarZ guns are sticks. Vanilla only sends {@code ServerboundUseItemPacket} when
 * {@code ItemStack.use} returns Success, so RMB on a gun looking at air or a
 * distant zombie sent nothing — the server never fired. Force the use packet.
 */
@Mixin(Minecraft.class)
public class GunUseMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	public MultiPlayerGameMode gameMode;

	@Shadow
	private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void minenite$gunsAlwaysSendUse(CallbackInfo ci) {
		if (this.player == null || this.gameMode == null) {
			return;
		}
		if (this.gameMode.isDestroying() || this.player.isHandsBusy()) {
			return;
		}
		ItemStack stack = this.player.getMainHandItem();
		if (!GunItemPose.isGun(stack)) {
			return;
		}
		this.rightClickDelay = 4;
		this.gameMode.useItem(this.player, InteractionHand.MAIN_HAND);
		ci.cancel();
	}
}
