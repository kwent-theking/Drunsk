package com.drunsk.ui;

import net.minecraft.client.resources.language.I18n;

/** Shared UI text helpers. / Общие помощники текста UI. */
public final class UiText {

    private UiText() {
    }

    /** Translate a relay failure reason; unknown ones pass through. / Перевод причины отказа релея; неизвестные — как есть. */
    public static String reason(String reason) {
        String key = "drunsk.reason." + reason;
        String t = I18n.get(key);
        return key.equals(t) ? reason : t;
    }
}
