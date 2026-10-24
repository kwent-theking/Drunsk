package com.drunsk;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import com.drunsk.ui.DmScreen;
import com.drunsk.ui.DrunskHubScreen;
import com.drunsk.ui.PairScreen;
import com.drunsk.ui.PassportScreen;
import com.drunsk.ui.DrunskYaclScreen;
import com.drunsk.ui.UtilsScreen;
import com.drunsk.utils.BedrockMiner;
import com.drunsk.utils.Utils;
import com.drunsk.utils.freecam.Freecam;
import com.drunsk.update.DrunskUpdateChecker;
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
    private static boolean debugEnabled() {
        return DrunskConfig.util.debug || Boolean.getBoolean("drunsk.debug");
    }

    private static void notifyVisualOnly(Minecraft client) {
        if (client.player != null) client.player.sendOverlayMessage(Component.translatable("drunsk.profile.visual_only_blocked"));
    }

    private static KeyMapping passportKey;
    private static KeyMapping utilsKey;
    private static KeyMapping freecamKey;
    private static KeyMapping minerKey;
    private static KeyMapping fishKey;
    private static KeyMapping totemKey;
    private static KeyMapping gammaKey;
    private static KeyMapping eatKey;
    private static KeyMapping toolKey;
    private static KeyMapping blockKey;

    /** BedrockMiner instance, created per-player on JOIN. / Экземпляр бедрок-майнера, создаётся на JOIN. */
    public static BedrockMiner bedrockMiner;

    @Override
    public void onInitializeClient() {
        DrunskConfig.load();
        if (!DrunskConfig.util.autoUpdatePromptShown) {
            LOGGER.info("[Drunsk] auto-update is disabled until the user enables it in Utilities (one-time prompt pending)");
            DrunskConfig.util.autoUpdatePromptShown = true;
            DrunskConfig.save();
        }
        DrunskUpdateChecker.checkAsync();

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
        fishKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.fish", InputConstants.Type.KEYSYM, 0, category));
        totemKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.totem", InputConstants.Type.KEYSYM, 0, category));
        gammaKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.gamma", InputConstants.Type.KEYSYM, 0, category));
        eatKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.eat", InputConstants.Type.KEYSYM, 0, category));
        toolKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.tool", InputConstants.Type.KEYSYM, 0, category));
        blockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.drunsk.block", InputConstants.Type.KEYSYM, 0, category));

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
            // create bedrock miner instance per join / создаём экземпляр на каждый JOIN
            if (client.player != null) {
                bedrockMiner = new BedrockMiner(client.player);
                debug("join player={} dimension={} position={}", client.player.getGameProfile().name(),
                        client.level == null ? "null" : client.level.dimension().identifier(), client.player.blockPosition());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RelayClient.get().disconnect();
            Freecam.onDisconnect();
            if (bedrockMiner != null) bedrockMiner.reset();
            bedrockMiner = null;
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
                if (client.level != null) {
                    if (DrunskConfig.util.externalFreecamPreferred &&
                            (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("freecam")
                                    || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("phantomlib")
                                    || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("freecammod"))) {
                        LOGGER.warn("[Drunsk] external Freecam is installed; Drunsk Freecam toggle skipped");
                        client.player.sendOverlayMessage(Component.translatable("drunsk.utils.freecam.external_detected"));
                    } else {
                        Freecam.toggle();
                    }
                }
            }
            while (minerKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                if (bedrockMiner != null && client.player != null) {
                    if (!DrunskConfig.util.bedrockMiner) {
                        client.player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.disabled_hint"));
                    } else {
                        // toggle armed state / взвести/снять
                        if (bedrockMiner.isArmed()) {
                            bedrockMiner.reset();
                            client.player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.disarmed"));
                        } else {
                            bedrockMiner.arm();
                            client.player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.armed"));
                        }
                    }
                }
            }
            while (fishKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                DrunskConfig.util.autoFish = !DrunskConfig.util.autoFish;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.autoFish")
                                .append(": ").append(DrunskConfig.util.autoFish ? "ON" : "OFF"));
            }
            while (totemKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                DrunskConfig.util.autoTotem = !DrunskConfig.util.autoTotem;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.autoTotem")
                                .append(": ").append(DrunskConfig.util.autoTotem ? "ON" : "OFF"));
            }
            while (gammaKey.consumeClick()) {
                DrunskConfig.util.gamma = !DrunskConfig.util.gamma;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.gamma")
                                .append(": ").append(DrunskConfig.util.gamma ? "ON" : "OFF"));
            }
            while (eatKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                DrunskConfig.util.autoEat = !DrunskConfig.util.autoEat;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.autoEat")
                                .append(": ").append(DrunskConfig.util.autoEat ? "ON" : "OFF"));
            }
            while (toolKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                DrunskConfig.util.autoTool = !DrunskConfig.util.autoTool;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.autoTool")
                                .append(": ").append(DrunskConfig.util.autoTool ? "ON" : "OFF"));
            }
            while (blockKey.consumeClick()) {
                if (!DrunskConfig.util.automationAllowed()) { notifyVisualOnly(client); continue; }
                DrunskConfig.util.autoBlock = !DrunskConfig.util.autoBlock;
                DrunskConfig.save();
                if (client.player != null) client.player.sendOverlayMessage(
                        Component.translatable("drunsk.utils.autoBlock")
                                .append(": ").append(DrunskConfig.util.autoBlock ? "ON" : "OFF"));
            }
            Utils.tick(client);
            if (bedrockMiner != null) bedrockMiner.tick();
            if (debugEnabled() && client.player != null && client.player.tickCount % 100 == 0) {
                debug("heartbeat tick={} player={} pos={} screen={} minerEnabled={} minerArmed={} minerRunning={} modes=[fish:{},totem:{},eat:{},tool:{},block:{}]",
                        client.player.tickCount, client.player.getGameProfile().name(), client.player.blockPosition(),
                        client.gui.screen(), DrunskConfig.util.bedrockMiner,
                        bedrockMiner != null && bedrockMiner.isArmed(),
                        bedrockMiner != null && bedrockMiner.isRunning(),
                        DrunskConfig.util.autoFishPacket, DrunskConfig.util.autoTotemPacket,
                        DrunskConfig.util.autoEatPacket, DrunskConfig.util.autoToolPacket,
                        DrunskConfig.util.autoBlockPacket);
            }
            // periodic position update for admin panel (every 5s)
            // / периодические координаты для админ-панели (раз в 5с)
            if (client.player != null && client.level != null
                    && client.player.tickCount % 100 == 0) {
                var bp = client.player.blockPosition();
                RelayClient.get().sendPositionUpdate(
                        bp.getX(), bp.getY(), bp.getZ(),
                        client.level.dimension().identifier().toString());
            }
        });

        LOGGER.info("[Drunsk] успешно хукнуто к моду \"freecam\" (fake-entity, 14 mixins)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"bedrock-miner\" (piston method)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"auto-fish\" (DATA_BITING)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"auto-totem\" (SWAP-40)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"gamma\" (full-bright)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"auto-eat\" (use-item)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"auto-tool\" (destroy-speed)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"auto-block\" (stack-swap)");
        LOGGER.info("[Drunsk] успешно хукнуто к моду \"dm\" (relay queue)");
    }

    private static void debug(String message, Object... args) {
        if (debugEnabled()) LOGGER.info(message, args);
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
        try {
            // YACL screen if available (bundled), fallback to built-in
            // / YACL-скрин если доступен (встроен), иначе встроенный
            client.gui.setScreen(DrunskYaclScreen.create(null));
        } catch (Throwable t) {
            client.gui.setScreen(new UtilsScreen());
        }
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
