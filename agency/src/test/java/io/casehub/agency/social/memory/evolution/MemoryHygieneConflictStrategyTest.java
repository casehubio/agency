package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy.ConflictResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryHygieneConflictStrategyTest {

    private final MemoryHygieneConflictStrategy strategy = new MemoryHygieneConflictStrategy();

    @Test
    void domainId_returns_memory_hygiene() {
        assertThat(strategy.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void clear_when_different_agentIds() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-2", 1, Map.of("agentId", "agent-2"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void conflicting_when_same_agentId() {
        var blockingId = UUID.randomUUID();
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");
        var active = Map.of(
                blockingId, new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).asInstanceOf(type(ConflictResult.Conflicting.class))
                .satisfies(c -> {
                    assertThat(c.blockingImprovementId()).isEqualTo(blockingId);
                    assertThat(c.reason()).contains("agent-1");
                });
    }

    @Test
    void clear_when_request_has_no_agentId_metadata() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of(), "memory-hygiene");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("confidence-weights", "memory-hygiene",
                        "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void clear_when_no_active_improvements() {
        var request = new ImprovementRequest("retention-threshold", "memory-hygiene",
                "agent-1", 1, Map.of("agentId", "agent-1"), "memory-hygiene");

        var result = strategy.check(request, Map.of(), 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }
}
