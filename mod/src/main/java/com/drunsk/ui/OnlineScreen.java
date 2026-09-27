package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Clan online list from the relay presence snapshot; click a row → DM screen.
 * / Список онлайна клана из presence-снимка релея; клик по строке — ЛС.
 */
public final class OnlineScreen extends DrunskScreen {

    private static final int ROW_H = 14;
    private static final int TOP = 40;
    private int scroll;
    private List<DrunskState.Passport> online;

    public OnlineScreen() {
        super(Component.translatable("drunsk.screen.online"));
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
        // presence pushes update DrunskState for free; only refresh the roster
        // / presence приходит пушами — обновляем только список паспортов
        online = DrunskState.get().passports().stream().filter(DrunskState.Passport::online).toList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.online"), width / 2, 16, 0xFFFFD700);
        List<DrunskState.Passport> list = online != null ? online : List.of();
        if (list.isEmpty()) {
            centered(g, I18n.get("drunsk.online.empty"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        int rows = Math.max(1, (height - TOP - 44) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, list.size() - rows));
        int y = TOP;
        int x = width / 2 - 100;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            DrunskState.Passport p = list.get(i);
            if (mouseY >= y && mouseY < y + ROW_H) {
                g.fill(x - 4, y - 1, x + 204, y + ROW_H - 1, 0x30FFFFFF);
            }
            String disc = p.discordName() != null ? ChatFormatting.DARK_GRAY + " (" + p.discordName() + ")" : "";
            text(g, ChatFormatting.GREEN + p.mcNick() + disc, x, y + 2, 0xFFFFFFFF);
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && online != null) {
            int rows = Math.max(1, (height - TOP - 44) / ROW_H);
            int y = TOP;
            for (int i = scroll; i < Math.min(online.size(), scroll + rows); i++) {
                if (event.y() >= y && event.y() < y + ROW_H) {
                    minecraft.gui.setScreen(new DmScreen(online.get(i).mcNick()));
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
}
