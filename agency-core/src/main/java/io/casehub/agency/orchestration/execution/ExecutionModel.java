package io.casehub.agency.orchestration.execution;

import io.casehub.agency.core.FailurePolicy;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.ActivationRule;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.deliberation.judgment.JudgmentPhase;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.engine.plan.DecompositionStrategy;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public record ExecutionModel<T>(
        RoutingStrategy<T> routing,
        DecompositionStrategy<T> decomposition,
        ActivationRule<T> activation,
        AggregationStrategy<T> aggregation,
        TerminationCondition<T> termination,
        Supplier<List<RoutingCandidate>> candidateSupplier,
        FailurePolicy failurePolicy,
        List<ExecutionEventListener> listeners,
        String task,
        PatternType patternType,
        @Nullable ExecutionBackend<T> backend,
        @Nullable JudgmentPhase<T> judgment
) {
    public ExecutionModel {
        Objects.requireNonNull(routing, "routing");
        Objects.requireNonNull(decomposition, "decomposition");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(termination, "termination");
        Objects.requireNonNull(candidateSupplier, "candidateSupplier");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        listeners = List.copyOf(listeners);
        Objects.requireNonNull(task, "task");
    }

    public ExecutionModel(RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition,
                          ActivationRule<T> activation, AggregationStrategy<T> aggregation,
                          TerminationCondition<T> termination,
                          Supplier<List<RoutingCandidate>> candidateSupplier,
                          FailurePolicy failurePolicy, List<ExecutionEventListener> listeners,
                          String task, PatternType patternType, ExecutionBackend<T> backend) {
        this(routing, decomposition, activation, aggregation, termination,
             candidateSupplier, failurePolicy, listeners, task, patternType, backend, null);
    }

    public ExecutionModel(RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition,
                          ActivationRule<T> activation, AggregationStrategy<T> aggregation,
                          TerminationCondition<T> termination,
                          Supplier<List<RoutingCandidate>> candidateSupplier,
                          FailurePolicy failurePolicy, List<ExecutionEventListener> listeners,
                          String task, PatternType patternType) {
        this(routing, decomposition, activation, aggregation, termination,
             candidateSupplier, failurePolicy, listeners, task, patternType, null, null);
    }

    public ExecutionModel(RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition,
                          ActivationRule<T> activation, AggregationStrategy<T> aggregation,
                          TerminationCondition<T> termination,
                          Supplier<List<RoutingCandidate>> candidateSupplier,
                          FailurePolicy failurePolicy, List<ExecutionEventListener> listeners,
                          String task) {
        this(routing, decomposition, activation, aggregation, termination,
             candidateSupplier, failurePolicy, listeners, task, null, null);
    }
}
