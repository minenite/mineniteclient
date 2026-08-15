package net.minenite.client.gun;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Crack and hole marks for tactical glass, as sent by the WarZ plugin.
 *
 * The server tracks every shot into a pane - where it entered, where it left,
 * how big the hole is, how far the cracks run - and broadcasts the result on
 * {@code pvpgunminus:glass}. Nothing on this side read it, so glass took damage
 * and shattered on schedule but never actually looked hit.
 *
 * Wire format (GlassService): a protocol byte, an action, then per pane a UTF-8
 * world key, block coordinates, the glass type ordinal, a damage byte and a list
 * of impacts. Every float is sent as a byte over 255.
 */
public final class GlassCrackStore {

    public static final int PROTOCOL = 2;
    private static final byte ACT_UPSERT = 1;
    private static final byte ACT_CLEAR = 2;
    private static final byte ACT_FULL = 3;

    private static final Map<Long, Pane> PANES = new ConcurrentHashMap<>();

    private GlassCrackStore() {
    }

    /** One shot through a pane. Faces are Direction ordinals; u/v are 0..1 across the face. */
    public record Impact(byte face, float u, float v, float holeR, float crackR,
                         int style, int severity, boolean hole,
                         byte exitFace, float exitU, float exitV) {
    }

    public record Pane(int x, int y, int z, int typeOrdinal, float damageRatio, List<Impact> impacts) {
    }

    public static void clear() {
        PANES.clear();
    }

    public static List<Pane> snapshot() {
        return PANES.isEmpty() ? List.of() : new ArrayList<>(PANES.values());
    }

    public static void accept(byte[] raw) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        int protocol = in.readUnsignedByte();
        if (protocol != PROTOCOL) {
            return;
        }
        byte action = in.readByte();
        String world = currentWorldKey();
        switch (action) {
            case ACT_CLEAR -> {
                String key = readWorldKey(in);
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                if (matches(world, key)) {
                    PANES.remove(BlockPos.asLong(x, y, z));
                }
            }
            case ACT_UPSERT -> readPane(in, world);
            case ACT_FULL -> {
                // A full sync replaces everything: it arrives on join and after a
                // reload, and the panes it does not mention no longer exist.
                PANES.clear();
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++) {
                    readPane(in, world);
                }
            }
            default -> {
            }
        }
    }

    private static void readPane(DataInputStream in, String world) throws Exception {
        String key = readWorldKey(in);
        int x = in.readInt();
        int y = in.readInt();
        int z = in.readInt();
        int type = in.readUnsignedByte();
        float damage = in.readUnsignedByte() / 255f;
        int impactCount = in.readUnsignedByte();
        List<Impact> impacts = new ArrayList<>(impactCount);
        for (int i = 0; i < impactCount; i++) {
            impacts.add(new Impact(
                    in.readByte(),
                    in.readUnsignedByte() / 255f,
                    in.readUnsignedByte() / 255f,
                    in.readUnsignedByte() / 255f,
                    in.readUnsignedByte() / 255f,
                    in.readUnsignedByte(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte() != 0,
                    in.readByte(),
                    in.readUnsignedByte() / 255f,
                    in.readUnsignedByte() / 255f));
        }
        if (!matches(world, key)) {
            return;
        }
        long at = BlockPos.asLong(x, y, z);
        if (impacts.isEmpty()) {
            PANES.remove(at);
        } else {
            PANES.put(at, new Pane(x, y, z, type, damage, impacts));
        }
    }

    private static String readWorldKey(DataInputStream in) throws Exception {
        int len = in.readUnsignedShort();
        byte[] raw = new byte[len];
        in.readFully(raw);
        return new String(raw, StandardCharsets.UTF_8);
    }

    /** Marks are keyed per world, so a pane in the nether must not draw in the overworld. */
    private static boolean matches(String current, String incoming) {
        return current.isEmpty() || incoming.isEmpty() || current.equals(incoming);
    }

    private static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }
}
