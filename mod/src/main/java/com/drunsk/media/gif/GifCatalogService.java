package com.drunsk.media.gif;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Thread-safe in-memory GIF catalog with atomic local persistence. JSON codec is injected. */
public final class GifCatalogService {
    public interface Codec {
        GifCatalogConfig read(Path file) throws IOException;
        void write(Path file, GifCatalogConfig config) throws IOException;
    }

    private final Path file;
    private final Codec codec;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private GifCatalogConfig config;

    public GifCatalogService(Path file, Codec codec) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.config = GifCatalogConfig.empty();
    }

    public GifCatalogConfig snapshot() {
        lock.readLock().lock();
        try { return config; } finally { lock.readLock().unlock(); }
    }

    public GifCatalogConfig load() throws IOException {
        lock.writeLock().lock();
        try {
            if (Files.exists(file)) config = Objects.requireNonNull(codec.read(file), "decoded config");
            return config;
        } finally { lock.writeLock().unlock(); }
    }

    public void add(GifEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        lock.writeLock().lock();
        try {
            List<GifEntry> entries = new ArrayList<>(config.catalog());
            if (entries.stream().anyMatch(item -> item.id().equals(entry.id()))) throw new IllegalArgumentException("duplicate GIF id: " + entry.id());
            entries.add(entry);
            persist(new GifCatalogConfig(GifCatalogConfig.CURRENT_SCHEMA_VERSION, entries, config.favoriteIds()));
        } finally { lock.writeLock().unlock(); }
    }

    public boolean remove(String id) throws IOException {
        lock.writeLock().lock();
        try {
            List<GifEntry> entries = config.catalog().stream().filter(entry -> !entry.id().equals(id)).toList();
            if (entries.size() == config.catalog().size()) return false;
            Set<String> favorites = new LinkedHashSet<>(config.favoriteIds());
            favorites.remove(id);
            persist(new GifCatalogConfig(GifCatalogConfig.CURRENT_SCHEMA_VERSION, entries, favorites));
            return true;
        } finally { lock.writeLock().unlock(); }
    }

    public boolean setFavorite(String id, boolean favorite) throws IOException {
        lock.writeLock().lock();
        try {
            if (config.catalog().stream().noneMatch(entry -> entry.id().equals(id))) return false;
            Set<String> favorites = new LinkedHashSet<>(config.favoriteIds());
            boolean changed = favorite ? favorites.add(id) : favorites.remove(id);
            if (changed) persist(new GifCatalogConfig(GifCatalogConfig.CURRENT_SCHEMA_VERSION, config.catalog(), favorites));
            return changed;
        } finally { lock.writeLock().unlock(); }
    }

    private void persist(GifCatalogConfig updated) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = parent == null
                ? Files.createTempFile(file.getFileName().toString(), ".tmp")
                : Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            codec.write(temp, updated);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            config = updated;
        } finally { Files.deleteIfExists(temp); }
    }
}
