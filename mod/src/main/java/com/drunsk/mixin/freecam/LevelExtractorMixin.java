package com.drunsk.mixin.freecam;

import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port of xolt LevelExtractorMixin (MIT, 26.x): the real player is skipped by
 * vanilla culling while the camera is detached — re-add its render state so
 * showPlayer works. / Ваниль не рисует игрока при отсоединённой камере —
 * добавляем его render state обратно, чтобы showPlayer работал.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Shadow
    private EntityRenderState extractEntity(Entity entity, float partialTick) {
        throw new AssertionError();
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void drunsk$onExtractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                                 LevelRenderState renderState, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Freecam.isEnabled() && Freecam.shouldShowPlayer()) {
            Entity player = mc.player;
            TickRateManager tickRateManager = mc.level.tickRateManager();
            float partial = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(player));
            renderState.entityRenderStates.add(this.extractEntity(player, partial));
        }
    }
}
