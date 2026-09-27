package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-tool: when the selected tool wears below the durability threshold,
 * swaps in the healthiest same-kind replacement from the inventory.
 * / Автозамена инструментов: когда выбранный инструмент изнашивается ниже
 * порога, подменяет его самым живым таким же из инвентаря.
 */
public final class AutoTool {

    private static final int CHECK_INTERVAL = 10;
    private static int lastCheck;

    private AutoTool() {
    }

    public static void tick(Minecraft mc) {
        if (!DrunskConfig.util.autoTool) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.isCreative()) return;
        if (player.tickCount - lastCheck < CHECK_INTERVAL) return;
        lastCheck = player.tickCount;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);
        if (!held.isDamageableItem()) return;
        double worn = (double) held.getDamageValue() / held.getMaxDamage();
        if (worn < DrunskConfig.util.autoToolDurability) return;

        // healthiest same-kind replacement / самая живая замена того же типа
        int best = -1;
        int bestHealth = held.getMaxDamage() - held.getDamageValue();
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty() || !s.isDamageableItem()) continue;
            if (!sameKind(held, s)) continue;
            int health = s.getMaxDamage() - s.getDamageValue();
            if (health > bestHealth) {
                bestHealth = health;
                best = i;
            }
        }
        if (best < 0) return;

        if (best < 9) {
            Utils.selectHotbar(mc, best, DrunskConfig.util.autoToolPacket);
        } else {
            Utils.swapSlots(mc, best, selected, DrunskConfig.util.autoToolPacket);
        }
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.getItem().getClass() == b.getItem().getClass();
    }
}
