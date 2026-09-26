package com.drunsk.ui;

import com.drunsk.DrunskClient;
import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Hub: my passport, passport book, transfer, casino, history, status.
 * / Главное меню: мой паспорт, книга паспортов, перевод, лудка, история, статус.
 */
public final class DrunskHubScreen extends Screen {

    public DrunskHubScreen(Screen back) {
        super(Component.translatable("drunsk.screen.hub"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 56;
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.my_passport"), b ->
                        minecraft.setScreen(new PassportScreen(null)))
                .bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.passport_book"), b ->
                        minecraft.setScreen(new PassportBookScreen()))
                .bounds(cx - 100, y + 24, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.transfer"), b ->
                        minecraft.setScreen(new TransferPickScreen()))
                .bounds(cx - 100, y + 48, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.casino"), b ->
                        minecraft.setScreen(new CasinoScreen()))
                .bounds(cx - 100, y + 72, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.history"), b ->
                        minecraft.setScreen(new HistoryScreen()))
                .bounds(cx - 100, y + 96, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.close"), b -> onClose())
                .bounds(cx - 49, y + 128, 98, 20).build());
        DrunskState.get().refreshMe();
        DrunskState.get().refreshList(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2;
        int y = 24;
        g.drawCenteredString(font, ChatFormatting.GOLD + I18n.get("drunsk.screen.hub"), cx, y, 0xFFFFD700);
        y += 14;
        // status line: connection + balance / строка статуса: соединение + баланс
        RelayClient.Status s = RelayClient.get().status();
        String conn = switch (s) {
            case ONLINE -> ChatFormatting.GREEN + I18n.get("drunsk.conn.online");
            case CONNECTING -> ChatFormatting.YELLOW + I18n.get("drunsk.conn.connecting");
            case OFFLINE -> ChatFormatting.RED + I18n.get("drunsk.conn.offline");
            case UNPAIRED -> ChatFormatting.RED + I18n.get("drunsk.conn.unpaired");
            case IDLE -> ChatFormatting.GRAY + I18n.get("drunsk.conn.idle");
        };
        g.drawCenteredString(font, conn, cx, y, 0xFFAAAAAA);
        y += 12;
        DrunskState.Me me = DrunskState.get().me();
        if (me != null) {
            g.drawCenteredString(font, I18n.get("drunsk.hub.balance_line",
                    String.format("%,d", me.balance()), me.currency()), cx, y, 0xFF88FF88);
        } else if (s == RelayClient.Status.UNPAIRED) {
            g.drawCenteredString(font, I18n.get("drunsk.hub.pair_hint"), cx, y, 0xFFCCCCCC);
        }
        String err = RelayClient.get().lastError();
        if (s == RelayClient.Status.OFFLINE && err != null && !err.isEmpty()) {
            g.drawCenteredString(font, ChatFormatting.DARK_GRAY + err, cx, height - 14, 0xFF666666);
        }
    }

    private int tickCounter;

    @Override
    public void tick() {
        super.tick();
        // keep balance fresh while hub is open / обновляем баланс, пока меню открыто
        if (++tickCounter % 100 == 0) {
            DrunskState.get().refreshMe();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
