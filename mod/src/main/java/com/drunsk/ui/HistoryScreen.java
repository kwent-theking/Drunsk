package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Transaction history: transfers and casino rounds. / История операций: переводы и лудка.
 */
public final class HistoryScreen extends Screen {

    private static final int ROW_H = 12;
    private static final int TOP = 36;
    private int scroll;

    public HistoryScreen() {
        super(Component.translatable("drunsk.screen.history"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(width / 2 - 49, height - 30, 98, 20).build());
        DrunskState.get().refreshHistory();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, ChatFormatting.GOLD + I18n.get("drunsk.screen.history"),
                width / 2, 14, 0xFFFFD700);
        List<DrunskState.HistoryEntry> list = DrunskState.get().history();
        if (list.isEmpty()) {
            g.drawCenteredString(font, I18n.get("drunsk.history.empty"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        int rows = Math.max(1, (height - TOP - 40) / ROW_H);
        scroll = Math.min(scroll, Math.max(0, list.size() - rows));
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm");
        int x = width / 2 - 160;
        int y = TOP;
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            DrunskState.HistoryEntry e = list.get(i);
            String kind = switch (e.kind()) {
                case "transfer" -> e.mine()
                        ? ChatFormatting.RED + "-" + e.amount() + " → " + e.to()
                        : ChatFormatting.GREEN + "+" + e.amount() + " ← " + e.from();
                case "coin", "dice", "roulette" -> (e.amount() >= 0 ? ChatFormatting.GREEN + "+" : ChatFormatting.RED + "")
                        + e.amount() + " " + I18n.get("drunsk.history." + e.kind());
                default -> e.kind() + " " + e.amount();
            };
            g.drawString(font, fmt.format(new Date(e.at())) + "  " + kind, x, y, 0xFFCCCCCC, false);
            y += ROW_H;
        }
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
