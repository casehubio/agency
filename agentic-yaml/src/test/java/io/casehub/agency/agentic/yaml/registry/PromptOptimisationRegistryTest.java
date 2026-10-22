package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.ConfidenceScorerSpec;
import io.casehub.agency.core.yaml.spec.DiversityStrategySpec;
import io.casehub.agency.core.yaml.spec.PromptOptimiserSpec;
import io.casehub.agency.social.memory.ArousalScorer;
import io.casehub.agency.social.memory.CompositeConfidenceScorer;
import io.casehub.agency.social.memory.SurpriseScorer;
import io.casehub.agency.optimisation.prompt.optimiser.FewShotOptimiser;
import io.casehub.agency.optimisation.prompt.optimiser.InstructionOptimiser;
import io.casehub.agency.optimisation.prompt.optimiser.OutcomeAwareDiversityStrategy;
import io.casehub.agency.optimisation.prompt.optimiser.TopNDiversityStrategy;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PromptOptimisationRegistryTest {

    @Nested
    class DiversityStrategyRegistryTests {
        private final DiversityStrategyRegistry registry = new DiversityStrategyRegistry();

        @Test
        void topN() {
            var result = registry.resolve(new DiversityStrategySpec.TopN());
            assertThat(result).isInstanceOf(TopNDiversityStrategy.class);
        }

        @Test
        void outcomeAware() {
            var result = registry.resolve(new DiversityStrategySpec.OutcomeAware(0.5));
            assertThat(result).isInstanceOf(OutcomeAwareDiversityStrategy.class);
        }
    }

    @Nested
    class PromptOptimiserRegistryTests {
        private final PromptOptimiserRegistry registry =
                new PromptOptimiserRegistry(new DiversityStrategyRegistry());

        @Test
        void fewShotDefault() {
            var result = registry.resolve(new PromptOptimiserSpec.FewShot(null), null);
            assertThat(result).isInstanceOf(FewShotOptimiser.class);
        }

        @Test
        void fewShotWithDiversity() {
            var spec = new PromptOptimiserSpec.FewShot(
                    new DiversityStrategySpec.OutcomeAware(0.3));
            var result = registry.resolve(spec, null);
            assertThat(result).isInstanceOf(FewShotOptimiser.class);
        }

        @Test
        void instructionWithProvider() {
            var provider = mock(AgentProvider.class);
            var result = registry.resolve(new PromptOptimiserSpec.Instruction(), provider);
            assertThat(result).isInstanceOf(InstructionOptimiser.class);
        }

        @Test
        void instructionWithoutProviderThrows() {
            assertThatThrownBy(() ->
                    registry.resolve(new PromptOptimiserSpec.Instruction(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AgentProvider");
        }
    }

    @Nested
    class ConfidenceScorerRegistryTests {
        private final ConfidenceScorerRegistry registry = new ConfidenceScorerRegistry();

        @Test
        void arousal() {
            var result = registry.resolve(new ConfidenceScorerSpec.Arousal());
            assertThat(result).isInstanceOf(ArousalScorer.class);
        }

        @Test
        void surprise() {
            var result = registry.resolve(new ConfidenceScorerSpec.Surprise());
            assertThat(result).isInstanceOf(SurpriseScorer.class);
        }

        @Test
        void composite() {
            var spec = new ConfidenceScorerSpec.Composite(List.of(
                    new ConfidenceScorerSpec.WeightedScorerEntry(
                            new ConfidenceScorerSpec.Arousal(), 0.6),
                    new ConfidenceScorerSpec.WeightedScorerEntry(
                            new ConfidenceScorerSpec.Surprise(), 0.4)));
            var result = registry.resolve(spec);
            assertThat(result).isInstanceOf(CompositeConfidenceScorer.class);
        }
    }
}
