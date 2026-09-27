package com.drunsk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent config: relay URL and per-nick pairing tokens.
 * / Постоянный конфиг: адрес релея и токены привязки по никам.
 */
public final class DrunskConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("drunsk.json");

    public static String relayUrl = "wss://xn--d1amilgk.online/drunsk";
    /** mc nick (lowercase) -> pairing token / ник (нижний регистр) -> токен привязки */
    public static final Map<String, String> tokens = new HashMap<>();

    /** Utility-module settings, persisted under the "utils" object. / Настройки утилит, живут в объекте "utils". */
    public static final Util util = new Util();

    /**
     * Per-module utility settings. `*Packet` selects the packet mode over the
     * vanilla (MultiPlayerGameMode) mode for modules that have both.
     * / Настройки модулей. `*Packet` выбирает пакетный режим вместо ванильного
     * (через MultiPlayerGameMode) там, где он есть.
     */
    public static final class Util {
        // toggles / переключатели
        public boolean autoFish, autoTotem, gamma, freecam, bedrockMiner, autoEat, autoTool, autoBlock;
        // packet vs vanilla per module / пакетный против ванильного по модулям
        // (bedrock miner is packet-only, freecam has no mode toggle)
        public boolean autoFishPacket = true, autoTotemPacket = true,
                autoEatPacket = true, autoToolPacket = true, autoBlockPacket = true;
        // freecam tuning / настройка фрикама
        public double freecamSpeedH = 1.0, freecamSpeedV = 0.8;
        public boolean freecamFreeze, freecamDisableOnDamage = true, freecamShowPlayer = true,
                freecamHideHand = true, freecamFullbright = true;
        // thresholds / пороги
        public double autoTotemHealth = 8.0;   // health points (hearts*2) / очки здоровья (сердца*2)
        public int autoEatFood = 14;           // food level to start eating / уровень еды для начала
        public double autoToolDurability = 0.08; // swap below this damage fraction / менять ниже этой доли износа
    }

    private DrunskConfig() {
    }

    public static synchronized void load() {
        try {
            if (!Files.exists(FILE)) {
                save();
                return;
            }
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            if (root.has("relayUrl") && root.get("relayUrl").getAsString().startsWith("wss://")) {
                relayUrl = root.get("relayUrl").getAsString();
            }
            if (root.has("tokens") && root.get("tokens").isJsonObject()) {
                for (var entry : root.getAsJsonObject("tokens").entrySet()) {
                    tokens.put(entry.getKey().toLowerCase(), entry.getValue().getAsString());
                }
            }
            if (root.has("utils") && root.get("utils").isJsonObject()) {
                readUtil(root.getAsJsonObject("utils"));
            }
        } catch (Exception e) {
            DrunskClient.LOGGER.warn("config load failed, using defaults", e);
        }
    }

    private static boolean optBool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    private static double optDouble(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    private static int optInt(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static void readUtil(JsonObject u) {
        util.autoFish = optBool(u, "autoFish", false);
        util.autoTotem = optBool(u, "autoTotem", false);
        util.gamma = optBool(u, "gamma", false);
        util.freecam = optBool(u, "freecam", false);
        util.bedrockMiner = optBool(u, "bedrockMiner", false);
        util.autoEat = optBool(u, "autoEat", false);
        util.autoTool = optBool(u, "autoTool", false);
        util.autoBlock = optBool(u, "autoBlock", false);
        util.autoFishPacket = optBool(u, "autoFishPacket", true);
        util.autoTotemPacket = optBool(u, "autoTotemPacket", true);
        util.autoEatPacket = optBool(u, "autoEatPacket", true);
        util.autoToolPacket = optBool(u, "autoToolPacket", true);
        util.autoBlockPacket = optBool(u, "autoBlockPacket", true);
        util.freecamSpeedH = optDouble(u, "freecamSpeedH", 1.0);
        util.freecamSpeedV = optDouble(u, "freecamSpeedV", 0.8);
        util.freecamFreeze = optBool(u, "freecamFreeze", false);
        util.freecamDisableOnDamage = optBool(u, "freecamDisableOnDamage", true);
        util.freecamShowPlayer = optBool(u, "freecamShowPlayer", true);
        util.freecamHideHand = optBool(u, "freecamHideHand", true);
        util.freecamFullbright = optBool(u, "freecamFullbright", true);
        util.autoTotemHealth = optDouble(u, "autoTotemHealth", 8.0);
        util.autoEatFood = optInt(u, "autoEatFood", 14);
        util.autoToolDurability = optDouble(u, "autoToolDurability", 0.08);
    }

    public static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("relayUrl", relayUrl);
            JsonObject t = new JsonObject();
            tokens.forEach(t::addProperty);
            root.add("tokens", t);
            JsonObject u = new JsonObject();
            u.addProperty("autoFish", util.autoFish);
            u.addProperty("autoTotem", util.autoTotem);
            u.addProperty("gamma", util.gamma);
            u.addProperty("freecam", util.freecam);
            u.addProperty("bedrockMiner", util.bedrockMiner);
            u.addProperty("autoEat", util.autoEat);
            u.addProperty("autoTool", util.autoTool);
            u.addProperty("autoBlock", util.autoBlock);
            u.addProperty("autoFishPacket", util.autoFishPacket);
            u.addProperty("autoTotemPacket", util.autoTotemPacket);
            u.addProperty("autoEatPacket", util.autoEatPacket);
            u.addProperty("autoToolPacket", util.autoToolPacket);
            u.addProperty("autoBlockPacket", util.autoBlockPacket);
            u.addProperty("freecamSpeedH", util.freecamSpeedH);
            u.addProperty("freecamSpeedV", util.freecamSpeedV);
            u.addProperty("freecamFreeze", util.freecamFreeze);
            u.addProperty("freecamDisableOnDamage", util.freecamDisableOnDamage);
            u.addProperty("freecamShowPlayer", util.freecamShowPlayer);
            u.addProperty("freecamHideHand", util.freecamHideHand);
            u.addProperty("freecamFullbright", util.freecamFullbright);
            u.addProperty("autoTotemHealth", util.autoTotemHealth);
            u.addProperty("autoEatFood", util.autoEatFood);
            u.addProperty("autoToolDurability", util.autoToolDurability);
            root.add("utils", u);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            DrunskClient.LOGGER.warn("config save failed", e);
        }
    }

    public static String tokenFor(String nick) {
        return tokens.get(nick.toLowerCase());
    }

    public static void setToken(String nick, String token) {
        tokens.put(nick.toLowerCase(), token);
        save();
    }
}
