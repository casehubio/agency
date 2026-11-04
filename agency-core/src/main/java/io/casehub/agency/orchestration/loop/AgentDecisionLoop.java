package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AgentBudgetView;

import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AgentDecisionLoop {

    private static final System.Logger LOG =
            System.getLogger(AgentDecisionLoop.class.getName());

    private final DecisionLoopDescriptor                                 descriptor;
    private final DecisionContext                                        context;
    private final SummarisationRunner<ObservationEvent, DecisionOutcome> runner;
    private final AgentBudgetView                                        budget;

    AgentDecisionLoop(DecisionLoopDescriptor descriptor,
                      DecisionContext context,
                      SummarisationRunner<ObservationEvent, DecisionOutcome> runner,
                      AgentBudgetView budget) {
        this.descriptor = descriptor;
        this.context    = context;
        this.runner     = runner;
        this.budget     = budget;
    }

    public void collect(ObservationEvent event) {
        runner.collect(new LevelEvent<>(event, event.timestamp().toEpochMilli(),
                                        DecisionLoopLevels.OBSERVATION, null));
    }

    public CompletionStage<Void> tick(long now) {
        if (budget.isBudgetExhausted()) {
            LOG.log(System.Logger.Level.DEBUG,
                    "Budget exhausted for {0} — skipping tick", context.agentId());
            return CompletableFuture.completedFuture(null);
        }
        return runner.tick(now);
    }

    public CompletionStage<Void> flush() {
        if (budget.isBudgetExhausted()) {
            return CompletableFuture.failedFuture(
                    new BudgetExhaustedException(context.agentId()));
        }
        return runner.flush();
    }

    public void close() {
        runner.clear();
    }

    public DecisionLoopDescriptor descriptor() {return descriptor;}

    public DecisionContext context()           {return context;}

    public AgentBudgetView budget()            {return budget;}
}
