package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import com.drunsk.mixin.ClientLevelPredictionAccessor;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Shared helpers for the utility modules: hotbar selection, inventory swap
 * clicks in vanilla and packet flavours, prediction sequences.
 * / Общие хелперы утилит: выбор слота хотбара, SWAP-клики инвентаря в
 * ванильном и пакетном вариантах, sequence для предсказаний.
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Per-tick dispatcher for all utility modules (END_CLIENT_TICK). Modules
     * are cheap idle checks; allocations happen only when one actually fires.
     * / Тиковый диспетчер утилит (END_CLIENT_TICK). В простое модули дёшевы,
     * аллокации — только в момент срабатывания.
     */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        AutoFish.tick(mc);
        AutoTotem.tick(mc);
        AutoEat.tick(mc);
        AutoTool.tick(mc);
        AutoBlock.tick(mc);
    }

    /** Drop transient module state on disconnect. / Сброс состояния модулей при дисконнекте. */
    public static void reset() {
        AutoFish.reset();
        AutoEat.reset();
    }

    public static boolean fullBrightActive() {
        return DrunskConfig.util.gamma
                || (Freecam.isEnabled() && DrunskConfig.util.freecamFullbright);
    }

    /** Inventory slot (0..35 hotbar+main, 40 offhand) → InventoryMenu slot. / Слот инвентаря → слот InventoryMenu. */
    public static int toMenuSlot(int invSlot) {
        if (invSlot < 9) return 36 + invSlot;          // hotbar
        if (invSlot < 36) return invSlot;              // main inventory
        return invSlot == Inventory.SLOT_OFFHAND ? 45 : -1;
    }

    /** Find the first inventory slot (0..35) matching the predicate, or -1. / Первый слот 0..35 под условие, иначе -1. */
    public static int findSlot(LocalPlayer player, java.util.function.Predicate<ItemStack> test) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (test.test(inv.getItem(i))) return i;
        }
        return -1;
    }

    /**
     * Swap an inventory slot with the offhand. Vanilla mode goes through
     * MultiPlayerGameMode; packet mode replicates the click and sends the
     * raw ServerboundContainerClickPacket (with the same snapshot/diff the
     * vanilla code builds).
     * / Своп слота с оффхендом. Ванильный режим — через MultiPlayerGameMode,
     * пакетный — тот же клик, но сырым ServerboundContainerClickPacket
     * (снимок/дифф как в ванильном коде).
     */
    public static void swapWithOffhand(Minecraft mc, int invSlot, boolean packetMode) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        int menuSlot = toMenuSlot(invSlot);
        if (menuSlot < 0) return;
        if (!packetMode) {
            mc.gameMode.handleContainerInput(player.containerMenu.containerId, menuSlot, 40,
                    ContainerInput.SWAP, player);
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        int n = menu.slots.size();
        ItemStack[] before = new ItemStack[n];
        for (int i = 0; i < n; i++) before[i] = menu.slots.get(i).getItem().copy();
        menu.clicked(menuSlot, 40, ContainerInput.SWAP, player);
        HashedPatchMap.HashGenerator gen = player.connection.decoratedHashOpsGenenerator();
        Int2ObjectOpenHashMap<HashedStack> changed = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack after = menu.slots.get(i).getItem();
            if (!ItemStack.matches(before[i], after)) {
                changed.put(i, HashedStack.create(after, gen));
            }
        }
        player.connection.send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) menuSlot, (byte) 40, ContainerInput.SWAP, changed,
                HashedStack.create(menu.getCarried(), gen)));
    }

    /**
     * Swap two inventory slots (both 0..35) through the open container menu.
     * Vanilla: MultiPlayerGameMode.handleContainerInput; packet: raw click.
     * / Своп двух слотов инвентаря (0..35) через открытое меню. Ваниль —
     * handleContainerInput, пакеты — сырой клик.
     */
    public static void swapSlots(Minecraft mc, int slotA, int slotB, boolean packetMode) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        // SWAP button semantics: hotbar index 0..8 (or 40 = offhand)
        // / семантика SWAP-button: индекс хотбара 0..8 (или 40 = оффхенд)
        if (slotA < 0 || slotA > 35 || slotB < 0 || slotB > 8) return;
        int menuA = toMenuSlot(slotA);
        AbstractContainerMenu menu = player.containerMenu;
        if (!packetMode) {
            mc.gameMode.handleContainerInput(menu.containerId, menuA, slotB, ContainerInput.SWAP, player);
            return;
        }
        int n = menu.slots.size();
        ItemStack[] before = new ItemStack[n];
        for (int i = 0; i < n; i++) before[i] = menu.slots.get(i).getItem().copy();
        menu.clicked(menuA, slotB, ContainerInput.SWAP, player);
        HashedPatchMap.HashGenerator gen = player.connection.decoratedHashOpsGenenerator();
        Int2ObjectOpenHashMap<HashedStack> changed = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack after = menu.slots.get(i).getItem();
            if (!ItemStack.matches(before[i], after)) {
                changed.put(i, HashedStack.create(after, gen));
            }
        }
        player.connection.send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) menuA, (byte) slotB, ContainerInput.SWAP, changed,
                HashedStack.create(menu.getCarried(), gen)));
    }

    /** Select a hotbar slot and notify the server in packet mode. / Выбирает слот хотбара, в пакетном режиме шлёт пакет. */
    public static void selectHotbar(Minecraft mc, int hotbarSlot, boolean packetMode) {
        LocalPlayer player = mc.player;
        if (player == null || hotbarSlot < 0 || hotbarSlot > 8) return;
        player.getInventory().setSelectedSlot(hotbarSlot);
        if (packetMode && player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }
        // vanilla mode: MultiPlayerGameMode.useItem*/destroy send the carried
        // packet themselves (ensureHasSentCarriedItem) / ванильный режим:
        // useItem*/destroy сами шлют пакет выбора (ensureHasSentCarriedItem)
    }

    /**
     * Sequence for raw interaction packets: start a block-state prediction and
     * return the current sequence. Caller sends the packet and closes the
     * handler. / Sequence для сырых пакетов: начинаем предсказание, возвращаем
     * текущий sequence; вызывающий шлёт пакет и закрывает handler.
     */
    public static BlockStatePredictionHandler startPrediction(Minecraft mc) {
        BlockStatePredictionHandler h =
                ((ClientLevelPredictionAccessor) mc.level).drunsk$getPredictionHandler();
        h.startPredicting();
        return h;
    }

    public static void sendWithSequence(Minecraft mc, java.util.function.IntFunction<Packet<?>> packet) {
        if (mc.level == null || mc.player == null) return;
        BlockStatePredictionHandler h = startPrediction(mc);
        try {
            mc.player.connection.send(packet.apply(h.currentSequence()));
        } finally {
            h.close();
        }
    }
}
