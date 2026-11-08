package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiSwarmTopologyTest {

    TestRegistry registry;
    MultiSwarmTopology topology;
    UUID caseId;

    @BeforeEach
    void setUp() {
        registry = new TestRegistry();
        caseId = UUID.randomUUID();
        topology = MultiSwarmTopology.builder()
                .registry(registry)
                .relevanceNetwork(RelevanceNetworks.standard())
                .escalationNetwork(EscalationNetworks.standard())
                .consistencyChecker(new DomainConsistencyChecker())
                .build();
    }

    @Test
    void describeTopology_returns_multi_swarm() {
        var descriptor = topology.describeTopology(caseId);
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.MULTI_SWARM);
    }

    @Test
    void builder_rejects_null_registry() {
        assertThatNullPointerException().isThrownBy(() ->
                MultiSwarmTopology.builder()
                        .relevanceNetwork(RelevanceNetworks.standard())
                        .escalationNetwork(EscalationNetworks.standard())
                        .consistencyChecker(new DomainConsistencyChecker())
                        .build());
    }

    @Test
    void registry_listener_creates_scorer_on_registration() {
        var swarmId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        assertThat(topology.hasScorer(swarmId)).isTrue();
    }

    @Test
    void registry_listener_removes_scorer_on_deregistration() {
        var swarmId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        registry.deregister(swarmId);
        assertThat(topology.hasScorer(swarmId)).isFalse();
    }

    @Test
    void onSwarmEvent_publishes_to_bus() {
        var swarmId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(
                swarmId, new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
        assertThat(topology.signalBus().replayBuffer()).isNotEmpty();
    }

    @Test
    void onSwarmEvent_ignores_unknown_swarm() {
        topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(
                UUID.randomUUID(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
        assertThat(topology.signalBus().replayBuffer()).isEmpty();
    }

    @Test
    void getScorer_returns_scorer_for_registered_swarm() {
        var swarmId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        assertThat(topology.getScorer(swarmId)).isNotNull();
    }

    static class TestRegistry implements MultiSwarmRegistry {
        private final ConcurrentHashMap<UUID, SwarmDescriptor> swarms = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<RegistryListener> listeners = new CopyOnWriteArrayList<>();

        @Override public void register(SwarmDescriptor d) {
            swarms.put(d.swarmId(), d);
            listeners.forEach(l -> l.onRegistered(d));
        }
        @Override public void deregister(UUID id) {
            if (swarms.remove(id) != null) listeners.forEach(l -> l.onDeregistered(id));
        }
        @Override public void updateInterests(UUID id, Set<String> caps) {
            listeners.forEach(l -> l.onInterestsUpdated(id, caps));
        }
        @Override public List<SwarmDescriptor> activeSwarms(UUID caseId) { return List.of(); }
        @Override public List<SwarmDescriptor> allActive() { return List.of(); }
        @Override public List<SwarmDescriptor> children(UUID parentId) { return List.of(); }
        @Override public @Nullable SwarmDescriptor get(UUID id) { return swarms.get(id); }
        @Override public void addListener(RegistryListener l) { listeners.add(l); }
    }
}
