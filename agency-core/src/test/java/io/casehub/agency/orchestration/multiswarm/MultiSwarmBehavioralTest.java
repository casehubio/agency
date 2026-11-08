package io.casehub.agency.orchestration.multiswarm;

import io.casehub.api.model.coordination.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.CrossSwarmConvergenceEvaluator;
import io.casehub.api.spi.stigmergy.ConvergenceSignal;
import io.casehub.api.spi.stigmergy.ConvergenceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MultiSwarmBehavioralTest {

    static final Set<String> ANALYSIS_CAPS = Set.of("analysis", "pattern-detection");
    static final Set<String> REMEDIATION_CAPS = Set.of("remediation", "analysis");
    static final Set<String> BILLING_CAPS = Set.of("billing", "invoicing");

    TestMultiSwarmRegistry registry;
    UUID caseId;
    UUID analysisSwarmId;
    UUID remediationSwarmId;

    @BeforeEach
    void initCommon() {
        registry = new TestMultiSwarmRegistry();
        caseId = UUID.randomUUID();
        analysisSwarmId = UUID.randomUUID();
        remediationSwarmId = UUID.randomUUID();
    }

    SwarmDescriptor descriptor(UUID swarmId, Set<String> capabilities) {
        return new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                capabilities, Set.of("agent-1"), null);
    }

    MultiSwarmSignal.Finding finding(UUID sourceSwarmId, Set<String> domainTags,
            String domain, String conclusion, double confidence) {
        return new MultiSwarmSignal.Finding(sourceSwarmId, domainTags,
                System.currentTimeMillis(), domain, conclusion, confidence, Map.of());
    }

    MultiSwarmSignal.Progress progress(UUID sourceSwarmId, Set<String> domainTags,
            double consensus, double stability) {
        return new MultiSwarmSignal.Progress(sourceSwarmId, domainTags,
                System.currentTimeMillis(),
                new SwarmProgress(consensus, stability, consensus, Instant.now()));
    }

    static class RecordingConvergenceEvaluator implements CrossSwarmConvergenceEvaluator {
        private final List<Map<UUID, SwarmProgress>> calls =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public ConvergenceSignal evaluate(Map<UUID, SwarmProgress> progressBySwarm) {
            calls.add(Map.copyOf(progressBySwarm));
            return new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.0, "Recording");
        }

        List<Map<UUID, SwarmProgress>> calls() {
            return List.copyOf(calls);
        }
    }

    @Nested
    class ConsumerSetupFlow {

        MultiSwarmTopology topology;

        @BeforeEach
        void setUp() {
            topology = MultiSwarmTopology.builder()
                    .registry(registry)
                    .weightedScoring(0.3, 0.6)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .build();
        }

        @AfterEach
        void tearDown() {
            topology.close();
        }

        @Test
        void builder_creates_topology_with_weighted_scoring() {
            assertThat(topology.describeTopology(caseId).topologyType())
                    .isEqualTo(TopologyType.MULTI_SWARM);
        }

        @Test
        void register_swarms_enables_signal_processing() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            var latch = new CountDownLatch(1);
            topology.signalBus().subscribe(s -> s instanceof MultiSwarmSignal.Finding,
                    s -> latch.countDown());

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:degraded", 0.9));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(topology.currentBeliefs().isEmpty()).isFalse();
        }

        @Test
        void topology_is_autocloseable() {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            topology.close();
        }
    }

    @Nested
    class SignalLifecycle {

        MultiSwarmTopology topology;

        @BeforeEach
        void setUp() {
            topology = MultiSwarmTopology.builder()
                    .registry(registry)
                    .weightedScoring(0.3, 0.6)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .build();
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));
        }

        @AfterEach
        void tearDown() {
            topology.close();
        }

        @Test
        void finding_from_overlapping_swarm_reaches_beliefs() throws InterruptedException {
            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:degraded", 0.9));

            Thread.sleep(500);

            assertThat(topology.currentBeliefs().all().stream()
                    .anyMatch(b -> b.value().domain().equals("system-health"))).isTrue();
        }

        @Test
        void contradictory_findings_resolved_by_belief_revision() throws InterruptedException {
            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:healthy", 0.9));
            Thread.sleep(300);

            assertThat(topology.currentBeliefs().all().stream()
                    .anyMatch(b -> b.value().conclusion().equals("node-7:healthy"))).isTrue();

            topology.signalBus().publish(finding(remediationSwarmId, REMEDIATION_CAPS,
                    "system-health", "node-7:degraded", 0.8));
            Thread.sleep(300);

            var beliefs = topology.currentBeliefs();
            assertThat(beliefs.all().stream()
                    .anyMatch(b -> b.value().conclusion().equals("node-7:degraded")))
                    .as("Newer belief wins in AGM revision").isTrue();
            assertThat(beliefs.all().stream()
                    .noneMatch(b -> b.value().conclusion().equals("node-7:healthy")))
                    .as("Contradicted belief contracted by AGM revision").isTrue();
        }

        @Test
        void irrelevant_finding_does_not_reach_beliefs() throws InterruptedException {
            var billingSwarmId = UUID.randomUUID();
            registry.register(descriptor(billingSwarmId, BILLING_CAPS));

            topology.signalBus().publish(finding(billingSwarmId, BILLING_CAPS,
                    "invoice-status", "overdue", 0.9));

            Thread.sleep(500);

            assertThat(topology.currentBeliefs().all().stream()
                    .noneMatch(b -> b.value().domain().equals("invoice-status"))).isTrue();
        }

        @Test
        void self_signals_not_scored_against_source() throws InterruptedException {
            var soloRegistry = new TestMultiSwarmRegistry();
            var soloSwarmId = UUID.randomUUID();
            var soloTopology = MultiSwarmTopology.builder()
                    .registry(soloRegistry)
                    .weightedScoring(0.3, 0.6)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .build();
            soloRegistry.register(descriptor(soloSwarmId, ANALYSIS_CAPS));

            soloTopology.signalBus().publish(finding(soloSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(soloTopology.currentBeliefs().isEmpty()).isTrue();
            soloTopology.close();
        }
    }

    @Nested
    class ConvergenceLifecycle {

        RecordingConvergenceEvaluator evaluator;
        MultiSwarmTopology topology;
        UUID thirdSwarmId;

        @BeforeEach
        void setUp() {
            evaluator = new RecordingConvergenceEvaluator();
            thirdSwarmId = UUID.randomUUID();
            topology = MultiSwarmTopology.builder()
                    .registry(registry)
                    .weightedScoring(0.3, 0.6)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .convergenceEvaluator(evaluator)
                    .build();
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));
            registry.register(descriptor(thirdSwarmId, Set.of("monitoring", "analysis")));
        }

        @AfterEach
        void tearDown() {
            topology.close();
        }

        @Test
        void convergence_evaluator_receives_progress_from_all_swarms()
                throws InterruptedException {
            topology.signalBus().publish(progress(analysisSwarmId, ANALYSIS_CAPS, 0.3, 0.3));
            topology.signalBus().publish(progress(remediationSwarmId, REMEDIATION_CAPS, 0.5, 0.5));
            topology.signalBus().publish(progress(thirdSwarmId,
                    Set.of("monitoring", "analysis"), 0.7, 0.7));

            Thread.sleep(500);

            assertThat(evaluator.calls()).anySatisfy(progressMap ->
                    assertThat(progressMap).containsKeys(
                            analysisSwarmId, remediationSwarmId, thirdSwarmId));
        }

        @Test
        void convergence_evaluator_not_called_with_single_swarm()
                throws InterruptedException {
            var soloRegistry = new TestMultiSwarmRegistry();
            var soloEvaluator = new RecordingConvergenceEvaluator();
            var soloTopology = MultiSwarmTopology.builder()
                    .registry(soloRegistry)
                    .weightedScoring(0.3, 0.6)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .convergenceEvaluator(soloEvaluator)
                    .build();
            var soloSwarmId = UUID.randomUUID();
            soloRegistry.register(descriptor(soloSwarmId, ANALYSIS_CAPS));

            soloTopology.signalBus().publish(progress(soloSwarmId, ANALYSIS_CAPS, 0.5, 0.5));
            Thread.sleep(500);

            assertThat(soloEvaluator.calls()).isEmpty();
            soloTopology.close();
        }

        @Test
        void progress_signals_available_via_cold_start_replay() throws InterruptedException {
            topology.signalBus().publish(progress(analysisSwarmId, ANALYSIS_CAPS, 0.3, 0.3));
            topology.signalBus().publish(progress(remediationSwarmId, REMEDIATION_CAPS, 0.5, 0.5));
            topology.signalBus().publish(progress(thirdSwarmId,
                    Set.of("monitoring", "analysis"), 0.7, 0.7));
            Thread.sleep(300);

            var replay = topology.signalBus().replayBuffer();
            assertThat(replay.stream()
                    .filter(s -> s instanceof MultiSwarmSignal.Progress)
                    .count()).isEqualTo(3);
        }
    }

    @Nested
    class DynamicSwarmMembership {

        MultiSwarmTopology topology;

        @BeforeEach
        void setUp() {
            topology = MultiSwarmTopology.builder()
                                         .registry(registry)
                                         .weightedScoring(0.3, 0.6)
                                         .consistencyChecker(new DomainConsistencyChecker())
                                         .build();
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));
        }

        @AfterEach
        void tearDown() {
            topology.close();
        }

        @Test
        void deregistration_cleans_beliefs_for_that_swarm() throws InterruptedException {
            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);
            assertThat(topology.currentBeliefs().isEmpty()).isFalse();

            registry.deregister(analysisSwarmId);

            assertThat(topology.currentBeliefs().all().stream()
                               .noneMatch(b -> b.value().swarmId().equals(analysisSwarmId))).isTrue();
        }

        @Test
        void deregistered_swarm_no_longer_scores_signals() throws InterruptedException {
            registry.deregister(remediationSwarmId);

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty())
                    .as("No remaining scorer to evaluate analysis signal (self-scoring skipped)")
                    .isTrue();
        }

        @Test
        void re_registration_restores_scoring() throws InterruptedException {
            registry.deregister(analysisSwarmId);
            assertThat(topology.hasScorer(analysisSwarmId)).isFalse();

            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();

            topology.signalBus().publish(finding(remediationSwarmId, REMEDIATION_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty()).isFalse();
        }

        @Test
        void deregistration_during_active_processing() throws InterruptedException {
            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            registry.deregister(remediationSwarmId);

            Thread.sleep(300);

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "network-status", "link-3:down", 0.8));
            Thread.sleep(300);

            assertThat(topology.signalBus().replayBuffer()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    class StrategySwitching {

        @Test
        void weighted_scoring_produces_beliefs() throws InterruptedException {
            var topology = MultiSwarmTopology.builder()
                                             .registry(registry)
                                             .weightedScoring(0.3, 0.6)
                                             .consistencyChecker(new DomainConsistencyChecker())
                                             .build();
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            topology.close();
        }

        @Test
        void bjt_scoring_produces_beliefs() throws InterruptedException {
            var bjtRegistry = new TestMultiSwarmRegistry();
            var topology = MultiSwarmTopology.builder()
                                             .registry(bjtRegistry)
                                             .bjtScoring(0.3, 0.6)
                                             .consistencyChecker(new DomainConsistencyChecker())
                                             .build();
            var caps = Set.of("analysis", "monitoring");
            bjtRegistry.register(descriptor(analysisSwarmId, caps));
            bjtRegistry.register(descriptor(remediationSwarmId, caps));

            topology.signalBus().publish(finding(analysisSwarmId, caps,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            topology.close();
        }

        @Test
        void both_strategies_agree_on_clear_signals() throws InterruptedException {
            var caps = Set.of("analysis", "monitoring");

            var weightedRegistry = new TestMultiSwarmRegistry();
            var weightedTopology = MultiSwarmTopology.builder()
                                                     .registry(weightedRegistry)
                                                     .weightedScoring(0.3, 0.6)
                                                     .consistencyChecker(new DomainConsistencyChecker())
                                                     .build();
            weightedRegistry.register(descriptor(analysisSwarmId, caps));
            weightedRegistry.register(descriptor(remediationSwarmId, caps));

            var bjtRegistry = new TestMultiSwarmRegistry();
            var bjtTopology = MultiSwarmTopology.builder()
                                                .registry(bjtRegistry)
                                                .bjtScoring(0.3, 0.6)
                                                .consistencyChecker(new DomainConsistencyChecker())
                                                .build();
            bjtRegistry.register(descriptor(analysisSwarmId, caps));
            bjtRegistry.register(descriptor(remediationSwarmId, caps));

            var signal = finding(analysisSwarmId, caps,
                                 "system-health", "node-7:degraded", 0.95);
            weightedTopology.signalBus().publish(signal);
            bjtTopology.signalBus().publish(signal);
            Thread.sleep(500);

            assertThat(weightedTopology.currentBeliefs().isEmpty())
                    .as("Weighted strategy: clear signal should produce beliefs").isFalse();
            assertThat(bjtTopology.currentBeliefs().isEmpty())
                    .as("BJT strategy: clear signal should produce beliefs").isFalse();

            weightedTopology.close();
            bjtTopology.close();
        }

        @Test
        void default_scoring_works_without_explicit_strategy() throws InterruptedException {
            var defaultRegistry = new TestMultiSwarmRegistry();
            var topology = MultiSwarmTopology.builder()
                                             .registry(defaultRegistry)
                                             .consistencyChecker(new DomainConsistencyChecker())
                                             .build();
            defaultRegistry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            defaultRegistry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
            Thread.sleep(500);

            assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            topology.close();
        }
    }

    @Nested
    class ErrorPaths {

        MultiSwarmTopology topology;

        @BeforeEach
        void setUp() {
            topology = MultiSwarmTopology.builder()
                                         .registry(registry)
                                         .weightedScoring(0.3, 0.6)
                                         .consistencyChecker(new DomainConsistencyChecker())
                                         .build();
        }

        @AfterEach
        void tearDown() {
            topology.close();
        }

        @Test
        void unknown_swarm_event_silently_ignored() {
            topology.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(
                    UUID.randomUUID(),
                    new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
            assertThat(topology.signalBus().replayBuffer()).isEmpty();
        }

        @Test
        void finding_from_unknown_source_does_not_crash() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));

            topology.signalBus().publish(finding(UUID.randomUUID(),
                                                 Set.of("unknown"), "unknown-domain", "something", 0.5));
            Thread.sleep(300);
        }

        @Test
        void builder_rejects_null_registry() {
            assertThatNullPointerException().isThrownBy(() ->
                                                                MultiSwarmTopology.builder()
                                                                                  .weightedScoring(0.3, 0.6)
                                                                                  .consistencyChecker(new DomainConsistencyChecker())
                                                                                  .build());
        }

        @Test
        void builder_rejects_null_consistency_checker() {
            assertThatNullPointerException().isThrownBy(() ->
                                                                MultiSwarmTopology.builder()
                                                                                  .registry(registry)
                                                                                  .weightedScoring(0.3, 0.6)
                                                                                  .build());
        }

        @Test
        void drain_notifications_returns_empty_when_beliefs_resolved() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            topology.signalBus().publish(finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:healthy", 0.9));
            Thread.sleep(300);
            topology.signalBus().publish(finding(remediationSwarmId, REMEDIATION_CAPS,
                                                 "system-health", "node-7:degraded", 0.8));
            Thread.sleep(300);

            assertThat(topology.drainNotifications())
                    .as("AGM revision resolves contradictions — no CONFLICT notifications produced")
                    .isEmpty();
            assertThat(topology.drainNotifications())
                    .as("Subsequent drain also empty").isEmpty();
        }

        @Test
        void close_then_queries_do_not_throw() {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            topology.close();

            assertThat(topology.currentBeliefs()).isNotNull();
            assertThat(topology.drainNotifications()).isEmpty();
            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();
        }
    }


}
