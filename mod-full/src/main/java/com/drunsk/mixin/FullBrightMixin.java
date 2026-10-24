package com.drunsk.mixin;

import com.drunsk.utils.Utils;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full-bright: raises the lightmap brightness floor after vanilla computes it
 * (Lightmap.render reads state.brightness later). / Полная яркость: поднимаем
 * brightness в state после ванильного расчёта — Lightmap.render читает его позже.
 */
@Mixin(LightmapRenderStateExtractor.class)
public class FullBrightMixin {

    @Inject(method = "extract", at = @At("RETURN"))
    private void drunsk$fullBright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (Utils.fullBrightActive()) {
            // 16.0f as in xolt's >=26.1 full-bright: the shader treats brightness
            // as a scale, so values above 1 flatten the light curve to maximum.
            // / 16.0f как у xolt для 26.1+: brightness — масштаб, значения > 1
            // выводят кривую света в максимум.
            state.brightness = 16.0f;
        }
    }
}
