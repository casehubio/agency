package io.casehub.agency.agentic.yaml.registry;

import io.casehub.api.spi.QuorumConfig;
import io.casehub.api.spi.judgment.CallerConfig;
import io.casehub.api.spi.routing.CandidateSetSpec;
import io.casehub.agency.agentic.yaml.spec.CallerConfigSpec;
import io.casehub.agency.agentic.yaml.spec.QuorumConfigSpec;
import io.casehub.platform.api.expression.MvelExpressionEvaluator;
import org.jspecify.annotations.Nullable;

public class CallerConfigRegistry {

    private final CandidateSetStrategyRegistry candidateRegistry;

    public CallerConfigRegistry(CandidateSetStrategyRegistry candidateRegistry) {
        this.candidateRegistry = candidateRegistry;
    }

    public CallerConfig resolve(CallerConfigSpec spec) {
        return switch (spec) {
            case CallerConfigSpec.Human h -> resolveHuman(h);
            case CallerConfigSpec.Llm l -> new CallerConfig.Llm(l.modelId(), l.modelName(), l.systemPrompt());
            case CallerConfigSpec.A2A a -> new CallerConfig.A2A(a.endpoint(), a.skill(), a.streaming());
            case CallerConfigSpec.Any ignored -> new CallerConfig.Any();
        };
    }

    private CallerConfig.Human resolveHuman(CallerConfigSpec.Human h) {
        return new CallerConfig.Human(
                h.candidateGroups() != null
                        ? new CandidateSetSpec.Inline(candidateRegistry.resolve(h.candidateGroups()))
                        : null,
                h.candidateUsers() != null
                        ? new CandidateSetSpec.Inline(candidateRegistry.resolve(h.candidateUsers()))
                        : null,
                h.title(),
                h.titleExpression() != null
                        ? new MvelExpressionEvaluator(h.titleExpression())
                        : null,
                h.outcomes(),
                h.claimDeadlineHours(),
                h.scope(),
                h.scopeExpression() != null
                        ? new MvelExpressionEvaluator(h.scopeExpression())
                        : null,
                h.priority(),
                h.templateRef(),
                h.payloadType() != null ? resolveClass(h.payloadType()) : null,
                h.quorum() != null ? resolveQuorum(h.quorum()) : null);
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unknown payload type: " + className, e);
        }
    }

    private static QuorumConfig resolveQuorum(QuorumConfigSpec q) {
        return new QuorumConfig(q.instances(), q.required(),
                q.onThresholdReached(), q.allowSameAssignee());
    }
}
