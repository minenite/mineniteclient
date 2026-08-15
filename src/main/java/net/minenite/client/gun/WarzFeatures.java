package net.minenite.client.gun;

/**
 * Session feature flags from WarZ ({@code pvpgunminus:features}).
 * Default off — foreign servers never send this packet.
 */
public final class WarzFeatures {
	public static final int FLAG_LEAVES = 1;
	public static final int FLAG_CHAINLINK = 1 << 1;

	private static volatile int flags;

	private WarzFeatures() {
	}

	public static void accept(byte[] raw) {
		if (raw == null || raw.length < 5) {
			flags = 0;
			return;
		}
		try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(raw))) {
			in.readUnsignedByte();
			flags = in.readInt();
		} catch (Exception e) {
			flags = 0;
		}
	}

	public static void clear() {
		flags = 0;
	}

	public static boolean isLinked() {
		return flags != 0;
	}

	public static boolean leavesEnabled() {
		return (flags & FLAG_LEAVES) != 0;
	}

	public static boolean chainlinkEnabled() {
		return (flags & FLAG_CHAINLINK) != 0;
	}

	public static int flags() {
		return flags;
	}
}
