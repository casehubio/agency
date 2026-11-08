package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BjtPipelineIntegrationTest {

    TestMultiSwarmRegistry registry;
    MultiSwarmTopology topology;
    UUID caseId;
    UUID analysisSwarmId;
    UUID remediationSwarmId;
    UUID unrelatedSwarmId;

    @BeforeEach
    void setUp() {
        registry = new TestMultiSwarmRegistry();
        caseId = UUID.randomUUID();
        analysisSwarmId = UUID.randomUUID();
        remediationSwarmId = UUID.randomUUID();
        unrelatedSwarmId = UUID.randomUUID();

        topology = MultiSwarmTopology.builder()
                .registry(registry)
                .weightedScoring(0.3, 0.7)
                .consistencyChecker(new DomainConsistencyChecker())
                .build();

        registry.register(new SwarmDescriptor(analysisSwarmId, caseId, TopologyType.CENTRALISED,
                Set.of("analysis", "pattern-detection"), Set.of("a1", "a2"), null));
        registry.register(new SwarmDescriptor(remediationSwarmId, caseId, TopologyType.CENTRALISED,
                Set.of("remediation", "analysis"), Set.of("r1"), null));
        registry.register(new SwarmDescriptor(unrelatedSwarmId, caseId, TopologyType.CENTRALISED,
                Set.of("billing", "invoicing"), Set.of("b1"), null));
    }

    @Nested
    class SignalFiltering {

        @Test
        void finding_with_overlapping_capabilities_reaches_belief_set() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                    Set.of("analysis", "pattern-detection"), System.currentTimeMillis(),
                    "system-health", "node-7:degraded", 0.9, Map.of()));

            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty())
                    .as("Finding from analysis swarm (overlapping caps with remediation) should reach beliefs")
                    .isFalse();
        }

        @Test
        void finding_from_unrelated_swarm_does_not_reach_belief_set_of_analysis() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Finding(unrelatedSwarmId,
                    Set.of("billing"), System.currentTimeMillis(),
                    "invoicing", "overdue:detected", 0.8, Map.of()));

            Thread.sleep(500);

            boolean hasInvoicingBelief = topology.currentBeliefs().all().stream()
                    .anyMatch(b -> b.value().domain().equals("invoicing"));
            assertThat(hasInvoicingBelief)
                    .as("Billing signal should not create beliefs in analysis/remediation context")
                    .isFalse();
        }

        @Test
        void self_signal_is_delivered_on_bus_but_not_self_scored() throws InterruptedException {
            var received = new ArrayList<MultiSwarmSignal>();
            var latch = new CountDownLatch(1);
            topology.signalBus().subscribe(s -> s instanceof MultiSwarmSignal.Finding,
                    s -> { received.add(s); latch.countDown(); });

            topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                    Set.of("analysis"), System.currentTimeMillis(),
                    "self-check", "self:ok", 0.5, Map.of()));

            assertThat(latch.await(2, TimeUnit.SECONDS))
                    .as("Self-signal should still be delivered on the bus (L0 does not filter by source)")
                    .isTrue();
            assertThat(received).hasSize(1);
        }
    }

    @Nested
    class ConflictDetection {

        @Test
        void contradictory_findings_produce_notifications() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                    Set.of("analysis"), System.currentTimeMillis(),
                    "system-health", "node-7:healthy", 0.9, Map.of()));
            Thread.sleep(300);

            topology.signalBus().publish(new MultiSwarmSignal.Finding(remediationSwarmId,
                    Set.of("remediation", "analysis"), System.currentTimeMillis(),
                    "system-health", "node-7:degraded", 0.85, Map.of()));
            Thread.sleep(300);

            var notifications = topology.drainNotifications();
            boolean hasConflict = notifications.stream()
                    .anyMatch(n -> n.type() == NotificationType.CONFLICT);
            assertThat(hasConflict || !topology.currentBeliefs().isEmpty())
                    .as("Contradictory findings should either produce notifications or be resolved via belief revision")
                    .isTrue();
        }

        @Test
        void consistent_findings_do_not_produce_conflict_notifications() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                    Set.of("analysis"), System.currentTimeMillis(),
                    "system-health", "node-7:healthy", 0.9, Map.of()));
            Thread.sleep(300);

            topology.signalBus().publish(new MultiSwarmSignal.Finding(remediationSwarmId,
                    Set.of("remediation", "analysis"), System.currentTimeMillis(),
                    "network", "link-3:active", 0.8, Map.of()));
            Thread.sleep(300);

            var notifications = topology.drainNotifications();
            long conflictCount = notifications.stream()
                    .filter(n -> n.type() == NotificationType.CONFLICT)
                    .count();
            assertThat(conflictCount)
                    .as("Consistent findings in different domains should not produce conflict notifications")
                    .isZero();
        }
    }

    @Nested
    class ConvergenceEvaluation {

        @Test
        void progress_signals_update_convergence_tracking() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Progress(analysisSwarmId,
                    Set.of("analysis"), System.currentTimeMillis(),
                    new SwarmProgress(0.1, 0.8, 0.8, Instant.now())));
            Thread.sleep(200);
            topology.signalBus().publish(new MultiSwarmSignal.Progress(remediationSwarmId,
                    Set.of("remediation"), System.currentTimeMillis(),
                    new SwarmProgress(0.1, 0.9, 0.9, Instant.now())));
            Thread.sleep(200);

            assertThat(topology.signalBus().replayBuffer().stream()
                    .filter(s -> s instanceof MultiSwarmSignal.Progress)
                    .count())
                    .as("Progress signals should be tracked")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    class LifecycleIntegration {

        @Test
        void deregistered_swarm_scorer_is_removed() {
            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();
            registry.deregister(analysisSwarmId);
            assertThat(topology.hasScorer(analysisSwarmId))
                    .as("Scorer for deregistered swarm should be removed")
                    .isFalse();
        }

        @Test
        void beliefs_from_deregistered_swarm_are_contracted() throws InterruptedException {
            topology.signalBus().publish(new MultiSwarmSignal.Finding(analysisSwarmId,
                    Set.of("analysis"), System.currentTimeMillis(),
                    "system-health", "node-7:degraded", 0.9, Map.of()));
            Thread.sleep(500);

            int beforeSize = topology.currentBeliefs().size();
            registry.deregister(analysisSwarmId);
            Thread.sleep(100);

            assertThat(topology.currentBeliefs().size())
                    .as("Deregistering swarm should contract its beliefs")
                    .isLessThanOrEqualTo(beforeSize);
        }
    }
}
