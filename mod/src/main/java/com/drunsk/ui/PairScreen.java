package com.drunsk.ui;

import com.drunsk.DrunskClient;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Pairing screen: shows the 6-digit code to type as !паспорт <код> in Discord.
 * / Экран привязки: показывает 6-значный код для !паспорт <код> в Discord.
 */
public final class PairScreen extends Screen {

    private enum Phase { BUSY, CODE, DONE, FAILED }

    private Phase phase = Phase.BUSY;
    private String code = "";
    private String failReason = "";

    public PairScreen() {
        super(Component.translatable("drunsk.screen.pair"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(width / 2 - 49, height - 52, 98, 20).build());
        requestCode();
    }

    private void requestCode() {
        String nick = minecraft.player == null ? null : minecraft.player.getGameProfile().name();
        if (nick == null) {
            phase = Phase.FAILED;
            failReason = I18n.get("drunsk.err.no_nick");
            return;
        }
        phase = Phase.BUSY;
        CompletableFuture<String> f = RelayClient.get().requestPairCode(nick);
        f.whenComplete((c, e) -> minecraft.execute(() -> {
            if (e != null) {
                phase = Phase.FAILED;
                failReason = e.getCause() != null ? String.valueOf(e.getCause().getMessage()) : e.getMessage();
                DrunskClient.LOGGER.warn("pair request failed", e);
            } else {
                code = c;
                phase = Phase.CODE;
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2;
        int y = height / 2 - 60;
        g.drawCenteredString(font, Component.translatable("drunsk.screen.pair"), cx, y, 0xFFFFFFFF);
        y += 20;
        switch (phase) {
            case BUSY -> g.drawCenteredString(font, I18n.get("drunsk.msg.loading"), cx, y, 0xFFAAAAAA);
            case CODE -> {
                g.drawCenteredString(font, ChatFormatting.GOLD + code, cx, y + 4, 0xFFFFD700);
                g.drawCenteredString(font, I18n.get("drunsk.pair.hint1"), cx, y + 24, 0xFFCCCCCC);
                g.drawCenteredString(font, I18n.get("drunsk.pair.hint2", code), cx, y + 36, 0xFFCCCCCC);
                g.drawCenteredString(font, I18n.get("drunsk.pair.waiting"), cx, y + 56, 0xFF888888);
            }
            case DONE -> g.drawCenteredString(font, ChatFormatting.GREEN + I18n.get("drunsk.pair.done"), cx, y, 0xFF88FF88);
            case FAILED -> {
                g.drawCenteredString(font, ChatFormatting.RED + I18n.get("drunsk.pair.failed"), cx, y, 0xFFFF6666);
                g.drawCenteredString(font, failReason == null ? "" : failReason, cx, y + 14, 0xFFAA6666);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (phase == Phase.CODE && RelayClient.get().isReady()) {
            // relay reconnected with the fresh token: pairing completed
            // релей переподключился уже с токеном — привязка завершена
            phase = Phase.DONE;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
