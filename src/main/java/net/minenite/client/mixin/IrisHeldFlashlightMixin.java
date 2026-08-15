package net.minenite.client.mixin;

import net.minenite.client.gun.FlashlightVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simply Upscaled flashlight is a held-light cone in the shader. Iris only
 * feeds {@code heldBlockLightValue} for block items, so a WarZ stick/gun
 * would stay dark — push 15 while the WarZ light is on.
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier", remap = false)
public class IrisHeldFlashlightMixin {
	@Shadow
	private int lightValue;

	@Shadow
	private InteractionHand hand;

	@Inject(method = "update", at = @At("RETURN"))
	private void minenite$warzFlashlightHeldLight(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || hand == null) {
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		int emit = FlashlightVision.emission(stack);
		if (emit > this.lightValue) {
			this.lightValue = emit;
		}
	}
}
