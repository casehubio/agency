package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.termination.GoalReached;
import io.casehub.agency.agentic.termination.MaxIterationsTermination;
import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.yaml.spec.TerminationSpec;
import io.casehub.agency.conversation.orchestration.AllAgreedTermination;
import io.casehub.agency.conversation.orchestration.ContestedEscalation;
import io.casehub.agency.conversation.orchestration.SupervisorTermination;
import io.casehub.agency.negotiation.AcceptedTermination;
import io.casehub.agency.negotiation.DeadlineTermination;
import io.casehub.agency.negotiation.TerminalOutcomeTermination;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TerminationConditionRegistry {

    private @Nullable Function<TerminationSpec, @Nullable TerminationCondition<?>> fallback;

    public void registerFallback(Function<TerminationSpec, @Nullable TerminationCondition<?>> fallback) {
        this.fallback = fallback;
    }

    @SuppressWarnings("unchecked")
    public <T> TerminationCondition<T> resolve(TerminationSpec spec,
                                                @Nullable ExpressionEngine engine) {
        if (fallback != null) {
            var result = fallback.apply(spec);
            if (result != null) return (TerminationCondition<T>) result;
        }
        return switch (spec) {
            case TerminationSpec.MaxIterations mi ->
                    new MaxIterationsTermination<>(mi.iterations());
            case TerminationSpec.GoalReached gr -> {
                if (engine == null) {
                    throw new IllegalStateException(
                            "GoalReached requires ExpressionEngine for predicate compilation");
                }
                @SuppressWarnings("unchecked")
                var compiled = engine.compile(gr.when(),
                        (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
                yield (TerminationCondition<T>) new GoalReached<>(
                        state -> Boolean.TRUE.equals(compiled.eval((Map<String, Object>) state)));
            }
            case TerminationSpec.JudgeConvergence jc ->
                    throw new UnsupportedOperationException(
                            "judge-convergence resolved by PatternCompiler (needs agent ref)");
            case TerminationSpec.AllAgreed aa ->
                    (TerminationCondition<T>) new AllAgreedTermination(
                            aa.resolvedStatuses() != null ? aa.resolvedStatuses()
                                    : Set.of("RESOLVED", "ACCEPTED"));
            case TerminationSpec.Supervisor sv ->
                    sv.signalType() != null
                            ? (TerminationCondition<T>) new SupervisorTermination(sv.role(), sv.signalType())
                            : (TerminationCondition<T>) new SupervisorTermination(sv.role());
            case TerminationSpec.Contested ct ->
                    (TerminationCondition<T>) new ContestedEscalation(ct.maxDisputeRounds());
            case TerminationSpec.Convergence cv ->
                    throw new UnsupportedOperationException(
                            "convergence termination requires state extraction functions");
            case TerminationSpec.SinglePass sp -> new MaxIterationsTermination<>(1);
            case TerminationSpec.AgentCount ac ->
                    throw new UnsupportedOperationException(
                            "agent-count resolved by PatternCompiler (needs agent list)");
            case TerminationSpec.Accepted ignored ->
                    (TerminationCondition<T>) new AcceptedTermination();
            case TerminationSpec.TerminalOutcome ignored ->
                    (TerminationCondition<T>) new TerminalOutcomeTermination();
            case TerminationSpec.Deadline dl ->
                    (TerminationCondition<T>) new DeadlineTermination(
                            java.time.Instant.now().plus(dl.timeout()));
        };
    }
}
