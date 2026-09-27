package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.FreeCamera;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of xolt LocalPlayerMixin (MIT): rotation follows the FreeCamera; Baritone compat. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends EntityMixin {

    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void drunsk$onIsCamera(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isEnabled() && drunsk$self() == Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }

    @Override
    protected void drunsk$onGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (Freecam.isEnabled()) {
            cir.setReturnValue(Freecam.getFreeCamera().getViewXRot(partialTick));
        }
    }

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void drunsk$onGetViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (Freecam.isEnabled()) {
            cir.setReturnValue(Freecam.getFreeCamera().getViewYRot(partialTick));
        }
    }

    @Unique
    private LocalPlayer drunsk$self() {
        return (LocalPlayer) (Object) this;
    }
}
