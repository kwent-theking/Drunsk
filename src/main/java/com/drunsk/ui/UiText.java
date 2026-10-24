package com.drunsk.ui;

import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

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

    /**
     * Thousands-grouped number that always uses ASCII commas.
     * Default locale (ru) groups with a no-break space the MC font can't draw —
     * that produced the garbage after the digits.
     * / Число с разделителями тысяч, всегда ASCII-запятая. Дефолтная локаль (ru)
     * разделяет неразрывным пробелом, которого нет в шрифте MC — отсюда кракозябры.
     */
    public static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
