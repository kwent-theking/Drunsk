package com.drunsk.relay;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public record DmEntry(String from, String to, String text, long at) {
    }

    public record ChatEntry(String from, String text, long at) {
    }

    private volatile Me me;
    private volatile List<Passport> passports = List.of();
    private volatile List<HistoryEntry> history = List.of();
    private volatile Set<String> online = Set.of();
    private volatile long lastBalanceChangedAt;
    private volatile List<ChatEntry> chatThread = List.of();
    /** peer nick (lowercase) -> thread, oldest first / ник собеседника -> тред, старые первыми */
    private final Map<String, List<DmEntry>> dmThreads = new ConcurrentHashMap<>();
    /** unread counters by peer / счётчики непрочитанных по собеседникам */
    private final Map<String, Integer> unread = new ConcurrentHashMap<>();
    /** outgoing DMs queued while the relay is unreachable / исходящие ЛС в очереди, пока релей недоступен */
    private final java.util.Queue<DmOutbox> dmOutbox = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private record DmOutbox(String to, String text) {
    }

    private DrunskState() {
        RelayClient.get().onOnline(this::flushDmOutbox);
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
        RelayClient.get().onPush("dm", msg -> {
            DmEntry e = parseDm(msg);
            appendDm(e);
            String meNick = myNick();
            if (!e.from().equalsIgnoreCase(meNick)) {
                unread.merge(e.from().toLowerCase(), 1, Integer::sum);
                if (Minecraft.getInstance().player != null) {
                    runOnRender(() -> Minecraft.getInstance().player.sendOverlayMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "drunsk.dm.notify", e.from(), e.text())));
                }
            }
        });
        RelayClient.get().onPush("chat", msg -> {
            ChatEntry e = new ChatEntry(msg.get("from").getAsString(),
                    msg.get("text").getAsString(), msg.get("at").getAsLong());
            List<ChatEntry> thread = new ArrayList<>(chatThread);
            thread.add(e);
            if (thread.size() > 50) thread = new ArrayList<>(thread.subList(thread.size() - 50, thread.size()));
            chatThread = List.copyOf(thread);
            String meNick = myNick();
            if (!e.from().equalsIgnoreCase(meNick) && Minecraft.getInstance().player != null) {
                runOnRender(() -> Minecraft.getInstance().player.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "drunsk.chat.notify", e.from(), e.text())));
            }
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

    // --- DM / ЛС -------------------------------------------------------------

    public List<DmEntry> dmThread(String peer) {
        return dmThreads.getOrDefault(peer.toLowerCase(), List.of());
    }

    public int unreadDm(String peer) {
        return unread.getOrDefault(peer.toLowerCase(), 0);
    }

    public void markDmRead(String peer) {
        unread.remove(peer.toLowerCase());
    }

    public void fetchDmHistory(String peer, java.util.function.Consumer<Boolean> done) {
        if (!RelayClient.get().isReady()) {
            runOnRender(() -> done.accept(false));
            return;
        }
        RelayClient.get().sendRequest("dm_history", req -> req.addProperty("peer", peer))
                .whenComplete((r, e) -> runOnRender(() -> {
                    if (e != null || r == null || !r.get("ok").getAsBoolean()) {
                        done.accept(false);
                        return;
                    }
                    List<DmEntry> out = new ArrayList<>();
                    r.getAsJsonArray("messages").forEach(el -> out.add(parseDm(el.getAsJsonObject())));
                    dmThreads.put(peer.toLowerCase(), List.copyOf(out));
                    done.accept(true);
                }));
    }

    public void sendDm(String peer, String text, java.util.function.Consumer<Boolean> done) {
        if (!RelayClient.get().isReady()) {
            // relay unreachable: queue the message and deliver it when the
            // socket comes back online / релей недоступен: кладём в очередь,
            // доставим когда сокет вернётся в ONLINE
            dmOutbox.add(new DmOutbox(peer, text));
            runOnRender(() -> done.accept(true)); // pretend sent / делаем вид что отправлено
            return;
        }
        RelayClient.get().sendRequest("dm_send", req -> {
            req.addProperty("to", peer);
            req.addProperty("text", text);
        }).whenComplete((r, e) -> runOnRender(() -> {
            boolean ok = e == null && r != null && r.get("ok").getAsBoolean();
            if (!ok && (e != null || (r != null && "offline".equals(r.has("reason") ? r.get("reason").getAsString() : "")))) {
                // transient failure — queue for retry / временный сбой — в очередь
                dmOutbox.add(new DmOutbox(peer, text));
                done.accept(true);
            } else {
                done.accept(ok);
            }
        }));
    }

    /** Send everything queued while the relay was down. / Отправляем всё, что скопилось, пока релей лежал. */
    private void flushDmOutbox() {
        DmOutbox item;
        while ((item = dmOutbox.poll()) != null) {
            final DmOutbox queued = item;
            RelayClient.get().sendRequest("dm_send", req -> {
                req.addProperty("to", queued.to());
                req.addProperty("text", queued.text());
            });
        }
    }

    // --- global chat / общий чат ---------------------------------------------

    public List<ChatEntry> chatThread() {
        return chatThread;
    }

    public void fetchChatHistory(java.util.function.Consumer<Boolean> done) {
        if (!RelayClient.get().isReady()) {
            runOnRender(() -> done.accept(false));
            return;
        }
        RelayClient.get().sendRequest("chat_history", null)
                .whenComplete((r, e) -> runOnRender(() -> {
                    if (e != null || r == null || !r.get("ok").getAsBoolean()) {
                        done.accept(false);
                        return;
                    }
                    List<ChatEntry> out = new ArrayList<>();
                    r.getAsJsonArray("messages").forEach(el -> {
                        JsonObject o = el.getAsJsonObject();
                        out.add(new ChatEntry(o.get("from").getAsString(),
                                o.get("text").getAsString(), o.get("at").getAsLong()));
                    });
                    chatThread = List.copyOf(out);
                    done.accept(true);
                }));
    }

    public void sendChat(String text, java.util.function.Consumer<Boolean> done) {
        if (!RelayClient.get().isReady()) {
            runOnRender(() -> done.accept(false));
            return;
        }
        RelayClient.get().sendRequest("chat_send", req -> req.addProperty("text", text))
                .whenComplete((r, e) -> runOnRender(() -> {
                    boolean ok = e == null && r != null && r.get("ok").getAsBoolean();
                    done.accept(ok);
                }));
    }

    private static DmEntry parseDm(JsonObject o) {
        return new DmEntry(o.get("from").getAsString(), o.get("to").getAsString(),
                o.get("text").getAsString(), o.get("at").getAsLong());
    }

    private void appendDm(DmEntry e) {
        String meNick = myNick();
        String peer = e.from().equalsIgnoreCase(meNick) ? e.to() : e.from();
        String key = peer.toLowerCase();
        List<DmEntry> thread = new ArrayList<>(dmThreads.getOrDefault(key, List.of()));
        thread.add(e);
        if (thread.size() > 100) thread = new ArrayList<>(thread.subList(thread.size() - 100, thread.size()));
        dmThreads.put(key, List.copyOf(thread));
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
