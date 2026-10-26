package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.platform.agent.AgentProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SwarmCoordinatorBuilder {

    private final List<SwarmPromptSection> additionalSections   = new ArrayList<>();
    private       AgentProvider            agentProvider;
    private       ExperienceReplayConfig   experienceConfig     = ExperienceReplayConfig.defaults();
    private       SwarmInvocationPolicy    invocationPolicy     = SwarmInvocationPolicy.defaults();
    private       int                      preFilterThreshold   = 12;
    private       double                   convergenceThreshold = 0.8;
    private       SystemPromptCustomiser   promptCustomiser;


    public SwarmCoordinatorBuilder agentProvider(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
        return this;
    }

    public SwarmCoordinatorBuilder experienceConfig(ExperienceReplayConfig config) {
        this.experienceConfig = config;
        return this;
    }

    public SwarmCoordinatorBuilder invocationPolicy(SwarmInvocationPolicy policy) {
        this.invocationPolicy = policy;
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

    public LlmSwarmCoordinator build() {
        Objects.requireNonNull(agentProvider, "agentProvider is required");

        var context           = new SwarmContext();
        var tracker           = new SwarmInvocationTracker(invocationPolicy);
        var experienceSection = new SwarmExperienceSection(experienceConfig);
        var routingStrategy   = new SwarmRoutingStrategy(agentProvider, experienceSection, preFilterThreshold, tracker);
        var aggregation       = new CollectiveReasoningAggregation(agentProvider, tracker, promptCustomiser);
        var convergence       = new SwarmConvergenceTermination(convergenceThreshold);
        var eventSource       = new SwarmEventSource(context);
        var fallback          = new SwarmFallbackStrategy();

        var sections = new ArrayList<SwarmPromptSection>();
        sections.add(experienceSection);
        sections.addAll(additionalSections);

        return new LlmSwarmCoordinator(
                agentProvider, context, routingStrategy, aggregation,
                convergence, eventSource, tracker, fallback, sections);
    }
}
