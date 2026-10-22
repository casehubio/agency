package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.CollectAll;
import io.casehub.agency.orchestration.decomposition.StaticDecomposition;
import io.casehub.agency.orchestration.execution.AgentInvoker;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.routing.SequentialRouting;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.engine.plan.TaskNode;
import io.smallrye.mutiny.Uni;

public class HtnBuilder<T> extends AbstractPatternBuilder<T, HtnBuilder<T>> {

    private TaskNode<T> rootTask;

    public HtnBuilder() {
        this.task          = "htn";
        this.patternType   = io.casehub.agency.orchestration.execution.PatternType.HTN;
        this.routing       = new SequentialRouting<>();
        this.decomposition = new StaticDecomposition<>();
        this.activation    = new OnExplicitDispatch<>();
        this.aggregation   = new CollectAll<>();
        this.termination   = ctx -> ctx.iterationCount() >= 1
                ? new TerminationDecision.Complete(ctx.results())
                : TerminationDecision.Continue.INSTANCE;
    }

    public HtnBuilder<T> rootTask(TaskNode<T> rootTask) {
        this.rootTask = rootTask;
        return this;
    }

    @Override
    public HtnBuilder<T> agents(io.casehub.agency.core.AgentRef... agents) {
        return (HtnBuilder<T>) super.agents(agents);
    }

    @Override
    public HtnBuilder<T> agents(io.casehub.agency.core.RoutingCandidate... candidates) {
        return (HtnBuilder<T>) super.agents(candidates);
    }


    @Override
    public Uni<ExecutionResult> execute(T initialContext) {
        if (rootTask == null) {
            throw new IllegalStateException("rootTask must be set before execute()");
        }

        if (candidateSupplier == null) {
            candidateSupplier = java.util.List::of;
        }

        var model    = build();
        var executor = new HtnExecutor<T>(AgentInvoker.defaultInvoker());

        return Uni.createFrom().item(() -> executor.execute(rootTask, model, initialContext));}

}
