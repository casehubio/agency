package io.casehub.agency.orchestration.budget;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class BudgetTelemetryEventTest {

    @Test
    void acquisition_carriesAllFields() {
        var pressure = new BudgetPressure(0.5, BudgetPriority.NORMAL);
        var now = Instant.now();
        var event = new BudgetTelemetryEvent.Acquisition(
            "corr-1", "agent-1", "topo-1", BudgetPriority.NORMAL, pressure, true, now);
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.topologyId()).isEqualTo("topo-1");
        assertThat(event.priority()).isEqualTo(BudgetPriority.NORMAL);
        assertThat(event.granted()).isTrue();
        assertThat(event.pressure().utilisation()).isEqualTo(0.5);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    void acquisition_deniedEvent() {
        var pressure = new BudgetPressure(0.95, BudgetPriority.NORMAL);
        var event = new BudgetTelemetryEvent.Acquisition(
            "corr-2", "agent-1", "topo-1", BudgetPriority.NORMAL, pressure, false, Instant.now());
        assertThat(event.granted()).isFalse();
        assertThat(event.pressure().utilisation()).isEqualTo(0.95);
    }

    @Test
    void outcome_carriesAllFields() {
        var now = Instant.now();
        var event = new BudgetTelemetryEvent.Outcome(
            "corr-1", "claude-sonnet-5-5", 1500, 320, now);
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.modelId()).isEqualTo("claude-sonnet-5-5");
        assertThat(event.tokensConsumed()).isEqualTo(1500);
        assertThat(event.latencyMs()).isEqualTo(320);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    void sealedInterface() {
        assertThat(BudgetTelemetryEvent.class).isSealed();
        assertThat(BudgetTelemetryEvent.class.getPermittedSubclasses())
            .hasSize(2);
    }
}
