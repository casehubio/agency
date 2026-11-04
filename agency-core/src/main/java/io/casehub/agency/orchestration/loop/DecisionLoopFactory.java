package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetAwareSummariser;
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
            AutonomyBudgetPool pool) {

        var sections = sectionRegistry.resolveAll(descriptor.promptSectionNames());
        var view     = pool.createView(context.agentId(), descriptor.budgetPriority());

        ContentSummariser<ObservationEvent, DecisionOutcome> withSections =
                new SectionAwareContentSummariser(reasoner, sections, context);

        ContentSummariser<ObservationEvent, DecisionOutcome> budgetAware =
                new BudgetAwareSummariser<>(withSections, view);

        var gate = new InFlightGate<>(budgetAware);

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
