package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.AggregationStrategy;
import io.casehub.agency.agentic.aggregation.MajorityVote;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.RoutingDecision;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class VotingBuilder<T> extends AbstractPatternBuilder<T, VotingBuilder<T>> {

    public VotingBuilder() {
        this.task = "vote";
        this.patternType = io.casehub.agency.agentic.model.PatternType.VOTING;
        this.routing = ctx -> new RoutingDecision.Selected(
                ctx.candidates().stream().map(RoutingCandidate::ref).toList());
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new MajorityVote<>();
        this.termination = ctx -> ctx.iterationCount() >= 1
                ? new TerminationDecision.Complete(ctx.results())
                : TerminationDecision.Continue.INSTANCE;
    }

    public VotingBuilder<T> evaluators(AgentRef... agents) {
        return (VotingBuilder<T>) super.agents(agents);
    }

    public VotingBuilder<T> strategy(AggregationStrategy<T> strategy) {
        this.aggregation = strategy;
        return this;
    }
}
