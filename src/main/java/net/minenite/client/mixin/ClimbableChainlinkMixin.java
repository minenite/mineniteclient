package net.minenite.client.mixin;

import net.minenite.client.gun.ChainlinkClient;
import net.minenite.client.gun.WarzFeatures;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only WarZ-marked chainlink iron bars are climbable — never vanilla iron bars.
 * Do not mark climbable while walking on the ground (vanilla climb friction feels like lag).
 */
@Mixin(LivingEntity.class)
public abstract class ClimbableChainlinkMixin {
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void minenite$climbChainlink(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isSpectator() || self.level() == null) {
			return;
		}
		if (!WarzFeatures.chainlinkEnabled()) {
			return;
		}
		boolean jumpHeld = self instanceof LocalPlayer local
				&& local.input != null
				&& local.input.keyPresses.jump();
		if (self.onGround() && !jumpHeld) {
			return;
		}
		BlockPos in = self.blockPosition();
		BlockState state = self.getInBlockState();
		if (state.is(Blocks.IRON_BARS) && ChainlinkClient.isChainlink(self.level(), in)) {
			cir.setReturnValue(true);
			return;
		}
		BlockPos feet = self.blockPosition();
		for (BlockPos p : new BlockPos[]{
				feet.north(), feet.south(), feet.east(), feet.west(),
				feet.above().north(), feet.above().south(), feet.above().east(), feet.above().west()
		}) {
			if (self.level().getBlockState(p).is(Blocks.IRON_BARS)
					&& ChainlinkClient.isChainlink(self.level(), p)) {
				cir.setReturnValue(true);
				return;
			}
		}
	}
}
