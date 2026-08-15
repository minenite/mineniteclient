package net.minenite.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minenite.client.gun.ThrownItemFlags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WarZ guns spawn snowballs. Draw those as a 1px dark-gray billboard instead
 * of the vanilla snowball sprite. Thrown cans / bottles keep the flying item.
 */
@Mixin(ThrownItemRenderer.class)
public class GunSnowballRendererMixin {
	@Unique
	private static final Identifier MINENITE$WHITE =
			Identifier.withDefaultNamespace("textures/block/white_concrete.png");
	@Unique
	private static final RenderType MINENITE$DOT = RenderTypes.entityCutout(MINENITE$WHITE);
	@Unique
	private static final int MINENITE$GRAY = 0xFF2A2A2A;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void minenite$hideSnowballShadow(Entity entity, ThrownItemRenderState state, float partialTick, CallbackInfo ci) {
		if (!(entity instanceof Snowball snowball)) {
			return;
		}
		boolean tracer = minenite$isGunTracer(snowball.getItem());
		if (state instanceof ThrownItemFlags flags) {
			flags.minenite$setGunTracer(tracer);
		}
		if (tracer) {
			state.shadowRadius = 0f;
		}
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void minenite$gunSnowballDot(
			ThrownItemRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera,
			CallbackInfo ci) {
		if (state.entityType != EntityTypes.SNOWBALL) {
			return;
		}
		if (state instanceof ThrownItemFlags flags && !flags.minenite$isGunTracer()) {
			return;
		}
		double dist = Math.sqrt(Math.max(1.0, state.distanceToCameraSq));
		float size = (float) (dist * 0.00135);
		poseStack.pushPose();
		poseStack.mulPose(camera.orientation);
		poseStack.scale(size, size, size);
		int light = state.lightCoords;
		submitNodeCollector.submitCustomGeometry(poseStack, MINENITE$DOT, (pose, buffer) -> {
			minenite$dotVert(buffer, pose, light, -0.5F, -0.5F, 0F, 1F);
			minenite$dotVert(buffer, pose, light, 0.5F, -0.5F, 1F, 1F);
			minenite$dotVert(buffer, pose, light, 0.5F, 0.5F, 1F, 0F);
			minenite$dotVert(buffer, pose, light, -0.5F, 0.5F, 0F, 0F);
		});
		poseStack.popPose();
		ci.cancel();
	}

	@Unique
	private static void minenite$dotVert(
			VertexConsumer buffer,
			PoseStack.Pose pose,
			int light,
			float x,
			float y,
			float u,
			float v) {
		buffer.addVertex(pose, x, y, 0.0F)
				.setColor(MINENITE$GRAY)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** Gun bullets use a default snowball or {@code pvpgunminus:bullet} nugget. */
	@Unique
	private static boolean minenite$isGunTracer(ItemStack item) {
		if (item == null || item.isEmpty()) {
			return true;
		}
		return item.is(Items.SNOWBALL) || item.is(Items.IRON_NUGGET);
	}
}
