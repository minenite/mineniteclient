package net.minenite.client.gun;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Shared LIGHTNING-pipeline quads for lasers, tracers, and muzzle flashes. */
public final class LightningQuads {
	private static final RenderPipeline PIPELINE = RenderPipelines.LIGHTNING;
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();
	private static final StagedVertexBuffer STAGED = new StagedVertexBuffer(() -> "Minenite WARZ beams", RenderType.SMALL_BUFFER_SIZE);

	private LightningQuads() {
	}

	public static void close() {
		STAGED.close();
	}

	public static void draw(PoseStack matrices, Vec3 camera, Consumer<DrawCtx> fill) {
		try {
			VertexFormat formatBinding = PIPELINE.getVertexFormatBinding(0);
			if (formatBinding == null) {
				return;
			}
			PrimitiveTopology primitive = PIPELINE.getPrimitiveTopology();
			StagedVertexBuffer.Draw draw = STAGED.appendDraw(
					formatBinding,
					primitive,
					primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);
			matrices.pushPose();
			matrices.translate(-camera.x, -camera.y, -camera.z);
			VertexConsumer builder = STAGED.getVertexBuilder(draw);
			Matrix4fc pose = matrices.last().pose();
			fill.accept(new DrawCtx(pose, builder, camera));
			matrices.popPose();
			STAGED.upload();
			StagedVertexBuffer.ExecuteInfo info = STAGED.getExecuteInfo(draw);
			if (info != null) {
				execute(Minecraft.getInstance(), info);
			}
			STAGED.endFrame();
		} catch (Throwable t) {
			try {
				STAGED.endFrame();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void quadBeam(DrawCtx ctx, float x0, float y0, float z0, float x1, float y1, float z1,
			float halfWidth, float r, float g, float b, float a) {
		Vec3 start = new Vec3(x0, y0, z0);
		Vec3 end = new Vec3(x1, y1, z1);
		Vec3 along = end.subtract(start);
		if (along.lengthSqr() < 1.0E-8 || halfWidth <= 0.0001f || a <= 0.01f) {
			return;
		}
		Vec3 dir = along.normalize();
		Vec3 mid = start.add(end).scale(0.5);
		Vec3 toCam = ctx.camera.subtract(mid);
		double camDist = Math.sqrt(Math.max(1.0E-8, toCam.lengthSqr()));
		if (camDist < 0.28) {
			return;
		}
		float fade = rangeFade(camDist);
		float nearSoft = camDist < 4.0 ? (0.55f + 0.45f * (float) (camDist / 4.0)) : 1f;
		float aa = a * fade * nearSoft;
		if (aa <= 0.01f) {
			return;
		}
		float hw = distanceCompensatedHalfWidth(halfWidth, camDist);
		Vec3 side = dir.cross(toCam);
		if (side.lengthSqr() < 1.0E-8) {
			side = dir.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0E-8) {
				side = new Vec3(1, 0, 0);
			}
		}
		side = side.normalize().scale(hw);
		vert(ctx, start.subtract(side), r, g, b, aa);
		vert(ctx, start.add(side), r, g, b, aa);
		vert(ctx, end.add(side), r, g, b, aa);
		vert(ctx, end.subtract(side), r, g, b, aa);
	}

	/** World-scale beam — no laser distance compensation, so a flashlight cone stays wide. */
	public static void quadBeamWorld(DrawCtx ctx, float x0, float y0, float z0, float x1, float y1, float z1,
			float halfWidth, float r, float g, float b, float a) {
		Vec3 start = new Vec3(x0, y0, z0);
		Vec3 end = new Vec3(x1, y1, z1);
		Vec3 along = end.subtract(start);
		if (along.lengthSqr() < 1.0E-8 || halfWidth <= 0.0001f || a <= 0.01f) {
			return;
		}
		Vec3 dir = along.normalize();
		Vec3 mid = start.add(end).scale(0.5);
		Vec3 toCam = ctx.camera.subtract(mid);
		if (toCam.lengthSqr() < 1.0E-6) {
			toCam = dir.cross(new Vec3(0, 1, 0));
		}
		Vec3 side = dir.cross(toCam);
		if (side.lengthSqr() < 1.0E-8) {
			side = dir.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0E-8) {
				side = new Vec3(1, 0, 0);
			}
		}
		side = side.normalize().scale(halfWidth);
		vert(ctx, start.subtract(side), r, g, b, a);
		vert(ctx, start.add(side), r, g, b, a);
		vert(ctx, end.add(side), r, g, b, a);
		vert(ctx, end.subtract(side), r, g, b, a);
	}

	/** World-scale camera-facing disc. Size is metres, not laser-compensated. */
	public static void billboardWorld(DrawCtx ctx, float x, float y, float z, float half,
			float r, float g, float b, float a) {
		if (half <= 0.0001f || a <= 0.01f) {
			return;
		}
		Vec3 center = new Vec3(x, y, z);
		Vec3 toCam = ctx.camera.subtract(center);
		double camDist = Math.sqrt(Math.max(1.0E-8, toCam.lengthSqr()));
		Vec3 forward = toCam.scale(1.0 / camDist);
		Vec3 right = forward.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-8) {
			right = new Vec3(1, 0, 0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(forward).normalize().scale(half);
		right = right.scale(half);
		vert(ctx, center.subtract(right).subtract(up), r, g, b, a);
		vert(ctx, center.add(right).subtract(up), r, g, b, a);
		vert(ctx, center.add(right).add(up), r, g, b, a);
		vert(ctx, center.subtract(right).add(up), r, g, b, a);
	}

	public static void billboard(DrawCtx ctx, float x, float y, float z, float half,
			float r, float g, float b, float a) {
		if (half <= 0.0001f || a <= 0.01f) {
			return;
		}
		Vec3 center = new Vec3(x, y, z);
		Vec3 toCam = ctx.camera.subtract(center);
		double camDist = Math.sqrt(Math.max(1.0E-8, toCam.lengthSqr()));
		float aa = a * rangeFade(camDist);
		if (aa <= 0.01f) {
			return;
		}
		float hw = distanceCompensatedHalfWidth(half, camDist) * 1.35f;
		Vec3 forward = toCam.scale(1.0 / camDist);
		Vec3 right = forward.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-8) {
			right = new Vec3(1, 0, 0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(forward).normalize().scale(hw);
		right = right.scale(hw);
		vert(ctx, center.subtract(right).subtract(up), r, g, b, aa);
		vert(ctx, center.add(right).subtract(up), r, g, b, aa);
		vert(ctx, center.add(right).add(up), r, g, b, aa);
		vert(ctx, center.subtract(right).add(up), r, g, b, aa);
	}

	private static float rangeFade(double camDist) {
		if (camDist <= 200f) {
			return 1f;
		}
		if (camDist >= 280f) {
			return 0.08f;
		}
		return 1f - (float) ((camDist - 200f) / 80f) * 0.92f;
	}

	private static float distanceCompensatedHalfWidth(float baseHw, double camDist) {
		float boost = 1f + Math.min(3.2f, (float) camDist / 55f);
		float hw = baseHw * boost;
		float minHw = (float) Math.max(0.004, camDist * 0.00095);
		float maxHw = camDist < 6.0
				? (float) Math.max(0.006, camDist * 0.01)
				: (float) Math.max(0.08, camDist * 0.018);
		if (hw < minHw) {
			hw = minHw;
		}
		if (hw > maxHw) {
			hw = maxHw;
		}
		return hw;
	}

	private static void vert(DrawCtx ctx, Vec3 p, float r, float g, float b, float a) {
		ctx.buffer.addVertex(ctx.pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
	}

	private static void execute(Minecraft client, StagedVertexBuffer.ExecuteInfo info) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		if (colorTexture == null) {
			return;
		}
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "mineniteclient laser", colorTexture, Optional.empty(),
						mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
			renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	public record DrawCtx(Matrix4fc pose, VertexConsumer buffer, Vec3 camera) {
	}
}
