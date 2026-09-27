package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port of xolt LivingEntityMixin (MIT): leaving freecam when the real player takes damage. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow public abstract float getHealth();

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void drunsk$onSetHealth(float health, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (Freecam.isEnabled() && Freecam.shouldDisableOnDamage()
                && drunsk$this() == mc.player && mc.player != null
                && !mc.player.isCreative() && getHealth() > health) {
            Freecam.disableNextTick();
        }
    }

    @Unique
    private LivingEntity drunsk$this() {
        return (LivingEntity) (Object) this;
    }
}
