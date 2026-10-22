package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.RoundRobinRouting;
import io.casehub.agency.agentic.termination.MaxIterationsTermination;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.function.Predicate;

public class LoopBuilder<T> extends AbstractPatternBuilder<T, LoopBuilder<T>> {

    private int maxIterations = 10;

    public LoopBuilder() {
        this.task = "loop";
        this.patternType = io.casehub.agency.agentic.model.PatternType.LOOP;
        this.routing = new RoundRobinRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new PassThrough<>();
        this.termination = new MaxIterationsTermination<>(maxIterations);
    }

    public LoopBuilder<T> maxIterations(int max) {
        this.maxIterations = max;
        this.termination = new MaxIterationsTermination<>(max);
        return this;
    }

    public LoopBuilder<T> exitCondition(Predicate<T> predicate) {
        var maxIter = this.maxIterations;
        this.termination = ctx -> {
            if (predicate.test(ctx.state())) {
                return new TerminationDecision.Complete(ctx.state());
            }
            if (ctx.iterationCount() >= maxIter) {
                return new TerminationDecision.Complete("Max iterations");
            }
            return TerminationDecision.Continue.INSTANCE;
        };
        return this;
    }

    @Override
    public LoopBuilder<T> agents(AgentRef... agents) {
        return (LoopBuilder<T>) super.agents(agents);
    }
}
