package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.aggregation.MajorityVote;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.termination.TerminationDecision;

public class VotingBuilder<T> extends AbstractPatternBuilder<T, VotingBuilder<T>> {

    public VotingBuilder() {
        this.task = "vote";
        this.patternType = io.casehub.agency.orchestration.execution.PatternType.VOTING;
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
