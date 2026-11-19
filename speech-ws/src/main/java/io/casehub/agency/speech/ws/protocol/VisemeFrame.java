package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;

public record VisemeFrame(String viseme, @JsonAlias("start-ms") long startMs,
                          @JsonAlias("end-ms") long endMs, double weight) {
    public VisemeFrame {
        if (viseme == null || viseme.isBlank()) {throw new IllegalArgumentException("viseme required");}
        if (endMs < startMs) {throw new IllegalArgumentException("endMs < startMs");}
        if (weight < 0 || weight > 1.0) {throw new IllegalArgumentException("weight must be 0..1");}
    }

    public VisemeFrame(String viseme, long startMs, long endMs) {
        this(viseme, startMs, endMs, 1.0);
    }

    public long durationMs() {
        return endMs - startMs;
    }
}
