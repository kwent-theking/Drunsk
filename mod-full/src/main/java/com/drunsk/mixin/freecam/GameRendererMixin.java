package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of xolt GameRendererMixin (MIT): hide hand, no block outline while detached. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void drunsk$onRenderItemInHand(CallbackInfo ci) {
        if (Freecam.isEnabled() && Freecam.shouldHideHand()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void drunsk$onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
