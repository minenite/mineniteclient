package net.minenite.client.gun.warz;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

/**
 * Client state for the WarZ effects the server broadcasts.
 *
 * Each of these channels was registered with an empty handler so NeoForge
 * negotiation would succeed, and every packet was dropped - the server has been
 * sending smoke clouds, flares, anomalies, blast shocks, lock-on progress,
 * storms and workbench positions the whole time with nothing listening.
 *
 * The wire formats here are read straight from the services that write them:
 * SmokeService, FlareService, AnomalyService, LaserCompanionBridge#encodeBlast,
 * JavelinService, WeatherService and GunWorkbenchService.
 */
public final class WarzFxStores {

    private WarzFxStores() {
    }

    private static DataInputStream in(byte[] raw) {
        return new DataInputStream(new ByteArrayInputStream(raw));
    }

    /* ------------------------------------------------------------ smoke */

    public record Cloud(int id, int type, double x, double y, double z,
                        float radius, float density, int age, int life,
                        boolean irPrimary, float nvgWash, float thermalBlock, int rgb) {
        /** 0 at birth, 1 at the end of its life. */
        public float life01() {
            return life <= 0 ? 1f : Math.min(1f, age / (float) life);
        }
    }

    private static final Map<Integer, Cloud> CLOUDS = new ConcurrentHashMap<>();
    private static final byte SMOKE_UPSERT = 1;
    private static final byte SMOKE_REMOVE = 2;
    private static final byte SMOKE_CLEAR_ALL = 3;

    public static List<Cloud> clouds() {
        return CLOUDS.isEmpty() ? List.of() : new ArrayList<>(CLOUDS.values());
    }

    public static void acceptSmoke(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        byte action = d.readByte();
        if (action == SMOKE_CLEAR_ALL) {
            CLOUDS.clear();
            return;
        }
        if (action == SMOKE_REMOVE) {
            CLOUDS.remove(d.readInt());
            return;
        }
        // UPSERT and the full sync share a shape: a short count, then clouds.
        int count = d.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int id = d.readInt();
            CLOUDS.put(id, new Cloud(id, d.readUnsignedByte(),
                    d.readFloat(), d.readFloat(), d.readFloat(),
                    d.readFloat(), d.readFloat(),
                    d.readUnsignedShort(), d.readUnsignedShort(),
                    d.readUnsignedByte() != 0, d.readFloat(), d.readFloat(), d.readInt()));
        }
    }

    /* ------------------------------------------------------------ flares */

    public record Flare(int id, int colorId, double x, double y, double z,
                        int light, int bloomRgb, int lightR, int lightG, int lightB) {
    }

    private static final Map<Integer, Flare> FLARES = new ConcurrentHashMap<>();

    public static List<Flare> flares() {
        return FLARES.isEmpty() ? List.of() : new ArrayList<>(FLARES.values());
    }

    public static void acceptFlare(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        byte action = d.readByte();
        if (action == 3) {
            FLARES.clear();
            return;
        }
        if (action == 2) {
            FLARES.remove(d.readInt());
            return;
        }
        int count = d.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            int id = d.readInt();
            int colorId = d.readUnsignedByte();
            double x = d.readFloat();
            double y = d.readFloat();
            double z = d.readFloat();
            int light = d.readUnsignedByte();
            int bloom = d.readInt();
            int r = 0;
            int g = 0;
            int b = 0;
            if (d.readUnsignedByte() != 0) {
                r = d.readInt();
                g = d.readInt();
                b = d.readInt();
            }
            FLARES.put(id, new Flare(id, colorId, x, y, z, light, bloom, r, g, b));
        }
    }

    /* --------------------------------------------------------- anomalies */

    public record Anomaly(long hi, long lo, int type, double x, double y, double z,
                          float yaw, float pitch, int flags,
                          float scaleY, float interference, float lookProgress) {
    }

    private static volatile List<Anomaly> anomalies = List.of();

    public static List<Anomaly> anomalies() {
        return anomalies;
    }

    public static void acceptAnomaly(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        int count = d.readUnsignedByte();
        List<Anomaly> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(new Anomaly(d.readLong(), d.readLong(), d.readUnsignedByte(),
                    d.readDouble(), d.readDouble(), d.readDouble(),
                    d.readFloat(), d.readFloat(), d.readUnsignedByte(),
                    d.readFloat(), d.readFloat(), d.readFloat()));
        }
        anomalies = List.copyOf(out);
    }

    /* ------------------------------------------------------------- blast */

    /** Shock from a nearby explosion: shake, flash, ringing ears and muffling. */
    public record Blast(float pressure, float flash, float tinnitus, float muffle,
                        float knockback, float dirX, float dirZ,
                        int shakeTicks, int flashTicks, int tinnitusTicks, int muffleTicks,
                        long startedAtMs) {
    }

    private static volatile Blast blast;

    public static Blast blast() {
        return blast;
    }

    public static void acceptBlast(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        if (d.readByte() != 2) {
            return;
        }
        blast = new Blast(d.readFloat(), d.readFloat(), d.readFloat(), d.readFloat(),
                d.readFloat(), d.readFloat(), d.readFloat(),
                d.readUnsignedShort(), d.readUnsignedShort(),
                d.readUnsignedShort(), d.readUnsignedShort(),
                System.currentTimeMillis());
    }

    /* ------------------------------------------------------ javelin lock */

    private static volatile boolean locked;
    private static volatile int lockProgress;

    public static boolean javelinLocked() {
        return locked;
    }

    /** 0..100. */
    public static int javelinProgress() {
        return lockProgress;
    }

    public static void acceptJavelin(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        locked = d.readUnsignedByte() != 0;
        lockProgress = d.readUnsignedByte();
    }

    /* ----------------------------------------------------------- weather */

    public record Weather(int event, float intensity, float wind, int remainingTicks) {
    }

    private static volatile Weather weather = new Weather(0, 0f, 0f, 0);

    public static Weather weather() {
        return weather;
    }

    public static void acceptWeather(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        weather = new Weather(d.readUnsignedByte(), d.readFloat(), d.readFloat(), d.readInt());
    }

    /* --------------------------------------------------------- workbench */

    private static final Set<Long> WORKBENCHES = ConcurrentHashMap.newKeySet();

    public static boolean isWorkbench(BlockPos pos) {
        return pos != null && WORKBENCHES.contains(pos.asLong());
    }

    public static void acceptWorkbench(byte[] raw) throws Exception {
        DataInputStream d = in(raw);
        d.readUnsignedByte();
        int action = d.readUnsignedByte();
        if (action != 5) {
            // Only the full sync carries positions; anything else is a nudge to
            // re-read, and the next sync will bring the truth.
            return;
        }
        int count = d.readUnsignedShort();
        Set<Long> fresh = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            int x = d.readInt();
            int y = d.readInt();
            int z = d.readInt();
            int len = d.readUnsignedShort();
            byte[] name = new byte[len];
            d.readFully(name);
            // The name travels for the screen title; the position is what the
            // renderer needs.
            new String(name, StandardCharsets.UTF_8);
            fresh.add(BlockPos.asLong(x, y, z));
        }
        WORKBENCHES.clear();
        WORKBENCHES.addAll(fresh);
    }

    /* ------------------------------------------------------------- reset */

    public static void clearAll() {
        CLOUDS.clear();
        FLARES.clear();
        WORKBENCHES.clear();
        anomalies = List.of();
        blast = null;
        locked = false;
        lockProgress = 0;
        weather = new Weather(0, 0f, 0f, 0);
    }
}
