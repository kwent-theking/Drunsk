package com.drunsk.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for Drunsk screens: non-pausing, with text helpers over the 26.x
 * GuiGraphicsExtractor API (drawString/drawCenteredString are gone).
 * / База экранов Drunsk: без паузы, хелперы текста поверх нового
 * GuiGraphicsExtractor (26.x).
 */
public abstract class DrunskScreen extends Screen {

    protected DrunskScreen(Component title) {
        super(title);
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    protected void centered(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.centeredText(font, s, x, y, color);
    }

    protected void centered(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.centeredText(font, c, x, y, color);
    }

    protected void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(font, c, x, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
