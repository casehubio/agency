package io.casehub.agency.core.yaml.registry;

import io.casehub.api.model.coordination.TopologyType;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TopologySpecRegistryTest {

    private final TopologySpecRegistry registry = new TopologySpecRegistry();

    @Test
    void swarm_defaultsToDistributed() {
        assertThat(registry.resolve(new TopologySpec.Swarm(null)))
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void swarm_authorityOverride() {
        assertThat(registry.resolve(new TopologySpec.Swarm("hybrid")))
                .isEqualTo(TopologyType.HYBRID);
    }

    @Test
    void hive_defaultsToCentralised() {
        assertThat(registry.resolve(new TopologySpec.Hive(null, null, null, null)))
                .isEqualTo(TopologyType.CENTRALISED);
    }

    @Test
    void hive_authorityOverride() {
        assertThat(registry.resolve(new TopologySpec.Hive("hierarchical", null, null, null)))
                .isEqualTo(TopologyType.HIERARCHICAL);
    }

    @Test
    void mesh_defaultsToDistributed() {
        assertThat(registry.resolve(new TopologySpec.Mesh(null)))
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void many_defaultsToDistributed() {
        assertThat(registry.resolve(new TopologySpec.Many()))
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void graduated_authority() {
        assertThat(registry.resolve(new TopologySpec.Swarm("graduated")))
                .isEqualTo(TopologyType.GRADUATED);
    }

    @Test
    void unknownAuthority_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.resolve(new TopologySpec.Swarm("unknown")));
    }
}
