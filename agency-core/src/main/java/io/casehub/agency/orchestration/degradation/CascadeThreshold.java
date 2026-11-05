package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetPriority;

import java.util.Map;
import java.util.Set;

public interface CascadeThreshold {
    boolean isBreached(Set<String> degradedLoopIds, int totalLoops,
                       Map<String, BudgetPriority> loopPriorities);
}
