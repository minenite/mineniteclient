package net.minenite.client.gun;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Placeholder for optional third-party player-animation clips.
 * GunCarryPoseMixin falls back to built-in arm poses when this returns false.
 */
public final class PlayerAnimBridge {
	private PlayerAnimBridge() {
	}

	public static boolean apply(PlayerModel model, AvatarRenderState state, boolean rightMain) {
		return false;
	}
}
