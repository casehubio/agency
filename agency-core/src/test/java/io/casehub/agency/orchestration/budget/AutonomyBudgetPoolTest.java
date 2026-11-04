package io.casehub.agency.orchestration.budget;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AutonomyBudgetPoolTest {

    @Test
    void tryAcquire_normalSucceedsUnderLimit() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 100_000, 0.2, 5, 50_000), "test-topo");
        var grant = pool.tryAcquire(BudgetPriority.NORMAL);
        assertThat(grant).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(pool.callsUsed()).isEqualTo(1);
    }

    @Test
    void tryAcquire_normalDeniedWhenReserveReached() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 100_000, 0.2, 10, 100_000), "test-topo");
        for (int i = 0; i < 8; i++) {
            assertThat(pool.tryAcquire(BudgetPriority.NORMAL)).isInstanceOf(BudgetGrant.Granted.class);
        }
        var denied = pool.tryAcquire(BudgetPriority.NORMAL);
        assertThat(denied).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(pool.callsUsed()).isEqualTo(8);
    }

    @Test
    void tryAcquire_reservedAccessesFullPool() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 100_000, 0.2, 10, 100_000), "test-topo");
        for (int i = 0; i < 8; i++) {
            pool.tryAcquire(BudgetPriority.NORMAL);
        }
        assertThat(pool.tryAcquire(BudgetPriority.RESERVED)).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(pool.tryAcquire(BudgetPriority.RESERVED)).isInstanceOf(BudgetGrant.Granted.class);
        assertThat(pool.tryAcquire(BudgetPriority.RESERVED)).isInstanceOf(BudgetGrant.Denied.class);
    }

    @Test
    void tryAcquire_deniedDoesNotIncrementCounter() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(2, 100_000, 0.0, 2, 100_000), "test-topo");
        pool.tryAcquire(BudgetPriority.NORMAL);
        pool.tryAcquire(BudgetPriority.NORMAL);
        pool.tryAcquire(BudgetPriority.NORMAL);
        assertThat(pool.callsUsed()).isEqualTo(2);
    }

    @Test
    void recordTokens_accumulates() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(50, 1000, 0.0, 10, 500), "test-topo");
        pool.recordTokens("c1", 400);
        pool.recordTokens("c2", 300);
        assertThat(pool.tokensUsed()).isEqualTo(700);
    }

    @Test
    void tryAcquire_deniedWhenTokensExhausted() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(50, 500, 0.0, 10, 500), "test-topo");
        pool.recordTokens("c1", 600);
        var denied = pool.tryAcquire(BudgetPriority.NORMAL);
        assertThat(denied).isInstanceOf(BudgetGrant.Denied.class);
    }

    @Test
    void tryAcquire_normalDeniedByTokenReserve() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(50, 1000, 0.2, 10, 1000), "test-topo");
        pool.recordTokens("c1", 801);
        assertThat(pool.tryAcquire(BudgetPriority.NORMAL)).isInstanceOf(BudgetGrant.Denied.class);
        assertThat(pool.tryAcquire(BudgetPriority.RESERVED)).isInstanceOf(BudgetGrant.Granted.class);
    }

    @Test
    void pressure_reflectsWorstDimension() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 1000, 0.0, 10, 1000), "test-topo");
        pool.tryAcquire(BudgetPriority.NORMAL);
        pool.recordTokens("c1", 500);
        assertThat(pool.currentPressure().utilisation()).isCloseTo(0.5, within(0.01));
    }

    @Test
    void pressure_cappedAtOne() {
        var pool = new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(10, 1000, 0.0, 10, 1000), "test-topo");
        pool.recordTokens("c1", 1500);
        assertThat(pool.currentPressure().utilisation()).isEqualTo(1.0);
    }

    @Test
    void topologyId_preserved() {
        var pool = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "my-topo-123");
        assertThat(pool.topologyId()).isEqualTo("my-topo-123");
    }

    @Test
    void grantedCarriesCorrelationId() {
        var pool = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "t");
        var grant = (BudgetGrant.Granted) pool.tryAcquire(BudgetPriority.NORMAL);
        assertThat(grant.correlationId()).isNotNull().isNotEmpty();
    }

    @Test
    void createView_returnsView() {
        var pool = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "t");
        var view = pool.createView("agent-1", BudgetPriority.NORMAL);
        assertThat(view).isNotNull();
        assertThat(view.agentId()).isEqualTo("agent-1");
    }

    @Test
    void releaseView_removesView() {
        var pool = new AutonomyBudgetPool(AutonomyBudgetPolicy.defaults(), "t");
        pool.createView("agent-1", BudgetPriority.NORMAL);
        pool.releaseView("agent-1");
    }
}
