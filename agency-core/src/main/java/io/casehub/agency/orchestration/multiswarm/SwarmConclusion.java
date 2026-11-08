package io.casehub.agency.orchestration.multiswarm;

import java.util.Objects;
import java.util.UUID;

public record SwarmConclusion(UUID swarmId, String domain, String conclusion, double confidence) {
    public SwarmConclusion {
        Objects.requireNonNull(swarmId);
        Objects.requireNonNull(domain);
        Objects.requireNonNull(conclusion);
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
    }
}
