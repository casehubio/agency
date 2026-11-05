package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class AgentDecisionLoop {

    private static final System.Logger LOG =
            System.getLogger(AgentDecisionLoop.class.getName());

    private final DecisionLoopDescriptor                                 descriptor;
    private final DecisionContext                                        context;
    private final SummarisationRunner<ObservationEvent, DecisionOutcome> runner;
    private final AgentBudgetView                                        budget;
    private final Consumer<DecisionLoopEvent>                            eventSink;

    AgentDecisionLoop(DecisionLoopDescriptor descriptor,
                      DecisionContext context,
                      SummarisationRunner<ObservationEvent, DecisionOutcome> runner,
                      AgentBudgetView budget,
                      Consumer<DecisionLoopEvent> eventSink) {
        this.descriptor = descriptor;
        this.context    = context;
        this.runner     = runner;
        this.budget     = budget;
        this.eventSink  = eventSink;
    }

    public void collect(ObservationEvent event) {
        eventSink.accept(new DecisionLoopEvent.ObservationGathered(
                context.caseId(), context.agentId(), event.key(), Instant.now()));
        runner.collect(new LevelEvent<>(event, event.timestamp().toEpochMilli(),
                                        DecisionLoopLevels.OBSERVATION, null));
    }

    public CompletionStage<Void> tick(long now) {return runner.tick(now);}

    public CompletionStage<Void> flush() {return runner.flush();}

    public void close() {
        runner.clear();
    }

    public DecisionLoopDescriptor descriptor() {return descriptor;}

    public DecisionContext context()           {return context;}

    public AgentBudgetView budget()            {return budget;}
}
