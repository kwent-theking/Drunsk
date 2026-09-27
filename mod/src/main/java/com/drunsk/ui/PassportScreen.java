package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Passport view: own passport (target == null) or another player's.
 * / Просмотр паспорта: своего (target == null) или чужого.
 */
public final class PassportScreen extends DrunskScreen {

    private static final int LINE = 12;

    private final String target; // null = own / null = свой
    private DrunskState.Passport data;
    private String error;
    private boolean loaded;

    public PassportScreen(String target) {
        super(Component.translatable(target == null ? "drunsk.screen.my_passport" : "drunsk.screen.passport_of"));
        this.target = target;
    }

    @Override
    protected void init() {
        load();
        int cx = width / 2;
        if (target != null) {
            addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.transfer"), b ->
                            minecraft.gui.setScreen(new TransferScreen(target)))
                    .bounds(cx - 102, height - 52, 98, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.dm"), b ->
                            minecraft.gui.setScreen(new DmScreen(target)))
                    .bounds(cx - 102, height - 78, 98, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(target != null ? cx + 4 : cx - 49, height - 52, 98, 20).build());
    }

    private void load() {
        loaded = false;
        error = null;
        String nick = target != null ? target : DrunskState.get().myNick();
        if (nick == null || nick.isEmpty()) {
            error = I18n.get("drunsk.err.not_paired");
            loaded = true;
            return;
        }
        if (target == null) {
            DrunskState.Me me = DrunskState.get().me();
            if (me != null) {
                data = new DrunskState.Passport(me.userId(), me.mcNick(), me.discordName(),
                        me.balance(), me.since(), me.owner(), true, false);
                loaded = true;
                DrunskState.get().refreshMe(); // live update / живое обновление
                return;
            }
        }
        DrunskState.get().fetchPassport(nick, p -> {
            if (p == null) {
                error = I18n.get(target == null ? "drunsk.err.no_own_passport" : "drunsk.err.no_passport", nick);
            } else {
                data = p;
            }
            loaded = true;
        });
        // refresh own data in background while watching someone / свои данные фоном
        DrunskState.get().refreshMe();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = 24, y = 24;
        drawHeader(g, x, y);
        y += 26;
        if (!loaded) {
            text(g, I18n.get("drunsk.msg.loading"), x, y, 0xFFAAAAAA);
            return;
        }
        if (error != null) {
            text(g, error, x, y, 0xFFFF6666);
            return;
        }
        DrunskState.Passport p = data;
        String balance = p.hidden() || p.balance() == null
                ? I18n.get("drunsk.passport.hidden_balance")
                : UiText.num(p.balance()) + " " + currency();
        text(g, I18n.get("drunsk.passport.mc_nick", p.mcNick()), x, y, 0xFFFFFFFF);
        y += LINE;
        text(g, I18n.get("drunsk.passport.discord",
                p.discordName() != null ? p.discordName() : "?"), x, y, 0xFFB8B8B8);
        y += LINE;
        text(g, I18n.get("drunsk.passport.balance", balance), x, y, 0xFF88FF88);
        y += LINE;
        text(g, I18n.get("drunsk.passport.registered",
                new SimpleDateFormat("dd.MM.yyyy").format(new Date(p.since()))), x, y, 0xFFB8B8B8);
        y += LINE;
        String status = p.online()
                ? ChatFormatting.GREEN + I18n.get("drunsk.passport.online")
                : ChatFormatting.GRAY + I18n.get("drunsk.passport.offline");
        text(g, status, x, y, 0xFFB8B8B8);
        y += LINE;
        if (p.owner()) {
            text(g, ChatFormatting.GOLD + I18n.get("drunsk.passport.clan_owner"), x, y, 0xFFFFD700);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, int x, int y) {
        RelayClient.Status s = RelayClient.get().status();
        String conn = switch (s) {
            case ONLINE -> ChatFormatting.GREEN + I18n.get("drunsk.conn.online");
            case CONNECTING -> ChatFormatting.YELLOW + I18n.get("drunsk.conn.connecting");
            case OFFLINE -> ChatFormatting.RED + I18n.get("drunsk.conn.offline");
            case UNPAIRED -> ChatFormatting.RED + I18n.get("drunsk.conn.unpaired");
            case IDLE -> ChatFormatting.GRAY + I18n.get("drunsk.conn.idle");
        };
        text(g, Component.translatable("drunsk.screen.passport_title"), x, y, 0xFFFFFFFF);
        text(g, conn, x + 90, y, 0xFFAAAAAA);
    }

    private String currency() {
        DrunskState.Me me = DrunskState.get().me();
        return me != null ? me.currency() : I18n.get("drunsk.currency");
    }
}
