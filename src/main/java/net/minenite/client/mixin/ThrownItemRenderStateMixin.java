package net.minenite.client.mixin;

import net.minenite.client.gun.ThrownItemFlags;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ThrownItemRenderState.class)
public abstract class ThrownItemRenderStateMixin implements ThrownItemFlags {
	@Unique
	private boolean minenite$gunTracer;

	@Override
	public boolean minenite$isGunTracer() {
		return this.minenite$gunTracer;
	}

	@Override
	public void minenite$setGunTracer(boolean value) {
		this.minenite$gunTracer = value;
	}
}
