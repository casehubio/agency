package io.casehub.agency.social.memory.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryHygieneCategoryProviderTest {

    private final MemoryHygieneCategoryProvider provider = new MemoryHygieneCategoryProvider();

    @Test
    void domainId_returns_memory_hygiene() {
        assertThat(provider.domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void categories_returns_single_category() {
        var categories = provider.categories();
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().id()).isEqualTo("memory-hygiene");
        assertThat(categories.getFirst().domainId()).isEqualTo("memory-hygiene");
    }

    @Test
    void stages_returns_three_ordered_stages_without_gates() {
        var stages = provider.stages();
        assertThat(stages).hasSize(3);
        assertThat(stages.get(0).id()).isEqualTo("evaluate");
        assertThat(stages.get(0).ordinal()).isEqualTo(0);
        assertThat(stages.get(0).gateCheckpoint()).isFalse();
        assertThat(stages.get(1).id()).isEqualTo("tune");
        assertThat(stages.get(1).ordinal()).isEqualTo(1);
        assertThat(stages.get(1).gateCheckpoint()).isFalse();
        assertThat(stages.get(2).id()).isEqualTo("verify");
        assertThat(stages.get(2).ordinal()).isEqualTo(2);
        assertThat(stages.get(2).gateCheckpoint()).isFalse();
    }
}
