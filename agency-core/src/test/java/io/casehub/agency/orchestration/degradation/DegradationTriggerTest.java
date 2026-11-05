package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DegradationTriggerTest {

    private AutonomyBudgetPool pool(int maxCalls) {
        return new AutonomyBudgetPool(
            new AutonomyBudgetPolicy(maxCalls, Long.MAX_VALUE, 0.0, maxCalls, Long.MAX_VALUE), "test");
    }

    @Test
    void budgetTrigger_doesNotFire_whenBudgetAvailable() {
        var p = pool(10);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        var trigger = new BudgetDegradationTrigger(view);
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void budgetTrigger_fires_whenBudgetExhausted() {
        var p = pool(1);
        var view = p.createView("agent-1", BudgetPriority.NORMAL);
        view.tryAcquire();
        var trigger = new BudgetDegradationTrigger(view);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    @Test
    void composite_doesNotFire_whenNoTriggersFire() {
        var trigger = new CompositeDegradationTrigger(List.of(() -> false, () -> false));
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void composite_fires_whenAnyTriggerFires() {
        var trigger = new CompositeDegradationTrigger(List.of(() -> false, () -> true));
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    @Test
    void composite_emptyList_doesNotFire() {
        var trigger = new CompositeDegradationTrigger(List.of());
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void cascadeTrigger_doesNotFire_beforeActivation() {
        var trigger = new CascadeDegradationTrigger();
        assertThat(trigger.shouldDegrade()).isFalse();
    }

    @Test
    void cascadeTrigger_fires_afterActivation() {
        var trigger = new CascadeDegradationTrigger();
        trigger.activate();
        assertThat(trigger.shouldDegrade()).isTrue();
    }
}
