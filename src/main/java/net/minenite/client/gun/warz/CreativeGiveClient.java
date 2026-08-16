package net.minenite.client.gun.warz;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import net.minecraft.client.Minecraft;

/**
 * The server materialises WarZ items picked out of the creative menu, because
 * the client only has a plain stick with a model on it and creative would hand
 * over exactly that. This tells the client the swap has happened so the screen
 * can refresh and show the real item.
 */
public final class CreativeGiveClient {

    private CreativeGiveClient() {
    }

    public static void accept(byte[] raw) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        in.readUnsignedByte();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.inventoryMenu.broadcastChanges();
        }
    }
}
