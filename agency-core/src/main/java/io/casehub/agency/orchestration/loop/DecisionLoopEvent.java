package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.BudgetPressure;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public sealed interface DecisionLoopEvent {

    UUID caseId();
    String agentId();
    Instant timestamp();

    record LoopTriggered(
        UUID caseId,
        String agentId,
        DecisionScope scope,
        CommunicationMode communicationMode,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public LoopTriggered {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(scope);
            Objects.requireNonNull(communicationMode);
            Objects.requireNonNull(timestamp);
        }
    }

    record ObservationGathered(
        UUID caseId,
        String agentId,
        String key,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public ObservationGathered {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(key);
            Objects.requireNonNull(timestamp);
        }
    }

    record ReasoningStarted(
        UUID caseId,
        String agentId,
        String correlationId,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public ReasoningStarted {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(correlationId);
            Objects.requireNonNull(timestamp);
        }
    }

    record ReasoningCompleted(
        UUID caseId,
        String agentId,
        String correlationId,
        long tokensConsumed,
        long latencyMs,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public ReasoningCompleted {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(correlationId);
            Objects.requireNonNull(timestamp);
            if (tokensConsumed < 0) throw new IllegalArgumentException("tokensConsumed must be non-negative");
            if (latencyMs < 0) throw new IllegalArgumentException("latencyMs must be non-negative");
        }
    }

    record ActionDispatched(
        UUID caseId,
        String agentId,
        String outcomeType,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public ActionDispatched {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(outcomeType);
            Objects.requireNonNull(timestamp);
        }
    }

    record BudgetExhausted(
        UUID caseId,
        String agentId,
        String reason,
        BudgetPressure pressure,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public BudgetExhausted {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(pressure);
            Objects.requireNonNull(timestamp);
        }
    }

    record Degraded(
        UUID caseId,
        String agentId,
        String capability,
        String reason,
        boolean fallbackActive,
        Instant timestamp
    ) implements DecisionLoopEvent {
        public Degraded {
            Objects.requireNonNull(caseId);
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(capability);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(timestamp);
        }
    }
}
