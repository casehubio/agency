package io.casehub.agency.orchestration.pattern;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.orchestration.activation.OnExplicitDispatch;
import io.casehub.agency.orchestration.aggregation.CollectAll;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.execution.ExecutionModel;
import io.casehub.agency.orchestration.routing.RoundRobinRouting;
import io.casehub.agency.orchestration.termination.JudgeConvergence;
import io.casehub.agency.orchestration.termination.MaxIterationsTermination;
import io.casehub.agency.orchestration.termination.TerminationCondition;

public class DebateBuilder<T> extends AbstractPatternBuilder<T, DebateBuilder<T>> {

    private int maxRounds = 5;
    private AgentRef judge;
    private boolean convergenceExplicitlySet;

    public DebateBuilder() {
        this.task = "debate";
        this.patternType = io.casehub.agency.orchestration.execution.PatternType.DEBATE;
        this.routing = new RoundRobinRouting<>();
        this.decomposition = new IdentityDecomposition<>();
        this.activation = new OnExplicitDispatch<>();
        this.aggregation = new CollectAll<>();
        this.termination = new MaxIterationsTermination<>(maxRounds);
    }

    public DebateBuilder<T> debaters(AgentRef... agents) {
        return (DebateBuilder<T>) super.agents(agents);
    }

    public DebateBuilder<T> judge(AgentRef judge) {
        this.judge = judge;
        return this;
    }

    public DebateBuilder<T> maxRounds(int rounds) {
        this.maxRounds = rounds;
        this.termination = new MaxIterationsTermination<>(rounds);
        this.convergenceExplicitlySet = false;
        return this;
    }

    public DebateBuilder<T> convergence(TerminationCondition<T> convergence) {
        this.termination = convergence;
        this.convergenceExplicitlySet = true;
        return this;
    }

    @Override
    public ExecutionModel<T> build() {
        if (judge != null && convergenceExplicitlySet) {
            throw new IllegalStateException(
                    "judge() and convergence() are mutually exclusive — "
                            + "judge() creates its own JudgeConvergence termination");
        }

        if (judge != null) {
            this.termination = new JudgeConvergence<>(judge, maxRounds);
        }

        return super.build();
    }
}
