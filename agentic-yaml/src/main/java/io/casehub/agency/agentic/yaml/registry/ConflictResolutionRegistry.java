package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.ConflictResolutionSpec;
import io.casehub.agency.deliberation.normative.ConflictResolutionStrategy;
import io.casehub.agency.deliberation.normative.EscalationResolution;
import io.casehub.agency.deliberation.normative.MostRestrictiveResolution;
import io.casehub.agency.deliberation.normative.PriorityResolution;
import io.casehub.agency.deliberation.normative.RecencyResolution;
import io.casehub.agency.deliberation.normative.SpecificityResolution;

public class ConflictResolutionRegistry {

    @SuppressWarnings("unchecked")
    public <T> ConflictResolutionStrategy<T> resolve(ConflictResolutionSpec spec) {
        return switch (spec) {
            case ConflictResolutionSpec.Priority p -> new PriorityResolution<>();
            case ConflictResolutionSpec.Specificity s -> new SpecificityResolution<>();
            case ConflictResolutionSpec.Recency r -> new RecencyResolution<>();
            case ConflictResolutionSpec.MostRestrictive mr ->
                    (ConflictResolutionStrategy<T>) (ConflictResolutionStrategy<?>) new MostRestrictiveResolution();
            case ConflictResolutionSpec.Escalation e ->
                    (ConflictResolutionStrategy<T>) new EscalationResolution<>(e.escalationDecision());
        };
    }
}
