package com.drunsk.relay;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side cache of relay data. All fields updated from the relay thread,
 * read from the render thread — volatile snapshots only.
 * / Клиентский кэш данных релея. Пишется из потока релея, читается из
 * render-потока — только volatile-снимки.
 */
public final class DrunskState {

    private static final DrunskState INSTANCE = new DrunskState();

    public static DrunskState get() {
        return INSTANCE;
    }

    public record Me(String userId, String mcNick, String discordName, long balance,
                     long since, boolean owner, String currency) {
    }

    public record Passport(String userId, String mcNick, String discordName, Long balance,
                           long since, boolean owner, boolean online, boolean hidden) {
    }

    public record HistoryEntry(String from, String to, long amount, String kind, String detail,
                               long at, boolean mine) {
    }

    private volatile Me me;
    private volatile List<Passport> passports = List.of();
    private volatile List<HistoryEntry> history = List.of();
    private volatile Set<String> online = Set.of();
    private volatile long lastBalanceChangedAt;

    private DrunskState() {
        RelayClient.get().onPush("presence", msg -> {
            Set<String> set = new HashSet<>();
            msg.getAsJsonArray("online").forEach(e -> set.add(e.getAsString()));
            online = Set.copyOf(set);
        });
        RelayClient.get().onPush("balance_changed", msg -> {
            lastBalanceChangedAt = System.currentTimeMillis();
            // refresh silently / тихое обновление
            refreshMe();
        });
    }

    public Me me() {
        return me;
    }

    public String myNick() {
        Me m = me;
        return m != null ? m.mcNick() : "";
    }

    public List<Passport> passports() {
        return passports;
    }

    public List<HistoryEntry> history() {
        return history;
    }

    public boolean isOnline(String nickLower) {
        return online.contains(nickLower);
    }

    public long lastBalanceChangedAt() {
        return lastBalanceChangedAt;
    }

    // --- fetchers (async, update cache) / загрузчики ------------------------

    public void refreshMe() {
        if (!RelayClient.get().isReady()) return;
        RelayClient.get().sendRequest("me", null).whenComplete((r, e) -> {
            if (e == null && r.get("ok").getAsBoolean()) me = parseMe(r);
        });
    }

    public void refreshList(Runnable done) {
        if (!RelayClient.get().isReady()) return;
        RelayClient.get().sendRequest("list", null).whenComplete((r, e) -> {
            if (e == null && r.get("ok").getAsBoolean()) {
                List<Passport> out = new ArrayList<>();
                r.getAsJsonArray("passports").forEach(el -> {
                    JsonObject o = el.getAsJsonObject();
                    out.add(new Passport(
                            o.get("userId").getAsString(),
                            o.get("mcNick").getAsString(),
                            optString(o, "discordName"),
                            o.has("balance") && !o.get("balance").isJsonNull()
                                    ? o.get("balance").getAsLong() : null,
                            o.has("since") ? o.get("since").getAsLong() : 0,
                            false,
                            o.has("online") && o.get("online").getAsBoolean(),
                            o.has("hidden") && o.get("hidden").getAsBoolean()));
                });
                passports = List.copyOf(out);
            }
            if (done != null) runOnRender(done);
        });
    }

    public void fetchPassport(String nick, java.util.function.Consumer<Passport> cb) {
        if (!RelayClient.get().isReady()) {
            runOnRender(() -> cb.accept(null));
            return;
        }
        RelayClient.get().sendRequest("passport", req -> req.addProperty("mcNick", nick))
                .whenComplete((r, e) -> runOnRender(() -> {
                    if (e != null || !r.get("ok").getAsBoolean()) {
                        cb.accept(null);
                        return;
                    }
                    cb.accept(new Passport(
                            r.get("userId").getAsString(),
                            r.get("mcNick").getAsString(),
                            optString(r, "discordName"),
                            r.has("balance") && !r.get("balance").isJsonNull()
                                    ? r.get("balance").getAsLong() : null,
                            r.has("since") ? r.get("since").getAsLong() : 0,
                            r.has("owner") && r.get("owner").getAsBoolean(),
                            r.has("online") && r.get("online").getAsBoolean(),
                            false));
                }));
    }

    public void refreshHistory() {
        if (!RelayClient.get().isReady()) return;
        RelayClient.get().sendRequest("history", req -> req.addProperty("limit", 30))
                .whenComplete((r, e) -> {
                    if (e == null && r.get("ok").getAsBoolean()) {
                        List<HistoryEntry> out = new ArrayList<>();
                        r.getAsJsonArray("history").forEach(el -> {
                            JsonObject o = el.getAsJsonObject();
                            out.add(new HistoryEntry(
                                    o.get("from").getAsString(), o.get("to").getAsString(),
                                    o.get("amount").getAsLong(), o.get("kind").getAsString(),
                                    o.has("detail") ? o.get("detail").getAsString() : "",
                                    o.get("at").getAsLong(), o.get("mine").getAsBoolean()));
                        });
                        history = List.copyOf(out);
                    }
                });
    }

    private static Me parseMe(JsonObject o) {
        return new Me(
                o.get("userId").getAsString(),
                o.get("mcNick").getAsString(),
                optString(o, "discordName"),
                o.get("balance").getAsLong(),
                o.has("since") ? o.get("since").getAsLong() : 0,
                o.has("owner") && o.get("owner").getAsBoolean(),
                o.has("currency") ? o.get("currency").getAsString() : "чекушек");
    }

    private static String optString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    public static void runOnRender(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            r.run();
        } else {
            mc.execute(r);
        }
    }
}
