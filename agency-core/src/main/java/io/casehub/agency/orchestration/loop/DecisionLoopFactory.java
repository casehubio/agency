package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.stigmergy.llm.InFlightGate;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.agency.summarisation.EmissionPolicy;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.WindowPolicy;
import io.casehub.api.model.coordination.ObservationStrategy;

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
            io.casehub.agency.orchestration.degradation.DegradationTrigger additionalTrigger,
            java.util.function.Consumer<io.casehub.agency.orchestration.swarm.SwarmDegradedEvent> degradationSink,
            java.util.function.Consumer<io.casehub.agency.orchestration.budget.BudgetTelemetryEvent> budgetEventSink) {
        var sections = sectionRegistry.resolveAll(descriptor.promptSectionNames());
        var view     = pool.createView(context.agentId(), descriptor.budgetPriority());

        var budgetTrigger = new io.casehub.agency.orchestration.degradation.BudgetDegradationTrigger(view);
        var compositeTrigger = new io.casehub.agency.orchestration.degradation.CompositeDegradationTrigger(
                java.util.List.of(budgetTrigger, additionalTrigger));

        ContentSummariser<ObservationEvent, DecisionOutcome> withSections =
                new SectionAwareContentSummariser(reasoner, sections, context);

        ContentSummariser<ObservationEvent, DecisionOutcome> budgetAware =
                new io.casehub.agency.orchestration.budget.BudgetAwareSummariser<>(withSections, view, budgetEventSink, r -> 0L, "unknown");

        var degradationAware = new io.casehub.agency.orchestration.degradation.DegradationAwareSummariser<>(
                budgetAware, heuristic, compositeTrigger, degradationSink, descriptor.name());

        var gate = new InFlightGate<>(degradationAware);

        EmissionPolicy<ObservationEvent, ?> emissionPolicy = buildEmissionPolicy(
                descriptor.observationStrategy());

        var runner = SummarisationRunner.<ObservationEvent, DecisionOutcome>builder(
                                                gate.summariser().asSummariser(),
                                                outputBus,
                                                DecisionLoopLevels.DECISION)
                                        .emissionPolicy(gate.gating(emissionPolicy))
                                        .build();

        return new AgentDecisionLoop(descriptor, context, runner, view);
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
