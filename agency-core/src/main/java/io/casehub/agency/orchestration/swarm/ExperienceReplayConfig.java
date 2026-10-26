package io.casehub.agency.orchestration.swarm;

public record ExperienceReplayConfig(int maxTraces, double minSimilarity) {

    public ExperienceReplayConfig {
        if (maxTraces < 0) throw new IllegalArgumentException("maxTraces must be >= 0");
        if (minSimilarity < 0.0 || minSimilarity > 1.0)
            throw new IllegalArgumentException("minSimilarity must be in [0.0, 1.0]");
    }

    public static ExperienceReplayConfig defaults() {
        return new ExperienceReplayConfig(5, 0.3);
    }
}
