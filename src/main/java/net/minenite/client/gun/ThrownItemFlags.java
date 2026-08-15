package net.minenite.client.gun;

/**
 * ThrownItemRenderState mixin flag: gun snowballs stay 1px tracers;
 * cans / bottles keep the vanilla flying-item sprite.
 */
public interface ThrownItemFlags {
	boolean minenite$isGunTracer();

	void minenite$setGunTracer(boolean value);
}
