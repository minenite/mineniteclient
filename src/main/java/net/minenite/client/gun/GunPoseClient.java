package net.minenite.client.gun;

import net.minecraft.world.entity.Avatar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache of server-broadcast gun pose flags (scoreboard tags do not sync). */
public final class GunPoseClient {
	public static final byte FLAG_GUN = 1;
	public static final byte FLAG_AIM = 2;
	public static final byte FLAG_FIRE = 4;
	public static final byte FLAG_SCOPE = 8;
	public static final byte FLAG_RELOAD = 16;

	private static final Map<UUID, Byte> FLAGS = new ConcurrentHashMap<>();

	private GunPoseClient() {
	}

	public static void reset() {
		FLAGS.clear();
	}

	public static void accept(UUID player, byte flags) {
		if (player == null) {
			return;
		}
		if (flags == 0) {
			FLAGS.remove(player);
		} else {
			FLAGS.put(player, flags);
		}
	}

	public static boolean has(Avatar avatar, byte flag) {
		if (avatar == null) {
			return false;
		}
		Byte f = FLAGS.get(avatar.getUUID());
		return f != null && (f & flag) != 0;
	}

	public static boolean aiming(Avatar avatar) {
		return has(avatar, FLAG_AIM) || has(avatar, FLAG_SCOPE);
	}

	public static boolean hipfiring(Avatar avatar) {
		return has(avatar, FLAG_FIRE);
	}

	public static boolean holdingGun(Avatar avatar) {
		return has(avatar, FLAG_GUN);
	}

	public static boolean reloading(Avatar avatar) {
		return has(avatar, FLAG_RELOAD);
	}
}
