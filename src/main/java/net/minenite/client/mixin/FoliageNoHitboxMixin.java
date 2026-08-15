package net.minenite.client.mixin;

import net.minenite.client.foliage.FoliageBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Strips selection outlines and collision from foliage client-side.
 *
 * <p>{@code getShape} drives the black block outline and click targeting;
 * {@code getCollisionShape} drives movement. Both become empty so leaves and
 * plants neither highlight nor block the player locally.
 *
 * <p>The two-arg collision overload is intercepted because it can return a
 * cached solid shape without calling the three-arg path.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FoliageNoHitboxMixin {
    @Inject(
            method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true)
    private void minenite$emptyFoliageShape(
            BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (FoliageBlocks.isFoliage((BlockState) (Object) this)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true)
    private void minenite$emptyFoliageCollisionCached(
            BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (FoliageBlocks.isFoliage((BlockState) (Object) this)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true)
    private void minenite$emptyFoliageCollision(
            BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (FoliageBlocks.isFoliage((BlockState) (Object) this)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
