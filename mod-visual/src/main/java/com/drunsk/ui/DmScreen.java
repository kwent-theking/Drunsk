package com.drunsk.ui;

import com.drunsk.DrunskClient;
import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * DM chat with one clan member over the relay (stored server-side, pushed
 * live). Framed panel, Enter sends, back goes to hub.
 * / ЛС с друном через релей. Панель с рамкой, Enter отправляет, назад — в хаб.
 */
public final class DmScreen extends DrunskScreen {

    private static final int ROW_H = 12;

    private final String peer;
    private EditBox input;
    private Button sendBtn;
    private String status;
    private int statusColor = 0xFFAAAAAA;
    private int scroll;

    public DmScreen(String peer) {
        super(Component.translatable("drunsk.screen.dm", peer));
        this.peer = peer;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        input = new EditBox(font, cx - 130, height - 34, 200, 20,
                Component.translatable("drunsk.dm.placeholder"));
        input.setMaxLength(400);
        addRenderableWidget(input);
        sendBtn = addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.send"), b -> send())
                .bounds(cx + 76, height - 34, 60, 20).build());
        // back → hub / назад → хаб
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b ->
                        minecraft.gui.setScreen(new DrunskHubScreen()))
                .bounds(cx - 49, height - 58, 98, 20).build());
        setInitialFocus(input);
        loadHistory();
        DrunskState.get().markDmRead(peer);
    }

    private void loadHistory() {
        status = I18n.get("drunsk.msg.loading");
        statusColor = 0xFFAAAAAA;
        DrunskState.get().fetchDmHistory(peer, ok -> {
            status = ok ? null : I18n.get("drunsk.dm.error");
            statusColor = 0xFFFF6666;
        });
    }

    private void send() {
        String text = input.getValue().trim();
        if (text.isEmpty() || !RelayClient.get().isReady()) return;
        input.setValue("");
        sendBtn.active = false;
        DrunskState.get().sendDm(peer, text, ok -> {
            sendBtn.active = true;
            if (!ok) {
                status = I18n.get("drunsk.dm.error");
                statusColor = 0xFFFF6666;
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = width / 2;
        int panelW = 320;
        int panelX = cx - panelW / 2;
        int top = 38;
        int bottom = height - 66;

        // panel background + border / фон панели + рамка
        g.fill(panelX, top - 4, panelX + panelW, bottom + 4, 0x90000000);
        g.fill(panelX, top - 4, panelX + panelW, top - 3, 0xFF555555);
        g.fill(panelX, bottom + 3, panelX + panelW, bottom + 4, 0xFF555555);
        g.fill(panelX, top - 4, panelX + 1, bottom + 4, 0xFF555555);
        g.fill(panelX + panelW - 1, top - 4, panelX + panelW, bottom + 4, 0xFF555555);

        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.dm", peer), cx, 14, 0xFFFFD700);
        boolean online = DrunskState.get().isOnline(peer.toLowerCase());
        centered(g, online ? ChatFormatting.GREEN + I18n.get("drunsk.passport.online")
                : ChatFormatting.GRAY + I18n.get("drunsk.passport.offline"), cx, 26, 0xFFAAAAAA);

        List<DrunskState.DmEntry> list = DrunskState.get().dmThread(peer);
        int rows = Math.max(1, (bottom - top) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, list.size() - rows));
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
        String me = DrunskState.get().myNick();
        int x = panelX + 8;
        int y = top;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            DrunskState.DmEntry e = list.get(i);
            boolean mine = e.from().equalsIgnoreCase(me);
            String line = ChatFormatting.DARK_GRAY + fmt.format(new Date(e.at())) + " "
                    + (mine ? ChatFormatting.AQUA : ChatFormatting.YELLOW) + e.from() + ChatFormatting.GRAY + ": "
                    + ChatFormatting.WHITE + e.text();
            text(g, font.plainSubstrByWidth(line, panelW - 16), x, y, 0xFFDDDDDD);
            y += ROW_H;
        }
        if (status != null) {
            centered(g, status, cx, bottom + 6, statusColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Enter sends the message / Enter отправляет сообщение
        if (event.key() == 257 && input.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(event);
    }
}
