package net.minenite.client.gun;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the crack and hole marks the WarZ plugin records for tactical glass.
 *
 * The marks are geometry, not a texture: for each impact the entry and exit
 * faces get a burst of radial cracks and concentric rings sized by the round
 * that made them, plus a punched hole and a dark shaft between the two faces so
 * a through-shot reads from the side as well as head on.
 *
 * Ported from the old server's client mod, which drew this through a custom
 * pipeline on a Fabric render event. The geometry and the constants are the same
 * ones; only the plumbing changed - here it hangs off NeoForge's
 * RenderLevelStageEvent, the same hook the laser filaments use.
 */
public final class GlassCrackRenderer {

    /** Mark thickness. Cracks are drawn as short quads a half-pixel across. */
    private static final float PIXEL = 1f / 32f;
    /** Half-thickness of a vanilla pane (2/16), plus a bias so marks sit off the surface. */
    private static final float PANE_HALF = 1f / 16f + 0.0025f;
    private static final float BLOCK_EPS = 0.0025f;
    /** Beyond this the marks are sub-pixel; skip the geometry entirely. */
    private static final double MAX_DISTANCE = 48.0;

    private GlassCrackRenderer() {
    }

    public static void close() {
        GlassCrackQuads.close();
    }

    @SubscribeEvent
    public static void afterTranslucent(RenderLevelStageEvent.AfterTranslucentParticles event) {
        draw(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos);
    }

