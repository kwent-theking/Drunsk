package com.drunsk.media.music;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Thread-safe playback snapshot store; contains no client/game runtime calls. */
public final class PlaybackStateService {
    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.idle());
    private final CopyOnWriteArrayList<Consumer<PlaybackState>> listeners = new CopyOnWriteArrayList<>();

    public PlaybackState snapshot() { return state.get(); }

    public AutoCloseable subscribe(Consumer<PlaybackState> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(state.get());
        return () -> listeners.remove(listener);
    }

    public void update(PlaybackState next) {
        state.set(Objects.requireNonNull(next, "next"));
        listeners.forEach(listener -> listener.accept(next));
    }

    public void clear() { update(PlaybackState.idle()); }
}
