package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

/**
 * Auto-eat: below the food threshold, picks food into hand and eats until
 * restored. If food is already selected — eat immediately; if in hotbar —
 * select it; if in main inventory — swap into the current hotbar slot.
 * / Автоеда: ниже порога еды берёт еду в руку и ест до восстановления.
 * Если еда уже выбрана — ест сразу; в хотбаре — выбирает слот; в основном
 * инвентаре — свапает в текущий слот хотбара (без переключения).
 */
public final class AutoEat {

    private static final int CHECK_INTERVAL = 5;

    private static boolean eating;
    private static InteractionHand eatHand;
    private static int prevSlot = -1;
    private static int lastCheck;

    private AutoEat() {
    }

    public static void reset() {
        stopEating(null);
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            stopEating(mc);
            return;
        }
        if (!DrunskConfig.util.autoEat || player.isCreative() || player.isSpectator()) {
            stopEating(mc);
            return;
        }

        if (eating) {
            if (player.getFoodData().getFoodLevel() >= 20 || !player.isUsingItem()) {
                stopEating(mc);
            }
            return;
        }

        if (player.tickCount - lastCheck < CHECK_INTERVAL) return;
        lastCheck = player.tickCount;

        if (player.getFoodData().getFoodLevel() >= DrunskConfig.util.autoEatFood) return;
        if (player.isUsingItem()) return;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);

        // food already in hand — eat immediately, no switching
        // / еда уже в руке — ест сразу, без переключения
        if (isEdible(player, held)) {
            eatHand = InteractionHand.MAIN_HAND;
            startUse(mc, player);
            eating = true;
            return;
        }

        int slot = bestFoodSlot(player);
        if (slot < 0) return;

        if (slot < 9) {
            // food in hotbar — select that slot
            // / еда в хотбаре — выбираем этот слот
            prevSlot = selected;
            Utils.selectHotbar(mc, slot, DrunskConfig.util.autoEatPacket);
        } else {
            // food in main inventory — swap into the CURRENT hotbar slot
            // / еда в основном инвентаре — свапаем в ТЕКУЩИЙ слот хотбара
            prevSlot = selected;
            Utils.swapSlots(mc, slot, selected, DrunskConfig.util.autoEatPacket);
        }

        eatHand = InteractionHand.MAIN_HAND;
        startUse(mc, player);
        eating = true;
    }

    private static void startUse(Minecraft mc, LocalPlayer player) {
        if (DrunskConfig.util.autoEatPacket) {
            final float yRot = player.getYRot();
            final float xRot = player.getXRot();
            Utils.sendWithSequence(mc, seq -> new ServerboundUseItemPacket(eatHand, seq, yRot, xRot));
            player.startUsingItem(eatHand);
        } else {
            mc.gameMode.useItem(player, eatHand);
        }
    }

    private static void stopEating(Minecraft mc) {
        if (!eating) return;
        eating = false;
        eatHand = null;
        if (mc == null) return;
        LocalPlayer player = mc.player;
        if (player != null) {
            if (player.isUsingItem()) {
                if (DrunskConfig.util.autoEatPacket && player.connection != null) {
                    player.connection.send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                            net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
                    player.releaseUsingItem();
                } else if (mc.gameMode != null) {
                    mc.gameMode.releaseUsingItem(player);
                }
            }
            if (prevSlot >= 0 && prevSlot != player.getInventory().getSelectedSlot()) {
                Utils.selectHotbar(mc, prevSlot, DrunskConfig.util.autoEatPacket);
            }
            prevSlot = -1;
        }
    }

    private static boolean isEdible(LocalPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return false;
        if (!food.canAlwaysEat() && player.getFoodData().getFoodLevel() + food.nutrition() > 20) return false;
        return true;
    }

    /** Hotbar-first search for the most nutritious food. / Ищем самую питательную еду, хотбар в приоритете. */
    private static int bestFoodSlot(LocalPlayer player) {
        int best = -1;
        int bestNutrition = -1;
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? 0 : 9;
            int to = pass == 0 ? 9 : 36;
            for (int i = from; i < to; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food == null) continue;
                if (!food.canAlwaysEat() && player.getFoodData().getFoodLevel() + food.nutrition() > 20) continue;
                if (food.nutrition() > bestNutrition) {
                    bestNutrition = food.nutrition();
                    best = i;
                }
            }
            if (best >= 0) return best;
        }
        return best;
    }
}
