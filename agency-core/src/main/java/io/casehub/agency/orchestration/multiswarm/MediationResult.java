package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public sealed interface MediationResult {

    record Resolved(
            String resolution,
            @Nullable Belief<SwarmConclusion> updatedBelief,
            List<CrossSwarmNotification> notifications
    ) implements MediationResult {
        public Resolved {
            Objects.requireNonNull(resolution);
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }
    }

    record FallbackApplied(
            String fallbackAction,
            String reason,
            List<CrossSwarmNotification> notifications
    ) implements MediationResult {
        public FallbackApplied {
            Objects.requireNonNull(fallbackAction);
            Objects.requireNonNull(reason);
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }
    }

    record Escalated(String reason) implements MediationResult {
        public Escalated {
            Objects.requireNonNull(reason);
        }
    }
}
