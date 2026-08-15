package net.minenite.client.gun;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD bloom used to live here. Simply Upscaled lights the look-cone in the
 * shader (Complementary held lighting + Iris emission), so a screen oval
 * would just sit on top and look fake.
 */
public final class FlashlightOverlay {
	private FlashlightOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
	}
}
