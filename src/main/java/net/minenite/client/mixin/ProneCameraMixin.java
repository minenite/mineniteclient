package net.minenite.client.mixin;

import net.minenite.client.gun.ProneClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raise first-person eye while prone so ADS isn't buried in the floor. */
@Mixin(Camera.class)
public abstract class ProneCameraMixin {
	private static final double PRONE_EYE_LIFT = 0.12;

	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "update", at = @At("TAIL"))
	private void minenite$proneEyeLift(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!ProneClient.isProne()) {
			return;
		}
		Vec3 p = this.position;
		this.setPosition(new Vec3(p.x, p.y + PRONE_EYE_LIFT, p.z));
	}
}
