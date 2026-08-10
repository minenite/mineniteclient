package net.minenite.client.mixin;

import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a modded client connect to a server that does not have its mods.
 *
 * <p>When the server is not NeoForge, or is NeoForge without the same mods, the
 * client negotiates its own required channels against what that server offers.
 * Any client mod declaring a channel it needs on both sides fails the match and
 * the connection is refused before it starts:
 *
 * <pre>
 * You are trying to connect to a server that is not running NeoForge, but you
 * have mods that require it. A connection could not be established.
 * </pre>
 *
 * <p>That check is a sensible default and this deliberately overrides it. On a
 * network where one client joins servers with different mod sets - or a vanilla
 * server between them - the client is expected to carry mods the server has
 * never heard of.
 *
 * <h2>The tradeoff, stated plainly</h2>
 *
 * <p>The check exists to turn "this server lacks a mod you need" into one clear
 * message instead of odd behaviour later. Overriding it gives up that clarity.
 * A client mod that tries to send on a channel the server does not have will
 * fail at send time rather than at connect time, and any feature depending on a
 * server-side counterpart simply will not work. Nothing is corrupted by this -
 * the mod's content is absent from that server anyway - but a broken feature
 * will look like a bug rather than an unmet requirement.
 *
 * <p>Vanilla servers are the easy case: none of the client's modded content
 * exists to interact with, so there is nothing to half-work.
 *
 * <p>Note this is only the negotiation. Data the client cannot resolve is
 * handled separately, in {@link DataComponentInitializersMixin}; both are needed
 * to actually join and play.
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
}
