package com.drunsk.media.gif;

import java.net.URI;
import java.util.Objects;

/** Local GIF catalog item; contains metadata only, no Minecraft dependencies. */
public record GifEntry(String id, String title, URI source, String category, long addedAtEpochMillis) {
    public GifEntry {
        id = requireText(id, "id");
        title = Objects.requireNonNullElse(title, "").strip();
        source = Objects.requireNonNull(source, "source");
        category = Objects.requireNonNullElse(category, "").strip();
        if (addedAtEpochMillis < 0) throw new IllegalArgumentException("addedAtEpochMillis must be non-negative");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
