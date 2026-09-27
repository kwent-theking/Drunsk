package com.drunsk.ui;

import com.drunsk.DrunskConfig;
import com.drunsk.utils.freecam.Freecam;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Utilities screen: per-module toggles plus vanilla/packet mode switches for
 * the modules that have both. Settings persist into DrunskConfig immediately.
 * / Экран утилит: переключатели модулей + режим ваниль/пакеты там, где он
 * есть. Настройки сразу пишутся в DrunskConfig.
 */
public final class UtilsScreen extends DrunskScreen {

    private record Row(String key, BooleanSupplier get, Consumer<Boolean> set, boolean hasMode,
                       BooleanSupplier modeGet, Consumer<Boolean> modeSet) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Button> toggleBtns = new ArrayList<>();
    private final List<Button> modeBtns = new ArrayList<>();

    public UtilsScreen() {
        super(Component.translatable("drunsk.screen.utils"));
    }

    @Override
    protected void init() {
        rows.clear();
        toggleBtns.clear();
        modeBtns.clear();
        DrunskConfig.Util u = DrunskConfig.util;
        rows.add(new Row("autoFish", () -> u.autoFish, v -> u.autoFish = v,
                true, () -> u.autoFishPacket, v -> u.autoFishPacket = v));
        rows.add(new Row("autoTotem", () -> u.autoTotem, v -> u.autoTotem = v,
                true, () -> u.autoTotemPacket, v -> u.autoTotemPacket = v));
        rows.add(new Row("gamma", () -> u.gamma, v -> u.gamma = v,
                false, null, null));
        rows.add(new Row("freecam", () -> Freecam.isEnabled(), v -> toggleFreecam(v),
                false, null, null));
        rows.add(new Row("bedrockMiner", () -> u.bedrockMiner, v -> u.bedrockMiner = v,
                true, () -> u.bedrockPacket, v -> u.bedrockPacket = v));
        rows.add(new Row("autoEat", () -> u.autoEat, v -> u.autoEat = v,
                true, () -> u.autoEatPacket, v -> u.autoEatPacket = v));
        rows.add(new Row("autoTool", () -> u.autoTool, v -> u.autoTool = v,
                true, () -> u.autoToolPacket, v -> u.autoToolPacket = v));
        rows.add(new Row("autoBlock", () -> u.autoBlock, v -> u.autoBlock = v,
                true, () -> u.autoBlockPacket, v -> u.autoBlockPacket = v));

        int cx = width / 2;
        int y = height / 2 - 84;
        for (Row row : rows) {
            Button on = addRenderableWidget(Button.builder(stateLabel(row.get().getAsBoolean()), b -> {
                boolean nv = !row.get().getAsBoolean();
                row.set().accept(nv);
                refreshButtons();
                DrunskConfig.save();
            }).bounds(cx + 4, y, 30, 20).build());
            toggleBtns.add(on);
            // module name is drawn to the left of the toggle in extractRenderState
            // / название модуля рисуем слева от переключателя

            if (row.hasMode()) {
                Button mode = addRenderableWidget(Button.builder(modeLabel(row.modeGet().getAsBoolean()), b -> {
                    boolean nv = !row.modeGet().getAsBoolean();
                    row.modeSet().accept(nv);
                    refreshButtons();
                    DrunskConfig.save();
                }).bounds(cx + 40, y, 110, 20).build());
                modeBtns.add(mode);
            } else {
                modeBtns.add(null);
            }
            y += 22;
        }
        addRenderableWidget(Button.builder(Component.translatable("drunsk.btn.back"), b -> onClose())
                .bounds(cx - 49, y + 6, 98, 20).build());
    }

    private static void toggleFreecam(boolean want) {
        DrunskConfig.util.freecam = want;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (want != Freecam.isEnabled()) Freecam.toggle();
    }

    private void refreshButtons() {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            toggleBtns.get(i).setMessage(stateLabel(row.get().getAsBoolean()));
            Button mode = modeBtns.get(i);
            if (mode != null) {
                mode.setMessage(modeLabel(row.modeGet().getAsBoolean()));
                mode.active = row.get().getAsBoolean();
            }
        }
    }

    private static Component stateLabel(boolean on) {
        return Component.literal(on ? ChatFormatting.GREEN + "●" + ChatFormatting.RESET
                : ChatFormatting.RED + "○");
    }

    private static Component modeLabel(boolean packet) {
        return Component.translatable(packet ? "drunsk.utils.mode.packet" : "drunsk.utils.mode.vanilla");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        centered(g, ChatFormatting.GOLD + I18n.get("drunsk.screen.utils"), width / 2, 18, 0xFFFFD700);
        int cx = width / 2;
        int y = height / 2 - 84;
        for (Row row : rows) {
            String name = I18n.get("drunsk.utils." + row.key());
            text(g, name, cx - 4 - font.width(name), y + 6, 0xFFDDDDDD);
            y += 22;
        }
    }
}
