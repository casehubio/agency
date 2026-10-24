package io.casehub.agency.orchestration.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityTypeTest {

    @Test
    void shouldHave28Capabilities() {
        assertThat(CapabilityType.values()).hasSize(28);
    }

    @Test
    void orchestrationCapabilities() {
        assertThat(CapabilityType.SUPERVISOR.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.CHOREOGRAPHY.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.SAGA.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.DECOMPOSITION.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
    }

    @Test
    void deliberationCapabilities() {
        assertThat(CapabilityType.VOTING.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.DEBATE.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.NEGOTIATION.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.COALITION.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.NORMATIVE.category()).isEqualTo(CapabilityCategory.DELIBERATION);
    }

    @Test
    void perceptionCapabilities() {
        assertThat(CapabilityType.SUMMARISATION.category()).isEqualTo(CapabilityCategory.PERCEPTION);
        assertThat(CapabilityType.NARRATIVE.category()).isEqualTo(CapabilityCategory.PERCEPTION);
        assertThat(CapabilityType.OBSERVATION.category()).isEqualTo(CapabilityCategory.PERCEPTION);
    }

    @Test
    void communicationCapabilities() {
        assertThat(CapabilityType.CHANNEL_EXECUTION.category()).isEqualTo(CapabilityCategory.COMMUNICATION);
        assertThat(CapabilityType.CHANNEL_SUMMARY.category()).isEqualTo(CapabilityCategory.COMMUNICATION);
    }

    @Test
    void governanceCapabilities() {
        assertThat(CapabilityType.JUDGMENT.category()).isEqualTo(CapabilityCategory.GOVERNANCE);
        assertThat(CapabilityType.OVERSIGHT.category()).isEqualTo(CapabilityCategory.GOVERNANCE);
        assertThat(CapabilityType.ATTESTATION.category()).isEqualTo(CapabilityCategory.GOVERNANCE);
        assertThat(CapabilityType.TRUST.category()).isEqualTo(CapabilityCategory.GOVERNANCE);
    }

    @Test
    void optimisationCapabilities() {
        assertThat(CapabilityType.PROMPT_OPTIMISATION.category()).isEqualTo(CapabilityCategory.OPTIMISATION);
    }

    @Test
    void socialCapabilities() {
        assertThat(CapabilityType.MEMORY_HYGIENE.category()).isEqualTo(CapabilityCategory.SOCIAL);
        assertThat(CapabilityType.COGNITION.category()).isEqualTo(CapabilityCategory.SOCIAL);
        assertThat(CapabilityType.REFLECTION.category()).isEqualTo(CapabilityCategory.SOCIAL);
    }

    @Test
    void playbookCapabilities() {
        assertThat(CapabilityType.SEQUENCE.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.PARALLEL.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.LOOP.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.CONDITIONAL.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.MATCH.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.TRY_CATCH.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
    }

    @ParameterizedTest
    @EnumSource(CapabilityType.class)
    void everyCategoryIsNonNull(CapabilityType ct) {
        assertThat(ct.category()).isNotNull();
    }

    @Test
    void patternTypeBridgeCoversAllPatternTypes() {
        for (PatternType pt : PatternType.values()) {
            assertThat(pt.toCapabilityType()).isNotNull();
            assertThat(pt.toCapabilityType().toPatternType())
                    .isPresent()
                    .hasValue(pt);
        }
    }

    @Test
    void nonPatternCapabilitiesReturnEmptyPatternType() {
        assertThat(CapabilityType.NEGOTIATION.toPatternType()).isEmpty();
        assertThat(CapabilityType.SUMMARISATION.toPatternType()).isEmpty();
        assertThat(CapabilityType.COGNITION.toPatternType()).isEmpty();
        assertThat(CapabilityType.CHOREOGRAPHY.toPatternType()).isEmpty();
        assertThat(CapabilityType.SAGA.toPatternType()).isEmpty();
    }

    @Test
    void htnMapsToDecomposition() {
        assertThat(PatternType.HTN.toCapabilityType()).isEqualTo(CapabilityType.DECOMPOSITION);
        assertThat(CapabilityType.DECOMPOSITION.toPatternType()).hasValue(PatternType.HTN);
    }
}
