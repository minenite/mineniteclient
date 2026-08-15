package net.minenite.client.mixin;

import net.minenite.client.gun.ScopeOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraFovMixin {
    @Shadow
    private float fov;

    @Shadow
    private float depthFar;

    @Shadow
    protected abstract void setupPerspective(float zNear, float zFar, float fov, float width, float height);

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void minenite$scopeZoomCalc(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float mult = ScopeOverlay.fovMult();
        if (mult >= 0.999f) {
            return;
        }
        cir.setReturnValue(Math.max(2.5f, cir.getReturnValueF() * mult));
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void minenite$scopeZoomTail(DeltaTracker deltaTracker, CallbackInfo ci) {
        float mult = ScopeOverlay.fovMult();
        if (mult >= 0.999f) {
            return;
        }
        float base = Minecraft.getInstance().options.fov().get().floatValue();
        this.fov = Math.max(2.5f, base * mult);
        var window = Minecraft.getInstance().getWindow();
        this.setupPerspective(0.05F, this.depthFar, this.fov, window.getWidth(), window.getHeight());
    }
}
