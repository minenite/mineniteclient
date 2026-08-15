package net.minenite.client.gun.vision;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thermal material DB: base temperature (0…1), emissivity, thermal mass (inertia).
 * Tuned for stronger cool/warm separation (stone cold, wood mild, sand sun-bakes, metal sinks).
 */
public final class ThermalMaterial {
	public record Props(float baseTemp, float emissivity, float thermalMass, boolean heatSource) {
	}

	private static final Props AIR = new Props(0.18f, 0.05f, 0.05f, false);
	private static final Props STONE = new Props(0.16f, 0.95f, 1.55f, false);
	private static final Props DIRT = new Props(0.30f, 0.94f, 0.85f, false);
	private static final Props WATER = new Props(0.18f, 0.97f, 1.7f, false);
	private static final Props ICE = new Props(0.04f, 0.98f, 1.25f, false);
	private static final Props SAND = new Props(0.34f, 0.9f, 0.55f, false);
	private static final Props WOOD = new Props(0.27f, 0.92f, 0.55f, false);
	private static final Props LEAVES = new Props(0.29f, 0.88f, 0.22f, false);
	/** Metals appear cooler (low emissivity) unless heated. */
	private static final Props METAL = new Props(0.14f, 0.28f, 0.9f, false);
	private static final Props LAVA = new Props(1.0f, 0.96f, 0.35f, true);
	private static final Props FIRE = new Props(0.96f, 0.92f, 0.12f, true);
	private static final Props CAMPFIRE = new Props(0.9f, 0.93f, 0.3f, true);
	private static final Props TORCH = new Props(0.74f, 0.86f, 0.18f, true);
	private static final Props MAGMA = new Props(0.92f, 0.95f, 0.45f, true);
	private static final Props DEFAULT = new Props(0.24f, 0.9f, 1.0f, false);

	private ThermalMaterial() {
	}

