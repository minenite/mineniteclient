package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Photocathode sampling along the look ray — hottest lamp and how close it is. */
public final class LightLookSampler {
	private static final double MAX_DIST = 40.0;
	private static final double STEP = 2.5;
	private static final float CLOSE_BLIND_M = 3.25f;

	public record HotSpot(
			float intensity,
			float distance,
			float x,
			float y,
			float z,
			float proximity
	) {
		public static final HotSpot NONE = new HotSpot(0f, 99f, 0f, 0f, 0f, 0f);
	}

	private LightLookSampler() {
	}

	public static HotSpot sample(Minecraft mc, LocalPlayer player, float partialTicks) {
		if (mc.level == null || player == null) {
			return HotSpot.NONE;
		}
		Vec3 eye = player.getEyePosition(partialTicks);
		Vec3 look = player.getViewVector(partialTicks);

		float peak = 0f;
		float peakDist = 99f;
		float hx = (float) (eye.x + look.x);
		float hy = (float) (eye.y + look.y);
		float hz = (float) (eye.z + look.z);
		double reached = MAX_DIST;

		BlockHitResult hit = mc.level.clip(new ClipContext(
				eye,
				eye.add(look.scale(MAX_DIST)),
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player
		));
		if (hit.getType() != HitResult.Type.MISS) {
			reached = Math.min(MAX_DIST, eye.distanceTo(hit.getLocation()) + 0.35);
			BlockPos hitPos = hit.getBlockPos();
			int emit = mc.level.getBrightness(LightLayer.BLOCK, hitPos);
			int emitNeighbor = mc.level.getBrightness(LightLayer.BLOCK, hitPos.relative(hit.getDirection()));
			float surface = Math.max(emit, emitNeighbor) / 15f;
			float surfaceHot = surface * 1.2f;
			if (surfaceHot > peak) {
				peak = surfaceHot;
				peakDist = (float) eye.distanceTo(hit.getLocation());
				Vec3 loc = hit.getLocation();
				hx = (float) loc.x;
				hy = (float) loc.y;
				hz = (float) loc.z;
			}
		}

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (double d = 0.35; d <= reached; d += STEP) {
			Vec3 p = eye.add(look.scale(d));
			cursor.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
			float bl = mc.level.getBrightness(LightLayer.BLOCK, cursor) / 15f;
			float sk = mc.level.getBrightness(LightLayer.SKY, cursor) / 15f;
			float skLit = sk * (1f - NvgVision.skyDarken01(mc));
			float local = Math.max(bl * 1.15f, skLit * 0.55f);
			float atten = (float) (1.0 / (1.0 + d * d * 0.012));
			float contrib = local * atten;
			if (contrib > peak) {
				peak = contrib;
				peakDist = (float) d;
				hx = (float) p.x;
				hy = (float) p.y;
				hz = (float) p.z;
			}
		}

		BlockPos eyePos = BlockPos.containing(eye);
		float eyeBlock = mc.level.getBrightness(LightLayer.BLOCK, eyePos) / 15f;
		if (eyeBlock > 0.35f) {
			float eyeHot = eyeBlock * 1.1f;
			if (eyeHot > peak * 0.85f) {
				peak = Math.max(peak, eyeHot);
				peakDist = Math.min(peakDist, 0.6f);
				hx = (float) eye.x + (float) look.x * 0.8f;
				hy = (float) eye.y + (float) look.y * 0.8f;
				hz = (float) eye.z + (float) look.z * 0.8f;
			}
		}

		peak = Mth.clamp(peak, 0f, 2.0f);
		float close = 1f - Mth.clamp(peakDist / CLOSE_BLIND_M, 0f, 1f);
		close = close * close;
		float bright = Mth.clamp((peak - 0.35f) / 0.65f, 0f, 1f);
		float proximity = Mth.clamp(close * bright, 0f, 1f);
		return new HotSpot(peak, peakDist, hx, hy, hz, proximity);
	}
}
