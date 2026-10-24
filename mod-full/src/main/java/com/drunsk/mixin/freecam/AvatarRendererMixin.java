package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of xolt AvatarRendererMixin (MIT, 26.x): suppress the FreeCamera
 * nametag (an empty render state with no shadow pieces is the camera).
 * / Гасим ник-таг фейковой камеры (пустой state без теней — это камера).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void drunsk$onSubmitNameDisplay(AvatarRenderState renderState, PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState,
                                            CallbackInfo ci) {
        if (Freecam.isEnabled() && renderState.shadowPieces.isEmpty()) {
            ci.cancel();
        }
    }
}
