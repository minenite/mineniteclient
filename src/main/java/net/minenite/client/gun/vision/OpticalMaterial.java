package net.minenite.client.gun.vision;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coarse optical material DB for thermal occlusion (glass/leaves leak, stone cuts).
 */
public final class OpticalMaterial {
	public record Props(
			float reflectivity,
			float absorption,
			float ior,
			float transmission,
			float scatter,
			boolean metal,
			boolean water,
			boolean glass
	) {
	}

	private static final Props AIR = new Props(0f, 0f, 1.0003f, 1f, 0.002f, false, false, false);
	private static final Props STONE = new Props(0.05f, 0.92f, 1.5f, 0f, 0.02f, false, false, false);
	private static final Props WATER = new Props(0.02f, 0.25f, 1.333f, 0.88f, 0.35f, false, true, false);
	private static final Props GLASS = new Props(0.08f, 0.05f, 1.52f, 0.96f, 0.01f, false, false, true);
	private static final Props LEAVES = new Props(0.04f, 0.55f, 1.3f, 0.15f, 0.55f, false, false, false);
	private static final Props GOLD = new Props(0.95f, 0.08f, 0.47f, 0f, 0.01f, true, false, false);
	private static final Props IRON = new Props(0.72f, 0.25f, 2.9f, 0f, 0.02f, true, false, false);
	private static final Props ICE = new Props(0.12f, 0.15f, 1.31f, 0.85f, 0.2f, false, false, false);
	private static final Props SAND = new Props(0.18f, 0.7f, 1.45f, 0f, 0.25f, false, false, false);
	private static final Props DEFAULT = new Props(0.06f, 0.85f, 1.5f, 0f, 0.05f, false, false, false);

	private OpticalMaterial() {
	}

	public static Props of(BlockState state) {
		if (state == null || state.isAir()) {
			return AIR;
		}
		if (state.is(Blocks.WATER) || (!state.getFluidState().isEmpty()
				&& state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))) {
			return WATER;
		}
		if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS)) {
			return GLASS;
		}
		if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE)) {
			return ICE;
		}
		if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.RAW_GOLD_BLOCK)) {
			return GOLD;
		}
		if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.IRON_BARS) || state.is(Blocks.RAW_IRON_BLOCK)) {
			return IRON;
		}
		if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
			return SAND;
		}
		String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
		if (id.contains("glass")) {
			return GLASS;
		}
		if (id.contains("leaves") || id.contains("moss") || id.contains("vine")) {
			return LEAVES;
		}
		if (id.contains("ore") || id.contains("stone") || id.contains("deepslate") || id.contains("cobble")
				|| id.contains("andesite") || id.contains("diorite") || id.contains("granite")) {
			return STONE;
		}
		if (id.contains("iron") || id.contains("copper") || id.contains("netherite")) {
			return IRON;
		}
		if (id.contains("gold")) {
			return GOLD;
		}
		if (id.contains("sand") || id.contains("dirt") || id.contains("gravel")) {
			return SAND;
		}
		return DEFAULT;
	}

	public static float continueFactor(Props p, boolean infrared) {
		float t = p.transmission();
		float abs = p.absorption() * (infrared && p.water() ? 1.45f : 1f);
		return Mth.clamp(t * (1f - abs * 0.35f), 0f, 1f);
	}
}
