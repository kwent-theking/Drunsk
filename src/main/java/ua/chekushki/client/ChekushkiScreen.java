package ua.chekushki.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ChekushkiScreen extends Screen {
    private static final String API = "https://31.77.147.126.sslip.io/botpanel";
    private static final String TOKEN = "ed3b950270600c1b76f5d57cdb694b56339de6ed8498b380";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final List<Passport> passports = new ArrayList<>();
    private String statusText = "Загрузка...";
    private long balance;
    private double chance = 40.0;
    private boolean verified;
    private EditBox recipient;
    private EditBox transferAmount;
    private EditBox casinoAmount;
    private int tab = 0;

    private record Passport(String nick, String type, long issuedAt) {}

    public ChekushkiScreen() {
        super(Component.literal("Чекушки Drunsk"));
    }

    @Override
    protected void init() {
        int w = this.width, h = this.height;
        recipient = new EditBox(this.font, w / 2 - 100, 78, 200, 20, Component.literal("Получатель"));
        transferAmount = new EditBox(this.font, w / 2 - 100, 108, 200, 20, Component.literal("Сумма"));
        casinoAmount = new EditBox(this.font, w / 2 - 100, 108, 200, 20, Component.literal("Ставка"));
        recipient.setMaxLength(16);
        transferAmount.setMaxLength(12);
        casinoAmount.setMaxLength(12);
        addWidget(recipient); addWidget(transferAmount); addWidget(casinoAmount);
        addRenderableWidget(Button.builder(Component.literal("Баланс"), b -> tab = 0).bounds(w / 2 - 150, 32, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Перевести"), b -> tab = 1).bounds(w / 2 - 75, 32, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Лудка"), b -> tab = 2).bounds(w / 2, 32, 70, 20).build());
        if (nick().equalsIgnoreCase("_Belmo")) {
            addRenderableWidget(Button.builder(Component.literal("Паспорта"), b -> { tab = 3; loadPassports(); }).bounds(w / 2 + 75, 32, 70, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(w / 2 - 50, h - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> transfer()).bounds(w / 2 + 30, 138, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Крутить"), b -> casino()).bounds(w / 2 + 30, 138, 120, 20).build());
        refresh();
    }

    private String nick() {
        return ChekushkiClientInitializer.nick(Minecraft.getInstance());
    }

    private void refresh() {
        statusText = "Загрузка...";
        request("GET", "/mod/status?nick=" + url(nick()), null).thenAccept(json -> {
            synchronized (this) {
                verified = json.get("verified").getAsBoolean();
                balance = json.get("balance").getAsLong();
                chance = json.get("casino_chance").getAsDouble();
                statusText = verified ? "Аккаунт подтверждён" : "Аккаунт не подтверждён";
            }
        }).exceptionally(e -> { statusText = "Ошибка соединения"; return null; });
    }

    private void loadPassports() {
        passports.clear();
        statusText = "Загрузка паспортов...";
        request("GET", "/mod/passports?nick=" + url(nick()), null).thenAccept(json -> {
            passports.clear();
            json.get("passports").getAsJsonArray().forEach(item -> {
                JsonObject p = item.getAsJsonObject();
                passports.add(new Passport(p.get("nick").getAsString(), p.get("type").getAsString(), p.get("issued_at").getAsLong()));
            });
            statusText = "Паспортов: " + passports.size();
        }).exceptionally(e -> { statusText = "Ошибка загрузки"; return null; });
    }

    private void transfer() {
        statusText = "Отправка...";
        JsonObject body = new JsonObject();
        body.addProperty("from", nick());
        body.addProperty("to", recipient.getValue().trim());
        try { body.addProperty("amount", Long.parseLong(transferAmount.getValue().trim())); }
        catch (Exception e) { statusText = "Неверная сумма"; return; }
        request("POST", "/mod/transfer", body).thenAccept(json -> {
            synchronized (this) {
                balance = json.get("balance").getAsLong();
                statusText = "Перевод выполнен";
            }
        }).exceptionally(e -> { statusText = "Ошибка перевода"; return null; });
    }

    private void casino() {
        statusText = "Крутим...";
        JsonObject body = new JsonObject();
        body.addProperty("nick", nick());
        try { body.addProperty("amount", Long.parseLong(casinoAmount.getValue().trim())); }
        catch (Exception e) { statusText = "Неверная ставка"; return; }
        request("POST", "/mod/casino", body).thenAccept(json -> {
            synchronized (this) {
                balance = json.get("balance").getAsLong();
                boolean win = json.get("win").getAsBoolean();
                var reels = json.get("reels").getAsJsonArray();
                statusText = String.format("[%d | %d | %d] %s", reels.get(0).getAsInt(), reels.get(1).getAsInt(), reels.get(2).getAsInt(), win ? "777! Выигрыш x2!" : "Проигрыш");
            }
        }).exceptionally(e -> { statusText = "Ошибка лудки"; return null; });
    }

    private java.util.concurrent.CompletableFuture<JsonObject> request(String method, String path, JsonObject body) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path))
                    .header("Authorization", "Bearer " + TOKEN)
                    .timeout(Duration.ofSeconds(10));
                if ("GET".equals(method)) builder.GET();
                else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
                return GSON.fromJson(HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body(), JsonObject.class);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        super.render(gui, mouseX, mouseY, delta);
        int w = this.width / 2;
        gui.drawCenteredString(this.font, this.title, w, 16, 0xFFFFFF);
        gui.drawCenteredString(this.font, "Баланс: " + balance + " чекушек", w, 58, 0xFFD700);
        gui.drawCenteredString(this.font, statusText, w, this.height - 52, 0xE0E0E0);
        recipient.setVisible(tab == 1); transferAmount.setVisible(tab == 1);
        casinoAmount.setVisible(tab == 2);
        if (tab == 1) {
            gui.drawString(this.font, "Получатель:", w - 110, 82, 0xFFFFFF);
            gui.drawString(this.font, "Сумма:", w - 110, 112, 0xFFFFFF);
            gui.drawString(this.font, "Аккаунт: " + (verified ? "подтверждён" : "не подтверждён"), w - 110, 168, 0xFFFFFF);
        } else if (tab == 2) {
            gui.drawString(this.font, "Ставка:", w - 110, 112, 0xFFFFFF);
            gui.drawString(this.font, "Шанс 777: " + chance + "%", w - 110, 168, 0xFFFFFF);
        } else if (tab == 3 && nick().equalsIgnoreCase("_Belmo")) {
            int y = 82;
            for (Passport p : passports) {
                if (y > height - 55) break;
                String line = String.format("Ник: %-16s Тип: %-8s Выдан: %s", p.nick(), p.type(), java.time.Instant.ofEpochSecond(p.issuedAt()).toString().substring(0, 10));
                gui.drawString(this.font, line, 20, y, 0x80FF80); y += 13;
            }
        }
    }
}
