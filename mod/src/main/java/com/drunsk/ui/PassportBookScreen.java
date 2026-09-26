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
 * Passport book: scrollable list of all clan passports; click a row to open it.
 * / Книга паспортов: прокручиваемый список паспортов клана, клик — открыть.
 */
public final class PassportBookScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int TOP = 40;
    private int scroll;
    private long lastRefresh = 0;

    public PassportBookScreen() {
        super(Component.translatable("drunsk.screen.book"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(width / 2 - 49, height - 30, 98, 20).build());
        DrunskState.get().refreshList(null);
    }

    @Override
    public void tick() {
        super.tick();
        // light refresh every 5 s while open / лёгкое обновление раз в 5 с
        if (System.currentTimeMillis() - lastRefresh > 5000) {
            lastRefresh = System.currentTimeMillis();
            DrunskState.get().refreshList(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, ChatFormatting.GOLD + I18n.get("drunsk.screen.book"),
                width / 2, 16, 0xFFFFD700);
        List<DrunskState.Passport> list = DrunskState.get().passports();
        if (list.isEmpty()) {
            g.drawCenteredString(font, I18n.get("drunsk.book.empty"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        int rows = Math.max(1, (height - TOP - 44) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, list.size() - rows));
        int y = TOP;
        int x = width / 2 - 150;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            DrunskState.Passport p = list.get(i);
            boolean hovered = mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                g.fill(x - 4, y - 1, width - x + 4, y + ROW_H - 1, 0x30FFFFFF);
            }
            String name = (p.online() ? ChatFormatting.GREEN : ChatFormatting.GRAY) + p.mcNick();
            String disc = p.discordName() != null ? ChatFormatting.DARK_GRAY + " (" + p.discordName() + ")" : "";
            String bal = p.hidden() || p.balance() == null
                    ? ChatFormatting.DARK_GRAY + I18n.get("drunsk.passport.hidden_balance")
                    : ChatFormatting.YELLOW + String.format("%,d", p.balance());
            g.drawString(font, name + disc, x, y + 2, 0xFFFFFFFF, false);
            g.drawString(font, bal, width - x - font.width(bal), y + 2, 0xFFFFFFFF, false);
            y += ROW_H;
        }
        if (list.size() > rows) {
            g.drawCenteredString(font, I18n.get("drunsk.book.scroll", scroll + 1,
                    (list.size() + rows - 1) / rows), width / 2, height - 42, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            List<DrunskState.Passport> list = DrunskState.get().passports();
            int rows = Math.max(1, (height - TOP - 44) / ROW_H);
            int y = TOP;
            for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
                if (event.y() >= y && event.y() < y + ROW_H) {
                    DrunskState.Passport p = list.get(i);
                    minecraft.setScreen(new PassportScreen(p.mcNick()));
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