    public static void draw(PoseStack matrices, Vec3 camera) {
        List<GlassCrackStore.Pane> panes = GlassCrackStore.snapshot();
        if (panes.isEmpty() || ThermalVision.isWearing()) {
            // Thermal sees heat, not surface detail; the original hid them too.
            return;
        }
        GlassCrackQuads.draw(matrices, camera, ctx -> {
            for (GlassCrackStore.Pane pane : panes) {
                double dx = pane.x() + 0.5 - camera.x;
                double dy = pane.y() + 0.5 - camera.y;
                double dz = pane.z() + 0.5 - camera.z;
                if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
                    continue;
                }
                boolean thin = isThinGlass(pane.x(), pane.y(), pane.z());
                for (GlassCrackStore.Impact impact : pane.impacts()) {
                    renderImpact(ctx, pane, impact, thin);
                }
            }
        });
    }

    /** Panes and bars sit in the middle of their block; solid glass fills it. */
    private static boolean isThinGlass(int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(new BlockPos(x, y, z));
        if (state.isAir()) {
            return false;
        }
        String name = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
        return name.endsWith("glass_pane") || name.contains("iron_bars");
    }

    private static void renderImpact(GlassCrackQuads.DrawCtx ctx, GlassCrackStore.Pane pane,
                                     GlassCrackStore.Impact impact, boolean thin) {
        Direction entry = face(impact.face());
        Direction exit = face(impact.exitFace());
        drawPattern(ctx, pane, entry, impact.u(), impact.v(), impact, thin);
        // The far side is marked too: either where the round actually left, or
        // the opposite face when it stopped inside.
        if (impact.exitFace() != impact.face()) {
            drawPattern(ctx, pane, exit, impact.exitU(), impact.exitV(), impact, thin);
        } else {
            drawPattern(ctx, pane, entry.getOpposite(), impact.u(), impact.v(), impact, thin);
        }
        if (impact.hole() || impact.holeR() > 0.04f) {
            drawTunnel(ctx, pane, entry, impact.u(), impact.v(), exit, impact.exitU(), impact.exitV(),
                    impact.holeR(), thin);
        }
    }

    private static void drawPattern(GlassCrackQuads.DrawCtx ctx, GlassCrackStore.Pane pane, Direction dir,
                                    float u, float v, GlassCrackStore.Impact impact, boolean thin) {
        int bx = pane.x();
        int by = pane.y();
        int bz = pane.z();
        float holeR = impact.holeR();
        float crackR = impact.crackR();
        float sev = impact.severity() / 255f;
        int style = impact.style();

        // Charcoal and slate: readable on glass without glowing.
        float cr;
        float cg;
        float cb;
        float ca;
        switch (style) {
            case 3, 7, 8 -> {
                cr = 0.38f;
                cg = 0.39f;
                cb = 0.41f;
                ca = 0.62f + sev * 0.20f;
            }
            case 9 -> {
                cr = 0.36f;
                cg = 0.37f;
                cb = 0.40f;
                ca = 0.60f;
            }
            case 5 -> {
                cr = 0.40f;
                cg = 0.41f;
                cb = 0.42f;
                ca = 0.50f + sev * 0.18f;
            }
            default -> {
                cr = 0.20f;
                cg = 0.20f;
                cb = 0.21f;
                ca = 0.78f + sev * 0.12f;
            }
        }

        // Ray count is the glass's shatter signature: a spiderweb throws more
        // arms than a polycarbonate dent.
        int rays = switch (style) {
            case 1 -> 14;
            case 2, 7, 12 -> 12;
            case 6, 10, 11 -> 10;
            case 5 -> 6;
            default -> 8;
        };
        float seed = bx * 12.9898f + by * 78.233f + bz * 37.719f + u * 50f + v * 90f + dir.ordinal() * 3.7f;

        for (int r = 0; r < rays; r++) {
            float ang = (float) (Math.PI * 2.0 * r / rays + hash(seed + r) * 0.35f);
            float len = crackR * (0.55f + hash(seed + r * 3.1f) * 0.55f);
            int steps = Math.max(3, (int) (len / PIXEL));
            for (int s = 1; s <= steps; s++) {
                float t = s / (float) steps;
                float ru = u + Mth.cos(ang) * len * t;
                float rv = v + Mth.sin(ang) * len * t;
                if (ru < 0.02f || ru > 0.98f || rv < 0.02f || rv > 0.98f) {
                    break;
                }
                float px = PIXEL * (0.7f + sev * 0.5f);
                markOnFace(ctx, bx, by, bz, dir, ru, rv, px, cr, cg, cb, ca * (1f - t * 0.35f), thin);
                // Spiderweb and craze styles fork; the others run straight.
                if ((style == 1 || style == 2 || style == 7) && s % 3 == 0) {
                    float a2 = ang + (hash(seed + s) - 0.5f) * 1.2f;
                    markOnFace(ctx, bx, by, bz, dir,
                            ru + Mth.cos(a2) * PIXEL * 2.5f, rv + Mth.sin(a2) * PIXEL * 2.5f,
                            px * 0.8f, cr, cg, cb, ca * 0.7f, thin);
                }
            }
        }

        int rings = style == 5 ? 1 : (2 + (int) (sev * 2));
        for (int ring = 1; ring <= rings; ring++) {
            float rad = crackR * (ring / (float) (rings + 1));
            int segs = 10 + ring * 4;
            for (int s = 0; s < segs; s++) {
                float ang = (float) (Math.PI * 2.0 * s / segs);
                float ru = u + Mth.cos(ang) * rad;
                float rv = v + Mth.sin(ang) * rad;
                if (ru < 0.02f || ru > 0.98f || rv < 0.02f || rv > 0.98f) {
                    continue;
                }
                markOnFace(ctx, bx, by, bz, dir, ru, rv, PIXEL * 0.85f, cr, cg, cb, ca * 0.75f, thin);
            }
        }

        if (impact.hole() || holeR > 0.03f) {
            float hr = Math.max(PIXEL * 1.2f, holeR);
            for (int s = 0; s < 12; s++) {
                float ang = (float) (Math.PI * 2.0 * s / 12);
                markOnFace(ctx, bx, by, bz, dir,
                        u + Mth.cos(ang) * hr, v + Mth.sin(ang) * hr,
                        PIXEL, 0.10f, 0.10f, 0.11f, 0.85f, thin);
            }
            int fill = Math.max(4, (int) (hr / PIXEL));
            for (int iy = -fill; iy <= fill; iy++) {
                for (int ix = -fill; ix <= fill; ix++) {
                    if ((ix * ix + iy * iy) * PIXEL * PIXEL > hr * hr) {
                        continue;
                    }
                    markOnFace(ctx, bx, by, bz, dir,
                            u + ix * PIXEL * 0.9f, v + iy * PIXEL * 0.9f,
                            PIXEL * 0.95f, 0.07f, 0.07f, 0.08f, 0.82f, thin);
                }
            }
        }

        // A pane near failure hazes over around the worst hits.
        if (pane.damageRatio() > 0.55f && (style == 3 || style == 7 || style == 8)) {
            float haze = crackR * (0.4f + pane.damageRatio());
            for (int i = 0; i < 18; i++) {
                float ru = u + (hash(seed + i) - 0.5f) * haze * 2f;
                float rv = v + (hash(seed + i * 7) - 0.5f) * haze * 2f;
                if (ru < 0.05f || ru > 0.95f || rv < 0.05f || rv > 0.95f) {
                    continue;
                }
                markOnFace(ctx, bx, by, bz, dir, ru, rv, PIXEL * 1.4f, 0.32f, 0.33f, 0.34f, 0.28f, thin);
            }
        }
    }

    /** Dark shaft between entry and exit, so a through-hole reads from the side. */
    private static void drawTunnel(GlassCrackQuads.DrawCtx ctx, GlassCrackStore.Pane pane,
                                   Direction entry, float u, float v,
                                   Direction exit, float eu, float ev, float holeR, boolean thin) {
        float r = Math.max(PIXEL, holeR * 0.85f);
        Vec3 a = facePoint(pane, entry, u, v, thin);
        Vec3 b = facePoint(pane, exit, eu, ev, thin);
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 dir = along.normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8) {
            side = dir.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize().scale(r);
        Vec3 up = dir.cross(side).normalize().scale(r);
        for (Vec3 off : new Vec3[]{side, up}) {
            GlassCrackQuads.quad(ctx,
                    (float) (a.x - off.x), (float) (a.y - off.y), (float) (a.z - off.z),
                    (float) (a.x + off.x), (float) (a.y + off.y), (float) (a.z + off.z),
                    (float) (b.x + off.x), (float) (b.y + off.y), (float) (b.z + off.z),
                    (float) (b.x - off.x), (float) (b.y - off.y), (float) (b.z - off.z),
                    0.05f, 0.05f, 0.06f, 0.9f);
        }
    }

    private static Vec3 facePoint(GlassCrackStore.Pane pane, Direction dir, float u, float v, boolean thin) {
        float plane = planeOffset(dir, thin);
        return switch (dir) {
            case UP, DOWN -> new Vec3(pane.x() + u, pane.y() + plane, pane.z() + v);
            case NORTH, SOUTH -> new Vec3(pane.x() + u, pane.y() + v, pane.z() + plane);
            default -> new Vec3(pane.x() + plane, pane.y() + v, pane.z() + u);
        };
    }

    /** One mark, laid flat on the given face at (u, v). */
    private static void markOnFace(GlassCrackQuads.DrawCtx ctx, int bx, int by, int bz, Direction dir,
                                   float u, float v, float size,
                                   float r, float g, float b, float a, boolean thin) {
        float half = size * 0.5f;
        float plane = planeOffset(dir, thin);
        switch (dir) {
            case UP -> {
                float y = by + plane;
                GlassCrackQuads.quad(ctx,
                        bx + u - half, y, bz + v - half,
                        bx + u + half, y, bz + v - half,
                        bx + u + half, y, bz + v + half,
                        bx + u - half, y, bz + v + half, r, g, b, a);
            }
            case DOWN -> {
                float y = by + plane;
                GlassCrackQuads.quad(ctx,
                        bx + u - half, y, bz + v + half,
                        bx + u + half, y, bz + v + half,
                        bx + u + half, y, bz + v - half,
                        bx + u - half, y, bz + v - half, r, g, b, a);
            }
            case SOUTH -> {
                float z = bz + plane;
                GlassCrackQuads.quad(ctx,
                        bx + u - half, by + v - half, z,
                        bx + u + half, by + v - half, z,
                        bx + u + half, by + v + half, z,
                        bx + u - half, by + v + half, z, r, g, b, a);
            }
            case NORTH -> {
                float z = bz + plane;
                GlassCrackQuads.quad(ctx,
                        bx + u + half, by + v - half, z,
                        bx + u - half, by + v - half, z,
                        bx + u - half, by + v + half, z,
                        bx + u + half, by + v + half, z, r, g, b, a);
            }
            case EAST -> {
                float x = bx + plane;
                GlassCrackQuads.quad(ctx,
                        x, by + v - half, bz + u - half,
                        x, by + v - half, bz + u + half,
                        x, by + v + half, bz + u + half,
                        x, by + v + half, bz + u - half, r, g, b, a);
            }
            default -> {
                float x = bx + plane;
                GlassCrackQuads.quad(ctx,
                        x, by + v - half, bz + u + half,
                        x, by + v - half, bz + u - half,
                        x, by + v + half, bz + u - half,
                        x, by + v + half, bz + u + half, r, g, b, a);
            }
        }
    }

    /** Where the face sits inside the block: mid-block for a pane, the edge for solid glass. */
    private static float planeOffset(Direction dir, boolean thin) {
        if (thin) {
            return switch (dir) {
                case UP, SOUTH, EAST -> 0.5f + PANE_HALF;
                default -> 0.5f - PANE_HALF;
            };
        }
        return switch (dir) {
            case UP, SOUTH, EAST -> 1f + BLOCK_EPS;
            default -> -BLOCK_EPS;
        };
    }

    private static Direction face(byte f) {
        return switch (f) {
            case 0 -> Direction.DOWN;
            case 1 -> Direction.UP;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.WEST;
            case 5 -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    private static float hash(float n) {
        float x = Mth.sin(n * 12.9898f) * 43758.5453f;
        return x - Mth.floor(x);
    }
}
