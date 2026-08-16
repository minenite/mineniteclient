package net.minenite.client.gun.warz;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minenite.client.gun.NvgVision;
import net.minenite.client.gun.ThermalVision;

/**
 * Smoke clouds, flares and anomalies, drawn as particles each tick.
 *
 * The server owns the state - where a cloud is, how big, how far through its
 * life, whether it blocks thermal - and sends it on its own channel. All three
 * channels were being dropped, so a smoke grenade made a bang and then nothing
 * visible, and flares and anomalies were invisible entirely.
 *
 * Particles rather than a custom pipeline: they cost nothing to get right, they
 * respect the player's particle setting, and they sit correctly in the world
 * without fighting the shader pack.
 */
public final class WarzWorldFx {

    /** Beyond this a cloud is a smudge; not worth the particles. */
    private static final double MAX_DISTANCE = 96.0;

    private WarzWorldFx() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        long time = mc.level.getGameTime();
        smoke(mc, eye, time);
        flares(mc, eye, time);
        anomalies(mc, eye, time);
    }

    private static boolean tooFar(Vec3 eye, double x, double y, double z) {
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        return dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE;
    }

    private static void smoke(Minecraft mc, Vec3 eye, long time) {
        boolean thermal = ThermalVision.isWearing();
        for (WarzFxStores.Cloud cloud : WarzFxStores.clouds()) {
            if (tooFar(eye, cloud.x(), cloud.y(), cloud.z())) {
                continue;
            }
            // A thermal-blocking cloud is the point of the grenade: keep drawing
            // it under thermal so the wall of white is visible there too.
            if (thermal && cloud.thermalBlock() <= 0.05f) {
                continue;
            }
            float life = cloud.life01();
            // Billows out early, thins as it dies.
            float fill = Mth.clamp(cloud.density() * (life < 0.15f ? life / 0.15f : 1f - (life - 0.15f) * 0.6f),
                    0f, 1f);
            int puffs = Math.max(1, (int) (cloud.radius() * cloud.radius() * 0.55f * fill));
            java.util.Random rng = new java.util.Random(cloud.id() * 31L + time / 2);
            for (int i = 0; i < puffs; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double dist = Math.sqrt(rng.nextDouble()) * cloud.radius();
                double px = cloud.x() + Math.cos(ang) * dist;
                double pz = cloud.z() + Math.sin(ang) * dist;
                double py = cloud.y() + rng.nextDouble() * Math.max(1.0, cloud.radius() * 0.65);
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz,
                        (rng.nextDouble() - 0.5) * 0.01, 0.005, (rng.nextDouble() - 0.5) * 0.01);
            }
        }
    }

    private static void flares(Minecraft mc, Vec3 eye, long time) {
        for (WarzFxStores.Flare flare : WarzFxStores.flares()) {
            if (tooFar(eye, flare.x(), flare.y(), flare.z())) {
                continue;
            }
            java.util.Random rng = new java.util.Random(flare.id() * 17L + time);
            // Burning core plus the smoke it throws off.
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(ParticleTypes.FLAME,
                        flare.x() + (rng.nextDouble() - 0.5) * 0.25,
                        flare.y() + rng.nextDouble() * 0.2,
                        flare.z() + (rng.nextDouble() - 0.5) * 0.25,
                        0, 0.01, 0);
            }
            if ((time & 1L) == 0L) {
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                        flare.x(), flare.y() + 0.35, flare.z(),
                        (rng.nextDouble() - 0.5) * 0.02, 0.05, (rng.nextDouble() - 0.5) * 0.02);
            }
            // NVG washes out on a burning flare, which is what makes IR flares worth carrying.
            if (NvgVision.isWearing()) {
                mc.level.addParticle(ParticleTypes.END_ROD, flare.x(), flare.y() + 0.2, flare.z(), 0, 0.01, 0);
            }
        }
    }

    private static void anomalies(Minecraft mc, Vec3 eye, long time) {
        for (WarzFxStores.Anomaly anomaly : WarzFxStores.anomalies()) {
            if (tooFar(eye, anomaly.x(), anomaly.y(), anomaly.z())) {
                continue;
            }
            java.util.Random rng = new java.util.Random(anomaly.hi() ^ time);
            double height = Math.max(1.0, anomaly.scaleY());
            for (int i = 0; i < 6; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double r = 0.4 + rng.nextDouble() * 0.9;
                mc.level.addParticle(ParticleTypes.PORTAL,
                        anomaly.x() + Math.cos(ang) * r,
                        anomaly.y() + rng.nextDouble() * height,
                        anomaly.z() + Math.sin(ang) * r,
                        -Math.cos(ang) * 0.05, 0.02, -Math.sin(ang) * 0.05);
            }
            // Interference is how badly it disturbs optics; show it as sparks.
            if (anomaly.interference() > 0.35f && (time & 3L) == 0L) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        anomaly.x() + (rng.nextDouble() - 0.5),
                        anomaly.y() + rng.nextDouble() * height,
                        anomaly.z() + (rng.nextDouble() - 0.5), 0, 0, 0);
            }
        }
    }
}
