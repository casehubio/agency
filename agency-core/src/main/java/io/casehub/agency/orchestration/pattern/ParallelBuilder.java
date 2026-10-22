package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.CollectAll;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.termination.TerminationDecision;

public class ParallelBuilder<T> extends AbstractPatternBuilder<T, ParallelBuilder<T>> {

    public ParallelBuilder() {
        this.task = "parallel";
        this.patternType = io.casehub.agency.orchestration.execution.PatternType.PARALLEL;
        this.routing = ctx -> new RoutingDecision.Selected(
                ctx.candidates().stream().map(RoutingCandidate::ref).toList());
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = ctx -> ctx.iterationCount() >= 1
                ? new TerminationDecision.Complete(ctx.results())
                : TerminationDecision.Continue.INSTANCE;
    }

    @Override
    public ParallelBuilder<T> agents(AgentRef... agents) {
        return (ParallelBuilder<T>) super.agents(agents);
    }
}
