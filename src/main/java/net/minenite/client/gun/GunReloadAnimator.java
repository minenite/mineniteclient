package net.minenite.client.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-person reload dip / mag-cycle approximation (SAG-style phases without GeckoLib).
 */
public final class GunReloadAnimator {
	public enum Family {
		PISTOL, REVOLVER, SMG, RIFLE, SHOTGUN, SNIPER, LMG, GENERIC
	}

	private record Track(long startMs, Family family) {
	}

	private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();
	private static final long DEFAULT_MS = 1500L;

	private GunReloadAnimator() {
	}

	public static Family familyFor(ItemStack stack) {
		int cmd = cmdOf(stack);
		if (cmd <= 0) {
			return Family.GENERIC;
		}
		if (cmd <= 1006) {
			return Family.PISTOL;
		}
		if (cmd <= 1018) {
			return cmd == 1015 || cmd == 1018 ? Family.REVOLVER : Family.SMG;
		}
		if (cmd <= 1024) {
			return Family.SMG;
		}
		if (cmd <= 1030) {
			return Family.RIFLE;
		}
		if (cmd <= 1036) {
			return Family.LMG;
		}
		if (cmd <= 1042) {
			return Family.SHOTGUN;
		}
		if (cmd <= 1054) {
			return Family.SNIPER;
		}
		return Family.GENERIC;
	}

	private static int cmdOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd == null) {
			return 0;
		}
		Float f0 = cmd.getFloat(0);
		if (f0 != null) {
			return f0.intValue();
		}
		for (float f : cmd.floats()) {
			if (f >= 1000f) {
				return (int) f;
			}
		}
		return 0;
	}

	public static void tickLocal(Player player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUUID();
		boolean reloading = GunPoseClient.reloading(player);
		if (reloading) {
			TRACKS.compute(id, (k, prev) -> {
				if (prev != null) {
					return prev;
				}
				return new Track(System.currentTimeMillis(), familyFor(player.getMainHandItem()));
			});
		} else {
			TRACKS.remove(id);
		}
	}

	public static float progress(Avatar avatar) {
		if (avatar == null) {
			return -1f;
		}
		if (!GunPoseClient.reloading(avatar)) {
			TRACKS.remove(avatar.getUUID());
			return -1f;
		}
		Track t = TRACKS.get(avatar.getUUID());
		if (t == null) {
			t = new Track(System.currentTimeMillis(), Family.GENERIC);
			TRACKS.put(avatar.getUUID(), t);
		}
		long elapsed = System.currentTimeMillis() - t.startMs();
		return Mth.clamp(elapsed / (float) DEFAULT_MS, 0f, 1f);
	}

	public static Family family(Avatar avatar) {
		Track t = avatar != null ? TRACKS.get(avatar.getUUID()) : null;
		return t != null ? t.family() : Family.GENERIC;
	}

	public static boolean applyFirstPerson(PoseStack pose, Avatar avatar) {
		float p = progress(avatar);
		if (p < 0f) {
			return false;
		}
		Family fam = family(avatar);
		float dip;
		float yaw;
		float roll;
		float pull;
		if (p < 0.18f) {
			float t = p / 0.18f;
			dip = smooth(t) * -0.35f;
			yaw = smooth(t) * 12f;
			roll = smooth(t) * -8f;
			pull = smooth(t) * -0.15f;
		} else if (p < 0.45f) {
			float t = (p - 0.18f) / 0.27f;
			dip = -0.35f - smooth(t) * magOutDip(fam);
			yaw = 12f + smooth(t) * 25f;
			roll = -8f - smooth(t) * 18f;
			pull = -0.15f - smooth(t) * 0.45f;
		} else if (p < 0.75f) {
			float t = (p - 0.45f) / 0.30f;
			dip = -0.35f - magOutDip(fam) + smooth(t) * (magOutDip(fam) * 0.7f);
			yaw = 37f - smooth(t) * 28f;
			roll = -26f + smooth(t) * 20f;
			pull = -0.60f + smooth(t) * 0.50f;
		} else {
			float t = (p - 0.75f) / 0.25f;
			dip = (-0.35f - magOutDip(fam) * 0.3f) * (1f - smooth(t));
			yaw = 9f * (1f - smooth(t));
			roll = -6f * (1f - smooth(t));
			pull = -0.10f * (1f - smooth(t));
		}
		pose.translate(pull * 0.15f, dip, pull);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.mulPose(Axis.XP.rotationDegrees(dip * 40f));
		return true;
	}

	public static void applyThirdPersonArms(float[] mainXyz, float[] offXyz, Family fam, float p) {
		if (p < 0f) {
			return;
		}
		float t = p < 0.5f ? p * 2f : (1f - p) * 2f;
		t = smooth(Mth.clamp(t, 0f, 1f));
		float mag = switch (fam) {
			case SHOTGUN, LMG -> 1.15f;
			case PISTOL, REVOLVER -> 0.75f;
			default -> 1f;
		};
		mainXyz[0] = -0.95f - t * 0.35f * mag;
		mainXyz[1] = -0.55f - t * 0.4f;
		mainXyz[2] = 0.25f;
		offXyz[0] = -0.7f - t * 0.55f * mag;
		offXyz[1] = 0.85f + t * 0.35f;
		offXyz[2] = -0.35f;
	}

	private static float magOutDip(Family fam) {
		return switch (fam) {
			case SHOTGUN -> 0.55f;
			case LMG, SNIPER -> 0.48f;
			case PISTOL, REVOLVER -> 0.28f;
			default -> 0.40f;
		};
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	public static void reset() {
		TRACKS.clear();
	}

	public static void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			tickLocal(mc.player);
		}
	}
}
