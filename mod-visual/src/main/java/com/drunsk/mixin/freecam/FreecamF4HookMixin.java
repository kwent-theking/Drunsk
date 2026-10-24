package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When our freecam is active, consume the F4 (toggle perspective) click so
 * the vanilla perspective switch never fires — no "two freecams" conflict.
 * / Когда наш фрикам активен, съедаем клик F4 (смена перспективы), чтобы
 * ванильная смена перспективы не сработала — нет конфликта "двух фрикамов".
 */
@Mixin(Minecraft.class)
public class FreecamF4HookMixin {

    @Shadow
    public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void drunsk$blockF4(CallbackInfo ci) {
        if (Freecam.isEnabled() && options != null) {
            // consume all pending F4 clicks so vanilla never toggles perspective
            // / съедаем все клики F4, чтобы ваниль не переключил перспективу
            while (options.keyTogglePerspective.consumeClick()) {
                // swallowed
            }
        }
    }
}
