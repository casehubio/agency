package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;

import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CoordinationPlanTest {

    static final DecisionLoopDescriptor DESCRIPTOR = new DecisionLoopDescriptor(
            "test-loop", ObservationStrategy.EVENT_DRIVEN, DecisionScope.FULL_AUTONOMY,
            CommunicationMode.BROADCAST, BudgetPriority.NORMAL, List.of());

    static final DecisionOutcomeExecutor EXECUTOR = (agentId, outcome, ctx) ->
                                                            CompletableFuture.completedFuture(
                                                                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));

    static final AutonomyBudgetPool POOL = new AutonomyBudgetPool(
            AutonomyBudgetPolicy.defaults(), "test-topo");

    @Test
    void creates_with_descriptors_and_executor() {
        var plan = new CoordinationPlan(List.of(DESCRIPTOR), EXECUTOR, POOL);
        assertThat(plan.loopDescriptors()).containsExactly(DESCRIPTOR);
        assertThat(plan.executor()).isEqualTo(EXECUTOR);
        assertThat(plan.pool()).isEqualTo(POOL);
    }

    @Test
    void rejects_empty_descriptors() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CoordinationPlan(List.of(), EXECUTOR, POOL))
                .withMessageContaining("at least one");
    }

    @Test
    void rejects_null_executor() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CoordinationPlan(List.of(DESCRIPTOR), null, POOL))
                .withMessageContaining("executor");
    }

    @Test
    void rejects_null_pool() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CoordinationPlan(List.of(DESCRIPTOR), EXECUTOR, null))
                .withMessageContaining("pool");
    }

    @Test
    void descriptors_are_immutable_copy() {
        var mutable = new ArrayList<>(List.of(DESCRIPTOR));
        var plan    = new CoordinationPlan(mutable, EXECUTOR, POOL);
        mutable.clear();
        assertThat(plan.loopDescriptors()).hasSize(1);
    }
}
