package io.casehub.engine.agentic;

import io.casehub.agency.orchestration.loop.DecisionLoopEvent;
import io.casehub.api.spi.stigmergy.AgentLlmStatsProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmTelemetryAccumulatorTest {

    private final UUID caseId = UUID.randomUUID();

    @Test
    void singleEvent_recordsStats() {
        var accumulator = new LlmTelemetryAccumulator(caseId);
        accumulator.accept(new DecisionLoopEvent.ReasoningCompleted(
                caseId, "agent-1", "corr-1", 500L, 120L, Instant.now()));

        var stats = accumulator.getStats(caseId, "agent-1");
        assertThat(stats.invocationCount()).isEqualTo(1);
        assertThat(stats.tokensConsumed()).isEqualTo(500L);
        assertThat(stats.totalLatency()).isEqualTo(Duration.ofMillis(120));
    }

    @Test
    void multipleEvents_accumulates() {
        var accumulator = new LlmTelemetryAccumulator(caseId);
        accumulator.accept(new DecisionLoopEvent.ReasoningCompleted(
                caseId, "agent-1", "corr-1", 500L, 120L, Instant.now()));
        accumulator.accept(new DecisionLoopEvent.ReasoningCompleted(
                caseId, "agent-1", "corr-2", 300L, 80L, Instant.now()));

        var stats = accumulator.getStats(caseId, "agent-1");
        assertThat(stats.invocationCount()).isEqualTo(2);
        assertThat(stats.tokensConsumed()).isEqualTo(800L);
        assertThat(stats.totalLatency()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void multipleAgents_trackedIndependently() {
        var accumulator = new LlmTelemetryAccumulator(caseId);
        accumulator.accept(new DecisionLoopEvent.ReasoningCompleted(
                caseId, "agent-1", "corr-1", 500L, 120L, Instant.now()));
        accumulator.accept(new DecisionLoopEvent.ReasoningCompleted(
                caseId, "agent-2", "corr-2", 200L, 50L, Instant.now()));

        var stats1 = accumulator.getStats(caseId, "agent-1");
        var stats2 = accumulator.getStats(caseId, "agent-2");
        assertThat(stats1.invocationCount()).isEqualTo(1);
        assertThat(stats1.tokensConsumed()).isEqualTo(500L);
        assertThat(stats2.invocationCount()).isEqualTo(1);
        assertThat(stats2.tokensConsumed()).isEqualTo(200L);
    }

    @Test
    void unknownAgent_returnsZero() {
        var accumulator = new LlmTelemetryAccumulator(caseId);
        var stats = accumulator.getStats(caseId, "nonexistent");
        assertThat(stats).isEqualTo(AgentLlmStatsProvider.AgentLlmStats.ZERO);
    }

    @Test
    void nonReasoningEvent_ignored() {
        var accumulator = new LlmTelemetryAccumulator(caseId);
        accumulator.accept(new DecisionLoopEvent.ReasoningStarted(
                caseId, "agent-1", "corr-1", Instant.now()));

        var stats = accumulator.getStats(caseId, "agent-1");
        assertThat(stats).isEqualTo(AgentLlmStatsProvider.AgentLlmStats.ZERO);
    }
}
