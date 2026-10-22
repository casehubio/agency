package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.SequentialRouting;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

public class SequenceBuilder<T> extends AbstractPatternBuilder<T, SequenceBuilder<T>> {

    private int agentCount = 0;

    public SequenceBuilder() {
        this.task = "sequence";
        this.patternType = io.casehub.agency.agentic.model.PatternType.SEQUENCE;
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
    }

    @Override
    public SequenceBuilder<T> agents(AgentRef... agents) {
        this.agentCount = agents.length;
        int count = this.agentCount;
        this.routing = new SequentialRouting<>();
        this.termination = ctx -> {
            if (ctx.iterationCount() >= count) {
                return new TerminationDecision.Complete(ctx.results().isEmpty() ? null :
                        ctx.results().get(ctx.results().size() - 1).output());
            }
            return TerminationDecision.Continue.INSTANCE;
        };
        return (SequenceBuilder<T>) super.agents(agents);
    }

    @Override
    public SequenceBuilder<T> agents(RoutingCandidate... candidates) {
        this.agentCount = candidates.length;
        int count = this.agentCount;
        this.routing = new SequentialRouting<>();
        this.termination = ctx -> {
            if (ctx.iterationCount() >= count) {
                return new TerminationDecision.Complete(ctx.results().isEmpty() ? null :
                        ctx.results().get(ctx.results().size() - 1).output());
            }
            return TerminationDecision.Continue.INSTANCE;
        };
        return (SequenceBuilder<T>) super.agents(candidates);
    }
}
