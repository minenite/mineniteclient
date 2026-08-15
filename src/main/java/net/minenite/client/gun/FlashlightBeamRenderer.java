package net.minenite.client.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Look-direction spotlight in world space. Additive LIGHTNING quads, not vanilla
 * LIGHT blocks — a cone plus an impact pool on whatever the player is aiming at.
 */
public final class FlashlightBeamRenderer {
	private static final double RANGE = 32.0;

	private FlashlightBeamRenderer() {
	}

	@SubscribeEvent
	public static void afterTranslucent(RenderLevelStageEvent.AfterTranslucentParticles event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		float power = FlashlightVision.strength(player);
		if (power < 0.05f) {
			return;
		}
		draw(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos, player, power);
	}

	private static void draw(PoseStack matrices, Vec3 camera, LocalPlayer player, float power) {
		float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(pt);
		Vec3 look = player.getViewVector(pt);
		if (look.lengthSqr() < 1.0E-8) {
			return;
		}
		look = look.normalize();
		HitResult hit = player.level().clip(new ClipContext(
				eye, eye.add(look.scale(RANGE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 tip = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(RANGE)) : hit.getLocation();
		double dist = Math.max(0.8, eye.distanceTo(tip));

		Vec3 up = new Vec3(0, 1, 0);
		Vec3 right = look.cross(up);
		if (right.lengthSqr() < 1.0E-8) {
			right = new Vec3(1, 0, 0);
		} else {
			right = right.normalize();
		}
		// Offset to the gun / hand so first-person still sees the shaft leave the frame.
		Vec3 origin = eye.add(right.scale(0.20)).add(0, -0.16, 0).add(look.scale(0.28));
		Vec3 pool = tip.subtract(look.scale(Math.min(0.18, dist * 0.04)));

		boolean nvg = NvgVision.isWearing(player);
		boolean thermal = ThermalVision.isWearing(player);
		float pr = thermal ? 0.55f : 1f;
		float pg = thermal ? 0.55f : (nvg ? 1f : 0.93f);
		float pb = thermal ? 0.62f : (nvg ? 0.72f : 0.70f);
		final float beam = power * (thermal ? 0.40f : nvg ? 1.08f : 1f);

		LightningQuads.draw(matrices, camera, ctx -> {
			int segs = 12;
			for (int i = 0; i < segs; i++) {
				float t0 = i / (float) segs;
				float t1 = (i + 1) / (float) segs;
				Vec3 a = origin.add(tip.subtract(origin).scale(t0));
				Vec3 b = origin.add(tip.subtract(origin).scale(t1));
				float w0 = 0.06f + t0 * (float) dist * 0.048f;
				float w1 = 0.06f + t1 * (float) dist * 0.048f;
				float w = (w0 + w1) * 0.5f;
				float fall = (1f - t1 * 0.62f);
				LightningQuads.quadBeamWorld(ctx,
						(float) a.x, (float) a.y, (float) a.z,
						(float) b.x, (float) b.y, (float) b.z,
						w, pr, pg, pb, beam * 0.20f * fall);
				LightningQuads.quadBeamWorld(ctx,
						(float) a.x, (float) a.y, (float) a.z,
						(float) b.x, (float) b.y, (float) b.z,
						w * 2.4f, pr, pg * 0.95f, pb * 0.85f, beam * 0.08f * fall);
			}

			int discs = 8;
			for (int i = 1; i <= discs; i++) {
				float t = i / (float) discs;
				Vec3 p = origin.add(tip.subtract(origin).scale(t));
				float rad = 0.22f + t * (float) dist * 0.10f;
				float a = beam * 0.10f * (1f - t * 0.45f);
				LightningQuads.billboardWorld(ctx, (float) p.x, (float) p.y, (float) p.z,
						rad, pr, pg, pb, a);
			}

			float poolR = Mth.clamp(0.85f + (float) dist * 0.085f, 1.05f, 4.2f);
			float reach = Mth.clamp(1f - (float) (dist / RANGE), 0.22f, 1f);
			LightningQuads.billboardWorld(ctx, (float) pool.x, (float) pool.y, (float) pool.z,
					poolR * 1.85f, pr, pg * 0.92f, pb * 0.78f, beam * 0.22f * reach);
			LightningQuads.billboardWorld(ctx, (float) pool.x, (float) pool.y, (float) pool.z,
					poolR * 0.85f, pr, pg, pb, beam * 0.38f * reach);
			LightningQuads.billboardWorld(ctx, (float) pool.x, (float) pool.y, (float) pool.z,
					poolR * 0.28f, 1f, nvg ? 1f : 0.98f, nvg ? 0.82f : 0.88f, beam * 0.52f * reach);
		});
	}
}
