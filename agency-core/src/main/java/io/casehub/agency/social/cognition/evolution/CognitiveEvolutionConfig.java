package io.casehub.agency.social.cognition.evolution;

public record CognitiveEvolutionConfig(
        double curiosityFloor,
        double curiosityFullEngage,
        boolean circuitBreakerMoodEnabled) {

    public CognitiveEvolutionConfig {
        if (curiosityFloor < 0 || curiosityFloor > 1)
            throw new IllegalArgumentException("curiosityFloor must be in [0, 1]");
        if (curiosityFullEngage < 0 || curiosityFullEngage > 1)
            throw new IllegalArgumentException("curiosityFullEngage must be in [0, 1]");
        if (curiosityFloor > curiosityFullEngage)
            throw new IllegalArgumentException("curiosityFloor must be <= curiosityFullEngage");
    }

    public static CognitiveEvolutionConfig defaults() {
        return new CognitiveEvolutionConfig(0.3, 0.7, true);
    }

    public static CognitiveEvolutionConfig alwaysTick() {
        return new CognitiveEvolutionConfig(0.0, 0.0, true);
    }
}
