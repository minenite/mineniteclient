package net.minenite.client.gun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double-tap Shift prone. While ADS, Shift is hold-breath instead.
 */
public final class ProneClient {
	public static final String TAG = "pgm_prone";
	private static final long DOUBLE_TAP_MS = 450L;

	private static final Set<UUID> pronePlayers = ConcurrentHashMap.newKeySet();
	private static boolean shiftWasDown;
	private static long lastShiftPressMs;

	private ProneClient() {
	}

	public static void reset() {
		pronePlayers.clear();
		shiftWasDown = false;
		lastShiftPressMs = 0L;
	}

	public static void accept(UUID player, boolean prone) {
		if (player == null) {
			return;
		}
		if (prone) {
			pronePlayers.add(player);
		} else {
			pronePlayers.remove(player);
		}
	}

	public static boolean isProne() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && pronePlayers.contains(mc.player.getUUID());
	}

	public static boolean isProne(LocalPlayer player) {
		if (player == null) {
			return false;
		}
		return pronePlayers.contains(player.getUUID())
				|| player.entityTags().contains(TAG)
				|| (player.getPose() == Pose.SWIMMING && player.onGround() && !player.isInWater());
	}

	public static boolean shouldCrawlVisual(Avatar avatar) {
		if (avatar == null) {
			return false;
		}
		if (pronePlayers.contains(avatar.getUUID())) {
			return true;
		}
		return avatar.entityTags().contains(TAG)
				|| (avatar.getPose() == Pose.SWIMMING && !avatar.isInWater());
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gui.screen() != null) {
			shiftWasDown = false;
			return;
		}

		long window = client.getWindow().handle();
		boolean shiftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

		// Scoped: Shift = hold breath (jump to leave prone)
		if (ScopeOverlay.isScoped()) {
			shiftWasDown = shiftDown;
		} else if (shiftDown && !shiftWasDown) {
			long now = System.currentTimeMillis();
			if (lastShiftPressMs > 0L && now - lastShiftPressMs <= DOUBLE_TAP_MS) {
				requestToggle();
				lastShiftPressMs = 0L;
			} else {
				lastShiftPressMs = now;
			}
			shiftWasDown = shiftDown;
		} else {
			shiftWasDown = shiftDown;
		}

		if (isProne()) {
			player.setPose(Pose.SWIMMING);
			player.setSwimming(true);
		}
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !isProne(player) || ScopeOverlay.isScoped()) {
			return;
		}
		Font font = mc.font;
		String msg = "PRONE";
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		g.text(font, msg, w / 2 - font.width(msg) / 2, h - 52, 0xFFC8C8C8, true);
	}

	public static byte[] encodeToggle() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeByte(1);
			out.writeByte(0);
			return bos.toByteArray();
		} catch (Exception e) {
			return null;
		}
	}

	private static void requestToggle() {
		LaserNet.sendProneReq(encodeToggle());
	}
}
