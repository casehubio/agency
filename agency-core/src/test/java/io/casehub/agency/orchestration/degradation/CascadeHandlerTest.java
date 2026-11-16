package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.platform.agent.budget.BudgetPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeHandlerTest {

    private SwarmDegradedEvent degraded(String capability, boolean fallbackActive) {
        return new SwarmDegradedEvent(capability, "reason", fallbackActive);
    }

    @Test
    void tracksDegradedLoops() {
        var handler = new CascadeHandler(null, Map.of(), List.of());
        handler.onDegradation(degraded("agent-1", true));
        handler.onDegradation(degraded("agent-2", true));
        assertThat(handler.degradedLoops()).containsExactlyInAnyOrder("agent-1", "agent-2");
    }

    @Test
    void removesRecoveredLoops() {
        var handler = new CascadeHandler(null, Map.of(), List.of());
        handler.onDegradation(degraded("agent-1", true));
        handler.onDegradation(degraded("agent-1", false));
        assertThat(handler.degradedLoops()).isEmpty();
    }

    @Test
    void noThresholdMeansNoCascade() {
        var trigger = new CascadeDegradationTrigger();
        var handler = new CascadeHandler(null, Map.of("a", BudgetPriority.NORMAL), List.of(trigger));
        handler.onDegradation(degraded("a", true));
        assertThat(trigger.shouldDegrade()).isFalse();
        assertThat(handler.isCascadeTriggered()).isFalse();
    }

    @Test
    void triggersWhenThresholdBreached() {
        CascadeThreshold always = (degraded, total, priorities) -> true;
        var trigger = new CascadeDegradationTrigger();
        var handler = new CascadeHandler(always, Map.of("a", BudgetPriority.NORMAL), List.of(trigger));
        handler.onDegradation(degraded("a", true));
        assertThat(trigger.shouldDegrade()).isTrue();
        assertThat(handler.isCascadeTriggered()).isTrue();
    }

    @Test
    void cascadeTriggersOnlyOnce() {
        CascadeThreshold always = (degraded, total, priorities) -> true;
        var counter = new AtomicInteger(0);
        var handler = new CascadeHandler(always, Map.of("a", BudgetPriority.NORMAL), List.of(), counter::incrementAndGet);
        handler.onDegradation(degraded("a", true));
        handler.onDegradation(degraded("b", true));
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void cascadeActionRunsBeforeTriggers() {
        CascadeThreshold always = (degraded, total, priorities) -> true;
        var trigger = new CascadeDegradationTrigger();
        var actionRanBeforeTrigger = new AtomicInteger(0);
        var handler = new CascadeHandler(always, Map.of("a", BudgetPriority.NORMAL), List.of(trigger),
                () -> { if (!trigger.shouldDegrade()) actionRanBeforeTrigger.incrementAndGet(); });
        handler.onDegradation(degraded("a", true));
        assertThat(actionRanBeforeTrigger.get()).isEqualTo(1);
        assertThat(trigger.shouldDegrade()).isTrue();
    }

    @Test
    void recoveryDoesNotTriggerCascade() {
        CascadeThreshold always = (degraded, total, priorities) -> true;
        var trigger = new CascadeDegradationTrigger();
        var handler = new CascadeHandler(always, Map.of("a", BudgetPriority.NORMAL), List.of(trigger));
        handler.onDegradation(degraded("a", false));
        assertThat(trigger.shouldDegrade()).isFalse();
    }
}
