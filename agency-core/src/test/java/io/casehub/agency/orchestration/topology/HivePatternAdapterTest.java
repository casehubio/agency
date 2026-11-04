package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;

import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HivePatternAdapterTest {

    HivePatternAdapter adapter;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        adapter = new HivePatternAdapter(TestAgentProviders.noOp());
        topologyContext = new TopologyContext(
            UUID.randomUUID(),
            new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()),
            TestAgentProviders.noOp(),
            new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
            AutonomyBudgetPolicy.defaults());
    }

    @Test
    void execute_supervisor_returns_feedback() throws Exception {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = adapter.execute(
            OrchestrationPatternType.SUPERVISOR, "coordinator", outcome, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.agentId()).isEqualTo("coordinator");
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_debate_returns_feedback() throws Exception {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = adapter.execute(
            OrchestrationPatternType.DEBATE, "debater-1", outcome, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_voting_returns_feedback() throws Exception {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = adapter.execute(
            OrchestrationPatternType.VOTING, "voter-1", outcome, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_scope_gated_returns_violation() throws Exception {
        var gated = new DecisionOutcome.ScopeGated(
            io.casehub.api.model.coordination.DecisionType.PROVISIONING,
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
        var fb = adapter.execute(
            OrchestrationPatternType.SUPERVISOR, "coordinator", gated, topologyContext)
            .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
    }

    @Test
    void onFeedback_does_not_throw() {
        adapter.onFeedback(
            OrchestrationPatternType.SUPERVISOR,
            new CoordinationFeedback("agent-1",
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
                FeedbackSignal.SUCCESS));
    }
}
