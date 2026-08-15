package net.minenite.client.mixin;

import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minenite.client.gun.LaserNet;
import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a modded client connect to a server that does not have its mods, and
 * accept WarZ plugin channels that were dropped from payload negotiation.
 */
@Mixin(ClientNetworkRegistry.class)
public class ClientNetworkRegistryMixin {

    private static final Logger MINENITE$LOGGER = LoggerFactory.getLogger("MineniteClient");

    /**
     * Treats a failed channel negotiation as acceptable.
     *
     * <p>Redirecting the result check rather than the negotiation itself keeps
     * the real reasons intact, so they can be logged: what follows is the same
     * code path a server with no modded channels takes anyway.
     */
    @Redirect(
            method = "initializeOtherConnection",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/network/negotiation/NegotiationResult;success()Z"))
    private static boolean minenite$allowServersMissingOurMods(NegotiationResult result) {
        if (result.success()) {
            return true;
        }

        // Worth a real log line: this is the moment a mod silently loses its
        // server side, and it should be findable when a feature does nothing.
        MINENITE$LOGGER.info("Connecting anyway to a server missing {} of this client's channels: {}",
                result.failureReasons().size(), result.failureReasons().keySet());
        return true;
    }

	@Inject(method = "handleModdedPayload", at = @At("HEAD"), cancellable = true)
	private static void minenite$acceptPvpGunMinus(
			ClientCommonPacketListener listener,
			ClientboundCustomPayloadPacket packet,
			CallbackInfo ci) {
		CustomPacketPayload payload = packet.payload();
		if (payload == null) {
			return;
		}
		Identifier id = payload.type().id();
		if (id == null || !"pvpgunminus".equals(id.getNamespace())) {
			return;
		}
		if (payload instanceof LaserNet.BytesPayload bytes) {
			LaserNet.handleIncoming(id, bytes.raw());
		}
		ci.cancel();
	}
}
