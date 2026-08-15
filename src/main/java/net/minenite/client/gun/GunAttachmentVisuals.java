package net.minenite.client.gun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads WarZ attachment PDC and renders 3D part models on the held gun.
 */
public final class GunAttachmentVisuals {
	private static final ItemStackRenderState SCRATCH = new ItemStackRenderState();

	private enum Slot {
		OPTIC, SUPPRESSOR, LASER, LIGHT, ADAPTER, GRIP
	}

	private record Piece(int cmd, Slot slot) {
	}

	private GunAttachmentVisuals() {
	}

	public static String opticId(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return "irons";
		}
		String v = stringVal(pbv, "optic");
		return v == null || v.isBlank() ? "irons" : v;
	}

	public static String gripId(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return "";
		}
		String v = stringVal(pbv, "grip");
		if (v == null || v.isBlank() || "none".equals(v)) {
			return "";
		}
		return v;
	}

	public static String gunId(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return "";
		}
		String v = stringVal(pbv, "gun_id");
		return v == null ? "" : v;
	}

	public static int zeroYards(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return 100;
		}
		int z = intVal(pbv, "zero_yards");
		return z <= 0 ? 100 : z;
	}

	public static int reticleRgb(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return 0xFF2828;
		}
		String laser = stringVal(pbv, "laser_color");
		if (laser == null || laser.isBlank() || "none".equals(laser) || "ir".equals(laser)) {
			return 0xFF2828;
		}
		return laserRgb(laser);
	}

	public record LaserSight(boolean active, boolean infrared, int rgb) {
		public static final LaserSight OFF = new LaserSight(false, false, 0xFF2828);
	}

	public static LaserSight laserSight(ItemStack gun) {
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return LaserSight.OFF;
		}
		boolean peq = byteOn(pbv, "peq15");
		boolean laserMod = byteOn(pbv, "laser_mod");
		String color = stringVal(pbv, "laser_color");
		if (color == null || color.isBlank() || "none".equals(color)) {
			color = peq ? "green" : "red";
		}
		boolean irColor = "ir".equals(color);
		String mode = stringVal(pbv, "optic_mode");
		if (mode == null || mode.isBlank()) {
			if (peq) {
				return LaserSight.OFF;
			}
			if (!laserMod) {
				return LaserSight.OFF;
			}
			return new LaserSight(true, irColor, irColor ? 0x2AFF4A : laserRgb(color));
		}
		boolean laserOn = "ir".equals(mode) || "green".equals(mode);
		if (!laserOn) {
			return LaserSight.OFF;
		}
		boolean infrared = "ir".equals(mode) || irColor;
		int rgb = infrared ? 0x2AFF4A : laserRgb(color);
		if (!peq && !laserMod) {
			return LaserSight.OFF;
		}
		return new LaserSight(true, infrared, rgb);
	}

	private static int laserRgb(String laser) {
		return switch (laser) {
			case "green" -> 0x82FF46;
			case "blue" -> 0x2878FF;
			case "yellow" -> 0xFFE628;
			case "orange" -> 0xFF8C1E;
			case "purple" -> 0xB43CFF;
			case "cyan" -> 0x28E6FF;
			case "pink" -> 0xFF50B4;
			case "white" -> 0xFFFFFF;
			case "ir" -> 0x2AFF4A;
			default -> 0xFF2828;
		};
	}

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light,
			ItemStack gun, LivingEntity living, boolean firstPerson) {
		if (gun == null || gun.isEmpty() || !GunItemPose.isGun(gun) || living == null) {
			return;
		}
		CompoundTag pbv = publicBukkit(gun);
		if (pbv == null) {
			return;
		}
		List<Piece> pieces = resolve(pbv);
		if (pieces.isEmpty()) {
			return;
		}
		for (Piece piece : pieces) {
			submitOne(poseStack, collector, light, living, piece, firstPerson);
		}
	}

	private static void submitOne(PoseStack poseStack, SubmitNodeCollector collector, int light,
			LivingEntity living, Piece piece, boolean firstPerson) {
		ItemStack stack = cmdStack(piece.cmd);
		SCRATCH.clear();
		Minecraft.getInstance().getItemModelResolver()
				.updateForLiving(SCRATCH, stack, ItemDisplayContext.FIXED, living);
		if (SCRATCH.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		applySlotTransform(poseStack, piece.slot, firstPerson);
		SCRATCH.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	private static void applySlotTransform(PoseStack pose, Slot slot, boolean fp) {
		float s = fp ? 0.40f : 0.52f;
		switch (slot) {
			case OPTIC -> {
				if (fp) {
					pose.translate(0.02, 0.14, 0.06);
					pose.mulPose(Axis.XP.rotationDegrees(-8f));
				} else {
					pose.translate(0.0, 0.10, 0.04);
					pose.mulPose(Axis.XP.rotationDegrees(-6f));
				}
				pose.scale(s, s, s);
			}
			case SUPPRESSOR -> {
				if (fp) {
					pose.translate(0.0, 0.02, 0.28);
				} else {
					pose.translate(0.0, 0.01, 0.32);
				}
				pose.scale(s * 0.95f, s * 0.95f, s * 1.15f);
			}
			case LASER, LIGHT -> {
				if (fp) {
					pose.translate(0.06, 0.05, 0.02);
				} else {
					pose.translate(0.05, 0.03, 0.0);
				}
				pose.scale(s * 0.85f, s * 0.85f, s * 0.85f);
			}
			case ADAPTER -> {
				if (fp) {
					pose.translate(0.0, -0.02, 0.04);
				} else {
					pose.translate(0.0, -0.04, 0.02);
				}
				pose.scale(s * 0.75f, s * 0.75f, s * 0.75f);
			}
			case GRIP -> {
				if (fp) {
					pose.translate(0.0, -0.04, 0.05);
				} else {
					pose.translate(0.0, -0.06, 0.03);
				}
				pose.scale(s * 0.9f, s * 0.9f, s * 0.9f);
			}
		}
	}

	private static List<Piece> resolve(CompoundTag pbv) {
		List<Piece> out = new ArrayList<>();
		String optic = stringVal(pbv, "optic");
		if (optic != null && !optic.isBlank() && !"irons".equals(optic)) {
			out.add(new Piece(cmdOptic(optic), Slot.OPTIC));
		}
		String sup = stringVal(pbv, "suppressor");
		if (sup != null && !sup.isBlank()) {
			out.add(new Piece(cmdSuppressor(sup), Slot.SUPPRESSOR));
		} else if (byteOn(pbv, "suppressor")) {
			out.add(new Piece(3111, Slot.SUPPRESSOR));
		}
		if (byteOn(pbv, "peq15")) {
			out.add(new Piece(3141, Slot.LIGHT));
		} else {
			String laser = stringVal(pbv, "laser_color");
			if (laser != null && !laser.isBlank() && !"none".equals(laser) && byteOn(pbv, "laser_mod")) {
				out.add(new Piece(cmdLaser(laser), Slot.LASER));
			}
			if (byteOn(pbv, "flashlight_mod")) {
				out.add(new Piece(3140, Slot.LIGHT));
			}
		}
		if (byteOn(pbv, "mag_adapter")) {
			out.add(new Piece(3142, Slot.ADAPTER));
		}
		String grip = stringVal(pbv, "grip");
		if (grip != null && !grip.isBlank() && !"none".equals(grip)) {
			out.add(new Piece(cmdGrip(grip), Slot.GRIP));
		}
		return out;
	}

	private static int cmdOptic(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "rds" -> 3101;
			case "eotech" -> 3102;
			case "holo_circle" -> 3103;
			case "acog" -> 3104;
			case "scope_6x" -> 3105;
			case "scope_8x" -> 3106;
			case "scope_barrett" -> 3107;
			default -> 3100;
		};
	}

	private static int cmdSuppressor(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "pistol" -> 3110;
			case "sniper" -> 3112;
			case "shotgun" -> 3113;
			default -> 3111;
		};
	}

	private static int cmdLaser(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "red" -> 3120;
			case "green" -> 3121;
			case "blue" -> 3122;
			case "yellow" -> 3123;
			case "orange" -> 3124;
			case "purple" -> 3125;
			case "cyan" -> 3126;
			case "pink" -> 3127;
			case "white" -> 3128;
			case "ir" -> 3129;
			default -> 3120;
		};
	}

	private static int cmdGrip(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "angled" -> 3151;
			case "bipod" -> 3152;
			case "handstop" -> 3153;
			default -> 3150;
		};
	}

	private static ItemStack cmdStack(int cmd) {
		ItemStack stack = new ItemStack(Items.IRON_NUGGET);
		stack.set(DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(List.of((float) cmd), List.of(), List.of(), List.of()));
		return stack;
	}

	private static CompoundTag publicBukkit(ItemStack gun) {
		CustomData custom = gun.get(DataComponents.CUSTOM_DATA);
		if (custom == null) {
			return null;
		}
		CompoundTag tag = custom.copyTag();
		if (tag.contains("PublicBukkitValues")) {
			Tag t = tag.get("PublicBukkitValues");
			if (t instanceof CompoundTag pbv) {
				return pbv;
			}
		}
		return tag;
	}

	public static boolean pdcFlag(ItemStack stack, String shortKey) {
		if (stack == null || stack.isEmpty() || shortKey == null || shortKey.isBlank()) {
			return false;
		}
		CompoundTag pbv = publicBukkit(stack);
		return pbv != null && byteOn(pbv, shortKey);
	}

	/** Raw PDC string (not lowercased). Bukkit keys are {@code pvpgunminus:shortKey}. */
	public static String pdcStringRaw(ItemStack stack, String shortKey) {
		if (stack == null || stack.isEmpty() || shortKey == null || shortKey.isBlank()) {
			return null;
		}
		CompoundTag pbv = publicBukkit(stack);
		if (pbv == null) {
			return null;
		}
		String v = readString(pbv, "pvpgunminus:" + shortKey);
		if (v != null) {
			return v;
		}
		v = readString(pbv, shortKey);
		if (v != null) {
			return v;
		}
		for (String key : pbv.keySet()) {
			if (key != null && key.endsWith(":" + shortKey)) {
				v = readString(pbv, key);
				if (v != null) {
					return v;
				}
			}
		}
		return null;
	}

	private static String stringVal(CompoundTag pbv, String shortKey) {
		String v = readString(pbv, "pvpgunminus:" + shortKey);
		if (v != null) {
			return v.toLowerCase(Locale.ROOT);
		}
		v = readString(pbv, shortKey);
		if (v != null) {
			return v.toLowerCase(Locale.ROOT);
		}
		for (String key : pbv.keySet()) {
			if (key != null && key.endsWith(":" + shortKey)) {
				v = readString(pbv, key);
				if (v != null) {
					return v.toLowerCase(Locale.ROOT);
				}
			}
		}
		return null;
	}

	private static boolean byteOn(CompoundTag pbv, String shortKey) {
		if (readByteOn(pbv, "pvpgunminus:" + shortKey) || readByteOn(pbv, shortKey)) {
			return true;
		}
		for (String key : pbv.keySet()) {
			if (key != null && key.endsWith(":" + shortKey) && readByteOn(pbv, key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean readByteOn(CompoundTag tag, String key) {
		if (tag == null || key == null || !tag.contains(key)) {
			return false;
		}
		try {
			return tag.getByte(key).orElse((byte) 0) == (byte) 1;
		} catch (Throwable t) {
			String s = readString(tag, key);
			return "1".equals(s) || "true".equalsIgnoreCase(s);
		}
	}

	private static String readString(CompoundTag tag, String key) {
		if (tag == null || key == null || !tag.contains(key)) {
			return null;
		}
		try {
			return tag.getString(key).orElse(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static int intVal(CompoundTag pbv, String shortKey) {
		int v = readInt(pbv, "pvpgunminus:" + shortKey);
		if (v != 0) {
			return v;
		}
		v = readInt(pbv, shortKey);
		if (v != 0) {
			return v;
		}
		for (String key : pbv.keySet()) {
			if (key != null && key.endsWith(":" + shortKey)) {
				v = readInt(pbv, key);
				if (v != 0) {
					return v;
				}
			}
		}
		return 0;
	}

	private static int readInt(CompoundTag tag, String key) {
		if (tag == null || key == null || !tag.contains(key)) {
			return 0;
		}
		try {
			return tag.getInt(key).orElse(0);
		} catch (Throwable t) {
			return 0;
		}
	}
}
