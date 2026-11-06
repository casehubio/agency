package io.casehub.agency.agentic.yaml.topology;

import io.casehub.agency.orchestration.topology.HivePatternAdapter;
import io.casehub.agency.orchestration.topology.HiveTopology;
import io.casehub.agency.orchestration.topology.OrchestrationPatternType;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.eidos.org.api.RoleSpec;
import io.casehub.platform.agent.AgentProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HiveTopologyCompiler {

    public record CompiledHive(
            HiveTopology topology,
            OrchestrationPatternType patternType,
            Map<String, AgentRole> agentRoles,
            List<RoleSpec> roleSpecs
    ) {}

    public CompiledHive compile(TopologySpec.Hive spec, AgentProvider agentProvider) {
        if (spec.roles() == null || spec.roles().isEmpty()) {
            throw new IllegalArgumentException("Hive topology requires at least one role");
        }

        var patternType = resolvePatternType(spec.orchestration());
        var agentRoles = buildAgentRoles(spec.roles());
        var adapter = new HivePatternAdapter(agentProvider);
        var topology = new HiveTopology(agentRoles, adapter, patternType);
        var roleSpecs = buildRoleSpecs(spec.policy());

        return new CompiledHive(topology, patternType, agentRoles, roleSpecs);
    }

    private static OrchestrationPatternType resolvePatternType(String orchestration) {
        if (orchestration == null) return OrchestrationPatternType.SUPERVISOR;
        return switch (orchestration.toLowerCase()) {
            case "supervisor" -> OrchestrationPatternType.SUPERVISOR;
            case "debate" -> OrchestrationPatternType.DEBATE;
            case "voting" -> OrchestrationPatternType.VOTING;
            default -> throw new IllegalArgumentException(
                    "Unknown orchestration type: " + orchestration
                    + ". Valid values: supervisor, debate, voting");
        };
    }

    private static Map<String, AgentRole> buildAgentRoles(List<String> roles) {
        var result = new LinkedHashMap<String, AgentRole>();
        for (int i = 0; i < roles.size(); i++) {
            var name = roles.get(i);
            var role = i == 0
                    ? AgentRole.COORDINATOR
                    : AgentRole.of(name, StructuralRole.CONTRACTOR);
            result.put(name, role);
        }
        return result;
    }

    private static List<RoleSpec> buildRoleSpecs(Map<String, TopologySpec.RolePolicySpec> policy) {
        if (policy == null || policy.isEmpty()) return List.of();
        return policy.entrySet().stream()
                .map(e -> e.getValue().toRoleSpec(e.getKey()))
                .toList();
    }
}
