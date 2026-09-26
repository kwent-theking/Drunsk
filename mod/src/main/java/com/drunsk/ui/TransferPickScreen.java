package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Pick a recipient, then open the transfer form. / Выбор получателя, потом форма перевода.
 */
public final class TransferPickScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int TOP = 40;
    private int scroll;

    public TransferPickScreen() {
        super(Component.translatable("drunsk.screen.transfer_pick"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(width / 2 - 49, height - 30, 98, 20).build());
        DrunskState.get().refreshList(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, ChatFormatting.GOLD + I18n.get("drunsk.screen.transfer_pick"),
                width / 2, 16, 0xFFFFD700);
        String myNick = DrunskState.get().myNick();
        List<DrunskState.Passport> list = DrunskState.get().passports().stream()
                .filter(p -> !p.mcNick().equalsIgnoreCase(myNick)).toList();
        if (list.isEmpty()) {
            g.drawCenteredString(font, I18n.get("drunsk.book.empty"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        int rows = Math.max(1, (height - TOP - 44) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, list.size() - rows));
        int y = TOP;
        int x = width / 2 - 120;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            DrunskState.Passport p = list.get(i);
            boolean hovered = mouseY >= y && mouseY < y + ROW_H;
            if (hovered) g.fill(x - 4, y - 1, x + 244, y + ROW_H - 1, 0x30FFFFFF);
            g.drawString(font, (p.online() ? ChatFormatting.GREEN : ChatFormatting.GRAY) + p.mcNick()
                    + (p.discordName() != null ? ChatFormatting.DARK_GRAY + " (" + p.discordName() + ")" : ""),
                    x, y + 2, 0xFFFFFFFF, false);
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            String myNick = DrunskState.get().myNick();
            List<DrunskState.Passport> list = DrunskState.get().passports().stream()
                    .filter(p -> !p.mcNick().equalsIgnoreCase(myNick)).toList();
            int rows = Math.max(1, (height - TOP - 44) / ROW_H);
            int y = TOP;
            for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
                if (event.y() >= y && event.y() < y + ROW_H) {
                    minecraft.setScreen(new TransferScreen(list.get(i).mcNick()));
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
