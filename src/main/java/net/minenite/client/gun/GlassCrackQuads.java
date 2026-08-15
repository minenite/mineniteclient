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
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * Built lazily, because Iris has to be told about it.
     *
     * DEBUG_QUADS looked like the obvious fit - untextured position-colour with
     * ordinary alpha - but Iris only overrides pipelines on its own list and
     * refuses the rest: "Missing program minecraft:pipeline/debug_quads in
     * override list", and the draw throws before a single crack is emitted. So
     * this registers its own pipeline, borrowing the lightning shaders but with
     * translucent blending instead of additive, and hands it to Iris as BASIC
     * (gbuffers_basic). LIGHTNING is the fallback: visible, but it glows.
     */
    private static RenderPipeline pipeline;
    private static final Logger LOG = LoggerFactory.getLogger("MineniteWARZ-glass");
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final org.joml.Matrix4f TEXTURE_MATRIX = new org.joml.Matrix4f();
    private static final StagedVertexBuffer STAGED =
            new StagedVertexBuffer(() -> "Minenite WARZ glass cracks", RenderType.SMALL_BUFFER_SIZE);

    private GlassCrackQuads() {
    }

    /**
     * NeoForge registers custom pipelines through its own mod-bus event rather
     * than the vanilla registry, which is private here.
     */
    public static void onRegisterPipelines(
            net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        RenderPipeline built = build();
        if (built != null) {
            event.registerPipeline(built);
        }
    }

    private static RenderPipeline pipeline() {
        RenderPipeline built = build();
        return built != null ? built : RenderPipelines.LIGHTNING;
    }

    private static RenderPipeline build() {
        if (pipeline != null) {
            return pipeline;
        }
        try {
            RenderPipeline built = (
                    RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath("mineniteclient", "glass_cracks"))
                            .withVertexShader("core/rendertype_lightning")
                            .withFragmentShader("core/rendertype_lightning")
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withDepthStencilState(DepthStencilState.DEFAULT)
                            .withCull(false)
                            .build());
            if (assignIrisProgram(built) == IrisAssign.FAILED) {
                LOG.warn("Iris would not take the glass pipeline; falling back to LIGHTNING (cracks will glow)");
                pipeline = RenderPipelines.LIGHTNING;
            } else {
                pipeline = built;
            }
        } catch (Throwable t) {
            LOG.warn("glass crack pipeline failed ({}), falling back to LIGHTNING", t.toString());
            pipeline = RenderPipelines.LIGHTNING;
        }
        return pipeline;
    }

    private enum IrisAssign { ABSENT, OK, FAILED }

    /** Optional Iris hook, by reflection - the mod must build without Iris present. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IrisAssign assignIrisProgram(RenderPipeline pipe) {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object program = Enum.valueOf((Class<Enum>) programClass.asSubclass(Enum.class), "BASIC");
            apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass).invoke(api, pipe, program);
            LOG.info("glass crack pipeline assigned to Iris BASIC");
            return IrisAssign.OK;
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            return IrisAssign.ABSENT;
        } catch (Throwable t) {
            LOG.warn("Iris assignPipeline: {}", t.toString());
            return IrisAssign.FAILED;
        }
    }

    public static void close() {
        STAGED.close();
    }

    public static void draw(PoseStack matrices, Vec3 camera, Consumer<DrawCtx> fill) {
        try {
            RenderPipeline PIPELINE = pipeline();
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
        RenderPipeline PIPELINE = pipeline();
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
