package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.PromptOptimiserSpec;
import io.casehub.agency.optimisation.prompt.PromptOptimiser;
import io.casehub.agency.optimisation.prompt.optimiser.FewShotOptimiser;
import io.casehub.agency.optimisation.prompt.optimiser.InstructionOptimiser;
import io.casehub.agency.optimisation.prompt.optimiser.TopNDiversityStrategy;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

public class PromptOptimiserRegistry {

    private final DiversityStrategyRegistry diversityRegistry;

    public PromptOptimiserRegistry(DiversityStrategyRegistry diversityRegistry) {
        this.diversityRegistry = diversityRegistry;
    }

    public PromptOptimiser resolve(PromptOptimiserSpec spec,
                                    @Nullable AgentProvider agentProvider) {
        return switch (spec) {
            case PromptOptimiserSpec.FewShot fs -> {
                var diversity = fs.diversity() != null
                        ? diversityRegistry.resolve(fs.diversity())
                        : new TopNDiversityStrategy();
                yield new FewShotOptimiser(diversity);
            }
            case PromptOptimiserSpec.Instruction ignored -> {
                if (agentProvider == null)
                    throw new IllegalStateException(
                            "instruction optimiser requires AgentProvider");
                yield new InstructionOptimiser(agentProvider);
            }
        };
    }
}
