package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Objects;
import java.util.Set;

public record JointIntentionSpec(
        @JsonAlias("intention-id") String intentionId,
        @JsonAlias("plan-description") String planDescription,
        Set<String> parties) {

    public JointIntentionSpec {
        Objects.requireNonNull(intentionId, "intentionId");
        Objects.requireNonNull(planDescription, "planDescription");
        if (parties == null || parties.isEmpty())
            throw new IllegalArgumentException("at least one party required");
        parties = Set.copyOf(parties);
    }
}
