package net.minenite.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn/lodestone compasses point at a target. Force the needle to true north (-Z). */
@Mixin(CompassAngleState.class)
public abstract class CompassNorthMixin {
	@Invoker("getRotationTowardsCompassTarget")
	abstract float minenite$rotationTowards(ItemOwner owner, long gameTime, BlockPos compassTargetPos);

	@Inject(method = "calculate", at = @At("HEAD"), cancellable = true)
	private void minenite$alwaysNorth(ItemStack itemStack, ClientLevel level, int seed, ItemOwner owner,
			CallbackInfoReturnable<Float> cir) {
		if (owner == null || level == null) {
			return;
		}
		Vec3 at = owner.position();
		BlockPos north = BlockPos.containing(at.x, at.y, at.z - 1_000_000.0);
		cir.setReturnValue(this.minenite$rotationTowards(owner, level.getGameTime(), north));
	}
}
