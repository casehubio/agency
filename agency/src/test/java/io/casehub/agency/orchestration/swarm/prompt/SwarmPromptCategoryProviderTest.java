package io.casehub.agency.orchestration.swarm.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwarmPromptCategoryProviderTest {

    private final SwarmPromptCategoryProvider provider = new SwarmPromptCategoryProvider();

    @Test
    void domainId_returns_swarm_prompt_optimisation() {
        assertThat(provider.domainId()).isEqualTo("swarm-prompt-optimisation");
    }

    @Test
    void categories_returns_single_category() {
        var categories = provider.categories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).id()).isEqualTo("swarm-prompt-tuning");
    }

    @Test
    void stages_returns_four_stages() {
        assertThat(provider.stages()).hasSize(4);
    }
}
