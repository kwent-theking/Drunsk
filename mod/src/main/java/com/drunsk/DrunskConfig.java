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
        } catch (Exception e) {
            DrunskClient.LOGGER.warn("config load failed, using defaults", e);
        }
    }

    public static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("relayUrl", relayUrl);
            JsonObject t = new JsonObject();
            tokens.forEach(t::addProperty);
            root.add("tokens", t);
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
