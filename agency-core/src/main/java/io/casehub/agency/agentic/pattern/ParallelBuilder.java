package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.CollectAll;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.RoutingDecision;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class ParallelBuilder<T> extends AbstractPatternBuilder<T, ParallelBuilder<T>> {

    public ParallelBuilder() {
        this.task = "parallel";
        this.patternType = io.casehub.agency.agentic.model.PatternType.PARALLEL;
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
