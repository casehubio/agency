package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.optimisation.prompt.VariantSelector;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopEvent;
import io.casehub.agency.orchestration.swarm.prompt.SwarmPromptOutcomeProvider;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SwarmCoordinatorBuilder {

    private final     List<SwarmPromptSection>   additionalSections   = new ArrayList<>();
    private           AgentProvider              agentProvider;
    private           ExperienceReplayConfig     experienceConfig     = ExperienceReplayConfig.defaults();
    private           AutonomyBudgetPolicy       budgetPolicy         = AutonomyBudgetPolicy.defaults();
    private           int                        preFilterThreshold   = 12;
    private           double                     convergenceThreshold = 0.8;
    private           SystemPromptCustomiser     promptCustomiser;
    private @Nullable VariantSelector            variantSelector;
    private @Nullable SwarmPromptOutcomeProvider outcomeProvider;
    private           Consumer<DecisionLoopEvent> loopEventSink = e -> {};


    public SwarmCoordinatorBuilder agentProvider(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
        return this;
    }

    public SwarmCoordinatorBuilder experienceConfig(ExperienceReplayConfig config) {
        this.experienceConfig = config;
        return this;
    }

    public SwarmCoordinatorBuilder budgetPolicy(AutonomyBudgetPolicy policy) {
        this.budgetPolicy = policy;
        return this;
    }

    public SwarmCoordinatorBuilder preFilterThreshold(int threshold) {
        this.preFilterThreshold = threshold;
        return this;
    }

    public SwarmCoordinatorBuilder convergenceThreshold(double threshold) {
        this.convergenceThreshold = threshold;
        return this;
    }


    public SwarmCoordinatorBuilder promptCustomiser(SystemPromptCustomiser customiser) {
        this.promptCustomiser = customiser;
        return this;
    }

    public SwarmCoordinatorBuilder promptSections(List<SwarmPromptSection> sections) {
        this.additionalSections.clear();
        this.additionalSections.addAll(sections);
        return this;
    }

    public SwarmCoordinatorBuilder addPromptSection(SwarmPromptSection section) {
        this.additionalSections.add(section);
        return this;
    }

    public SwarmCoordinatorBuilder variantSelector(VariantSelector selector) {
        this.variantSelector = selector;
        return this;
    }

    public SwarmCoordinatorBuilder outcomeProvider(SwarmPromptOutcomeProvider provider) {
        this.outcomeProvider = provider;
        return this;
    }

    public SwarmCoordinatorBuilder loopEventSink(Consumer<DecisionLoopEvent> sink) {
        this.loopEventSink = Objects.requireNonNull(sink);
        return this;
    }


    public io.casehub.agency.orchestration.topology.CentralisedTopology<SwarmContext> buildTopology() {
        Objects.requireNonNull(agentProvider, "agentProvider is required");

        var experienceSection = new SwarmExperienceSection(experienceConfig);
        var routingStrategy = new SwarmRoutingStrategy(agentProvider, experienceSection,
                                                       preFilterThreshold, promptCustomiser, additionalSections);
        var aggregation = new CollectiveReasoningAggregation(agentProvider, promptCustomiser);
        TerminationCondition<SwarmContext> termination = new SwarmConvergenceTermination(convergenceThreshold);

        String slot = variantSelector != null
                      ? variantSelector.selectSlot(java.util.UUID.randomUUID(), "swarm-coordination")
                      : "control";
        routingStrategy.setVariantSlot(slot);
        aggregation.setVariantSlot(slot);

        if (outcomeProvider != null) {
            var recorder = new io.casehub.agency.orchestration.swarm.prompt.SwarmOutcomeRecorder(termination, slot, outcomeProvider);
            termination = recorder;
        }

        var sections = new java.util.ArrayList<String>();
        sections.add("experience-replay");
        for (var s : additionalSections) {
            sections.add(s.getClass().getSimpleName());
        }

        var swarmContext = new SwarmContext();
        var provisioner = new SwarmProvisioningAdvisorImpl(agentProvider, swarmContext, promptCustomiser);
        provisioner.setVariantSlot(slot);

        var fallback = new SwarmFallbackStrategy();
        var routingHeuristic = new io.casehub.agency.orchestration.degradation.HeuristicRoutingSummariser(fallback, java.util.List::of);
        var aggregationHeuristic = new io.casehub.agency.orchestration.degradation.HeuristicAggregationSummariser(fallback);
        var provisioningHeuristic = new io.casehub.agency.orchestration.degradation.HeuristicProvisioningSummariser(fallback, 0, 10);

        return new io.casehub.agency.orchestration.topology.CentralisedTopology<>(
                swarmContext, routingStrategy, aggregation,
                termination, sections, provisioner, java.util.List::of,
                routingHeuristic, aggregationHeuristic, provisioningHeuristic,
                e -> {  // swarmEventHandler
                    switch (e) {
                        case SwarmCoordinationEvent.SwarmInitialised s -> swarmContext.onSwarmInitialised(s.experiences(), s.progress());
                        case SwarmCoordinationEvent.RoleChanged r -> swarmContext.onRoleChanged(r.roles(), r.progress());
                        case SwarmCoordinationEvent.TeamChanged t -> swarmContext.onTeamChanged(t.teams(), t.progress());
                        case SwarmCoordinationEvent.ProgressUpdated p -> swarmContext.onProgressUpdated(p.progress());
                        case SwarmCoordinationEvent.MemberCompleted m -> swarmContext.onMemberCompleted(m.agentId());
                        case SwarmCoordinationEvent.ProvisioningChanged p -> swarmContext.onProvisioningChanged(p.currentSize(), p.maxSize());
                    }
                },
                loopEventSink);
    }

}
