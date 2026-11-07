package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopologySpecTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void swarm_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: swarm
                authority: distributed
                """, TopologySpec.class);
        assertThat(spec).isInstanceOf(TopologySpec.Swarm.class);
        assertThat(spec.authority()).isEqualTo("distributed");
    }

    @Test
    void swarm_defaultAuthority() throws Exception {
        var spec = yaml.readValue("""
                pattern: swarm
                """, TopologySpec.class);
        assertThat(spec).isInstanceOf(TopologySpec.Swarm.class);
        assertThat(spec.authority()).isNull();
    }

    @Test
    void hive_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: hive
                authority: centralised
                roles:
                  - coordinator
                  - planner
                  - builder
                orchestration: supervisor
                """, TopologySpec.class);
        assertThat(spec).isInstanceOf(TopologySpec.Hive.class);
        var hive = (TopologySpec.Hive) spec;
        assertThat(hive.authority()).isEqualTo("centralised");
        assertThat(hive.roles()).containsExactly("coordinator", "planner", "builder");
        assertThat(hive.orchestration()).isEqualTo("supervisor");
    }

    @Test
    void hive_withPolicy() throws Exception {
        var spec = yaml.readValue("""
                pattern: hive
                roles:
                  - coordinator
                policy:
                  coordinator:
                    obligations:
                      - review-output
                    permissions:
                      - provision-agent
                """, TopologySpec.class);
        var hive = (TopologySpec.Hive) spec;
        assertThat(hive.policy()).containsKey("coordinator");
        var policy = hive.policy().get("coordinator");
        assertThat(policy.obligations()).containsExactly("review-output");
        assertThat(policy.permissions()).containsExactly("provision-agent");
    }

    @Test
    void mesh_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: mesh
                authority: distributed
                """, TopologySpec.class);
        assertThat(spec).isInstanceOf(TopologySpec.Mesh.class);
    }

    @Test
    void many_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: many
                """, TopologySpec.class);
        assertThat(spec).isInstanceOf(TopologySpec.Many.class);
        assertThat(spec.authority()).isNull();
    }

    @Test
    void rolePolicySpec_immutable() {
        var mutableObligations = new java.util.ArrayList<>(List.of("a"));
        var policy = new TopologySpec.RolePolicySpec(mutableObligations, null);
        mutableObligations.add("b");
        assertThat(policy.obligations()).hasSize(1);
    }

    @Test
    void hive_withCardinality() throws Exception {
        var spec = yaml.readValue("""
                                  pattern: hive
                                  roles:
                                    - coordinator
                                  policy:
                                    coordinator:
                                      obligations:
                                        - review-output
                                      permissions:
                                        - provision-agent
                                      minAgents: 1
                                      maxAgents: 3
                                  """, TopologySpec.class);
        var hive   = (TopologySpec.Hive) spec;
        var policy = hive.policy().get("coordinator");
        assertThat(policy.minAgents()).isEqualTo(1);
        assertThat(policy.maxAgents()).isEqualTo(3);
    }

    @Test
    void rolePolicySpec_toRoleSpec_maps_all_fields() {
        var policy = new TopologySpec.RolePolicySpec(
                List.of("review-output", "report-status"),
                List.of("provision-agent"),
                2, 5);
        var roleSpec = policy.toRoleSpec("coordinator");

        assertThat(roleSpec.roleName()).isEqualTo("coordinator");
        assertThat(roleSpec.minCardinality()).isEqualTo(2);
        assertThat(roleSpec.maxCardinality()).isEqualTo(5);
        assertThat(roleSpec.obligations()).containsExactly("review-output", "report-status");
        assertThat(roleSpec.permissions()).containsExactly("provision-agent");
        assertThat(roleSpec.policies()).hasSize(3);
        assertThat(roleSpec.policies().get(0).capabilityName()).isEqualTo("review-output");
        assertThat(roleSpec.policies().get(0).kind()).isEqualTo(io.casehub.eidos.org.api.PolicyKind.OBLIGATION);
        assertThat(roleSpec.policies().get(1).capabilityName()).isEqualTo("report-status");
        assertThat(roleSpec.policies().get(1).kind()).isEqualTo(io.casehub.eidos.org.api.PolicyKind.OBLIGATION);
        assertThat(roleSpec.policies().get(2).capabilityName()).isEqualTo("provision-agent");
        assertThat(roleSpec.policies().get(2).kind()).isEqualTo(io.casehub.eidos.org.api.PolicyKind.PERMISSION);
    }

    @Test
    void rolePolicySpec_toRoleSpec_defaults_cardinality() {
        var policy   = new TopologySpec.RolePolicySpec(List.of("task"), null, null, null);
        var roleSpec = policy.toRoleSpec("worker");

        assertThat(roleSpec.minCardinality()).isEqualTo(0);
        assertThat(roleSpec.maxCardinality()).isEqualTo(-1);
    }

    @Test
    void rolePolicySpec_toRoleSpec_empty_policies() {
        var policy   = new TopologySpec.RolePolicySpec(null, null, 1, 1);
        var roleSpec = policy.toRoleSpec("singleton");

        assertThat(roleSpec.policies()).isEmpty();
        assertThat(roleSpec.obligations()).isEmpty();
        assertThat(roleSpec.permissions()).isEmpty();
        assertThat(roleSpec.minCardinality()).isEqualTo(1);
        assertThat(roleSpec.maxCardinality()).isEqualTo(1);
    }


    @Test
    void rolePolicySpec_toRoleSpec_uses_vocabulary_when_present() {
        var policy = new TopologySpec.RolePolicySpec(
                List.of("diagnose"), null, 1, 3, "medical");
        var roleSpec = policy.toRoleSpec("doctor");

        assertThat(roleSpec.roleName()).isEqualTo("doctor");
        assertThat(roleSpec.roleVocabulary()).isEqualTo("medical");
    }

    @Test
    void rolePolicySpec_toRoleSpec_falls_back_to_roleName_when_no_vocabulary() {
        var policy = new TopologySpec.RolePolicySpec(
                List.of("diagnose"), null, 1, 3);
        var roleSpec = policy.toRoleSpec("doctor");

        assertThat(roleSpec.roleName()).isEqualTo("doctor");
        assertThat(roleSpec.roleVocabulary()).isEqualTo("doctor");
    }

    @Test
    void hive_withVocabulary_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: hive
                roles:
                  - doctor
                policy:
                  doctor:
                    obligations:
                      - diagnose
                    vocabulary: medical
                """, TopologySpec.class);
        var hive = (TopologySpec.Hive) spec;
        var policy = hive.policy().get("doctor");
        assertThat(policy.vocabulary()).isEqualTo("medical");
        assertThat(policy.toRoleSpec("doctor").roleVocabulary()).isEqualTo("medical");
    }

    @Test
    void rolePolicySpec_toRoleSpec_maps_incompatibleWith() {
        var policy = new TopologySpec.RolePolicySpec(
                List.of("review"), null, 1, 1, null, List.of("auditor", "observer"));
        var roleSpec = policy.toRoleSpec("coordinator");

        assertThat(roleSpec.incompatibleWith()).containsExactlyInAnyOrder("auditor", "observer");
    }

    @Test
    void rolePolicySpec_toRoleSpec_nullIncompatibleWith_yieldsEmptySet() {
        var policy = new TopologySpec.RolePolicySpec(List.of("task"), null);
        var roleSpec = policy.toRoleSpec("worker");

        assertThat(roleSpec.incompatibleWith()).isEmpty();
    }

    @Test
    void hive_withIncompatibleWith_deserialises() throws Exception {
        var spec = yaml.readValue("""
                pattern: hive
                roles:
                  - coordinator
                policy:
                  coordinator:
                    obligations:
                      - review
                    incompatibleWith:
                      - auditor
                """, TopologySpec.class);
        var hive = (TopologySpec.Hive) spec;
        var policy = hive.policy().get("coordinator");
        assertThat(policy.incompatibleWith()).containsExactly("auditor");
    }

    @Test
    void hive_rolesImmutable() {
        var mutableRoles = new java.util.ArrayList<>(List.of("coordinator"));
        var hive = new TopologySpec.Hive("centralised", mutableRoles, null, null);
        mutableRoles.add("extra");
        assertThat(hive.roles()).hasSize(1);
    }
}
