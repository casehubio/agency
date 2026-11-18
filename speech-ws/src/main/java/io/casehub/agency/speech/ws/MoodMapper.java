package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.Affect;
import org.jspecify.annotations.Nullable;

public class MoodMapper {

    private static final double DEAD_ZONE = 0.25;
    private static final double HYSTERESIS = 0.1;
    private static final long DWELL_MS = 5000;

    private @Nullable String adopted;
    private long adoptedAt;
    private long deadZoneEnteredAt = -1;

    public @Nullable String map(Affect affect, long now) {
        String candidate = classify(affect);
        if (candidate == null) {
            if (adopted == null) return null;
            if (deadZoneEnteredAt < 0) deadZoneEnteredAt = now;
            if ((now - deadZoneEnteredAt) < DWELL_MS) return adopted;
            adopted = null;
            deadZoneEnteredAt = -1;
            return null;
        }
        deadZoneEnteredAt = -1;
        if (candidate.equals(adopted)) {
            return adopted;
        }
        double magnitude = magnitude(affect);
        double threshold = adopted == null ? DEAD_ZONE : DEAD_ZONE + HYSTERESIS;
        if (magnitude < threshold) return adopted;
        if (adopted != null && (now - adoptedAt) < DWELL_MS) return adopted;
        adopted = candidate;
        adoptedAt = now;
        return adopted;
    }

    public @Nullable String current() { return adopted; }

    static @Nullable String classify(Affect a) {
        if (magnitude(a) < DEAD_ZONE) return null;
        boolean pPos = a.pleasure() >= 0;
        boolean aPos = a.arousal() >= 0;
        boolean dPos = a.dominance() >= 0;
        if (pPos && aPos && dPos) return "exuberant";
        if (pPos && aPos && !dPos) return "dependent";
        if (pPos && !aPos && dPos) return "relaxed";
        if (pPos && !aPos && !dPos) return "docile";
        if (!pPos && aPos && dPos) return "hostile";
        if (!pPos && aPos && !dPos) return "anxious";
        if (!pPos && !aPos && dPos) return "disdainful";
        return "bored";
    }

    private static double magnitude(Affect a) {
        return Math.sqrt(a.pleasure() * a.pleasure()
                       + a.arousal() * a.arousal()
                       + a.dominance() * a.dominance());
    }
}
