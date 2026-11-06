package io.casehub.agency.agentic.yaml.topology;

import io.casehub.agency.orchestration.topology.OrchestrationPatternType;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.agency.core.yaml.spec.TopologySpec.RolePolicySpec;
import io.casehub.eidos.org.api.PolicyKind;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HiveTopologyCompilerTest {

    private final AgentProvider agentProvider = mock(AgentProvider.class);
    private final HiveTopologyCompiler compiler = new HiveTopologyCompiler();

    @Test
    void compile_fullSpec_producesTopologyAndRoleSpecs() {
        var spec = new TopologySpec.Hive(
                "centralised",
                List.of("coordinator", "planner", "builder"),
                "supervisor",
                Map.of(
                        "coordinator", new RolePolicySpec(
                                List.of("review-output"), List.of("provision-agent"), 1, 1, "team-roles"),
                        "planner", new RolePolicySpec(
                                List.of("create-plan"), null, 1, 2, "team-roles")));

        var result = compiler.compile(spec, agentProvider);

        assertThat(result.topology()).isNotNull();
        assertThat(result.roleSpecs()).hasSize(2);
        assertThat(result.roleSpecs().stream().map(r -> r.roleName()).toList())
                .containsExactlyInAnyOrder("coordinator", "planner");
        assertThat(result.roleSpecs().stream().allMatch(r -> r.roleVocabulary().equals("team-roles")))
                .isTrue();
    }

    @Test
    void compile_defaultOrchestration_isSupervisor() {
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), null, null);
        var result = compiler.compile(spec, agentProvider);
        assertThat(result.patternType()).isEqualTo(OrchestrationPatternType.SUPERVISOR);
    }

    @Test
    void compile_debateOrchestration() {
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), "debate", null);
        var result = compiler.compile(spec, agentProvider);
        assertThat(result.patternType()).isEqualTo(OrchestrationPatternType.DEBATE);
    }

    @Test
    void compile_votingOrchestration() {
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), "voting", null);
        var result = compiler.compile(spec, agentProvider);
        assertThat(result.patternType()).isEqualTo(OrchestrationPatternType.VOTING);
    }

    @Test
    void compile_firstRoleIsInitiator_restAreContractors() {
        var spec = new TopologySpec.Hive(
                null, List.of("lead", "worker-1", "worker-2"), "supervisor", null);
        var result = compiler.compile(spec, agentProvider);

        assertThat(result.agentRoles().get("lead").structuralRole())
                .isEqualTo(StructuralRole.INITIATOR);
        assertThat(result.agentRoles().get("worker-1").structuralRole())
                .isEqualTo(StructuralRole.CONTRACTOR);
        assertThat(result.agentRoles().get("worker-2").structuralRole())
                .isEqualTo(StructuralRole.CONTRACTOR);
    }

    @Test
    void compile_emptyRoles_throws() {
        var spec = new TopologySpec.Hive(null, List.of(), "supervisor", null);
        assertThatThrownBy(() -> compiler.compile(spec, agentProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    void compile_nullRoles_throws() {
        var spec = new TopologySpec.Hive(null, null, "supervisor", null);
        assertThatThrownBy(() -> compiler.compile(spec, agentProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    void compile_unknownOrchestration_throws() {
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), "unknown", null);
        assertThatThrownBy(() -> compiler.compile(spec, agentProvider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void compile_nullPolicy_emptyRoleSpecs() {
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), null, null);
        var result = compiler.compile(spec, agentProvider);
        assertThat(result.roleSpecs()).isEmpty();
    }

    @Test
    void compile_policyWithVocabulary_passedThrough() {
        var spec = new TopologySpec.Hive(
                null, List.of("doctor"),
                "supervisor",
                Map.of("doctor", new RolePolicySpec(
                        List.of("diagnose"), null, 1, 3, "medical")));
        var result = compiler.compile(spec, agentProvider);

        assertThat(result.roleSpecs()).hasSize(1);
        var roleSpec = result.roleSpecs().getFirst();
        assertThat(roleSpec.roleName()).isEqualTo("doctor");
        assertThat(roleSpec.roleVocabulary()).isEqualTo("medical");
        assertThat(roleSpec.policies().getFirst().kind()).isEqualTo(PolicyKind.OBLIGATION);
    }

    @Test
    void compile_policyWithoutVocabulary_fallsBackToRoleName() {
        var spec = new TopologySpec.Hive(
                null, List.of("worker"),
                "supervisor",
                Map.of("worker", new RolePolicySpec(List.of("execute"), null)));
        var result = compiler.compile(spec, agentProvider);

        var roleSpec = result.roleSpecs().getFirst();
        assertThat(roleSpec.roleVocabulary()).isEqualTo("worker");
    }
}
