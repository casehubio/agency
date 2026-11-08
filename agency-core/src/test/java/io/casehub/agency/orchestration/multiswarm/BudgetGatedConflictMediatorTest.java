package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetGatedConflictMediatorTest {

    static final MediationResult PRIMARY_RESULT =
            new MediationResult.Resolved("llm-resolved", null, List.of());
    static final MediationResult FALLBACK_RESULT =
            new MediationResult.FallbackApplied("heuristic", "fallback", List.of());

    ConflictMediator primary = ctx -> PRIMARY_RESULT;
    ConflictMediator fallback = ctx -> FALLBACK_RESULT;
    ConflictContext context = new ConflictContext(
            new CrossSwarmNotification(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    NotificationType.CONFLICT,
                    new MultiSwarmSignal.Finding(java.util.UUID.randomUUID(), java.util.Set.of(),
                            System.currentTimeMillis(), "d", "c", 0.5, java.util.Map.of()),
                    0.8),
            new io.casehub.agency.deliberation.belief.BeliefSet<>(),
            List.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

    @Test
    void uses_fallback_when_pool_not_bound() {
        var gated = new BudgetGatedConflictMediator(primary, fallback);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(FALLBACK_RESULT);
    }

    @Test
    void uses_primary_when_budget_granted() {
        var gated = new BudgetGatedConflictMediator(primary, fallback);
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(100, 100_000L, 0.5, 10, 10_000L), "test");
        gated.bindPool(pool);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(PRIMARY_RESULT);
    }

    @Test
    void falls_back_on_primary_exception() {
        ConflictMediator throwingPrimary = ctx -> { throw new MediationException("LLM failed"); };
        var gated = new BudgetGatedConflictMediator(throwingPrimary, fallback);
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(100, 100_000L, 0.5, 10, 10_000L), "test");
        gated.bindPool(pool);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(FALLBACK_RESULT);
    }

    @Test
    void uses_fallback_when_budget_denied() {
        var gated = new BudgetGatedConflictMediator(primary, fallback);
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(1, 1L, 0.0, 1, 1L), "test");
        gated.bindPool(pool);
        // Exhaust the single-call budget
        pool.tryAcquire(io.casehub.agency.orchestration.budget.BudgetPriority.NORMAL);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(FALLBACK_RESULT);
    }
}
