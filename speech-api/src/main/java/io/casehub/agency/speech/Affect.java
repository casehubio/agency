package io.casehub.agency.speech;

public record Affect(double pleasure, double arousal, double dominance) {
    public Affect {
        if (pleasure < -1 || pleasure > 1) throw new IllegalArgumentException("pleasure must be in [-1, 1]");
        if (arousal < -1 || arousal > 1) throw new IllegalArgumentException("arousal must be in [-1, 1]");
        if (dominance < -1 || dominance > 1) throw new IllegalArgumentException("dominance must be in [-1, 1]");
    }
}
