package net.minenite.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the client putting an item into creative that the server cannot decode.
 *
 * <p>Creative slot changes send the item to the server, and an item is written as
 * a numeric registry id. A server that does not have the mod has no such id, so
 * it fails to read the packet and drops the connection - the client is left
 * looking at a decoder error for taking a block out of its own creative menu:
 *
 * <pre>
 * Internal Exception: io.netty.handler.codec.DecoderException: Failed to decode
 * packet 'serverbound/minecraft:set_creative_mode_slot'
 * </pre>
 *
 * <p>The message comes from the server and cannot be improved from here, so the
 * packet is not sent at all. Nothing appears in the slot, which is the honest
 * outcome: the item does not exist on that server, and no amount of sending will
 * make it.
 *
 * <p>Only non-vanilla items on a non-NeoForge connection are held back. On a
 * NeoForge server the registries are synced and modded items are perfectly
 * sendable, so nothing changes there.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    private static final Logger MINENITE$LOGGER = LoggerFactory.getLogger("MineniteClient");

    /**
     * Whether this stack would be unintelligible to the server we are on.
     *
     * <p>Namespace is the test rather than a registry lookup: the client's own
     * registry contains the item either way, since the client is the side that
     * loaded the mod. What matters is whether the other end could possibly know
     * it, and on a connection with no mod negotiation the answer is no for
     * anything outside {@code minecraft}.
     */
    @Unique
    private static boolean minenite$serverCannotDecode(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || connection.getConnectionType().isNeoForge()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && !"minecraft".equals(id.getNamespace());
    }

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void minenite$dontSendUnknownItem(ItemStack stack, int slot, CallbackInfo ci) {
        if (minenite$serverCannotDecode(stack)) {
            MINENITE$LOGGER.info("Not sending {} to a server without its mod - it would be kicked as an undecodable packet",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()));
            ci.cancel();
        }
    }

    @Inject(method = "handleCreativeModeItemDrop", at = @At("HEAD"), cancellable = true)
    private void minenite$dontDropUnknownItem(ItemStack stack, CallbackInfo ci) {
        if (minenite$serverCannotDecode(stack)) {
            ci.cancel();
        }
    }
}
