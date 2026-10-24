package com.drunsk.media.music;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Backend-neutral music source and playback adapter. Implementations may bridge any player. */
public interface MusicProvider {
    String id();
    CompletionStage<List<MusicTrack>> search(String query);
    CompletionStage<Optional<MusicTrack>> resolve(String trackId);
    CompletionStage<Void> play(MusicTrack track);
    CompletionStage<Void> pause();
    CompletionStage<Void> resume();
    CompletionStage<Void> stop();
    CompletionStage<Void> seek(long positionMillis);
}
