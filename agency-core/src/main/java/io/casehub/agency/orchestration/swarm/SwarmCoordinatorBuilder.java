package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.optimisation.prompt.VariantSelector;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.swarm.prompt.SwarmPromptOutcomeProvider;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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


    public io.casehub.agency.orchestration.topology.CentralisedTopology buildTopology() {
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

        return new io.casehub.agency.orchestration.topology.CentralisedTopology(
                swarmContext, routingStrategy, aggregation,
                termination, new SwarmFallbackStrategy(), sections,
                provisioner, java.util.List::of);
    }

}
