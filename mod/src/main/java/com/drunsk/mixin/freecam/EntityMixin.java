package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of xolt EntityMixin (MIT) for 26.2: mouse turns the FreeCamera, no
 * push interactions with it, optional player freeze.
 */
@Mixin(Entity.class)
public class EntityMixin {

    /// Overridden by LocalPlayerMixin. / Переопределяется в LocalPlayerMixin.
    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    protected void drunsk$onGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        // no-op
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void drunsk$onChangeLookDirection(double yRot, double xRot, CallbackInfo ci) {
        if (Freecam.isEnabled() && drunsk$this() == Minecraft.getInstance().player) {
            Freecam.getFreeCamera().turn(yRot, xRot);
            ci.cancel();
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void drunsk$onPushAwayFrom(Entity entity, CallbackInfo ci) {
        if (Freecam.isEnabled()
                && (entity == Freecam.getFreeCamera() || drunsk$this() == Freecam.getFreeCamera())) {
            ci.cancel();
        }
    }

    @Inject(method = "setDeltaMovement(DDD)V", at = @At("HEAD"), cancellable = true)
    private void drunsk$onSetVelocity(CallbackInfo ci) {
        if (drunsk$shouldFreeze()) ci.cancel();
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void drunsk$onUpdateVelocity(CallbackInfo ci) {
        if (drunsk$shouldFreeze()) ci.cancel();
    }

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void drunsk$onSetPosition(CallbackInfo ci) {
        if (drunsk$shouldFreeze()) ci.cancel();
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void drunsk$onSetPos(CallbackInfo ci) {
        if (drunsk$shouldFreeze()) ci.cancel();
    }

    @Unique
    private Entity drunsk$this() {
        return (Entity) (Object) this;
    }

    @Unique
    private boolean drunsk$shouldFreeze() {
        return Freecam.isEnabled() && drunsk$this() == Minecraft.getInstance().player
                && Freecam.shouldFreezePlayer();
    }
}
