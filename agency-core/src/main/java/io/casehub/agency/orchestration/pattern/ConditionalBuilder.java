package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.PassThrough;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.routing.FirstMatchRouting;
import io.casehub.agency.orchestration.termination.TerminationDecision;

import java.util.function.Predicate;

public class ConditionalBuilder<T> extends AbstractPatternBuilder<T, ConditionalBuilder<T>> {

    public ConditionalBuilder() {
        this.task = "conditional";
        this.patternType = io.casehub.agency.orchestration.execution.PatternType.CONDITIONAL;
        this.routing = new FirstMatchRouting<>(c -> true);
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = ctx -> ctx.iterationCount() >= 1
                ? new TerminationDecision.Complete(ctx.results())
                : TerminationDecision.Continue.INSTANCE;
    }

    public ConditionalBuilder<T> when(Predicate<RoutingCandidate> predicate, AgentRef agent) {
        this.routing = new FirstMatchRouting<>(predicate);
        return (ConditionalBuilder<T>) super.agents(agent);
    }
}
