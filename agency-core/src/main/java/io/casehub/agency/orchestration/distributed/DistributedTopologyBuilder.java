package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.platform.agent.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.LoopPromptSection;
import io.casehub.agency.orchestration.topology.DistributedTopology;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.platform.agent.AgentProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DistributedTopologyBuilder {

    private AgentProvider agentProvider;
    private final Map<String, AgentRole> agentRoles = new LinkedHashMap<>();
    private AutonomyBudgetPolicy budgetPolicy = AutonomyBudgetPolicy.defaults();
    private double convergenceThreshold = 0.8;
    private @Nullable SystemPromptCustomiser promptCustomiser;
    private final List<LoopPromptSection> additionalSections = new ArrayList<>();
    private final Map<String, String> modelOverrides = new HashMap<>();

    public DistributedTopologyBuilder agentProvider(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
        return this;
    }

    public DistributedTopologyBuilder agentRoles(Map<String, AgentRole> roles) {
        this.agentRoles.clear();
        this.agentRoles.putAll(roles);
        return this;
    }

    public DistributedTopologyBuilder budgetPolicy(AutonomyBudgetPolicy policy) {
        this.budgetPolicy = policy;
        return this;
    }

    public DistributedTopologyBuilder convergenceThreshold(double threshold) {
        this.convergenceThreshold = threshold;
        return this;
    }

    public DistributedTopologyBuilder promptCustomiser(SystemPromptCustomiser customiser) {
        this.promptCustomiser = customiser;
        return this;
    }

    public DistributedTopologyBuilder addPromptSection(LoopPromptSection section) {
        this.additionalSections.add(section);
        return this;
    }

    public DistributedTopologyBuilder agentModelOverride(String agentId, String modelId) {
        this.modelOverrides.put(agentId, modelId);
        return this;
    }

    public DistributedTopology buildTopology() {
        Objects.requireNonNull(agentProvider, "agentProvider is required");
        if (agentRoles.isEmpty()) {
            throw new IllegalArgumentException("at least one agent role is required");
        }

        var reasoner = new DistributedAgentReasoner(agentProvider, promptCustomiser);
        return new DistributedTopology(
                Map.copyOf(agentRoles), reasoner, budgetPolicy,
                convergenceThreshold, List.copyOf(additionalSections),
                Map.copyOf(modelOverrides));
    }
}
