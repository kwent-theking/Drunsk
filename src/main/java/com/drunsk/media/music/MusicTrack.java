package com.drunsk.media.music;

import java.net.URI;
import java.util.Objects;

/** Provider-independent track metadata. */
public record MusicTrack(String id, String title, String artist, URI resource, long durationMillis) {
    public MusicTrack {
        id = require(id, "id");
        title = Objects.requireNonNullElse(title, "").strip();
        artist = Objects.requireNonNullElse(artist, "").strip();
        resource = Objects.requireNonNull(resource, "resource");
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis must be non-negative");
    }
    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
