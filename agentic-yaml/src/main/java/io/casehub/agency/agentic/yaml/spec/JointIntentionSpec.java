package io.casehub.agency.core.yaml.spec;

import java.util.Objects;
import java.util.Set;

public record JointIntentionSpec(
        String intentionId,
        String planDescription,
        Set<String> parties) {

    public JointIntentionSpec {
        Objects.requireNonNull(intentionId, "intentionId");
        Objects.requireNonNull(planDescription, "planDescription");
        if (parties == null || parties.isEmpty())
            throw new IllegalArgumentException("at least one party required");
        parties = Set.copyOf(parties);
    }
}
