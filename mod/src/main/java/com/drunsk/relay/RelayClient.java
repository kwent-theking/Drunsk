package com.drunsk.relay;

import com.drunsk.DrunskClient;
import com.drunsk.DrunskConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * WebSocket client for the Drunsk relay: auto-reconnect with exponential
 * backoff, request/response correlation by id, push handling.
 * / WebSocket-клиент релея Drunsk: автопереподключение с экспоненциальным
 * backoff, сопоставление запрос/ответ по id, обработка пушей.
 *
 * <p>Callbacks fire on the relay thread — UI must marshal to the render thread.
 * / Колбэки на потоке релея — UI сам переносит на render-поток.
 */
public final class RelayClient {

    public enum Status { IDLE, CONNECTING, UNPAIRED, ONLINE, OFFLINE }

    private static final RelayClient INSTANCE = new RelayClient();

    public static RelayClient get() {
        return INSTANCE;
    }

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "drunsk-relay");
                t.setDaemon(true);
                return t;
            });

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private final AtomicReference<String> nick = new AtomicReference<>("");
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private volatile int backoffMs = 1000;
    private volatile boolean wantConnection = false;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> pingTask;
    private final StringBuilder frameBuf = new StringBuilder();

    /** Push listeners by message type / слушатели пушей по типу сообщения */
    private final Map<String, Consumer<JsonObject>> listeners = new ConcurrentHashMap<>();
    /** fired when the socket reaches ONLINE (after hello) / при переходе в ONLINE (после hello) */
    private final java.util.List<Runnable> onlineCallbacks = new java.util.concurrent.CopyOnWriteArrayList<>();

    private record Pending(CompletableFuture<JsonObject> future, long deadline) {
    }

    private RelayClient() {
        exec.scheduleWithFixedDelay(this::expirePending, 5, 5, TimeUnit.SECONDS);
    }

    /** Register a callback invoked each time the relay goes ONLINE. / Колбэк на каждый переход в ONLINE. */
    public void onOnline(Runnable r) {
        onlineCallbacks.add(r);
    }

    public Status status() {
        return status.get();
    }

    public boolean isReady() {
        return status.get() == Status.ONLINE;
    }

    public String lastError() {
        return lastError.get();
    }

    public boolean hasToken(String mcNick) {
        return DrunskConfig.tokenFor(mcNick) != null;
    }

    public void onPush(String type, Consumer<JsonObject> handler) {
        listeners.put(type, handler);
    }

    public synchronized void connect(String mcNick) {
        nick.set(mcNick);
        String token = DrunskConfig.tokenFor(mcNick);
        if (token == null) {
            status.set(Status.UNPAIRED);
            return;
        }
        wantConnection = true;
        backoffMs = 1000;
        openSocket(mcNick, token);
    }

    public synchronized void disconnect() {
        wantConnection = false;
        cancelReconnect();
        cancelPing();
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        status.set(Status.IDLE);
    }

    public void shutdown() {
        wantConnection = false;
        disconnect();
        exec.shutdownNow();
    }

    /**
     * Start pairing: connect without a token, ask for a code. When the player
     * confirms in Discord the relay pushes `paired`; handleFrame saves the
     * token and reconnects. Returned future yields the 6-digit code.
     * / Привязка: коннект без токена, запрос кода. После подтверждения в
     * Discord релей шлёт `paired` — токен сохраняется, идёт reconnect.
     */
    public CompletableFuture<String> requestPairCode(String mcNick) {
        nick.set(mcNick);
        wantConnection = true;
        status.set(Status.CONNECTING);
        openSocket(mcNick, null);
        return awaitConnected(8000).thenCompose(v ->
                sendRequest("pair_request", req -> req.addProperty("mcNick", mcNick))
                        .thenApply(resp -> {
                            if (!resp.get("ok").getAsBoolean()) {
                                throw new java.util.concurrent.CompletionException(
                                        new RuntimeException(resp.has("reason")
                                                ? resp.get("reason").getAsString() : "error"));
                            }
                            return resp.get("code").getAsString();
                        }));
    }

    private CompletableFuture<Void> awaitConnected(long timeoutMs) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        holder[0] = exec.scheduleWithFixedDelay(() -> {
            if (socket.get() != null && !socket.get().isInputClosed()) {
                f.complete(null);
                holder[0].cancel(false);
            } else if (System.currentTimeMillis() > deadline) {
                f.completeExceptionally(new TimeoutException("connect"));
                holder[0].cancel(false);
            }
        }, 50, 100, TimeUnit.MILLISECONDS);
        return f;
    }

    public CompletableFuture<JsonObject> sendRequest(String type, Consumer<JsonObject> filler) {
        return sendRequest(type, filler, 10_000);
    }

    public CompletableFuture<JsonObject> sendRequest(String type, Consumer<JsonObject> filler, long timeoutMs) {
        WebSocket ws = socket.get();
        if (ws == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("offline"));
        }
        int id = nextId.getAndIncrement();
        JsonObject msg = new JsonObject();
        msg.addProperty("id", id);
        msg.addProperty("type", type);
        if (filler != null) filler.accept(msg);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, new Pending(future, System.currentTimeMillis() + timeoutMs));
        ws.sendText(msg.toString(), true);
        return future;
    }

    // --- socket plumbing / внутренности сокета ------------------------------

    private void openSocket(String mcNick, String token) {
        cancelReconnect();
        status.set(Status.CONNECTING);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(DrunskConfig.relayUrl), new Listener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        onSocketFailure("connect: " + rootMessage(err));
                        return;
                    }
                    socket.set(ws);
                    if (token == null) {
                        // pairing mode: no hello, socket is ready for
                        // pair_request; handleFrame's `paired` reconnects with
                        // a real token.
                        // режим привязки: hello не шлём, сокет готов для
                        // pair_request; пуш `paired` переподключит с токеном.
                        backoffMs = 1000;
                        return;
                    }
                    int id = nextId.getAndIncrement();
                    JsonObject hello = new JsonObject();
                    hello.addProperty("id", id);
                    hello.addProperty("type", "hello");
                    hello.addProperty("mcNick", mcNick);
                    if (token != null) hello.addProperty("token", token);
                    // player metadata for admin panel / метаданные для админ-панели
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.getCurrentServer() != null) {
                        hello.addProperty("serverIp", mc.getCurrentServer().ip);
                    }
                    hello.addProperty("mcVersion", net.minecraft.SharedConstants.getCurrentVersion().name());
                    CompletableFuture<JsonObject> helloFuture = new CompletableFuture<>();
                    pending.put(id, new Pending(helloFuture, System.currentTimeMillis() + 10_000));
                    ws.sendText(hello.toString(), true);
                    helloFuture.whenComplete((resp, e) -> {
                        if (e != null || resp == null || !resp.get("ok").getAsBoolean()) {
                            String reason = (resp != null && resp.has("reason"))
                                    ? resp.get("reason").getAsString()
                                    : (e != null ? rootMessage(e) : "hello failed");
                            if ("unauthorized".equals(reason)) {
                                // token revoked/expired: drop it and go unpaired
                                // токен отозван/истёк: стираем и в UNPAIRED
                                DrunskConfig.tokens.remove(mcNick.toLowerCase());
                                DrunskConfig.save();
                                status.set(Status.UNPAIRED);
                                wantConnection = false;
                                DrunskClient.LOGGER.warn("hello unauthorized, token dropped");
                            } else {
                                onSocketFailure("hello: " + reason);
                            }
                            return;
                        }
                        status.set(Status.ONLINE);
                        backoffMs = 1000;
                        lastError.set("");
                        startPing();
                        DrunskClient.LOGGER.info("relay online as {}", mcNick);
                        for (Runnable r : onlineCallbacks) {
                            try {
                                r.run();
                            } catch (Exception ex) {
                                DrunskClient.LOGGER.warn("online callback failed", ex);
                            }
                        }
                    });
                });
    }

    private void startPing() {
        cancelPing();
        pingTask = exec.scheduleWithFixedDelay(() -> {
            WebSocket ws = socket.get();
            if (ws != null && status.get() == Status.ONLINE) {
                sendRequest("ping", null, 15_000).whenComplete((r, e) -> {
                    if (e != null) onSocketFailure("ping timeout");
                });
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void cancelPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    private void onSocketFailure(String why) {
        if (!wantConnection) return;
        lastError.set(why);
        status.set(Status.OFFLINE);
        cancelPing();
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "reset");
            } catch (Exception ignored) {
            }
        }
        // fail everything waiting / проваливаем ожидающие запросы
        for (Pending p : pending.values()) {
            p.future().completeExceptionally(new IllegalStateException("offline"));
        }
        pending.clear();
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        cancelReconnect();
        long delay = backoffMs;
        backoffMs = (int) Math.min(backoffMs * 2L, 30_000);
        DrunskClient.LOGGER.info("reconnect in {} ms", delay);
        reconnectTask = exec.schedule(() -> {
            if (!wantConnection) return;
            String n = nick.get();
            String token = DrunskConfig.tokenFor(n);
            if (token == null) {
                status.set(Status.UNPAIRED);
                return;
            }
            openSocket(n, token);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void expirePending() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Pending> e : pending.entrySet()) {
            if (e.getValue().deadline() < now) {
                pending.remove(e.getKey());
                e.getValue().future().completeExceptionally(new TimeoutException());
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String m = cur.getMessage();
        return m == null ? cur.getClass().getSimpleName() : m;
    }

    private final class Listener implements WebSocket.Listener {

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (frameBuf) {
                frameBuf.append(data);
                if (last) {
                    String whole = frameBuf.toString();
                    frameBuf.setLength(0);
                    handleFrame(whole);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            onSocketFailure("ws error: " + rootMessage(error));
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            onSocketFailure("closed by server (" + statusCode + ")");
            return null;
        }
    }

    private void handleFrame(String json) {
        JsonObject msg;
        try {
            msg = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        if (msg.has("id")) {
            Pending p = pending.remove(msg.get("id").getAsInt());
            if (p != null) {
                p.future().complete(msg);
                return;
            }
        }
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        if ("paired".equals(type)) {
            String token = msg.get("token").getAsString();
            DrunskConfig.setToken(nick.get(), token);
            DrunskClient.LOGGER.info("paired, token saved");
            // reconnect with the token / переподключаемся уже с токеном
            connect(nick.get());
        }
        Consumer<JsonObject> l = listeners.get(type);
        if (l != null) {
            try {
                l.accept(msg);
            } catch (Exception e) {
                DrunskClient.LOGGER.warn("push listener error", e);
            }
        }
    }
}
