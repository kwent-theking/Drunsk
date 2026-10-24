package com.drunsk.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** getBlockStatePredictionHandler is package-private in ClientLevel. / Метод пакетно-приватный. */
@Mixin(ClientLevel.class)
public interface ClientLevelPredictionAccessor {

    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler drunsk$getPredictionHandler();
}
