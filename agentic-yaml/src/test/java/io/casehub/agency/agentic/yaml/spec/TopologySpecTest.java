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
    void hive_rolesImmutable() {
        var mutableRoles = new java.util.ArrayList<>(List.of("coordinator"));
        var hive = new TopologySpec.Hive("centralised", mutableRoles, null, null);
        mutableRoles.add("extra");
        assertThat(hive.roles()).hasSize(1);
    }
}
