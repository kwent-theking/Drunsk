package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Admin panel: left side — actions (kick, mute, give/take), right side —
 * scrollable player info card. Admins: kwentgames, _Belmo.
 * / Админ-панель: слева — действия (кик, мут, дать/забрать), справа —
 * скроллируемая карточка игрока. Админы: kwentgames, _Belmo.
 */
public final class AdminScreen extends DrunskScreen {

    private static final int ROW_H = 12;

    private EditBox nickBox;
    private EditBox amountBox;
    private String status;
    private int statusColor = 0xFFAAAAAA;
    private JsonObject stats;
    private JsonObject playerInfo;
    private int scroll;
    private int lastLoadAt;
    private String loadedNick = "";
    private final List<String> infoLines = new ArrayList<>();

    public AdminScreen() {
        super(Component.translatable("drunsk.screen.admin"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int leftX = cx - 160;

        nickBox = new EditBox(font, leftX, height / 2 - 60, 140, 20,
                Component.translatable("drunsk.admin.nick"));
        nickBox.setMaxLength(16);
        addRenderableWidget(nickBox);

        amountBox = new EditBox(font, leftX, height / 2 - 34, 140, 20,
                Component.translatable("drunsk.admin.amount"));
        amountBox.setMaxLength(12);
        amountBox.setResponder(s -> {
            String digits = s.replaceAll("\\D", "");
            if (!digits.equals(s)) amountBox.setValue(digits);
        });
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.kick"), b -> doKick())
                .bounds(leftX, height / 2 - 4, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.mute"), b -> doMute())
                .bounds(leftX + 72, height / 2 - 4, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.unmute"), b -> doUnmute())
                .bounds(leftX, height / 2 + 20, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.give"), b -> doGive())
                .bounds(leftX + 72, height / 2 + 20, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.take"), b -> doTake())
                .bounds(leftX, height / 2 + 44, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.stats"), b -> doStats())
                .bounds(leftX + 72, height / 2 + 44, 68, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(leftX, height / 2 + 74, 140, 20).build());

        doStats();
    }

    private String nick() {
        return nickBox.getValue().trim();
    }

    private long amount() {
        try {
            return Long.parseLong(amountBox.getValue());
        } catch (Exception e) {
            return -1;
        }
    }

    private void doKick() {
        String n = nick();
        if (n.isEmpty()) return;
        admin("admin_kick", req -> req.addProperty("nick", n));
    }

    private void doMute() {
        String n = nick();
        if (n.isEmpty()) return;
        admin("admin_mute", req -> {
            req.addProperty("nick", n);
            req.addProperty("durationMs", 300000);
        });
    }

    private void doUnmute() {
        String n = nick();
        if (n.isEmpty()) return;
        admin("admin_unmute", req -> req.addProperty("nick", n));
    }

    private void doGive() {
        String n = nick();
        long a = amount();
        if (n.isEmpty() || a <= 0) return;
        admin("admin_give", req -> {
            req.addProperty("nick", n);
            req.addProperty("amount", a);
        });
    }

    private void doTake() {
        String n = nick();
        long a = amount();
        if (n.isEmpty() || a <= 0) return;
        admin("admin_take", req -> {
            req.addProperty("nick", n);
            req.addProperty("amount", a);
        });
    }

    private void doStats() {
        admin("admin_stats", null);
    }

    private void loadPlayer(String n) {
        admin("admin_player", req -> req.addProperty("nick", n));
    }

    private void admin(String type, java.util.function.Consumer<JsonObject> filler) {
        status = I18n.get("drunsk.msg.sending");
        statusColor = 0xFFAAAAAA;
        RelayClient.get().sendRequest(type, filler).whenComplete((r, e) -> DrunskState.runOnRender(() -> {
            if (e != null || r == null || !r.get("ok").getAsBoolean()) {
                String reason = (r != null && r.has("reason")) ? r.get("reason").getAsString() : "offline";
                status = I18n.get("drunsk.transfer.error", UiText.reason(reason));
                statusColor = 0xFFFF6666;
            } else {
                if ("admin_stats".equals(type)) {
                    stats = r;
                    status = null;
                } else if ("admin_player".equals(type)) {
                    playerInfo = r;
                    buildInfoLines();
                    status = null;
                } else {
                    status = ChatFormatting.GREEN + "OK";
                    statusColor = 0xFF88FF88;
                }
            }
        }));
    }

    private void buildInfoLines() {
        infoLines.clear();
        if (playerInfo == null) return;
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        infoLines.add(ChatFormatting.GOLD + playerInfo.get("nick").getAsString());
        infoLines.add(I18n.get("drunsk.admin.online") + ": " +
                (playerInfo.get("online").getAsBoolean()
                        ? ChatFormatting.GREEN + I18n.get("drunsk.passport.online")
                        : ChatFormatting.GRAY + I18n.get("drunsk.passport.offline")));
        infoLines.add(I18n.get("drunsk.admin.since") + ": " + fmt.format(new Date(playerInfo.get("since").getAsLong())));
        long playtimeMs = playerInfo.get("playtimeMs").getAsLong();
        long days = playtimeMs / 86400000;
        long hours = (playtimeMs % 86400000) / 3600000;
        long mins = (playtimeMs % 3600000) / 60000;
        infoLines.add(I18n.get("drunsk.admin.playtime") + ": " + days + "д " + hours + "ч " + mins + "м");
        String serverIp = playerInfo.has("serverIp") && !playerInfo.get("serverIp").isJsonNull()
                ? playerInfo.get("serverIp").getAsString() : "?";
        infoLines.add(I18n.get("drunsk.admin.server") + ": " + serverIp);
        String mcVersion = playerInfo.has("mcVersion") && !playerInfo.get("mcVersion").isJsonNull()
                ? playerInfo.get("mcVersion").getAsString() : "?";
        infoLines.add(I18n.get("drunsk.admin.version") + ": " + mcVersion);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = width / 2;
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.admin"), cx, 16, 0xFFFFD700);

        // left: stats summary / слева: сводная статистика
        if (stats != null) {
            int y = height / 2 - 60;
            int leftX = cx - 160;
            text(g, I18n.get("drunsk.admin.online") + ": " + stats.get("online").getAsInt(), leftX, y + 100, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.passports") + ": " + stats.get("passports").getAsInt(), leftX, y + 112, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.dms") + ": " + stats.get("dms").getAsInt(), leftX, y + 124, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.chats") + ": " + stats.get("chats").getAsInt(), leftX, y + 136, 0xFFCCCCCC);
        }

        // right: scrollable player info card / справа: скроллируемая карточка игрока
        int panelW = 180;
        int panelX = cx + 10;
        int top = height / 2 - 60;
        int bottom = height / 2 + 90;
        g.fill(panelX, top, panelX + panelW, bottom, 0x90000000);
        g.fill(panelX, top, panelX + panelW, top + 1, 0xFF555555);
        g.fill(panelX, bottom - 1, panelX + panelW, bottom, 0xFF555555);
        g.fill(panelX, top, panelX + 1, bottom, 0xFF555555);
        g.fill(panelX + panelW - 1, top, panelX + panelW, bottom, 0xFF555555);

        if (!infoLines.isEmpty()) {
            int rows = Math.max(1, (bottom - top - 8) / ROW_H);
            scroll = Math.min(scroll, Math.max(0, infoLines.size() - rows));
            int y = top + 6;
            for (int i = scroll; i < Math.min(infoLines.size(), scroll + rows); i++) {
                text(g, infoLines.get(i), panelX + 6, y, 0xFFDDDDDD);
                y += ROW_H;
            }
        } else {
            centered(g, I18n.get("drunsk.admin.no_player"), panelX + panelW / 2, top + 20, 0xFF888888);
        }

        if (status != null) {
            centered(g, status, cx, height / 2 + 100, statusColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // scroll the right panel / скроллим правую панель
        int cx = width / 2;
        int panelX = cx + 10;
        if (mouseX >= panelX && mouseX <= panelX + 180) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        // load player info once per nick change, debounced
        // / загружаем инфо игрока один раз на смену ника, с дебаунсом
        String n = nick();
        if (!n.isEmpty() && !n.equalsIgnoreCase(loadedNick)
                && minecraft.player != null
                && minecraft.player.tickCount - lastLoadAt > 10) {
            loadedNick = n;
            lastLoadAt = minecraft.player.tickCount;
            loadPlayer(n);
        }
    }
}
