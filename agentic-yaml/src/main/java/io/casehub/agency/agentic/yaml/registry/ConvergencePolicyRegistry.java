package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.ConvergencePolicySpec;
import io.casehub.agency.conversation.ConvergencePolicy;
import io.casehub.agency.conversation.ConvergencePolicies;

public class ConvergencePolicyRegistry {
    public ConvergencePolicy resolve(ConvergencePolicySpec spec) {
        return switch (spec) {
            case ConvergencePolicySpec.Structural s ->
                    ConvergencePolicies.structural(s.similarityThreshold(), s.staleRounds());
            case ConvergencePolicySpec.CommonGroundRatio cgr ->
                    ConvergencePolicies.commonGroundRatio(cgr.consensusThreshold(),
                            cgr.deadlockDisputeRatio());
            case ConvergencePolicySpec.Composite c ->
                    ConvergencePolicies.composite(
                            c.policies().stream().map(this::resolve)
                                    .toArray(ConvergencePolicy[]::new));
        };
    }
}
