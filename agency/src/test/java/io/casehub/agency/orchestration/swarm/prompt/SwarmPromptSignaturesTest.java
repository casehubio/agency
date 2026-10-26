package io.casehub.agency.orchestration.swarm.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmPromptSignaturesTest {

    @Test
    void teamFormation_hasCorrectId() {
        assertThat(SwarmPromptSignatures.TEAM_FORMATION.id()).isEqualTo("swarm-team-formation");
    }

    @Test
    void collectiveReasoning_hasCorrectId() {
        assertThat(SwarmPromptSignatures.COLLECTIVE_REASONING.id()).isEqualTo("swarm-collective-reasoning");
    }

    @Test
    void provisioning_hasCorrectId() {
        assertThat(SwarmPromptSignatures.PROVISIONING.id()).isEqualTo("swarm-provisioning");
    }

    @Test
    void allSignatures_haveDistinctIds() {
        var ids = java.util.Set.of(
                SwarmPromptSignatures.TEAM_FORMATION.id(),
                SwarmPromptSignatures.COLLECTIVE_REASONING.id(),
                SwarmPromptSignatures.PROVISIONING.id());
        assertThat(ids).hasSize(3);
    }

    @Test
    void allSignatures_haveNonEmptyBasePrompts() {
        assertThat(SwarmPromptSignatures.TEAM_FORMATION.baseSystemPrompt()).isNotBlank();
        assertThat(SwarmPromptSignatures.COLLECTIVE_REASONING.baseSystemPrompt()).isNotBlank();
        assertThat(SwarmPromptSignatures.PROVISIONING.baseSystemPrompt()).isNotBlank();
    }
}
