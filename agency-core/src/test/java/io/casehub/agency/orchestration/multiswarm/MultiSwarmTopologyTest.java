package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiSwarmTopologyTest {

    TestMultiSwarmRegistry registry;
    MultiSwarmTopology topology;
    UUID caseId;

    @BeforeEach
    void setUp() {
        registry = new TestMultiSwarmRegistry();
        caseId = UUID.randomUUID();
        topology = MultiSwarmTopology.builder()
                .registry(registry)
                .weightedScoring(0.3, 0.6)
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
                        .weightedScoring(0.5, 0.6)
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

    @Test
    void finding_signal_expands_belief_set() throws InterruptedException {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmA, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        registry.register(new SwarmDescriptor(swarmB, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("b1"), null));

        topology.signalBus().publish(new MultiSwarmSignal.Finding(swarmA,
                Set.of("analysis"), System.currentTimeMillis(),
                "system-health", "node-7:degraded", 0.9, Map.of()));

        Thread.sleep(200);
        assertThat(topology.currentBeliefs().isEmpty()).isFalse();
    }

    @Test
    void deregistration_cleans_up_beliefs() throws InterruptedException {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmA, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("a1"), null));
        registry.register(new SwarmDescriptor(swarmB, caseId, TopologyType.CENTRALISED,
                Set.of("analysis"), Set.of("b1"), null));

        topology.signalBus().publish(new MultiSwarmSignal.Finding(swarmA,
                Set.of("analysis"), System.currentTimeMillis(),
                "system-health", "node-7:degraded", 0.9, Map.of()));

        Thread.sleep(200);
        assertThat(topology.currentBeliefs().isEmpty()).isFalse();

        registry.deregister(swarmA);
        assertThat(topology.currentBeliefs().all().stream()
                .noneMatch(b -> b.value().swarmId().equals(swarmA))).isTrue();
    }

    @Test
    void close_does_not_throw() {
        topology.close();
    }
}
