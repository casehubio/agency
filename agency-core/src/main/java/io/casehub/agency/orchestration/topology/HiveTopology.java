package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class HiveTopology implements CoordinationTopology {

    private final Map<String, AgentRole> agentRoles;
    private final HivePatternAdapter patternAdapter;
    private final OrchestrationPatternType patternType;

    public HiveTopology(Map<String, AgentRole> agentRoles,
                        HivePatternAdapter patternAdapter,
                        OrchestrationPatternType patternType) {
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.patternAdapter = Objects.requireNonNull(patternAdapter);
        this.patternType = Objects.requireNonNull(patternType);
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(TopologyType.CENTRALISED, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        context.descriptor().coordinatorAgentId()
               .orElseThrow(() -> new IllegalStateException(
                       "HiveTopology requires a coordinator agent (INITIATOR role)"));

        var pool = new AutonomyBudgetPool(context.budgetPolicy(), context.caseId() + "-CENTRALISED");

        var coordinatorDescriptor = new DecisionLoopDescriptor(
                "hive-coordinator",
                ObservationStrategy.EVENT_DRIVEN,
                DecisionScope.FULL_AUTONOMY,
                CommunicationMode.BROADCAST,
                BudgetPriority.RESERVED,
                List.of("hive-coordination-strategy", "role-assignments"));

        var workerDescriptors = context.descriptor().agentRoles().entrySet().stream()
                                       .filter(e -> e.getValue().structuralRole() != StructuralRole.INITIATOR)
                                       .map(e -> new DecisionLoopDescriptor(
                                               "hive-worker-" + e.getKey(),
                                               ObservationStrategy.DEMAND_DRIVEN,
                                               DecisionScope.TASK_ADAPTATION,
                                               CommunicationMode.DIRECT_MESSAGING,
                                               BudgetPriority.NORMAL,
                                               List.of("role-context")))
                                       .toList();

        var allDescriptors = new ArrayList<DecisionLoopDescriptor>();
        allDescriptors.add(coordinatorDescriptor);
        allDescriptors.addAll(workerDescriptors);

        DecisionOutcomeExecutor executor = (agentId, outcome, ctx) ->
                                                   patternAdapter.execute(patternType, agentId, outcome, ctx);

        return new CoordinationPlan(allDescriptors, executor, pool);
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {
    }

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
        patternAdapter.onFeedback(patternType, feedback);
    }
}
