package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port of xolt OptionsMixin (MIT): F5 cannot switch perspective while detached. */
@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void drunsk$onSetPerspective(CallbackInfo ci) {
        if (Freecam.isEnabled()) {
            ci.cancel();
        }
    }
}
