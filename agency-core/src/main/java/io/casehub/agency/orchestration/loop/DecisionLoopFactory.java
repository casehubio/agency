package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetTelemetryEvent;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.agency.stigmergy.llm.InFlightGate;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.WindowPolicy;
import io.casehub.api.model.coordination.ObservationStrategy;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

public class DecisionLoopFactory {

    private final PromptSectionRegistry           sectionRegistry;
    private final EventStreamBus<DecisionOutcome> outputBus;

    public DecisionLoopFactory(PromptSectionRegistry sectionRegistry,
                               EventStreamBus<DecisionOutcome> outputBus) {
        this.sectionRegistry = sectionRegistry;
        this.outputBus       = outputBus;
    }

    public AgentDecisionLoop create(
            DecisionLoopDescriptor descriptor,
            DecisionContext context,
            ContentSummariser<ObservationEvent, DecisionOutcome> reasoner,
            ContentSummariser<ObservationEvent, DecisionOutcome> heuristic,
            AutonomyBudgetPool pool,
            DegradationTrigger additionalTrigger,
            Consumer<SwarmDegradedEvent> degradationSink,
            Consumer<BudgetTelemetryEvent> budgetEventSink) {
        return create(descriptor, context, reasoner, heuristic, pool,
                additionalTrigger, degradationSink, budgetEventSink, e -> {});
    }

    public AgentDecisionLoop create(
            DecisionLoopDescriptor descriptor,
            DecisionContext context,
            ContentSummariser<ObservationEvent, DecisionOutcome> reasoner,
            ContentSummariser<ObservationEvent, DecisionOutcome> heuristic,
            AutonomyBudgetPool pool,
            DegradationTrigger additionalTrigger,
            Consumer<SwarmDegradedEvent> degradationSink,
            Consumer<BudgetTelemetryEvent> budgetEventSink,
            Consumer<DecisionLoopEvent> loopEventSink) {
        var sections = sectionRegistry.resolveAll(descriptor.promptSectionNames());
        var view     = pool.createView(context.agentId(), descriptor.budgetPriority());

        var budgetTrigger = new io.casehub.agency.orchestration.degradation.BudgetDegradationTrigger(view);
        var compositeTrigger = new io.casehub.agency.orchestration.degradation.CompositeDegradationTrigger(
                List.of(budgetTrigger, additionalTrigger));

        Consumer<BudgetTelemetryEvent> budgetBridge = event -> {
            budgetEventSink.accept(event);
            switch (event) {
                case BudgetTelemetryEvent.Acquisition a when !a.granted() ->
                    loopEventSink.accept(new DecisionLoopEvent.BudgetExhausted(
                            context.caseId(), context.agentId(), "budget denied",
                            a.pressure(), a.timestamp()));
                case BudgetTelemetryEvent.Acquisition a ->
                    loopEventSink.accept(new DecisionLoopEvent.ReasoningStarted(
                            context.caseId(), context.agentId(),
                            a.correlationId(), a.timestamp()));
                case BudgetTelemetryEvent.Outcome o ->
                    loopEventSink.accept(new DecisionLoopEvent.ReasoningCompleted(
                            context.caseId(), context.agentId(), o.correlationId(),
                            o.tokensConsumed(), o.latencyMs(), o.timestamp()));
            }
        };

        Consumer<SwarmDegradedEvent> degradationBridge = event -> {
            degradationSink.accept(event);
            loopEventSink.accept(new DecisionLoopEvent.Degraded(
                    context.caseId(), context.agentId(), event.capability(),
                    event.reason(), event.fallbackActive(), Instant.now()));
        };

        ContentSummariser<ObservationEvent, DecisionOutcome> withSections =
                new SectionAwareContentSummariser(reasoner, sections, context);

        ContentSummariser<ObservationEvent, DecisionOutcome> budgetAware =
                new io.casehub.agency.orchestration.budget.BudgetAwareSummariser<>(withSections, view, budgetBridge, r -> 0L, "unknown");

        var degradationAware = new io.casehub.agency.orchestration.degradation.DegradationAwareSummariser<>(
                budgetAware, heuristic, compositeTrigger, degradationBridge, descriptor.name());

        var gate = new InFlightGate<>(degradationAware);

        EmissionPolicy<ObservationEvent, ?> emissionPolicy = buildEmissionPolicy(
                descriptor.observationStrategy());

        var runner = SummarisationRunner.<ObservationEvent, DecisionOutcome>builder(
                                                gate.summariser().asSummariser(),
                                                outputBus,
                                                DecisionLoopLevels.DECISION)
                                        .emissionPolicy(gate.gating(emissionPolicy))
                                        .build();

        loopEventSink.accept(new DecisionLoopEvent.LoopTriggered(
                context.caseId(), context.agentId(), context.scope(),
                context.communicationMode(), Instant.now()));

        return new AgentDecisionLoop(descriptor, context, runner, view, loopEventSink);
    }

    private EmissionPolicy<ObservationEvent, ?> buildEmissionPolicy(
            ObservationStrategy strategy) {
        return switch (strategy) {
            case EVENT_DRIVEN -> EmissionPolicy.window(WindowPolicy.ofCount(1));
            case TICK_DRIVEN -> EmissionPolicy.window(
                    WindowPolicy.ofAge(5000));
            case DEMAND_DRIVEN -> (buffered, state, now) -> false;
        };
    }
}
