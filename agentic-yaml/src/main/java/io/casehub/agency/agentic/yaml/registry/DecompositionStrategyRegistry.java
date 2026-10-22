package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.decomposition.CapabilityDependencyDecomposition;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.core.yaml.spec.DecompositionSpec;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class DecompositionStrategyRegistry {

    @SuppressWarnings("unchecked")
    public <T> DecompositionStrategy<T> resolve(DecompositionSpec spec,
                                                 @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case DecompositionSpec.Identity id -> new IdentityDecomposition<>();
            case DecompositionSpec.Static st ->
                    throw new UnsupportedOperationException(
                            "static decomposition with YAML methods not yet implemented");
            case DecompositionSpec.Llm llm ->
                    throw new UnsupportedOperationException(
                            "llm decomposition requires AgentProvider — use CDI injection");
            case DecompositionSpec.Hybrid hy ->
                    throw new UnsupportedOperationException(
                            "hybrid decomposition requires AgentProvider — use CDI injection");
            case DecompositionSpec.Heuristic he ->
                    throw new UnsupportedOperationException(
                            "heuristic decomposition requires DecompositionHeuristic — use CDI injection");
            case DecompositionSpec.Goap goap -> new CapabilityDependencyDecomposition<>();
            case DecompositionSpec.CapabilityDependency cd ->
                    new CapabilityDependencyDecomposition<>();
            case DecompositionSpec.ForwardReasoning fr ->
                    throw new UnsupportedOperationException(
                            "forward-reasoning requires stateCopier function — Java only");
        };
    }
}
