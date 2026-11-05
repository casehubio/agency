package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetPriority;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PriorityWeightedThreshold implements CascadeThreshold {

    private final double fraction;
    private final Map<BudgetPriority, Double> weights;

    public PriorityWeightedThreshold(double fraction, Map<BudgetPriority, Double> weights) {
        if (fraction <= 0.0 || fraction > 1.0)
            throw new IllegalArgumentException("fraction must be in (0.0, 1.0]");
        this.fraction = fraction;
        this.weights = Map.copyOf(Objects.requireNonNull(weights));
    }

    @Override
    public boolean isBreached(Set<String> degradedLoopIds, int totalLoops,
                              Map<String, BudgetPriority> loopPriorities) {
        if (totalLoops == 0) return false;
        double totalWeight = loopPriorities.values().stream()
            .mapToDouble(p -> weights.getOrDefault(p, 1.0)).sum();
        if (totalWeight == 0.0) return false;
        double degradedWeight = degradedLoopIds.stream()
            .filter(loopPriorities::containsKey)
            .mapToDouble(id -> weights.getOrDefault(loopPriorities.get(id), 1.0)).sum();
        return degradedWeight / totalWeight >= fraction;
    }
}
