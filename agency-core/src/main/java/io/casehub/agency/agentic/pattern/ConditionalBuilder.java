package io.casehub.agency.agentic.pattern;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.routing.FirstMatchRouting;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.smallrye.mutiny.Uni;

import java.util.function.Predicate;

public class ConditionalBuilder<T> extends AbstractPatternBuilder<T, ConditionalBuilder<T>> {

    public ConditionalBuilder() {
        this.task = "conditional";
        this.patternType = io.casehub.agency.agentic.model.PatternType.CONDITIONAL;
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
