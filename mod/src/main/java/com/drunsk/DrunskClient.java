package com.drunsk;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import com.drunsk.ui.DrunskHubScreen;
import com.drunsk.ui.PairScreen;
import com.drunsk.ui.PassportScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
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

    @Override
    public void onInitializeClient() {
        DrunskConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        passportKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.drunsk.passport", InputConstants.Type.KEYSYM, InputConstants.KEY_P, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(ClientCommandManager.literal("drunsk")
                    .executes(ctx -> { openHub(); return 1; })
                    .then(ClientCommandManager.literal("pair").executes(ctx -> { startPairing(); return 1; }))
                    .then(ClientCommandManager.argument("nick", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> {
                                String nick = com.mojang.brigadier.arguments.StringArgumentType
                                        .getString(ctx, "nick");
                                openPassportOf(nick);
                                return 1;
                            })));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // GameProfile — record в 1.21.11: name(), а не getName().
            String nick = client.player == null ? null : client.player.getGameProfile().name();
            if (nick != null) {
                RelayClient.get().connect(nick);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RelayClient.get().disconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            DrunskConfig.save();
            RelayClient.get().shutdown();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (passportKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    onPress(client);
                }
            }
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
                client.player.displayClientMessage(
                        Component.translatable("drunsk.msg.connecting"), true);
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

    private static void openHub() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new DrunskHubScreen(null));
    }

    private static void openPassportOf(String nick) {
        Minecraft client = Minecraft.getInstance();
        DrunskState state = DrunskState.get();
        if (nick.equals(state.myNick())) {
            client.setScreen(new PassportScreen(null));
            return;
        }
        client.setScreen(new PassportScreen(nick));
    }

    private static void startPairing() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new PairScreen());
    }
}
