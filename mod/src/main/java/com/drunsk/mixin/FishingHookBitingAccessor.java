package com.drunsk.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** DATA_BITING is private; the client reads the bite flag from synced entity data. / Флаг поклёвки из synced-данных. */
@Mixin(FishingHook.class)
public interface FishingHookBitingAccessor {

    @Accessor("DATA_BITING")
    static EntityDataAccessor<Boolean> drunsk$getBitingData() {
        throw new AssertionError();
    }
}
