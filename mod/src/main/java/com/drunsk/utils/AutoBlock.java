package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-block: when the selected block stack runs out while building, selects
 * the next stack of the same block. / Автозамена блоков: когда выбранный стак
 * блоков кончается, выбирает следующий стак того же блока.
 */
public final class AutoBlock {

    private static final int CHECK_INTERVAL = 4;
    private static int lastCheck;
    private static BlockItem trackedItem;

    private AutoBlock() {
    }

    public static void tick(Minecraft mc) {
        if (!DrunskConfig.util.autoBlock) {
            trackedItem = null;
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.isCreative()) return;
        if (player.tickCount - lastCheck < CHECK_INTERVAL) return;
        lastCheck = player.tickCount;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);
        if (held.getItem() instanceof BlockItem blockItem) {
            trackedItem = blockItem;
            return;
        }
        if (trackedItem == null || !held.isEmpty()) return;

        // selected stack emptied: find more of the tracked block
        // / выбранный стак кончился: ищем ещё такие же блоки
        int slot = Utils.findSlot(player, s -> s.getItem() == trackedItem);
        if (slot < 0) {
            trackedItem = null;
            return;
        }
        if (slot < 9) {
            Utils.selectHotbar(mc, slot, DrunskConfig.util.autoBlockPacket);
        } else {
            Utils.swapSlots(mc, slot, selected, DrunskConfig.util.autoBlockPacket);
        }
    }
}
