package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.api.model.improvement.ImprovementRequest;
import io.casehub.api.spi.improvement.ConflictStrategy.ConflictResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmPromptConflictStrategyTest {

    private final SwarmPromptConflictStrategy strategy = new SwarmPromptConflictStrategy();

    @Test
    void domainId_returns_swarm_prompt_optimisation() {
        assertThat(strategy.domainId()).isEqualTo("swarm-prompt-optimisation");
    }

    @Test
    void clear_when_no_active_improvements_share_signature() {
        var request = new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                "swarm-team-formation", 1,
                Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                        "swarm-provisioning", 1,
                        Map.of("signatureId", "swarm-provisioning"), "swarm-prompt-optimisation"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }

    @Test
    void conflict_when_active_improvement_targets_same_signature() {
        var request = new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                "swarm-team-formation", 1,
                Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation");
        var active = Map.of(
                UUID.randomUUID(), new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                        "swarm-team-formation", 1,
                        Map.of("signatureId", "swarm-team-formation"), "swarm-prompt-optimisation"));

        var result = strategy.check(request, active, 10);
        assertThat(result).isInstanceOf(ConflictResult.Conflicting.class);
    }

    @Test
    void clear_when_no_signature_key_in_metadata() {
        var request = new ImprovementRequest("swarm-prompt-tuning", "swarm-prompt-tuning",
                "swarm-team-formation", 1,
                Map.of(), "swarm-prompt-optimisation");

        var result = strategy.check(request, Map.of(), 10);
        assertThat(result).isInstanceOf(ConflictResult.Clear.class);
    }
}
