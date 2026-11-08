package io.casehub.agency.agentic.yaml.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.casehub.agency.core.yaml.spec.MultiSwarmCoordinationSpec;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class MultiSwarmTopologyCompilerTest {

    MultiSwarmRegistry registry = new StubRegistry();
    MultiSwarmTopologyCompiler compiler = new MultiSwarmTopologyCompiler();

    @Test
    void compiles_multi_swarm_with_child_swarms() {
        var spec = new TopologySpec.MultiSwarm(
                "coordinator",
                Map.of(
                        "analysis", new TopologySpec.Swarm(null),
                        "remediation", new TopologySpec.Swarm(null)),
                new MultiSwarmCoordinationSpec(0.7, 0.8, 0.85));
        var result = compiler.compile(spec, null, registry);
        assertThat(result.topology()).isNotNull();
        assertThat(result.topology().describeTopology(UUID.randomUUID()).topologyType())
                .isEqualTo(TopologyType.MULTI_SWARM);
    }

    @Test
    void compiles_without_coordination_spec() {
        var spec = new TopologySpec.MultiSwarm(
                "coordinator",
                Map.of("analysis", new TopologySpec.Swarm(null)),
                null);
        var result = compiler.compile(spec, null, registry);
        assertThat(result.topology()).isNotNull();
    }

    @Test
    void rejects_empty_swarms() {
        var spec = new TopologySpec.MultiSwarm("coordinator", Map.of(), null);
        assertThatIllegalArgumentException().isThrownBy(() ->
                compiler.compile(spec, null, registry));
    }

    static class StubRegistry implements MultiSwarmRegistry {
        private final ConcurrentHashMap<UUID, SwarmDescriptor> swarms = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<RegistryListener> listeners = new CopyOnWriteArrayList<>();
        @Override public void register(SwarmDescriptor d) { swarms.put(d.swarmId(), d); listeners.forEach(l -> l.onRegistered(d)); }
        @Override public void deregister(UUID id) { if (swarms.remove(id) != null) listeners.forEach(l -> l.onDeregistered(id)); }
        @Override public void updateInterests(UUID id, Set<String> caps) {}
        @Override public List<SwarmDescriptor> activeSwarms(UUID caseId) { return List.of(); }
        @Override public List<SwarmDescriptor> allActive() { return List.of(); }
        @Override public List<SwarmDescriptor> children(UUID parentId) { return List.of(); }
        @Override public @Nullable SwarmDescriptor get(UUID id) { return swarms.get(id); }
        @Override public void addListener(RegistryListener l) { listeners.add(l); }
    }
}
