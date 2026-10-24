package com.drunsk.media.gif;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable local GIF favorites/catalog configuration. */
public record GifCatalogConfig(int schemaVersion, List<GifEntry> catalog, Set<String> favoriteIds) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public GifCatalogConfig {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        catalog = List.copyOf(Objects.requireNonNull(catalog, "catalog"));
        favoriteIds = Set.copyOf(Objects.requireNonNull(favoriteIds, "favoriteIds"));
        Set<String> ids = catalog.stream().map(GifEntry::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!ids.containsAll(favoriteIds)) throw new IllegalArgumentException("favorites must refer to catalog entries");
    }

    public static GifCatalogConfig empty() {
        return new GifCatalogConfig(CURRENT_SCHEMA_VERSION, List.of(), Set.of());
    }
}
