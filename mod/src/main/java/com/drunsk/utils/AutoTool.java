package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Auto-tool: two functions.
 * 1) Durability swap: when the selected tool wears below the threshold,
 *    swap in the healthiest same-kind replacement.
 * 2) Type swap: when breaking a block the current tool is inefficient for
 *    (e.g. shovel on stone), auto-switch to the best tool in inventory.
 * / Авто-инструмент: две функции.
 * 1) По износу: когда выбранный инструмент изнашивается ниже порога,
 *    подменяет его самым живым таким же.
 * 2) По типу блока: если ломаешь блок не тем инструментом (лопата по камню),
 *    автоматически переключает на лучший инструмент в инвентаре.
 */
public final class AutoTool {

    private static final int CHECK_INTERVAL = 4;
    private static final float MIN_SPEED = 1.0f; // below this the tool is "wrong" for the block

    private static int lastCheck;
    private static int prevSlot = -1;
    private static int restoreAt = -1;

    private AutoTool() {
    }

    public static void tick(Minecraft mc) {
        if (!DrunskConfig.util.autoTool) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.isCreative()) return;

        // restore previous slot after the block is broken
        // / возвращаем предыдущий слот после того, как блок сломан
        if (prevSlot >= 0 && !mc.gameMode.isDestroying() && player.tickCount >= restoreAt) {
            Utils.selectHotbar(mc, prevSlot, DrunskConfig.util.autoToolPacket);
            prevSlot = -1;
            restoreAt = -1;
        }

        if (player.tickCount - lastCheck < CHECK_INTERVAL) return;
        lastCheck = player.tickCount;

        // only act while actually breaking a block
        // / работаем только когда реально ломаем блок
        if (!mc.gameMode.isDestroying()) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (state.isAir()) return;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);

        // 1) durability swap / по износу
        if (held.isDamageableItem()) {
            double worn = (double) held.getDamageValue() / held.getMaxDamage();
            if (worn >= DrunskConfig.util.autoToolDurability) {
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
                if (best >= 0) {
                    if (best < 9) {
                        Utils.selectHotbar(mc, best, DrunskConfig.util.autoToolPacket);
                    } else {
                        Utils.swapSlots(mc, best, selected, DrunskConfig.util.autoToolPacket);
                    }
                    return;
                }
            }
        }

        // 2) type swap: current tool is inefficient for this block
        // / по типу: текущий инструмент неэффективен для этого блока
        float heldSpeed = held.getDestroySpeed(state);
        if (heldSpeed >= MIN_SPEED) return; // already fine / и так норм

        int bestSlot = -1;
        float bestSpeed = heldSpeed;
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            float speed = s.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return;

        if (prevSlot < 0) prevSlot = selected;
        if (bestSlot < 9) {
            Utils.selectHotbar(mc, bestSlot, DrunskConfig.util.autoToolPacket);
        } else {
            Utils.swapSlots(mc, bestSlot, selected, DrunskConfig.util.autoToolPacket);
        }
        restoreAt = player.tickCount + 40; // restore after ~2s / вернём через ~2с
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.getItem().getClass() == b.getItem().getClass();
    }
}
