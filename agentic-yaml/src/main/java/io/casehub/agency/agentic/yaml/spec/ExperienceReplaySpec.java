package io.casehub.agency.core.yaml.spec;

public record ExperienceReplaySpec(int maxTraces, double minSimilarity) {

    public ExperienceReplaySpec {
        if (maxTraces < 0) throw new IllegalArgumentException("maxTraces must be >= 0");
        if (minSimilarity < 0.0 || minSimilarity > 1.0)
            throw new IllegalArgumentException("minSimilarity must be in [0.0, 1.0]");
    }

    public ExperienceReplaySpec() {
        this(5, 0.3);
    }
}
