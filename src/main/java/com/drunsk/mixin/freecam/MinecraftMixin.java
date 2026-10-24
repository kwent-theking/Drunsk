package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of xolt MinecraftMixin (MIT) for 26.2: block attacks/picks while
 * detached, disable freecam on disconnect, swallow hotbar keys while the
 * toggle key is held.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void drunsk$onDoAttack(CallbackInfoReturnable<Boolean> ci) {
        if (Freecam.isEnabled()) ci.cancel();
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void drunsk$onDoItemPick(CallbackInfo ci) {
        if (Freecam.isEnabled()) ci.cancel();
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void drunsk$onHandleBlockBreaking(CallbackInfo ci) {
        if (Freecam.isEnabled()) ci.cancel();
    }

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void drunsk$onDisconnect(CallbackInfo ci) {
        Freecam.onDisconnect();
    }
}
