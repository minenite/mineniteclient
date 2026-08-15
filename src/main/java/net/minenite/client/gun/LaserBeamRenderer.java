package net.minenite.client.gun;

import net.minenite.client.gun.vision.TemperatureField;
import net.minenite.client.gun.vision.ThermalPalette;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/** World-space emissive laser filament (LIGHTNING pipeline). */
public final class LaserBeamRenderer {
	private LaserBeamRenderer() {
	}

	public static void close() {
		LightningQuads.close();
	}

	@SubscribeEvent
	public static void afterTranslucent(RenderLevelStageEvent.AfterTranslucentParticles event) {
		draw(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos);
	}

	public static void draw(PoseStack matrices, Vec3 camera) {
		List<LaserWire.Beam> beams = LaserBeamStore.get().snapshot();
		List<FxStore.ActiveFx> fx = FxStore.get().snapshot();
		if (beams.isEmpty() && fx.isEmpty()) {
			return;
		}
		boolean nvg = NvgVision.isWearing();
		boolean thermal = ThermalVision.isWearing();
		LightningQuads.draw(matrices, camera, ctx -> {
			for (LaserWire.Beam beam : beams) {
				if (!canSee(beam, nvg, thermal)) {
					continue;
				}
				renderBeam(ctx, beam, nvg, thermal);
			}
			if (!thermal) {
				for (FxStore.ActiveFx one : fx) {
					renderFx(ctx, one);
				}
			}
		});
	}

	private static boolean canSee(LaserWire.Beam beam, boolean nvg, boolean thermal) {
		boolean ir = beam.gunIr() || beam.droneIr() || legacyIr(beam);
		if (thermal) {
			return beam.droneIr();
		}
		if (!ir) {
			return true;
		}
		return nvg || beam.droneIr();
	}

	private static boolean legacyIr(LaserWire.Beam beam) {
		if (beam.gunIr() || beam.droneIr()) {
			return false;
		}
		float br = ((beam.rgb() >> 16) & 0xFF) / 255f;
		float bg = ((beam.rgb() >> 8) & 0xFF) / 255f;
		float bb = (beam.rgb() & 0xFF) / 255f;
		return bg > 0.85f && br < 0.22f && bb > 0.15f && bb < 0.40f && bg > br * 3.5f;
	}

	private static void renderBeam(LightningQuads.DrawCtx ctx, LaserWire.Beam beam, boolean nvg, boolean thermal) {
		boolean ir = beam.gunIr() || beam.droneIr() || legacyIr(beam);
		if (ir && !nvg && !beam.droneIr()) {
			return;
		}
		float br = ((beam.rgb() >> 16) & 0xFF) / 255f;
		float bg = ((beam.rgb() >> 8) & 0xFF) / 255f;
		float bb = (beam.rgb() & 0xFF) / 255f;
		if (beam.droneIr()) {
			if (thermal) {
				br = bg = bb = TemperatureField.get().palette()
						== ThermalPalette.BLACK_HOT ? 0.06f : 1f;
			} else {
				br = bg = bb = 1f;
			}
		} else if (ir && nvg) {
			br = Math.min(0.2f, br * 0.25f);
			bg = Math.min(0.85f, bg * 0.75f + 0.08f);
			bb = Math.min(0.25f, bb * 0.3f);
		}
		float widthScale = thermal && beam.droneIr() ? 0.5f : 1f;
		float alphaScale = thermal && beam.droneIr() ? 0.85f : 1f;
		if (beam.suppressed()) {
			widthScale *= ir ? 0.45f : 0.65f;
			alphaScale *= ir ? 0.40f : 0.55f;
		}
		float base = Math.max(0.0045f, Math.min(0.012f, beam.baseWidth() * 0.034f));
		for (LaserWire.Segment seg : beam.segments()) {
			if ((seg.flags() & LaserWire.FLAG_REFLECTION) != 0) {
				continue;
			}
			float intensity = Math.max(0.1f, Math.min(1f, seg.intensity()));
			if (ir) {
				intensity *= 0.72f;
			}
			boolean water = (seg.flags() & LaserWire.FLAG_UNDERWATER) != 0;
			float width = base * Math.max(0.45f, seg.widthScale()) * widthScale;
			if (water) {
				width *= 1.25f;
				intensity *= ir ? 0.55f : 0.85f;
			}
			float a = Math.min(1f, (ir ? 0.72f : 0.95f) * intensity * alphaScale);
			LightningQuads.quadBeam(ctx, seg.x0(), seg.y0(), seg.z0(), seg.x1(), seg.y1(), seg.z1(),
					width, br, bg, bb, a);
		}
		float spark = Math.max(0.012f, beam.baseWidth() * 0.08f);
		if (beam.suppressed()) {
			spark *= 0.45f;
		}
		float tipA = (ir ? 0.55f : 0.72f) * (beam.suppressed() ? 0.4f : 1f);
		LightningQuads.billboard(ctx, beam.tipX(), beam.tipY(), beam.tipZ(), spark, br, bg, bb, tipA);
	}

	private static void renderFx(LightningQuads.DrawCtx ctx, FxStore.ActiveFx fx) {
		FxStore.Payload p = fx.payload;
		float life = fx.life();
		float br = ((p.rgb() >> 16) & 0xFF) / 255f;
		float bg = ((p.rgb() >> 8) & 0xFF) / 255f;
		float bb = (p.rgb() & 0xFF) / 255f;
		if (p.fxType() == FxStore.FX_MUZZLE) {
			float suppress = p.suppressed() ? 0.22f : 1f;
			float scale = Math.max(0.08f, p.scaleOrWidth()) * (0.35f + 0.65f * life) * suppress;
			float half = 0.06f * scale;
			float a = life * suppress;
			LightningQuads.billboard(ctx, p.x0(), p.y0(), p.z0(), half * 2.2f,
					br * 0.7f, bg * 0.55f, bb * 0.35f, 0.35f * a);
			LightningQuads.billboard(ctx, p.x0(), p.y0(), p.z0(), half,
					Math.min(1f, br * 1.15f), Math.min(1f, bg * 1.1f), Math.min(1f, bb * 0.9f), 0.85f * a);
			float len = 0.28f * scale;
			LightningQuads.quadBeam(ctx,
					p.x0(), p.y0(), p.z0(),
					p.x0() + p.x1() * len, p.y0() + p.y1() * len, p.z0() + p.z1() * len,
					half * 0.55f, br, bg * 0.85f, bb * 0.55f, 0.55f * a);
			return;
		}
		if (p.fxType() == FxStore.FX_TRACER) {
			float half = Math.max(0.008f, Math.min(0.06f, p.scaleOrWidth())) * (0.55f + 0.45f * life);
			LightningQuads.quadBeam(ctx, p.x0(), p.y0(), p.z0(), p.x1(), p.y1(), p.z1(),
					half * 1.8f, br * 0.65f, bg * 0.65f, bb * 0.65f, 0.28f * life);
			LightningQuads.quadBeam(ctx, p.x0(), p.y0(), p.z0(), p.x1(), p.y1(), p.z1(),
					half, Math.min(1f, br * 1.1f), Math.min(1f, bg * 1.05f), Math.min(1f, bb * 0.95f), 0.8f * life);
		}
	}
}
