package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.BudgetPressure;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DecisionLoopEventTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final String AGENT_ID = "agent-1";
    private static final Instant NOW = Instant.now();

    @Test
    void loopTriggered_validConstruction() {
        var event = new DecisionLoopEvent.LoopTriggered(
                CASE_ID, AGENT_ID, DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST, NOW);
        assertThat(event.caseId()).isEqualTo(CASE_ID);
        assertThat(event.agentId()).isEqualTo(AGENT_ID);
        assertThat(event.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
        assertThat(event.communicationMode()).isEqualTo(CommunicationMode.BROADCAST);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    void loopTriggered_nullScope_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DecisionLoopEvent.LoopTriggered(
                        CASE_ID, AGENT_ID, null, CommunicationMode.BROADCAST, NOW));
    }

    @Test
    void observationGathered_validConstruction() {
        var event = new DecisionLoopEvent.ObservationGathered(
                CASE_ID, AGENT_ID, "signal.quality", NOW);
        assertThat(event.key()).isEqualTo("signal.quality");
    }

    @Test
    void observationGathered_nullKey_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DecisionLoopEvent.ObservationGathered(
                        CASE_ID, AGENT_ID, null, NOW));
    }

    @Test
    void reasoningStarted_validConstruction() {
        var event = new DecisionLoopEvent.ReasoningStarted(
                CASE_ID, AGENT_ID, "corr-123", NOW);
        assertThat(event.correlationId()).isEqualTo("corr-123");
    }

    @Test
    void reasoningCompleted_validConstruction() {
        var event = new DecisionLoopEvent.ReasoningCompleted(
                CASE_ID, AGENT_ID, "corr-123", 500, 120, NOW);
        assertThat(event.tokensConsumed()).isEqualTo(500);
        assertThat(event.latencyMs()).isEqualTo(120);
    }

    @Test
    void reasoningCompleted_negativeTokens_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionLoopEvent.ReasoningCompleted(
                        CASE_ID, AGENT_ID, "corr-123", -1, 100, NOW));
    }

    @Test
    void reasoningCompleted_negativeLatency_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DecisionLoopEvent.ReasoningCompleted(
                        CASE_ID, AGENT_ID, "corr-123", 100, -1, NOW));
    }

    @Test
    void actionDispatched_validConstruction() {
        var event = new DecisionLoopEvent.ActionDispatched(
                CASE_ID, AGENT_ID, "WriteBack", NOW);
        assertThat(event.outcomeType()).isEqualTo("WriteBack");
    }

    @Test
    void budgetExhausted_validConstruction() {
        var pressure = new BudgetPressure(0.95, BudgetPriority.NORMAL);
        var event = new DecisionLoopEvent.BudgetExhausted(
                CASE_ID, AGENT_ID, "call budget exhausted", pressure, NOW);
        assertThat(event.reason()).isEqualTo("call budget exhausted");
        assertThat(event.pressure()).isEqualTo(pressure);
    }

    @Test
    void degraded_validConstruction() {
        var event = new DecisionLoopEvent.Degraded(
                CASE_ID, AGENT_ID, "reasoning", "latency exceeded", true, NOW);
        assertThat(event.capability()).isEqualTo("reasoning");
        assertThat(event.fallbackActive()).isTrue();
    }

    @Test
    void sealedInterfaceCommonFields() {
        DecisionLoopEvent event = new DecisionLoopEvent.LoopTriggered(
                CASE_ID, AGENT_ID, DecisionScope.TASK_ADAPTATION,
                CommunicationMode.DIRECT_MESSAGING, NOW);
        assertThat(event.caseId()).isEqualTo(CASE_ID);
        assertThat(event.agentId()).isEqualTo(AGENT_ID);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    void exhaustiveSwitch_coversAllVariants() {
        DecisionLoopEvent[] events = {
                new DecisionLoopEvent.LoopTriggered(CASE_ID, AGENT_ID,
                        DecisionScope.FULL_AUTONOMY, CommunicationMode.BROADCAST, NOW),
                new DecisionLoopEvent.ObservationGathered(CASE_ID, AGENT_ID, "k", NOW),
                new DecisionLoopEvent.ReasoningStarted(CASE_ID, AGENT_ID, "c", NOW),
                new DecisionLoopEvent.ReasoningCompleted(CASE_ID, AGENT_ID, "c", 0, 0, NOW),
                new DecisionLoopEvent.ActionDispatched(CASE_ID, AGENT_ID, "Route", NOW),
                new DecisionLoopEvent.BudgetExhausted(CASE_ID, AGENT_ID, "r",
                        new BudgetPressure(0.5, BudgetPriority.NORMAL), NOW),
                new DecisionLoopEvent.Degraded(CASE_ID, AGENT_ID, "c", "r", false, NOW),
        };
        for (DecisionLoopEvent e : events) {
            String label = switch (e) {
                case DecisionLoopEvent.LoopTriggered t -> "triggered";
                case DecisionLoopEvent.ObservationGathered o -> "observed";
                case DecisionLoopEvent.ReasoningStarted s -> "reasoning-start";
                case DecisionLoopEvent.ReasoningCompleted c -> "reasoning-done";
                case DecisionLoopEvent.ActionDispatched a -> "action";
                case DecisionLoopEvent.BudgetExhausted b -> "budget";
                case DecisionLoopEvent.Degraded d -> "degraded";
            };
            assertThat(label).isNotBlank();
        }
    }
}
