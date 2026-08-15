package net.minenite.client.gun;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

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

/**
 * Plain translucent quads in world space.
 *
 * Beams use {@link LightningQuads}, whose pipeline is additive - right for a
 * laser, wrong for a crack, which has to read as dark slate against glass rather
 * than glow. DEBUG_QUADS is the untextured position-colour pipeline with
 * ordinary alpha blending, which is what the original renderer registered a
 * custom pipeline to get.
 */
public final class GlassCrackQuads {

    private static final RenderPipeline PIPELINE = RenderPipelines.DEBUG_QUADS;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();
    private static final StagedVertexBuffer STAGED =
            new StagedVertexBuffer(() -> "Minenite WARZ glass cracks", RenderType.SMALL_BUFFER_SIZE);

    private GlassCrackQuads() {
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

    /** One flat quad from four corners, wound as given. */
    public static void quad(DrawCtx ctx,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float r, float g, float b, float a) {
        if (a <= 0.01f) {
            return;
        }
        ctx.buffer.addVertex(ctx.pose, x0, y0, z0).setColor(r, g, b, a);
        ctx.buffer.addVertex(ctx.pose, x1, y1, z1).setColor(r, g, b, a);
        ctx.buffer.addVertex(ctx.pose, x2, y2, z2).setColor(r, g, b, a);
        ctx.buffer.addVertex(ctx.pose, x3, y3, z3).setColor(r, g, b, a);
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
                .createRenderPass(() -> "minenite glass cracks", colorTexture, Optional.empty(),
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
