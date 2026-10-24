package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.FreeCamera;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of xolt EntityRenderDispatcherMixin (MIT): never render the FreeCamera body. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void drunsk$onShouldRender(Entity entity, Frustum culler, double camX, double camY, double camZ,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof FreeCamera) {
            cir.setReturnValue(false);
        }
    }
}
