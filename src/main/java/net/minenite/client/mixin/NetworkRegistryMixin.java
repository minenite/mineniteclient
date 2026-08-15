package net.minenite.client.mixin;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minenite.client.gun.LaserNet;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge refuses to send a payload that was dropped from connection setup.
 * CardForge used to omit pvpgunminus:* from negotiation, so hello never left
 * the client and the scope HUD stayed inactive. Allow those sends anyway.
 *
 * <p>getCodec fallback keeps plugin-message bodies intact when the channel was
 * not in payload setup (otherwise they decode as empty DiscardedPayload).
 */
@Mixin(NetworkRegistry.class)
public class NetworkRegistryMixin {

	@Inject(
			method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;)V",
			at = @At("HEAD"),
			cancellable = true)
	private static void minenite$allowPvpGunMinusSend(Packet<?> packet, ClientCommonPacketListener listener, CallbackInfo ci) {
		if (!(packet instanceof ServerboundCustomPayloadPacket custom)) {
			return;
		}
		CustomPacketPayload payload = custom.payload();
		if (payload == null) {
			return;
		}
		Identifier id = payload.type().id();
		if (id != null && "pvpgunminus".equals(id.getNamespace())) {
			ci.cancel();
		}
	}

	@Inject(method = "getCodec", at = @At("RETURN"), cancellable = true)
	private static void minenite$pvpGunMinusCodec(
			Identifier id,
			ConnectionProtocol protocol,
			PacketFlow flow,
			CallbackInfoReturnable<StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload>> cir) {
		if (cir.getReturnValue() != null || id == null || !"pvpgunminus".equals(id.getNamespace())) {
			return;
		}
		cir.setReturnValue(LaserNet.BytesPayload.codec(id));
	}
}
