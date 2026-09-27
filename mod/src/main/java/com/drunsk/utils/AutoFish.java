package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import com.drunsk.mixin.FishingHookBitingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * Auto-fishing: detects the bite via the hook's synced DATA_BITING flag,
 * retrieves, then recasts. Vanilla mode goes through MultiPlayerGameMode.useItem;
 * packet mode sends ServerboundUseItemPacket with a fresh prediction sequence.
 * / Автофиш: поклёвка по synced-флагу DATA_BITING, подсечка и перезаброс.
 * Ванильный режим — gameMode.useItem, пакетный — ServerboundUseItemPacket.
 */
public final class AutoFish {

    private static final int RECAST_DELAY_TICKS = 8;

    private static int recastAt = -1;

    private AutoFish() {
    }

    public static void reset() {
        recastAt = -1;
    }

    public static void tick(Minecraft mc) {
        if (!DrunskConfig.util.autoFish) {
            recastAt = -1;
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        InteractionHand rodHand = rodHand(player);
        if (rodHand == null) {
            recastAt = -1;
            return;
        }

        FishingHook hook = player.fishing;
        if (hook != null) {
            boolean biting = hook.getEntityData().get(FishingHookBitingAccessor.drunsk$getBitingData());
            if (biting && recastAt < 0) {
                useRod(mc, player, rodHand);
                recastAt = player.tickCount + RECAST_DELAY_TICKS;
            }
            return;
        }

        // hook gone (retrieved or lost): recast once after the delay
        // / крючка нет (подсечка прошла): перезабрасываем после задержки
        if (recastAt > 0 && player.tickCount >= recastAt) {
            recastAt = -1;
            useRod(mc, player, rodHand);
        }
    }

    private static InteractionHand rodHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.FISHING_ROD)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.FISHING_ROD)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static void useRod(Minecraft mc, LocalPlayer player, InteractionHand hand) {
        if (DrunskConfig.util.autoFishPacket) {
            final float yRot = player.getYRot();
            final float xRot = player.getXRot();
            Utils.sendWithSequence(mc,
                    seq -> new ServerboundUseItemPacket(hand, seq, yRot, xRot));
        } else {
            mc.gameMode.useItem(player, hand);
        }
    }
}
