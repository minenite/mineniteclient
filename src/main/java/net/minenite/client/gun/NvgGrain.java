package net.minenite.client.gun;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** One 256² noise texture instead of thousands of HUD fills. */
public final class NvgGrain {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("mineniteclient", "nvg_grain");
	private static boolean ready;

	private NvgGrain() {
	}

	public static void ensure() {
		if (ready) {
			return;
		}
		NativeImage img = new NativeImage(256, 256, false);
		long seed = 0xA5A5C0FFEE1234L;
		for (int y = 0; y < 256; y++) {
			for (int x = 0; x < 256; x++) {
				seed = splitMix(seed);
				int n = (int) ((seed >>> 48) & 255);
				int a;
				if (n > 232) {
					a = 70 + (n & 31);
				} else if (n < 18) {
					a = 36 + (n & 15);
				} else {
					a = 6 + (n & 11);
				}
				img.setPixel(x, y, ARGB.color(a, n, n, n));
			}
		}
		DynamicTexture tex = new DynamicTexture(() -> "nvg_grain", img);
		Minecraft.getInstance().getTextureManager().register(ID, tex);
		ready = true;
	}

	private static long splitMix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
