package com.drunsk.media.music;

import java.util.Objects;
import java.util.Optional;

/** Immutable provider-neutral playback snapshot. */
public record PlaybackState(Status status, MusicTrack track, long positionMillis, long updatedAtEpochMillis) {
    public enum Status { IDLE, PLAYING, PAUSED, STOPPED, ERROR }

    public PlaybackState {
        status = Objects.requireNonNull(status, "status");
        if (positionMillis < 0 || updatedAtEpochMillis < 0) throw new IllegalArgumentException("times must be non-negative");
        if (track == null && (status == Status.PLAYING || status == Status.PAUSED))
            throw new IllegalArgumentException("active playback requires a track");
        if (track != null && track.durationMillis() > 0) positionMillis = Math.min(positionMillis, track.durationMillis());
    }

    public Optional<MusicTrack> currentTrack() { return Optional.ofNullable(track); }
    public boolean isActive() { return status == Status.PLAYING || status == Status.PAUSED; }
    public static PlaybackState idle() { return new PlaybackState(Status.IDLE, null, 0, 0); }
}
