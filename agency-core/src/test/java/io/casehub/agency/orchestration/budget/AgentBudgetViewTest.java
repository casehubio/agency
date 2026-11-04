package io.casehub.agency.orchestration.budget;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgentBudgetViewTest {

    private AutonomyBudgetPool pool(int maxCalls, long maxTokens, int perAgent, long tokensPerAgent) {
        return new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(maxCalls, maxTokens, 0.0, perAgent, tokensPerAgent), "test");
    }

    @Test
    void tryAcquire_succeedsUpToLocalLimit() {
        var p = pool(100, 1_000_000, 3, 100_000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(view.callsUsed()).isEqualTo(3);
    }

    @Test
    void tryAcquire_failsWhenPoolExhausted() {
        var p = pool(2, 1_000_000, 2, 1_000_000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(view.callsUsed()).isEqualTo(2);
    }

    @Test
    void tryAcquire_failedDoesNotIncrementLocal() {
        var p = pool(100, 1_000_000, 1, 100_000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.tryAcquire();
        view.tryAcquire();
        assertThat(view.callsUsed()).isEqualTo(1);
    }

    @Test
    void recordTokens_propagatesToPool() {
        var p = pool(100, 1_000_000, 10, 100_000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.recordTokens("c1", 250);
        assertThat(view.tokensUsed()).isEqualTo(250);
        assertThat(p.tokensUsed()).isEqualTo(250);
    }

    @Test
    void isBudgetExhausted_whenLocalCallsExhausted() {
        var p = pool(100, 1_000_000, 1, 100_000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        assertThat(view.isBudgetExhausted()).isFalse();
        view.tryAcquire();
        assertThat(view.isBudgetExhausted()).isTrue();
    }

    @Test
    void isBudgetExhausted_whenLocalTokensExhausted() {
        var p = pool(100, 1_000_000, 10, 500);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.recordTokens("c1", 600);
        assertThat(view.isBudgetExhausted()).isTrue();
    }

    @Test
    void tryAcquire_deniedWhenLocalTokensExhausted() {
        var p = pool(100, 1_000_000, 10, 500);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.recordTokens("c1", 600);
        assertThat(view.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
    }

    @Test
    void topologyId_inheritedFromPool() {
        var p = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "my-topo");
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        assertThat(view.topologyId()).isEqualTo("my-topo");
    }

    @Test
    void agentId_preserved() {
        var p = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "t");
        var view = p.createView("agent-42", BudgetPriority.NORMAL);
        assertThat(view.agentId()).isEqualTo("agent-42");
    }

    @Test
    void priority_preserved() {
        var p    = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "t");
        var view = p.createView("agent-1", BudgetPriority.RESERVED);
        assertThat(view.priority()).isEqualTo(BudgetPriority.RESERVED);
    }


    @Test
    void localPressure_reflectsLocalUsage() {
        var p = pool(100, 1_000_000, 10, 1000);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.tryAcquire();
        view.recordTokens("c1", 500);
        assertThat(view.localPressure().utilisation()).isCloseTo(0.5, within(0.01));
    }

    @Test
    void multipleViews_sharePoolBudget() {
        var p = pool(3, 1_000_000, 3, 1_000_000);
        var v1 = p.createView("agent-1", BudgetPriority.NORMAL);
        var v2 = p.createView("agent-2", BudgetPriority.NORMAL);
        v1.tryAcquire();
        v1.tryAcquire();
        v2.tryAcquire();
        assertThat(v2.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(p.callsUsed()).isEqualTo(3);
    }

    @Test
    void reservedView_accessesReservePartition() {
        var p = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 100_000, 0.2, 10, 100_000), "t");
        var normalView = p.createView("worker", BudgetPriority.NORMAL);
        var reservedView = p.createView("coordinator", BudgetPriority.RESERVED);

        for (int i = 0; i < 8; i++) {
            normalView.tryAcquire();
        }
        assertThat(normalView.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(reservedView.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(reservedView.tryAcquire()).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(reservedView.tryAcquire()).isInstanceOf(BudgetGrant.Denied.class);
    }
}
