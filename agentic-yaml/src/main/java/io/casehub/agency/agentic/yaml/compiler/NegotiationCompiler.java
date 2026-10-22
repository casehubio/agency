package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.core.yaml.registry.TerminationConditionRegistry;
import io.casehub.agency.core.yaml.spec.AcceptancePolicySpec;
import io.casehub.agency.core.yaml.spec.NegotiationSpec;
import io.casehub.agency.core.yaml.spec.TerminationSpec;
import io.casehub.agency.deliberation.negotiation.*;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NegotiationCompiler {

    private final TerminationConditionRegistry terminationRegistry;
    private final @Nullable ExpressionEngine expressionEngine;

    public NegotiationCompiler(TerminationConditionRegistry terminationRegistry,
                                @Nullable ExpressionEngine expressionEngine) {
        this.terminationRegistry = terminationRegistry;
        this.expressionEngine = expressionEngine;
    }

    public CompiledNegotiation compile(NegotiationSpec spec) {
        var acceptance = compileAcceptance(spec.acceptance());
        var termination = compileTermination(spec.termination());
        var projection = new NegotiationProjection(spec.parties(), acceptance);
        return new CompiledNegotiation(projection, termination, spec.parties());
    }

    private AcceptancePolicy compileAcceptance(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous ignored -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority ignored -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }

    @SuppressWarnings("unchecked")
    private @Nullable TerminationCondition<NegotiationState> compileTermination(
            @Nullable List<TerminationSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        var conditions = specs.stream()
                .<TerminationCondition<NegotiationState>>map(
                        s -> terminationRegistry.resolve(s, expressionEngine))
                .toList();
        if (conditions.size() == 1) return conditions.get(0);
        return new NegotiationCompositeTermination(conditions);
    }
}
