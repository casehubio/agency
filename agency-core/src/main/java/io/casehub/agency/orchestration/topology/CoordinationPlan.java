package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CoordinationPlan(
        List<DecisionLoopDescriptor> loopDescriptors,
        DecisionOutcomeExecutor executor,
        AutonomyBudgetPool pool,
        Map<String, DegradationTrigger> loopTriggers,
        Map<String, ContentSummariser<ObservationEvent, DecisionOutcome>> loopHeuristics
) {
    public CoordinationPlan {
        loopDescriptors = List.copyOf(loopDescriptors);
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(pool, "pool must not be null");
        loopTriggers = Map.copyOf(Objects.requireNonNull(loopTriggers));
        loopHeuristics = Map.copyOf(Objects.requireNonNull(loopHeuristics));
        if (loopDescriptors.isEmpty()) {
            throw new IllegalArgumentException(
                    "CoordinationPlan must have at least one loop descriptor");
        }
    }

    public CoordinationPlan(List<DecisionLoopDescriptor> loopDescriptors,
                            DecisionOutcomeExecutor executor,
                            AutonomyBudgetPool pool) {
        this(loopDescriptors, executor, pool, Map.of(), Map.of());
    }
}
