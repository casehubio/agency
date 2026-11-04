package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;

import java.util.List;
import java.util.Objects;

public record CoordinationPlan(
        List<DecisionLoopDescriptor> loopDescriptors,
        DecisionOutcomeExecutor executor,
        AutonomyBudgetPool pool
) {
    public CoordinationPlan {
        loopDescriptors = List.copyOf(loopDescriptors);
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(pool, "pool must not be null");
        if (loopDescriptors.isEmpty()) {
            throw new IllegalArgumentException(
                    "CoordinationPlan must have at least one loop descriptor");
        }
    }
}
