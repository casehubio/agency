package io.casehub.agency.orchestration.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCategoryTest {

    @Test
    void shouldHaveEightCategories() {
        assertThat(CapabilityCategory.values()).hasSize(8);
    }

    @Test
    void shouldContainAllExpectedCategories() {
        assertThat(CapabilityCategory.values()).containsExactlyInAnyOrder(
                CapabilityCategory.ORCHESTRATION,
                CapabilityCategory.DELIBERATION,
                CapabilityCategory.PERCEPTION,
                CapabilityCategory.COMMUNICATION,
                CapabilityCategory.GOVERNANCE,
                CapabilityCategory.OPTIMISATION,
                CapabilityCategory.SOCIAL,
                CapabilityCategory.PLAYBOOK
        );
    }
}
