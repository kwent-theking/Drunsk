package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

/**
 * Auto-totem: keeps a totem in the offhand while HP is low. Swap goes through
 * the inventory menu (vanilla: MultiPlayerGameMode click, packet: raw
 * ServerboundContainerClickPacket — see Utils.swapWithOffhand).
 * / Автототем: держит тотем в оффхенде при низком HP. Своп через меню
 * инвентаря (ваниль: клик MultiPlayerGameMode, пакеты: сырой контейнер-пакет).
 */
public final class AutoTotem {

    private static int cooldownUntil;

    private AutoTotem() {
    }

    public static void tick(Minecraft mc) {
        if (!DrunskConfig.util.autoTotem) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        if (player.tickCount < cooldownUntil) return;

        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        if (player.getHealth() > DrunskConfig.util.autoTotemHealth && !player.isCreative()) return;

        int slot = Utils.findSlot(player, s -> s.is(Items.TOTEM_OF_UNDYING));
        if (slot < 0) return;
        Utils.swapWithOffhand(mc, slot, DrunskConfig.util.autoTotemPacket);
        cooldownUntil = player.tickCount + 4;
    }
}
