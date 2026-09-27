package com.drunsk.ui;

import com.drunsk.relay.DrunskState;
import com.drunsk.relay.RelayClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Money transfer to another passport. / Перевод денег другому паспорту.
 */
public final class TransferScreen extends DrunskScreen {

    private final String toNick;
    private EditBox amountBox;
    private Button sendBtn;
    private String message;
    private int messageColor = 0xFFAAAAAA;

    public TransferScreen(String toNick) {
        super(Component.translatable("drunsk.screen.transfer"));
        this.toNick = toNick;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        amountBox = new EditBox(font, cx - 60, height / 2 - 10, 120, 20,
                Component.translatable("drunsk.transfer.amount"));
        amountBox.setMaxLength(12);
        // 26.x has no setFilter: reject non-digits on input / фильтра нет — отбиваем нецифры на вводе
        amountBox.setResponder(s -> {
            String digits = s.replaceAll("\\D", "");
            if (!digits.equals(s)) amountBox.setValue(digits);
            updateSend();
        });
        addRenderableWidget(amountBox);

        sendBtn = addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.send"), b -> doSend())
                .bounds(cx - 60, height / 2 + 20, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(cx - 49, height / 2 + 50, 98, 20).build());
        updateSend();
        setInitialFocus(amountBox);
    }

    private void updateSend() {
        long amount = parseAmount();
        sendBtn.active = amount > 0 && RelayClient.get().isReady();
    }

    private long parseAmount() {
        try {
            return Long.parseLong(amountBox.getValue());
        } catch (Exception e) {
            return -1;
        }
    }

    private void doSend() {
        long amount = parseAmount();
        if (amount <= 0) return;
        message = I18n.get("drunsk.msg.sending");
        messageColor = 0xFFAAAAAA;
        sendBtn.active = false;
        RelayClient.get().sendRequest("transfer", req -> {
            req.addProperty("toNick", toNick);
            req.addProperty("amount", amount);
        }).whenComplete((r, e) -> DrunskState.runOnRender(() -> {
            updateSend();
            if (e != null || !r.get("ok").getAsBoolean()) {
                String reason = (r != null && r.has("reason")) ? r.get("reason").getAsString() : "offline";
                message = I18n.get("drunsk.transfer.error", UiText.reason(reason));
                messageColor = 0xFFFF6666;
            } else {
                message = I18n.get("drunsk.transfer.done", String.format("%,d", amount), toNick,
                        String.format("%,d", r.get("newBalance").getAsLong()));
                messageColor = 0xFF88FF88;
                amountBox.setValue("");
                DrunskState.get().refreshMe();
            }
        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = width / 2;
        centered(g, Component.translatable("drunsk.screen.transfer"), cx, height / 2 - 60, 0xFFFFFFFF);
        centered(g, I18n.get("drunsk.transfer.to", toNick), cx, height / 2 - 44, 0xFFCCCCCC);
        if (message != null) {
            centered(g, message, cx, height / 2 + 78, messageColor);
        }
    }
}
