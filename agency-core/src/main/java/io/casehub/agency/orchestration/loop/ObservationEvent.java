package io.casehub.agency.orchestration.loop;

import java.time.Instant;
import java.util.Objects;

public record ObservationEvent(String key, Object value, Instant timestamp) {

    public ObservationEvent {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static ObservationEvent of(String key, Object value) {
        return new ObservationEvent(key, value, Instant.now());
    }
}
