package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class SwarmOutcomeMapper {

    private final String signatureId;

    public SwarmOutcomeMapper(String signatureId) {
        this.signatureId = Objects.requireNonNull(signatureId);
    }

    static String normaliseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {return "FAILED";}
        return raw.toUpperCase().strip();
    }

    public VariantOutcome map(String variantId,
                              String convergenceOutcome,
                              double similarityScore,
                              @Nullable Duration executionDuration) {
        return new VariantOutcome(
                variantId,
                signatureId,
                normaliseOutcome(convergenceOutcome),
                similarityScore,
                executionDuration,
                Instant.now());
    }
}
