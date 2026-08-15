package net.minenite.client.mixin;

import net.minenite.client.gun.vision.TemperatureField;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void minenite$thermalExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		Vec3 c = packet.center();
		if (c == null) {
			return;
		}
		TemperatureField.get().explode(c.x, c.y, c.z, Math.max(0.5f, packet.radius()));
	}
}
