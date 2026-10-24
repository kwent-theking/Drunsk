package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Casino menu: coin flip, dice over/under, roulette (red / black / number).
 * The roll happens server-side; this is only the form.
 * / Меню лудки: монетка, кости, рулетка (красное/чёрное/число).
 * Бросок — на сервере, здесь только форма.
 */
public final class CasinoScreen extends DrunskScreen {

    private enum Game { COIN, DICE, ROULETTE }

    private Game game = Game.COIN;
    private EditBox betBox;
    private Button gameBtn;
    private Button pickA;
    private Button pickB;
    private Button pickC;
    private Button playBtn;
    private String resultLine;
    private int resultColor = 0xFFAAAAAA;
    private boolean busy;
    private int currentPick = 0; // 0=A 1=B 2=C
    private String pickAValue = "heads";
    private String pickBValue = "tails";
    private String pickCValue = "red";

    public CasinoScreen() {
        super(Component.translatable("drunsk.screen.casino"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 60;

        gameBtn = addRenderableWidget(Button.builder(gameLabel(), b -> cycleGame())
                .bounds(cx - 100, y, 200, 20).build());
        y += 26;

        pickA = addRenderableWidget(Button.builder(Component.literal("A"), b -> select(0))
                .bounds(cx - 100, y, 64, 20).build());
        pickB = addRenderableWidget(Button.builder(Component.literal("B"), b -> select(1))
                .bounds(cx - 32, y, 64, 20).build());
        pickC = addRenderableWidget(Button.builder(Component.literal("C"), b -> select(2))
                .bounds(cx + 36, y, 64, 20).build());
        y += 30;

        betBox = new EditBox(font, cx - 60, y, 120, 20, Component.translatable("drunsk.casino.bet"));
        betBox.setMaxLength(9);
        betBox.setResponder(s -> {
            String digits = s.replaceAll("\\D", "");
            if (!digits.equals(s)) betBox.setValue(digits);
        });
        betBox.setValue("10");
        addRenderableWidget(betBox);
        y += 30;

        playBtn = addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.play"), b -> play())
                .bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(cx - 49, y + 56, 98, 20).build());
        rebuildPicks();
    }

    private void select(int which) {
        if (which == 2 && !pickC.visible) return;
        currentPick = which;
    }

    private Component gameLabel() {
        return Component.translatable("drunsk.casino.game." + game.name().toLowerCase());
    }

    private void cycleGame() {
        game = Game.values()[(game.ordinal() + 1) % Game.values().length];
        gameBtn.setMessage(gameLabel());
        rebuildPicks();
    }

    private void rebuildPicks() {
        switch (game) {
            case COIN -> {
                pickA.setMessage(Component.translatable("drunsk.casino.coin.heads"));
                pickB.setMessage(Component.translatable("drunsk.casino.coin.tails"));
                pickC.visible = false;
                pickAValue = "heads";
                pickBValue = "tails";
            }
            case DICE -> {
                pickA.setMessage(Component.translatable("drunsk.casino.dice.over"));
                pickB.setMessage(Component.translatable("drunsk.casino.dice.under"));
                pickC.visible = false;
                pickAValue = "over";
                pickBValue = "under";
            }
            case ROULETTE -> {
                pickA.setMessage(Component.translatable("drunsk.casino.roulette.red"));
                pickB.setMessage(Component.translatable("drunsk.casino.roulette.black"));
                // number pick: bet box becomes 0..14 wheel number
                // / ставка-число: поле ставки становится числом 0..14
                pickC.setMessage(Component.translatable("drunsk.casino.roulette.number"));
                pickC.visible = true;
                pickAValue = "red";
                pickBValue = "black";
                pickCValue = "number";
            }
        }
        if (currentPick == 2 && !pickC.visible) currentPick = 0;
        resultLine = null;
    }

    private long bet() {
        try {
            return Long.parseLong(betBox.getValue());
        } catch (Exception e) {
            return -1;
        }
    }

    private String pickValue() {
        return switch (currentPick) {
            case 1 -> pickBValue;
            case 2 -> pickCValue;
            default -> pickAValue;
        };
    }

    private void play() {
        long bet = bet();
        if (bet <= 0 || busy) return;
        // roulette number: the field holds the wheel number when C is selected,
        // stake is fixed at 10 to keep the form simple
        // / рулетка-число: в поле — число 0..14, ставка фикс 10
        final Object pick;
        final long stake;
        if (game == Game.ROULETTE && currentPick == 2) {
            int n;
            try {
                n = Integer.parseInt(betBox.getValue());
            } catch (Exception e) {
                resultLine = I18n.get("drunsk.casino.bad_number");
                resultColor = 0xFFFF6666;
                return;
            }
            if (n < 0 || n > 14) {
                resultLine = I18n.get("drunsk.casino.bad_number");
                resultColor = 0xFFFF6666;
                return;
            }
            pick = n;
            stake = 10;
        } else {
            pick = pickValue();
            stake = bet;
        }
        busy = true;
        playBtn.active = false;
        resultLine = I18n.get("drunsk.msg.sending");
        resultColor = 0xFFAAAAAA;
        RelayClient.get().sendRequest("casino", req -> {
            req.addProperty("game", game.name().toLowerCase());
            req.addProperty("bet", stake);
            if (pick instanceof Integer i) req.addProperty("pick", i);
            else req.addProperty("pick", (String) pick);
        }).whenComplete((r, e) -> DrunskState.runOnRender(() -> {
            busy = false;
            playBtn.active = true;
            if (e != null || r == null || !r.get("ok").getAsBoolean()) {
                String reason = (r != null && r.has("reason")) ? r.get("reason").getAsString() : "offline";
                resultLine = I18n.get("drunsk.casino.error", UiText.reason(reason));
                resultColor = 0xFFFF6666;
            } else {
                boolean win = r.get("win").getAsBoolean();
                String detail = r.has("detail") ? r.get("detail").getAsString() : "";
                resultLine = (win ? ChatFormatting.GREEN + I18n.get("drunsk.casino.win",
                        UiText.num(r.get("payout").getAsLong()))
                        : ChatFormatting.RED + I18n.get("drunsk.casino.lose"))
                        + ChatFormatting.GRAY + " [" + detail + "]"
                        + ChatFormatting.RESET + " " + I18n.get("drunsk.casino.new_balance",
                        UiText.num(r.get("newBalance").getAsLong()));
                resultColor = win ? 0xFF88FF88 : 0xFFFF8888;
                DrunskState.get().refreshMe();
            }
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.casino"), width / 2, 20, 0xFFFFD700);
        DrunskState.Me me = DrunskState.get().me();
        if (me != null) {
            centered(g, I18n.get("drunsk.hub.balance_line",
                    UiText.num(me.balance()), I18n.get("drunsk.currency")), width / 2, 32, 0xFF88FF88);
        }
        // highlight the selected pick / подсветка выбранной ставки
        for (int i = 0; i < 3; i++) {
            Button b = i == 0 ? pickA : i == 1 ? pickB : pickC;
            if (b.visible && currentPick == i) {
                g.fill(b.getX() - 2, b.getY() - 2, b.getX() + b.getWidth() + 2,
                        b.getY() + b.getHeight() + 2, 0x40FFD700);
            }
        }
        if (game == Game.ROULETTE && currentPick == 2) {
            centered(g, I18n.get("drunsk.casino.number_hint"), width / 2, height / 2 + 30, 0xFFAAAAAA);
        }
        if (resultLine != null) {
            centered(g, resultLine, width / 2, height / 2 + 60, resultColor);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // keys 1/2/3 pick A/B/C / клавиши 1/2/3 выбирают ставку
        if (event.key() == 49) { select(0); return true; }
        if (event.key() == 50) { select(1); return true; }
        if (event.key() == 51) { select(2); return true; }
        return super.keyPressed(event);
    }
}
