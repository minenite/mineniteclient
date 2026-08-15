package net.minenite.client.gun.vision;

/** Night-vision phosphor modes. Multi NODS: H cycles. Fixed-color NODS: H toggles on/off. */
public enum NvgPalette {
	GREEN("Green"),
	WHITE("White"),
	AMBER("Amber"),
	BLUE("Blue"),
	RED("Red"),
	TRUE_COLOR("True Color");

	private final String label;

	NvgPalette(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public NvgPalette next() {
		NvgPalette[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public int[] washRgb() {
		return switch (this) {
			case GREEN -> new int[]{8, 235, 72};
			case WHITE -> new int[]{245, 250, 255};
			case AMBER -> new int[]{255, 175, 0};
			case BLUE -> new int[]{0, 90, 255};
			case RED -> new int[]{255, 16, 8};
			case TRUE_COLOR -> new int[]{128, 128, 128};
		};
	}

	public int[] washRgb2() {
		return switch (this) {
			case GREEN -> new int[]{20, 200, 110};
			case WHITE -> new int[]{220, 230, 240};
			case AMBER -> new int[]{255, 120, 0};
			case BLUE -> new int[]{0, 40, 220};
			case RED -> new int[]{220, 0, 20};
			case TRUE_COLOR -> new int[]{100, 100, 100};
		};
	}

	public float washStrength() {
		return switch (this) {
			case GREEN -> 1f;
			case WHITE -> 1.85f;
			case AMBER -> 2.05f;
			case BLUE -> 2.1f;
			case RED -> 2.15f;
			case TRUE_COLOR -> 0.22f;
		};
	}

	public float punchStrength() {
		return switch (this) {
			case GREEN, TRUE_COLOR -> 0f;
			case WHITE -> 0.55f;
			case AMBER, BLUE, RED -> 0.7f;
		};
	}

	public float grainMul() {
		return this == TRUE_COLOR ? 2.6f : (this == WHITE ? 1.45f : 1f);
	}

	public boolean trueColor() {
		return this == TRUE_COLOR;
	}

	/** Lightmap night-vision color (0–1). */
	public float[] visionRgb() {
		return switch (this) {
			case GREEN -> new float[]{0.18f, 1.0f, 0.32f};
			case WHITE -> new float[]{0.92f, 0.96f, 1.0f};
			case AMBER -> new float[]{1.0f, 0.72f, 0.10f};
			case BLUE -> new float[]{0.12f, 0.42f, 1.0f};
			case RED -> new float[]{1.0f, 0.10f, 0.08f};
			case TRUE_COLOR -> new float[]{0.88f, 0.88f, 0.86f};
		};
	}
}
