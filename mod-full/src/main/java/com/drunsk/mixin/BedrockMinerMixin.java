package com.drunsk.mixin;

import com.drunsk.utils.BedrockMiner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BedrockMiner armed-click hook (same idea as BedrockMiner's
 * ClientPlayerInteractionManagerMixin, MIT): when armed, a left click on
 * bedrock starts the piston sequence instead of a normal break.
 * / Клик по бедроку во взведённом состоянии запускает поршневую схему
 * вместо обычного ломания (порт хука BedrockMiner).
 */
@Mixin(MultiPlayerGameMode.class)
public class BedrockMinerMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void drunsk$onStartDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (BedrockMiner.onClickedBlock(Minecraft.getInstance(), pos, direction)) {
            cir.setReturnValue(true);
        }
    }
}
