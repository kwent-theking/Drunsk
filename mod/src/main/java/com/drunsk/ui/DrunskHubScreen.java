package com.drunsk.ui;

import com.drunsk.DrunskClient;
import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Hub: my passport, passport book, transfer, casino, history, online, DMs,
 * utilities, status. / Главное меню: мой паспорт, книга паспортов, перевод,
 * лудка, история, онлайн, ЛС, утилиты, статус.
 */
public final class DrunskHubScreen extends DrunskScreen {

    public DrunskHubScreen() {
        super(Component.translatable("drunsk.screen.hub"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 76;
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.my_passport"), b ->
                        minecraft.gui.setScreen(new PassportScreen(null)))
                .bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.passport_book"), b ->
                        minecraft.gui.setScreen(new PassportBookScreen()))
                .bounds(cx - 100, y + 24, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.transfer"), b ->
                        minecraft.gui.setScreen(new TransferPickScreen()))
                .bounds(cx - 100, y + 48, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.casino"), b ->
                        minecraft.gui.setScreen(new CasinoScreen()))
                .bounds(cx - 100, y + 72, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.history"), b ->
                        minecraft.gui.setScreen(new HistoryScreen()))
                .bounds(cx - 100, y + 96, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.online"), b ->
                        minecraft.gui.setScreen(new OnlineScreen()))
                .bounds(cx - 100, y + 120, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.dm"), b ->
                        minecraft.gui.setScreen(new DmListScreen()))
                .bounds(cx + 2, y + 120, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.utils"), b ->
                        minecraft.gui.setScreen(new UtilsScreen()))
                .bounds(cx - 100, y + 144, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.close"), b -> onClose())
                .bounds(cx - 49, y + 172, 98, 20).build());
        DrunskState.get().refreshMe();
        DrunskState.get().refreshList(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = width / 2;
        int y = 24;
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.hub"), cx, y, 0xFFFFD700);
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
        centered(g, conn, cx, y, 0xFFAAAAAA);
        y += 12;
        DrunskState.Me me = DrunskState.get().me();
        if (me != null) {
            centered(g, I18n.get("drunsk.hub.balance_line",
                    String.format("%,d", me.balance()), me.currency()), cx, y, 0xFF88FF88);
        } else if (s == RelayClient.Status.UNPAIRED) {
            centered(g, I18n.get("drunsk.hub.pair_hint"), cx, y, 0xFFCCCCCC);
        }
        String err = RelayClient.get().lastError();
        if (s == RelayClient.Status.OFFLINE && err != null && !err.isEmpty()) {
            centered(g, ChatFormatting.DARK_GRAY + err, cx, height - 14, 0xFF666666);
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
}
