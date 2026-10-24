package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of xolt MultiPlayerGameModeMixin (MIT): no interactions while detached, never hit yourself. */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void drunsk$onInteractBlock(LocalPlayer player, InteractionHand hand,
                                        BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (Freecam.isEnabled()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void drunsk$onInteractEntity(Player player, Entity entity, EntityHitResult hitResult,
                                         InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity == Minecraft.getInstance().player || Freecam.isEnabled()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void drunsk$onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        if (target == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }
}
