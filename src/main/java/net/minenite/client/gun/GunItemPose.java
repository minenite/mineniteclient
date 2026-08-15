package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Detects WarZ guns / ADS / hipfire for third-person posing + back sling.
 */
public final class GunItemPose {
	public static final String TAG_GUN = "pgm_gun";
	public static final String TAG_AIM = "pgm_aim";
	public static final String TAG_FIRE = "pgm_fire";

	private GunItemPose() {
	}

	public static boolean isGun(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)
				|| stack.is(Items.SPYGLASS) || stack.is(Items.SHIELD)) {
			return false;
		}
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		if (custom != null) {
			String nbt = custom.copyTag().toString().toLowerCase();
			if (nbt.contains("gun_id")) {
				return true;
			}
		}
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd != null) {
			for (float f : cmd.floats()) {
				if (f >= 1000f && f <= 1100f) {
					return true;
				}
			}
			Float f0 = cmd.getFloat(0);
			if (f0 != null && f0 >= 1000f && f0 <= 1100f) {
				return true;
			}
		}
		if (stack.get(DataComponents.LORE) != null) {
			String lore = String.valueOf(stack.get(DataComponents.LORE));
			if (lore.contains("Left: Aim") || lore.contains("Right: Fire")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAiming(Avatar avatar) {
		if (!(avatar instanceof LivingEntity living)) {
			return false;
		}
		if (GunPoseClient.aiming(avatar)) {
			return true;
		}
		if (living.entityTags().contains(TAG_AIM) || living.entityTags().contains("javelin_scope")) {
			return true;
		}
		if (isGun(living.getMainHandItem()) && living.hasEffect(MobEffects.SLOWNESS)) {
			var fx = living.getEffect(MobEffects.SLOWNESS);
			return fx != null && fx.getAmplifier() >= 3;
		}
		return false;
	}

	public static boolean isHipfiring(Avatar avatar) {
		if (!(avatar instanceof LivingEntity living)) {
			return false;
		}
		if (isAiming(avatar)) {
			return false;
		}
		if (GunPoseClient.hipfiring(avatar) && isGun(living.getMainHandItem())) {
			return true;
		}
		return living.entityTags().contains(TAG_FIRE) && isGun(living.getMainHandItem());
	}

	public static boolean holdingGunMain(Avatar avatar) {
		if (!(avatar instanceof LivingEntity living)) {
			return false;
		}
		return isGun(living.getMainHandItem());
	}

	/** Idle carry across the torso (not ADS, not hipfire). */
	public static boolean shouldAcrossBodyCarry(AvatarRenderState state) {
		ItemStack main = state.getMainHandItemStack();
		if (!isGun(main)) {
			return false;
		}
		HumanoidModel.ArmPose pose = state.mainArm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
		return pose != HumanoidModel.ArmPose.BOW_AND_ARROW
				&& pose != HumanoidModel.ArmPose.CROSSBOW_HOLD
				&& pose != HumanoidModel.ArmPose.CROSSBOW_CHARGE;
	}

	public static ItemStack offhandGun(AvatarRenderState state) {
		ItemStack off = state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack;
		return isGun(off) ? off : ItemStack.EMPTY;
	}

	public static boolean isOffhandGunArm(AvatarRenderState state, HumanoidArm arm) {
		return arm != state.mainArm && isGun(arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack);
	}

	public static LivingEntity livingFromState(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(state.id);
		return e instanceof LivingEntity living ? living : null;
	}
}
