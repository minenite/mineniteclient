package net.minenite.client.gun;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache of WarZ chainlink fence positions (climbable iron bars). */
public final class ChainlinkClient {
	public static final byte OP_UPSERT = 1;
	public static final byte OP_REMOVE = 2;
	public static final byte OP_FULL = 3;

	private static final Set<String> BARS = ConcurrentHashMap.newKeySet();

	private ChainlinkClient() {
	}

	public static void accept(byte[] raw) {
		if (raw == null || raw.length < 2) {
			return;
		}
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
			in.readUnsignedByte();
			byte op = in.readByte();
			if (op == OP_FULL) {
				BARS.clear();
				int n = in.readInt();
				for (int i = 0; i < n; i++) {
					String k = readUtf(in);
					if (k != null && !k.isBlank()) {
						BARS.add(k.toLowerCase(Locale.ROOT));
					}
				}
				return;
			}
			String k = readUtf(in);
			if (k == null || k.isBlank()) {
				return;
			}
			String key = k.toLowerCase(Locale.ROOT);
			if (op == OP_REMOVE) {
				BARS.remove(key);
			} else {
				BARS.add(key);
			}
		} catch (Exception ignored) {
		}
	}

	public static void clear() {
		BARS.clear();
	}

	public static boolean isChainlink(Level level, BlockPos pos) {
		if (level == null || pos == null || BARS.isEmpty()) {
			return false;
		}
		// Server keys use Bukkit world name (often "world"); client dim is "overworld".
		String suffix = ";" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ();
		for (String k : BARS) {
			if (k.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static String readUtf(DataInputStream in) throws java.io.IOException {
		int n = in.readUnsignedShort();
		if (n <= 0) {
			return "";
		}
		byte[] raw = new byte[n];
		in.readFully(raw);
		return new String(raw, StandardCharsets.UTF_8);
	}
}
