package net.minenite.client.mixin;

import net.minenite.client.gun.GunItemPose;
import net.minenite.client.gun.GunPoseClient;
import net.minenite.client.gun.GunReloadAnimator;
import net.minenite.client.gun.PlayerAnimBridge;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person gun / prone:
 * - Crawl lean is −90°, so head pitch 0 looks into the dirt; −π/2 (sky look) is world-forward
 * - ADS arms use −π + look so the gun matches that head
 */
@Mixin(PlayerModel.class)
public class GunCarryPoseMixin {
	@Shadow
	public ModelPart leftSleeve;
	@Shadow
	public ModelPart rightSleeve;

	@Inject(method = "setupAnim", at = @At("RETURN"))
	private void minenite$gunArms(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel self = (PlayerModel) (Object) this;
		boolean rightMain = state.mainArm == HumanoidArm.RIGHT;
		HumanoidModel.ArmPose mainPose = rightMain ? state.rightArmPose : state.leftArmPose;
		boolean aiming = mainPose == HumanoidModel.ArmPose.BOW_AND_ARROW
				|| mainPose == HumanoidModel.ArmPose.CROSSBOW_HOLD;

		if (state.swimAmount > 0f) {
			applyProneHead(self, state);
			if (aiming) {
				applyProneAimArms(self, state, rightMain);
				syncSleeves(self);
				return;
			}
		}

		LivingEntity living = GunItemPose.livingFromState(state);
		if (living instanceof Avatar avatar && GunPoseClient.reloading(avatar) && GunItemPose.isGun(state.getMainHandItemStack())) {
			if (PlayerAnimBridge.apply(self, state, rightMain)) {
				syncSleeves(self);
				return;
			}
			float p = GunReloadAnimator.progress(avatar);
			float[] mainA = new float[3];
			float[] offA = new float[3];
			GunReloadAnimator.applyThirdPersonArms(mainA, offA, GunReloadAnimator.family(avatar), p);
			ModelPart main = rightMain ? self.rightArm : self.leftArm;
			ModelPart off = rightMain ? self.leftArm : self.rightArm;
			main.xRot = mainA[0];
			main.yRot = rightMain ? mainA[1] : -mainA[1];
			main.zRot = rightMain ? mainA[2] : -mainA[2];
			off.xRot = offA[0];
			off.yRot = rightMain ? offA[1] : -offA[1];
			off.zRot = rightMain ? offA[2] : -offA[2];
			syncSleeves(self);
			return;
		}

		if (GunItemPose.isGun(state.getMainHandItemStack()) && PlayerAnimBridge.apply(self, state, rightMain)) {
			syncSleeves(self);
			return;
		}

		if (!GunItemPose.shouldAcrossBodyCarry(state)) {
			return;
		}
		ModelPart main = rightMain ? self.rightArm : self.leftArm;
		ModelPart off = rightMain ? self.leftArm : self.rightArm;

		if (rightMain) {
			main.xRot = -0.9f;
			main.yRot = -0.85f;
			main.zRot = 0.4f;
			off.xRot = -0.55f;
			off.yRot = 0.6f;
			off.zRot = -0.3f;
		} else {
			main.xRot = -0.9f;
			main.yRot = 0.85f;
			main.zRot = -0.4f;
			off.xRot = -0.55f;
			off.yRot = -0.6f;
			off.zRot = 0.3f;
		}
		syncSleeves(self);
	}

	/** Standing “look at sky” (−π/2) = prone looking forward after crawl −90°. */
	private static void applyProneHead(PlayerModel self, AvatarRenderState state) {
		float lookPitch = state.xRot * ((float) Math.PI / 180f);
		float lookYaw = state.yRot * ((float) Math.PI / 180f);
		self.head.xRot = (float) (-Math.PI / 2) + lookPitch;
		self.head.yRot = lookYaw;
	}

	private static void applyProneAimArms(PlayerModel self, AvatarRenderState state, boolean rightMain) {
		float lookPitch = state.xRot * ((float) Math.PI / 180f);
		float lookYaw = state.yRot * ((float) Math.PI / 180f);
		float x = (float) (-Math.PI) + lookPitch;

		if (rightMain) {
			self.rightArm.yRot = -0.1f + lookYaw;
			self.leftArm.yRot = 0.1f + lookYaw + 0.4f;
		} else {
			self.rightArm.yRot = -0.1f + lookYaw - 0.4f;
			self.leftArm.yRot = 0.1f + lookYaw;
		}
		self.rightArm.xRot = x;
		self.leftArm.xRot = x;
		self.rightArm.zRot = 0f;
		self.leftArm.zRot = 0f;
	}

	private void syncSleeves(PlayerModel self) {
		leftSleeve.xRot = self.leftArm.xRot;
		leftSleeve.yRot = self.leftArm.yRot;
		leftSleeve.zRot = self.leftArm.zRot;
		rightSleeve.xRot = self.rightArm.xRot;
		rightSleeve.yRot = self.rightArm.yRot;
		rightSleeve.zRot = self.rightArm.zRot;
	}
}
