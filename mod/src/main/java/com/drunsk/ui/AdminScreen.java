package com.drunsk.ui;

import com.drunsk.relay.RelayClient;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Admin panel: kick, mute/unmute, stats. Only for admins (kwentgames, _Belmo).
 * / Админ-панель: кик, мут/размут, статистика. Только для админов.
 */
public final class AdminScreen extends DrunskScreen {

    private EditBox nickBox;
    private String status;
    private int statusColor = 0xFFAAAAAA;
    private JsonObject stats;

    public AdminScreen() {
        super(Component.translatable("drunsk.screen.admin"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        nickBox = new EditBox(font, cx - 80, height / 2 - 40, 160, 20,
                Component.translatable("drunsk.admin.nick"));
        nickBox.setMaxLength(16);
        addRenderableWidget(nickBox);

        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.kick"), b -> doKick())
                .bounds(cx - 100, height / 2 - 10, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.mute"), b -> doMute())
                .bounds(cx + 5, height / 2 - 10, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.unmute"), b -> doUnmute())
                .bounds(cx - 100, height / 2 + 16, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.admin.stats"), b -> doStats())
                .bounds(cx + 5, height / 2 + 16, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(cx - 49, height / 2 + 50, 98, 20).build());
        doStats();
    }

    private String nick() {
        return nickBox.getValue().trim();
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

    private void doStats() {
        admin("admin_stats", null);
    }

    private void admin(String type, java.util.function.Consumer<JsonObject> filler) {
        status = I18n.get("drunsk.msg.sending");
        statusColor = 0xFFAAAAAA;
        RelayClient.get().sendRequest(type, filler).whenComplete((r, e) -> com.drunsk.relay.DrunskState.runOnRender(() -> {
            if (e != null || r == null || !r.get("ok").getAsBoolean()) {
                String reason = (r != null && r.has("reason")) ? r.get("reason").getAsString() : "offline";
                status = I18n.get("drunsk.transfer.error", UiText.reason(reason));
                statusColor = 0xFFFF6666;
            } else {
                if ("admin_stats".equals(type)) {
                    stats = r;
                    status = null;
                } else {
                    status = ChatFormatting.GREEN + "OK";
                    statusColor = 0xFF88FF88;
                }
            }
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = width / 2;
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.admin"), cx, 20, 0xFFFFD700);
        if (stats != null) {
            int y = height / 2 + 80;
            text(g, I18n.get("drunsk.admin.online") + ": " + stats.get("online").getAsInt(), cx - 80, y, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.passports") + ": " + stats.get("passports").getAsInt(), cx - 80, y + 12, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.dms") + ": " + stats.get("dms").getAsInt(), cx - 80, y + 24, 0xFFCCCCCC);
            text(g, I18n.get("drunsk.admin.chats") + ": " + stats.get("chats").getAsInt(), cx - 80, y + 36, 0xFFCCCCCC);
        }
        if (status != null) {
            centered(g, status, cx, height / 2 + 70, statusColor);
        }
    }
}
