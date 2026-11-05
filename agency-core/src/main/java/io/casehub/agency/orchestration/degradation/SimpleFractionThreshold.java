package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetPriority;

import java.util.Map;
import java.util.Set;

public class SimpleFractionThreshold implements CascadeThreshold {

    private final double fraction;

    public SimpleFractionThreshold(double fraction) {
        if (fraction <= 0.0 || fraction > 1.0)
            throw new IllegalArgumentException("fraction must be in (0.0, 1.0]");
        this.fraction = fraction;
    }

    @Override
    public boolean isBreached(Set<String> degradedLoopIds, int totalLoops,
                              Map<String, BudgetPriority> loopPriorities) {
        if (totalLoops == 0) return false;
        return (double) degradedLoopIds.size() / totalLoops >= fraction;
    }
}
