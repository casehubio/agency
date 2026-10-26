/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.agency.optimisation.prompt.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptOptimisationCategoryProviderTest {

    private final PromptOptimisationCategoryProvider provider =
            new PromptOptimisationCategoryProvider();

    @Test
    void domainId_returns_prompt_optimisation() {
        assertThat(provider.domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void categories_returns_single_prompt_tuning_category() {
        var categories = provider.categories();
        assertThat(categories).hasSize(1);
        assertThat(categories.getFirst().id()).isEqualTo("prompt-tuning");
        assertThat(categories.getFirst().domainId()).isEqualTo("prompt-optimisation");
    }

    @Test
    void stages_returns_four_ordered_stages_without_gates() {
        var stages = provider.stages();
        assertThat(stages).hasSize(4);
        assertThat(stages.stream().map(s -> s.id()).toList())
                .containsExactly("evaluate", "optimise", "promote", "outcome-recording");
        assertThat(stages.stream().allMatch(s -> !s.gateCheckpoint())).isTrue();
        for (int i = 0; i < stages.size(); i++) {
            assertThat(stages.get(i).ordinal()).isEqualTo(i);
        }
    }
}
