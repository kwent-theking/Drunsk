package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.FreeCamera;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of xolt BlockStateBaseMixin (MIT): the FreeCamera passes through
 * everything (ignoreAllCollision is always on in our config).
 * / Фрикам проходит сквозь блоки (у нас всегда ignoreAllCollision).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void drunsk$onGetCollisionShape(BlockGetter level, BlockPos pos, CollisionContext context,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof FreeCamera
                && Freecam.isEnabled()) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
