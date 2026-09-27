package com.drunsk;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import com.drunsk.ui.DmScreen;
import com.drunsk.ui.DrunskHubScreen;
import com.drunsk.ui.PairScreen;
import com.drunsk.ui.PassportScreen;
import com.drunsk.ui.UtilsScreen;
import com.drunsk.utils.BedrockMiner;
import com.drunsk.utils.Utils;
import com.drunsk.utils.freecam.Freecam;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drunsk client mod entry. / Точка входа клиентского мода Drunsk.
 *
 * <p>Key P: look at a player to open their passport, otherwise your own hub.
 * / Клавиша P: смотришь на игрока — его паспорт, иначе своё меню.
 */
public final class DrunskClient implements ClientModInitializer {

    public static final String MOD_ID = "drunsk";
    public static final Logger LOGGER = LoggerFactory.getLogger("drunsk");

    private static KeyMapping passportKey;
    private static KeyMapping utilsKey;
    private static KeyMapping freecamKey;
    private static KeyMapping minerKey;

    @Override
    public void onInitializeClient() {
        DrunskConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        passportKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.passport", InputConstants.Type.KEYSYM, InputConstants.KEY_P, category));
        utilsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.utils", InputConstants.Type.KEYSYM, InputConstants.KEY_U, category));
        freecamKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.freecam", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));
        minerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.miner", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(ClientCommands.literal("drunsk")
                    .executes(ctx -> { openHub(); return 1; })
                    .then(ClientCommands.literal("pair").executes(ctx -> { startPairing(); return 1; }))
                    .then(ClientCommands.literal("utils").executes(ctx -> { openUtils(); return 1; }))
                    .then(ClientCommands.literal("dm")
                            .then(ClientCommands.argument("nick", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .executes(ctx -> {
                                        openDm(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nick"));
                                        return 1;
                                    })))
                    .then(ClientCommands.argument("nick", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> {
                                openPassportOf(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "nick"));
                                return 1;
                            })));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // GameProfile — record: name(), не getName().
            String nick = client.player == null ? null : client.player.getGameProfile().name();
            if (nick != null) {
                RelayClient.get().connect(nick);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RelayClient.get().disconnect();
            Freecam.onDisconnect();
            BedrockMiner.reset();
            Utils.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            DrunskConfig.save();
            RelayClient.get().shutdown();
        });

        // Freecam input suppression must run before the player ticks.
        // / Подавление ввода фрикама — до тика игрока.
        ClientTickEvents.START_CLIENT_TICK.register(Freecam::preTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (passportKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    onPress(client);
                }
            }
            while (utilsKey.consumeClick()) {
                openUtils();
            }
            while (freecamKey.consumeClick()) {
                if (client.level != null) Freecam.toggle();
            }
            while (minerKey.consumeClick()) {
                if (client.player != null) BedrockMiner.toggleArmed(client.player);
            }
            Utils.tick(client);
            BedrockMiner.tick(client);
        });
    }

    private static void onPress(Minecraft client) {
        if (!RelayClient.get().isReady()) {
            // not paired yet or offline / ещё не привязан или офлайн
            String nick = client.player.getGameProfile().name();
            if (!RelayClient.get().hasToken(nick)) {
                startPairing();
            } else {
                RelayClient.get().connect(nick);
                client.player.sendOverlayMessage(
                        Component.translatable("drunsk.msg.connecting"));
            }
            return;
        }
        // look-at player → their passport, else hub / смотрим на игрока → его паспорт
        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player target
                && target != client.player) {
            openPassportOf(target.getGameProfile().name());
        } else {
            openHub();
        }
    }

    public static void openHub() {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new DrunskHubScreen());
    }

    public static void openUtils() {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new UtilsScreen());
    }

    public static void openDm(String nick) {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new DmScreen(nick));
    }

    public static void openPassportOf(String nick) {
        Minecraft client = Minecraft.getInstance();
        DrunskState state = DrunskState.get();
        if (nick.equals(state.myNick())) {
            client.gui.setScreen(new PassportScreen(null));
            return;
        }
        client.gui.setScreen(new PassportScreen(nick));
    }

    public static void startPairing() {
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(new PairScreen());
    }
}
