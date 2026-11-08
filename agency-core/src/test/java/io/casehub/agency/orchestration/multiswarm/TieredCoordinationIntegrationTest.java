package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TieredCoordinationIntegrationTest {

    TestRegistry registry;
    MultiSwarmTopology topology;
    UUID caseId;
    UUID analysisSwarmId;
    UUID remediationSwarmId;

    @BeforeEach
    void setUp() {
        registry = new TestRegistry();
        caseId = UUID.randomUUID();
        analysisSwarmId = UUID.randomUUID();
        remediationSwarmId = UUID.randomUUID();

        topology = MultiSwarmTopology.builder()
                .registry(registry)
                .relevanceNetwork(RelevanceNetworks.standard())
                .escalationNetwork(EscalationNetworks.standard())
                .consistencyChecker(new DomainConsistencyChecker())
                .relevanceThreshold(0.5)
                .escalationThreshold(0.6)
                .build();

        registry.register(new SwarmDescriptor(analysisSwarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis", "pattern-detection"), Set.of("a1", "a2"), null));
        registry.register(new SwarmDescriptor(remediationSwarmId, caseId, TopologyType.CENTRALISED,
                Set.of("remediation", "analysis"), Set.of("r1"), null));
    }

    @Test
    void l0_signal_delivery_between_swarms() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        topology.signalBus().subscribe(
                s -> s.domainTags().contains("analysis") && !s.sourceSwarmId().equals(remediationSwarmId),
                s -> { received.add(s); latch.countDown(); });

        topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                Set.of("analysis"), System.currentTimeMillis(),
                "system-health", "node-7:degraded", 0.9, Map.of("source", "metrics")));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Finding.class);
    }

    @Test
    void l1_bjt_scores_signal_relevance() {
        var scorer = topology.getScorer(remediationSwarmId);
        assertThat(scorer).isNotNull();
        double score = scorer.score(0.5, 0.8, 0.9, 0.0);
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void l2_belief_set_detects_contradiction() {
        var checker = new DomainConsistencyChecker();
        var beliefs = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(analysisSwarmId + ":system-health",
                        new SwarmConclusion(analysisSwarmId, "system-health", "node-7:healthy", 0.9), 90))
                .expand(Belief.of(remediationSwarmId + ":system-health",
                        new SwarmConclusion(remediationSwarmId, "system-health", "node-7:degraded", 0.7), 70));
        assertThat(checker.isConsistent(beliefs)).isFalse();

        var revised = beliefs.revise(
                Belief.of(remediationSwarmId + ":system-health",
                        new SwarmConclusion(remediationSwarmId, "system-health", "node-7:degraded", 0.7), 70),
                checker);
        assertThat(revised.size()).isLessThan(beliefs.size());
    }

    @Test
    void l0_cold_start_replay() {
        topology.signalBus().publish(new MultiSwarmSignal.Progress(analysisSwarmId,
                Set.of("analysis"), System.currentTimeMillis(), new SwarmProgress(0.3, 0.3, 0.3, Instant.now())));
        topology.signalBus().publish(new MultiSwarmSignal.Progress(analysisSwarmId,
                Set.of("analysis"), System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));

        var replay = topology.signalBus().replayBuffer();
        assertThat(replay).hasSize(2);
    }

    @Test
    void deregistration_cleans_up_scorers() {
        assertThat(topology.hasScorer(analysisSwarmId)).isTrue();
        registry.deregister(analysisSwarmId);
        assertThat(topology.hasScorer(analysisSwarmId)).isFalse();
    }

    @Test
    void high_relevance_scores_higher_than_low() {
        var scorer = topology.getScorer(analysisSwarmId);
        assertThat(scorer).isNotNull();
        double high = scorer.score(0.9, 0.9, 0.9, 0.9);
        double low = scorer.score(0.1, 0.1, 0.1, 0.1);
        assertThat(high).isGreaterThan(low);
    }

    static class TestRegistry implements MultiSwarmRegistry {
        private final ConcurrentHashMap<UUID, SwarmDescriptor> swarms = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<RegistryListener> listeners = new CopyOnWriteArrayList<>();
        @Override public void register(SwarmDescriptor d) { swarms.put(d.swarmId(), d); listeners.forEach(l -> l.onRegistered(d)); }
        @Override public void deregister(UUID id) { if (swarms.remove(id) != null) listeners.forEach(l -> l.onDeregistered(id)); }
        @Override public void updateInterests(UUID id, Set<String> caps) { listeners.forEach(l -> l.onInterestsUpdated(id, caps)); }
        @Override public List<SwarmDescriptor> activeSwarms(UUID caseId) { return swarms.values().stream().filter(d -> d.caseId().equals(caseId)).toList(); }
        @Override public List<SwarmDescriptor> allActive() { return List.copyOf(swarms.values()); }
        @Override public List<SwarmDescriptor> children(UUID parentId) { return swarms.values().stream().filter(d -> parentId.equals(d.parentSwarmId())).toList(); }
        @Override public @Nullable SwarmDescriptor get(UUID id) { return swarms.get(id); }
        @Override public void addListener(RegistryListener l) { listeners.add(l); }
    }
}
