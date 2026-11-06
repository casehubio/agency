package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HivePatternAdapterTest {

    private static TopologyContext contextWithRoles(
            io.casehub.platform.agent.AgentProvider agentProvider) {
        var roles = new LinkedHashMap<String, AgentRole>();
        roles.put("coordinator", AgentRole.COORDINATOR);
        roles.put("worker-1", AgentRole.of("worker-1", StructuralRole.CONTRACTOR));
        roles.put("worker-2", AgentRole.of("worker-2", StructuralRole.CONTRACTOR));

        return new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, roles),
                agentProvider,
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());
    }

    @Test
    void executeSupervisor_invokes_agentProvider() throws Exception {
        var invocationCount = new AtomicInteger();
        var provider = TestAgentProviders.counting(invocationCount, "approved");
        var adapter = new HivePatternAdapter(provider);
        var ctx = contextWithRoles(provider);
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = adapter.execute(
                OrchestrationPatternType.SUPERVISOR, "worker-1", outcome, ctx)
                .toCompletableFuture().get();

        assertThat(invocationCount.get()).isGreaterThan(0);
        assertThat(fb.agentId()).isEqualTo("worker-1");
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void executeDebate_invokes_agentProvider() throws Exception {
        var invocationCount = new AtomicInteger();
        var provider = TestAgentProviders.counting(invocationCount, "I agree with the outcome");
        var adapter = new HivePatternAdapter(provider);
        var ctx = contextWithRoles(provider);
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = adapter.execute(
                OrchestrationPatternType.DEBATE, "worker-1", outcome, ctx)
                .toCompletableFuture().get();

        assertThat(invocationCount.get()).isGreaterThan(0);
        assertThat(fb.agentId()).isEqualTo("worker-1");
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void executeVoting_invokes_agentProvider() throws Exception {
        var invocationCount = new AtomicInteger();
        var provider = TestAgentProviders.counting(invocationCount, "APPROVE");
        var adapter = new HivePatternAdapter(provider);
        var ctx = contextWithRoles(provider);
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = adapter.execute(
                OrchestrationPatternType.VOTING, "worker-1", outcome, ctx)
                .toCompletableFuture().get();

        assertThat(invocationCount.get()).isGreaterThan(0);
        assertThat(fb.agentId()).isEqualTo("worker-1");
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void executeSupervisor_with_failing_provider_returns_failure() throws Exception {
        var adapter = new HivePatternAdapter(TestAgentProviders.failing());
        var ctx     = contextWithRoles(TestAgentProviders.failing());
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());

        var fb = adapter.execute(
                                OrchestrationPatternType.SUPERVISOR, "worker-1", outcome, ctx)
                        .toCompletableFuture().get();

        assertThat(fb.agentId()).isEqualTo("worker-1");
        assertThat(fb.signal()).isNotNull();
    }

    @Test
    void execute_scope_gated_returns_violation() throws Exception {
        var adapter = new HivePatternAdapter(TestAgentProviders.noOp());
        var ctx = contextWithRoles(TestAgentProviders.noOp());
        var gated = new DecisionOutcome.ScopeGated(
                io.casehub.api.model.coordination.DecisionType.PROVISIONING,
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));

        var fb = adapter.execute(
                OrchestrationPatternType.SUPERVISOR, "coordinator", gated, ctx)
                .toCompletableFuture().get();

        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
    }

    @Test
    void onFeedback_does_not_throw() {
        var adapter = new HivePatternAdapter(TestAgentProviders.noOp());
        adapter.onFeedback(
                OrchestrationPatternType.SUPERVISOR,
                new CoordinationFeedback("agent-1",
                        new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
                        FeedbackSignal.SUCCESS));
    }
}
