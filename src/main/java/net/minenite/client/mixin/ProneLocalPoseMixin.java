package net.minenite.client.mixin;

import net.minenite.client.gun.ProneClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the local player in the crawl pose while the server says prone. */
@Mixin(Player.class)
public class ProneLocalPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void minenite$keepPronePose(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!(self instanceof LocalPlayer)) {
			return;
		}
		if (!ProneClient.isProne()) {
			return;
		}
		self.setPose(Pose.SWIMMING);
		self.setSwimming(true);
		ci.cancel();
	}
}
