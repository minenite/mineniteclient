package net.minenite.client.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders an offhand WarZ gun slung on the player's back. */
public final class GunBackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final ItemStackRenderState scratch = new ItemStackRenderState();

	public GunBackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
					   AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.isSpectator) {
			return;
		}
		ItemStack gun = GunItemPose.offhandGun(state);
		if (gun.isEmpty()) {
			return;
		}

		LivingEntity living = GunItemPose.livingFromState(state);
		scratch.clear();
		if (living != null) {
			Minecraft.getInstance().getItemModelResolver()
					.updateForLiving(scratch, gun, ItemDisplayContext.FIXED, living);
		}
		if (scratch.isEmpty()) {
			return;
		}

		GunBackSlingConfig c = GunBackSlingConfig.INSTANCE;

		poseStack.pushPose();
		PlayerModel model = getParentModel();
		model.root().translateAndRotate(poseStack);
		model.body.translateAndRotate(poseStack);

		poseStack.translate(c.tx, c.ty, c.tz);
		poseStack.mulPose(Axis.YP.rotationDegrees(c.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(c.pitch));
		poseStack.mulPose(Axis.YP.rotationDegrees(c.spin));
		poseStack.mulPose(Axis.ZP.rotationDegrees(c.roll));
		poseStack.scale(c.scale, c.scale, c.scale);

		scratch.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
