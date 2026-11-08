package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TieredCoordinationIntegrationTest {

    TestMultiSwarmRegistry registry;
    MultiSwarmTopology topology;
    UUID caseId;
    UUID analysisSwarmId;
    UUID remediationSwarmId;

    @BeforeEach
    void setUp() {
        registry = new TestMultiSwarmRegistry();
        caseId = UUID.randomUUID();
        analysisSwarmId = UUID.randomUUID();
        remediationSwarmId = UUID.randomUUID();

        topology = MultiSwarmTopology.builder()
                .registry(registry)
                .weightedScoring(0.3, 0.6)
                .consistencyChecker(new DomainConsistencyChecker())
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
                s -> s instanceof MultiSwarmSignal.Finding && !s.sourceSwarmId().equals(remediationSwarmId),
                s -> { received.add(s); latch.countDown(); });

        topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                Set.of("analysis"), System.currentTimeMillis(),
                "system-health", "node-7:degraded", 0.9, Map.of("source", "metrics")));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Finding.class);
    }

    @Test
    void l1_scorer_scores_signal_relevance() {
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

    @Test
    void end_to_end_finding_populates_beliefs() throws InterruptedException {
        topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                Set.of("analysis"), System.currentTimeMillis(),
                "system-health", "node-7:degraded", 0.9, Map.of()));

        Thread.sleep(300);

        assertThat(topology.currentBeliefs().isEmpty()).isFalse();
        assertThat(topology.currentBeliefs().all().stream()
                .anyMatch(b -> b.value().domain().equals("system-health"))).isTrue();
    }
}
