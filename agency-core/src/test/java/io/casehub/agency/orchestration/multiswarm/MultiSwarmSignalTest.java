package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.stigmergy.SwarmProgress;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MultiSwarmSignalTest {

    @Test
    void finding_signal() {
        var signal = new MultiSwarmSignal.Finding(UUID.randomUUID(), Set.of("analysis"),
                System.currentTimeMillis(), "system-health", "node-7:degraded", 0.9, Map.of("source", "metrics"));
        assertThat(signal.domain()).isEqualTo("system-health");
        assertThat(signal.conclusion()).isEqualTo("node-7:degraded");
        assertThat(signal.confidence()).isEqualTo(0.9);
    }

    @Test
    void handoff_signal() {
        var signal = new MultiSwarmSignal.Handoff(UUID.randomUUID(), Set.of("remediation"),
                System.currentTimeMillis(), "system-health", Map.of("issue", "degraded"),
                Set.of("remediation"));
        assertThat(signal.requiredCapabilities()).containsExactly("remediation");
    }

    @Test
    void resource_claim_signal() {
        var signal = new MultiSwarmSignal.ResourceClaim(UUID.randomUUID(), Set.of("analysis"),
                System.currentTimeMillis(), Set.of("agent-a"), Set.of("pattern-detection"));
        assertThat(signal.agentIds()).containsExactly("agent-a");
    }

    @Test
    void sealed_interface_has_common_accessor() {
        MultiSwarmSignal signal = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of("x"),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThat(signal.sourceSwarmId()).isNotNull();
        assertThat(signal.domainTags()).containsExactly("x");
        assertThat(signal.timestamp()).isGreaterThan(0);
    }
}
