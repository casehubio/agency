package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.BudgetPriority;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeThresholdTest {

    @Test
    void simpleFraction_notBreached_belowThreshold() {
        var threshold = new SimpleFractionThreshold(0.5);
        var result = threshold.isBreached(Set.of("loop-1"), 3,
            Map.of("loop-1", BudgetPriority.NORMAL, "loop-2", BudgetPriority.NORMAL, "loop-3", BudgetPriority.NORMAL));
        assertThat(result).isFalse();
    }

    @Test
    void simpleFraction_breached_atThreshold() {
        var threshold = new SimpleFractionThreshold(0.5);
        var result = threshold.isBreached(Set.of("loop-1", "loop-2"), 3,
            Map.of("loop-1", BudgetPriority.NORMAL, "loop-2", BudgetPriority.NORMAL, "loop-3", BudgetPriority.NORMAL));
        assertThat(result).isTrue();
    }

    @Test
    void simpleFraction_notBreached_zeroTotalLoops() {
        var threshold = new SimpleFractionThreshold(0.5);
        assertThat(threshold.isBreached(Set.of(), 0, Map.of())).isFalse();
    }

    @Test
    void priorityWeighted_reservedCountsMore() {
        var weights = Map.of(BudgetPriority.RESERVED, 2.0, BudgetPriority.NORMAL, 1.0);
        var threshold = new PriorityWeightedThreshold(0.5, weights);
        var priorities = Map.of("coord", BudgetPriority.RESERVED, "w1", BudgetPriority.NORMAL, "w2", BudgetPriority.NORMAL);
        assertThat(threshold.isBreached(Set.of("coord"), 3, priorities)).isTrue();
    }

    @Test
    void priorityWeighted_normalOnlyNotEnough() {
        var weights = Map.of(BudgetPriority.RESERVED, 2.0, BudgetPriority.NORMAL, 1.0);
        var threshold = new PriorityWeightedThreshold(0.5, weights);
        var priorities = Map.of("coord", BudgetPriority.RESERVED, "w1", BudgetPriority.NORMAL, "w2", BudgetPriority.NORMAL);
        assertThat(threshold.isBreached(Set.of("w1"), 3, priorities)).isFalse();
    }
}
