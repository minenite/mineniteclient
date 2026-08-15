package net.minenite.client.mixin;

import net.minenite.client.gun.ScopeOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class ScopeCameraMixin {
    @Shadow
    private float yRot;
    @Shadow
    private float xRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "update", at = @At("TAIL"))
    private void minenite$scopeSway(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ScopeOverlay.isScoped()) {
            return;
        }
        float yaw = ScopeOverlay.swayYaw();
        float pitch = ScopeOverlay.swayPitch();
        if (yaw == 0f && pitch == 0f) {
            return;
        }
        this.setRotation(this.yRot + yaw, this.xRot + pitch);
    }
}
