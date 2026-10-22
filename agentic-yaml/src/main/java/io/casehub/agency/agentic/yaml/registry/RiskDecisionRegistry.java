package io.casehub.agency.agentic.yaml.registry;

import io.casehub.api.spi.RiskDecision;
import io.casehub.agency.agentic.yaml.spec.RiskDecisionSpec;

public class RiskDecisionRegistry {

    private final CandidateSetStrategyRegistry candidateRegistry;

    public RiskDecisionRegistry(CandidateSetStrategyRegistry candidateRegistry) {
        this.candidateRegistry = candidateRegistry;
    }

    public RiskDecision resolve(RiskDecisionSpec spec) {
        return switch (spec) {
            case RiskDecisionSpec.Autonomous ignored ->
                    new RiskDecision.Autonomous();
            case RiskDecisionSpec.GateRequired g -> {
                Class<?> resolutionType = null;
                if (g.resolutionType() != null) {
                    try {
                        resolutionType = Class.forName(g.resolutionType());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException(
                                "Unknown resolutionType: " + g.resolutionType(), e);
                    }
                }
                yield new RiskDecision.GateRequired(
                        g.reason(), g.reversible(),
                        candidateRegistry.resolve(g.candidateGroups()),
                        g.expiresIn(), g.scope(), resolutionType, g.quorum());
            }
        };
    }
}