	public static Props of(BlockState state) {
		if (state == null || state.isAir()) {
			return AIR;
		}
		// Laser "glow" LIGHT cubes — treat as empty (never stamp as heat squares)
		if (state.is(Blocks.LIGHT)) {
			return AIR;
		}
		if (state.is(Blocks.LAVA) || (!state.getFluidState().isEmpty()
				&& state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))) {
			return LAVA;
		}
		if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
			return FIRE;
		}
		if (state.is(Blocks.MAGMA_BLOCK)) {
			return MAGMA;
		}
		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
				|| state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
			return TORCH;
		}
		if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
			boolean lit = state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
			return lit ? CAMPFIRE : WOOD;
		}
		if (state.is(Blocks.WATER) || (!state.getFluidState().isEmpty()
				&& state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))) {
			return WATER;
		}
		if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
			return ICE;
		}
		String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
		if (id.contains("leaves") || id.contains("moss") || id.contains("vine") || id.contains("grass")) {
			return LEAVES;
		}
		if (id.contains("log") || id.contains("plank") || id.contains("wood") || id.contains("fence")
				|| id.contains("door") || id.contains("trapdoor") || id.contains("stairs") && id.contains("oak")) {
			return WOOD;
		}
		if (id.contains("sand") || id.contains("gravel") || id.contains("dirt") || id.contains("grass_block")
				|| id.contains("podzol") || id.contains("mud") || id.contains("clay") || id.contains("farmland")
				|| id.contains("terracotta") || id.contains("concrete_powder")) {
			return id.contains("sand") || id.contains("gravel") ? SAND : DIRT;
		}
		if (id.contains("iron") || id.contains("copper") || id.contains("gold") || id.contains("netherite")
				|| id.contains("anvil") || id.contains("rail") || id.contains("chain") || id.contains("hopper")
				|| id.contains("cauldron") || id.contains("bars")) {
			return METAL;
		}
		if (id.contains("stone") || id.contains("deepslate") || id.contains("cobble") || id.contains("ore")
				|| id.contains("brick") || id.contains("basalt") || id.contains("blackstone")
				|| id.contains("andesite") || id.contains("diorite") || id.contains("granite")
				|| id.contains("calcite") || id.contains("tuff")) {
			return STONE;
		}
		return DEFAULT;
	}

	/** Solar heating bias for exposed daytime surfaces (stronger on low-mass soils). */
	public static float sunBias(float dayFactor, float skyOpen) {
		return Mth.clamp(dayFactor * skyOpen * 0.48f, 0f, 0.48f);
	}

	/** Extra shade cooling when the cell cannot see sky. */
	public static float shadeBias(boolean canSeeSky, float skyLight) {
		if (canSeeSky) {
			return 0f;
		}
		return Mth.clamp(0.14f * (1f - skyLight), 0f, 0.14f);
	}

	/** Rain cooling bias. */
	public static float rainBias(float rain) {
		return Mth.clamp(rain * 0.16f, 0f, 0.16f);
	}

	public static boolean isLightBlock(BlockState state) {
		return state != null && state.is(Blocks.LIGHT);
	}

	public static boolean isFlame(BlockState state) {
		if (state == null || state.isAir()) {
			return false;
		}
		if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
			return true;
		}
		if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
			return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
		}
		return false;
	}

	public static boolean isLava(BlockState state) {
		if (state == null) {
			return false;
		}
		return state.is(Blocks.LAVA) || (!state.getFluidState().isEmpty()
				&& state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA));
	}

	/**
	 * Leaves / plants / soft vegetation — do not stamp as thermal block markers.
	 * World mesh + wash reads better; they only soft-occlude heat behind them.
	 */
	public static boolean isVegetation(BlockState state) {
		if (state == null || state.isAir() || isLightBlock(state)) {
			return false;
		}
		Props props = of(state);
		if (props == LEAVES) {
			return true;
		}
		String id = state.getBlock().builtInRegistryHolder().key().identifier().getPath();
		if (id.contains("leaves") || id.contains("vine") || id.contains("moss_carpet")
				|| id.contains("hanging_roots") || id.contains("spore_blossom")
				|| id.contains("pink_petals") || id.contains("wildflowers")
				|| id.contains("leaf_litter")) {
			return true;
		}
		if (id.equals("short_grass") || id.equals("tall_grass") || id.equals("fern")
				|| id.equals("large_fern") || id.equals("dead_bush") || id.equals("bush")
				|| id.contains("seagrass") || id.contains("kelp") || id.contains("sugar_cane")
				|| id.contains("bamboo") || id.contains("sapling") || id.contains("propagule")
				|| id.contains("mushroom") || id.contains("fungus") || id.contains("roots")
				|| id.contains("sprouts") || id.contains("azalea") && !id.contains("log")) {
			return true;
		}
		if (id.contains("flower") || id.contains("lilac") || id.contains("rose_bush")
				|| id.contains("peony") || id.contains("sunflower") || id.contains("tulip")
				|| id.contains("orchid") || id.contains("dandelion") || id.contains("poppy")
				|| id.contains("allium") || id.contains("azure") || id.contains("cornflower")
				|| id.contains("lily") || id.contains("wither_rose") || id.contains("torchflower")
				|| id.contains("pitcher") || id.contains("berry") || id.contains("crop")
				|| id.contains("wheat") || id.contains("carrot") || id.contains("potato")
				|| id.contains("beetroot") || id.contains("melon_stem") || id.contains("pumpkin_stem")
				|| id.contains("cave_vines") || id.contains("weeping_vines") || id.contains("twisting_vines")) {
			return true;
		}
		// Non-occluding plant-shaped blocks that aren't heat sources
		if (!props.heatSource() && !state.canOcclude() && (id.contains("grass") || id.contains("plant")
				|| id.contains("weed") || id.contains("fern"))) {
			return true;
		}
		return false;
	}
}
