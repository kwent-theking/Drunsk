package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.FreeCamera;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of xolt CameraMixin (MIT) for 26.2: instant eye-height + no submersion fog. */
@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Entity entity;
    @Shadow private float eyeHeightOld;
    @Shadow private float eyeHeight;

    @Inject(method = "setEntity", at = @At("HEAD"))
    private void drunsk$onSetEntity(Entity entity, CallbackInfo ci) {
        if (entity == null || this.entity == null) {
            return;
        }
        if (entity instanceof FreeCamera || this.entity instanceof FreeCamera) {
            this.eyeHeightOld = this.eyeHeight = entity.getEyeHeight();
        }
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void drunsk$onGetSubmersionType(CallbackInfoReturnable<FogType> cir) {
        if (Freecam.isEnabled()) {
            cir.setReturnValue(FogType.NONE);
        }
    }
}
