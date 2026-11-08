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
import java.util.function.Consumer;

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

    MultiSwarmSignal.ResourceClaim resourceClaim(UUID sourceSwarmId, Set<String> domainTags,
            Set<String> agentIds, Set<String> capabilities) {
        return new MultiSwarmSignal.ResourceClaim(sourceSwarmId, domainTags,
                System.currentTimeMillis(), agentIds, capabilities);
    }

    void publishAndAwait(MultiSwarmTopology topology, MultiSwarmSignal signal) throws InterruptedException {
        var latch = new CountDownLatch(1);
        Consumer<MultiSwarmSignal> callback = s -> latch.countDown();
        topology.signalBus().subscribe(s -> true, callback);
        topology.signalBus().publish(signal);
        assertThat(latch.await(2, TimeUnit.SECONDS)).as("Signal delivery timed out").isTrue();
        topology.signalBus().unsubscribe(callback);
        Thread.sleep(500);
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
            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:degraded", 0.9));

            assertThat(topology.currentBeliefs().all().stream()
                    .anyMatch(b -> b.value().domain().equals("system-health"))).isTrue();
        }

        @Test
        void contradictory_findings_resolved_by_belief_revision() throws InterruptedException {
            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                    "system-health", "node-7:healthy", 0.9));

            assertThat(topology.currentBeliefs().all().stream()
                    .anyMatch(b -> b.value().conclusion().equals("node-7:healthy"))).isTrue();

            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                    "system-health", "node-7:degraded", 0.8));

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
            try {
                soloRegistry.register(descriptor(soloSwarmId, ANALYSIS_CAPS));

                soloTopology.signalBus().publish(finding(soloSwarmId, ANALYSIS_CAPS,
                        "system-health", "node-7:degraded", 0.9));
                Thread.sleep(500);

                assertThat(soloTopology.currentBeliefs().isEmpty()).isTrue();
            } finally {
                soloTopology.close();
            }
        }

        @Test
        void resource_contention_feeds_escalation_gate() throws InterruptedException {
            var lowThresholdRegistry = new TestMultiSwarmRegistry();
            var lowThresholdTopology = MultiSwarmTopology.builder()
                    .registry(lowThresholdRegistry)
                    .weightedScoring(0.3, 0.2)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .build();
            try {
                lowThresholdRegistry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
                lowThresholdRegistry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

                publishAndAwait(lowThresholdTopology, resourceClaim(analysisSwarmId, ANALYSIS_CAPS,
                        Set.of("agent-1", "agent-2", "agent-3"), ANALYSIS_CAPS));
                publishAndAwait(lowThresholdTopology, resourceClaim(remediationSwarmId, REMEDIATION_CAPS,
                        Set.of("agent-2", "agent-3", "agent-4"), REMEDIATION_CAPS));

                publishAndAwait(lowThresholdTopology, progress(analysisSwarmId, ANALYSIS_CAPS, 0.5, 0.5));

                assertThat(lowThresholdTopology.drainNotifications())
                        .as("Overlapping resource claims should feed escalation gate")
                        .anyMatch(n -> n.type() == NotificationType.DEPENDENCY);
            } finally {
                lowThresholdTopology.close();
            }
        }

        @Test
        void non_overlapping_claims_produce_no_contention() throws InterruptedException {
            var lowThresholdRegistry = new TestMultiSwarmRegistry();
            var caps = Set.of("analysis", "monitoring");
            var lowThresholdTopology = MultiSwarmTopology.builder()
                    .registry(lowThresholdRegistry)
                    .weightedScoring(0.01, 0.4)
                    .consistencyChecker(new DomainConsistencyChecker())
                    .build();
            try {
                lowThresholdRegistry.register(descriptor(analysisSwarmId, caps));
                lowThresholdRegistry.register(descriptor(remediationSwarmId, caps));

                publishAndAwait(lowThresholdTopology, resourceClaim(analysisSwarmId, caps,
                        Set.of("agent-1", "agent-2"), caps));
                publishAndAwait(lowThresholdTopology, resourceClaim(remediationSwarmId, caps,
                        Set.of("agent-3", "agent-4"), caps));

                publishAndAwait(lowThresholdTopology, progress(analysisSwarmId, caps, 0.5, 0.5));

                assertThat(lowThresholdTopology.drainNotifications())
                        .as("Non-overlapping claims should not trigger escalation")
                        .noneMatch(n -> n.type() == NotificationType.DEPENDENCY);
            } finally {
                lowThresholdTopology.close();
            }
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
            publishAndAwait(topology, progress(analysisSwarmId, ANALYSIS_CAPS, 0.3, 0.3));
            publishAndAwait(topology, progress(remediationSwarmId, REMEDIATION_CAPS, 0.5, 0.5));
            publishAndAwait(topology, progress(thirdSwarmId,
                    Set.of("monitoring", "analysis"), 0.7, 0.7));

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
            try {
                var soloSwarmId = UUID.randomUUID();
                soloRegistry.register(descriptor(soloSwarmId, ANALYSIS_CAPS));

                soloTopology.signalBus().publish(progress(soloSwarmId, ANALYSIS_CAPS, 0.5, 0.5));
                Thread.sleep(500);

                assertThat(soloEvaluator.calls()).isEmpty();
            } finally {
                soloTopology.close();
            }
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
            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));
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
        void interests_update_resets_scorer_and_gate() {
            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();
            var scorerBefore = topology.getScorer(analysisSwarmId);

            registry.updateInterests(analysisSwarmId, Set.of("billing", "invoicing"));

            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();
            assertThat(topology.getScorer(analysisSwarmId))
                    .as("Scorer should be replaced after capability change")
                    .isNotSameAs(scorerBefore);
        }

        @Test
        void re_registration_restores_scoring() throws InterruptedException {
            registry.deregister(analysisSwarmId);
            assertThat(topology.hasScorer(analysisSwarmId)).isFalse();

            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            assertThat(topology.hasScorer(analysisSwarmId)).isTrue();

            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                                                 "system-health", "node-7:degraded", 0.9));

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
            try {
                registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
                registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

                publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                     "system-health", "node-7:degraded", 0.9));

                assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            } finally {
                topology.close();
            }
        }

        @Test
        void bjt_scoring_produces_beliefs() throws InterruptedException {
            var bjtRegistry = new TestMultiSwarmRegistry();
            var topology = MultiSwarmTopology.builder()
                                             .registry(bjtRegistry)
                                             .bjtScoring(0.3, 0.6)
                                             .consistencyChecker(new DomainConsistencyChecker())
                                             .build();
            try {
                var caps = Set.of("analysis", "monitoring");
                bjtRegistry.register(descriptor(analysisSwarmId, caps));
                bjtRegistry.register(descriptor(remediationSwarmId, caps));

                topology.signalBus().publish(finding(analysisSwarmId, caps,
                                                     "system-health", "node-7:degraded", 0.9));
                Thread.sleep(500);

                assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            } finally {
                topology.close();
            }
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
            var bjtRegistry = new TestMultiSwarmRegistry();
            var bjtTopology = MultiSwarmTopology.builder()
                                                .registry(bjtRegistry)
                                                .bjtScoring(0.3, 0.6)
                                                .consistencyChecker(new DomainConsistencyChecker())
                                                .build();
            try {
                weightedRegistry.register(descriptor(analysisSwarmId, caps));
                weightedRegistry.register(descriptor(remediationSwarmId, caps));
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
            } finally {
                weightedTopology.close();
                bjtTopology.close();
            }
        }

        @Test
        void default_scoring_works_without_explicit_strategy() throws InterruptedException {
            var defaultRegistry = new TestMultiSwarmRegistry();
            var topology = MultiSwarmTopology.builder()
                                             .registry(defaultRegistry)
                                             .consistencyChecker(new DomainConsistencyChecker())
                                             .build();
            try {
                defaultRegistry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
                defaultRegistry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

                publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                     "system-health", "node-7:degraded", 0.9));

                assertThat(topology.currentBeliefs().isEmpty()).isFalse();
            } finally {
                topology.close();
            }
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

            publishAndAwait(topology, finding(UUID.randomUUID(),
                                                 Set.of("unknown"), "unknown-domain", "something", 0.5));

            assertThat(topology.currentBeliefs().isEmpty())
                    .as("Unknown source finding should not produce beliefs").isTrue();
            assertThat(topology.signalBus().replayBuffer()).hasSize(1);
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
        void contradictory_findings_produce_conflict_notification() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:healthy", 0.9));
            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                                                 "system-health", "node-7:degraded", 0.8));

            var notifications = topology.drainNotifications();
            assertThat(notifications)
                    .as("Contradictory findings should produce CONFLICT notification")
                    .isNotEmpty();
            assertThat(notifications).allSatisfy(n ->
                    assertThat(n.type()).isEqualTo(NotificationType.CONFLICT));

            assertThat(topology.drainNotifications())
                    .as("Drain is one-shot — subsequent drain returns empty").isEmpty();
        }

        @Test
        void consistent_findings_produce_no_conflict_notification() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                 "system-health", "node-7:healthy", 0.9));
            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                                                 "system-health", "node-7:healthy", 0.8));

            assertThat(topology.drainNotifications())
                    .as("Consistent findings should not produce CONFLICT notifications")
                    .isEmpty();
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


    @Nested
    class CompletionLifecycle {

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
        void completion_signal_tracks_domain() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk-assessment", "completed successfully"));

            assertThat(topology.completedDomains(analysisSwarmId))
                    .containsExactly("risk-assessment");
            assertThat(topology.completedDomains(remediationSwarmId)).isEmpty();
        }

        @Test
        void multiple_completions_accumulate_domains() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk-assessment", "done"));
            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "compliance-check", "done"));

            assertThat(topology.completedDomains(analysisSwarmId))
                    .containsExactlyInAnyOrder("risk-assessment", "compliance-check");
        }

        @Test
        void all_complete_false_with_partial_completions() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk-assessment", "done"));

            assertThat(topology.isAllComplete()).isFalse();
        }

        @Test
        void all_complete_true_when_all_swarms_complete() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk-assessment", "done"));
            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    remediationSwarmId, REMEDIATION_CAPS, System.currentTimeMillis(),
                    "health-check", "done"));

            assertThat(topology.isAllComplete()).isTrue();
        }

        @Test
        void deregistration_removes_completion_state() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk-assessment", "done"));

            topology.onDeregistered(analysisSwarmId);

            assertThat(topology.completedDomains(analysisSwarmId)).isEmpty();
        }

        @Test
        void completed_domains_empty_for_unknown_swarm() {
            assertThat(topology.completedDomains(UUID.randomUUID())).isEmpty();
        }

        @Test
        void belief_eviction_when_enabled() throws InterruptedException {
            var evictingTopology = MultiSwarmTopology.builder()
                                                     .registry(registry)
                                                     .weightedScoring(0.3, 0.6)
                                                     .consistencyChecker(new DomainConsistencyChecker())
                                                     .evictBeliefsOnCompletion(true)
                                                     .build();
            try {
                registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
                registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

                publishAndAwait(evictingTopology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                                          "risk", "node-7:healthy", 0.9));
                assertThat(evictingTopology.currentBeliefs().all()).isNotEmpty();

                publishAndAwait(evictingTopology, new MultiSwarmSignal.Completion(
                        analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                        "risk", "done"));

                assertThat(evictingTopology.currentBeliefs().all()).isEmpty();
            } finally {
                evictingTopology.close();
            }
        }

        @Test
        void belief_retained_when_eviction_disabled() throws InterruptedException {
            registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
            registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                              "risk", "node-7:healthy", 0.9));

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "risk", "done"));

            assertThat(topology.currentBeliefs().all()).isNotEmpty();
        }

    }

    @Nested
    class CompletionEscalation {

        MultiSwarmTopology topology;

        @BeforeEach
        void setUp() {
            topology = MultiSwarmTopology.builder()
                                         .registry(registry)
                                         .weightedScoring(0.3, 0.1)
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
        void progress_from_completed_swarm_does_not_trigger_escalation() throws InterruptedException {
            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                              "system-health", "node-7:healthy", 0.9));
            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                                              "system-health", "node-7:degraded", 0.9));
            topology.drainNotifications();

            publishAndAwait(topology, new MultiSwarmSignal.Completion(
                    analysisSwarmId, ANALYSIS_CAPS, System.currentTimeMillis(),
                    "system-health", "done"));

            publishAndAwait(topology, progress(analysisSwarmId, ANALYSIS_CAPS, 0.5, 0.5));

            var notifications = topology.drainNotifications();
            assertThat(notifications)
                    .noneMatch(n -> n.type() == NotificationType.DEPENDENCY);
        }

        @Test
        void progress_from_non_completed_swarm_can_trigger_escalation() throws InterruptedException {
            // Create conflict notifications (don't drain — leave them as gate input)
            publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                                              "system-health", "node-7:healthy", 0.9));
            publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                                              "system-health", "node-7:degraded", 0.9));

            // Add resource contention
            publishAndAwait(topology, resourceClaim(analysisSwarmId, ANALYSIS_CAPS,
                                                    Set.of("agent-1", "agent-2"), Set.of()));
            publishAndAwait(topology, resourceClaim(remediationSwarmId, REMEDIATION_CAPS,
                                                    Set.of("agent-1", "agent-2"), Set.of()));

            // Drain existing notifications to isolate the DEPENDENCY check
            topology.drainNotifications();

            // Progress from non-completed swarm with high gate inputs
            publishAndAwait(topology, progress(remediationSwarmId, REMEDIATION_CAPS, 0.5, 0.5));

            var notifications = topology.drainNotifications();
            assertThat(notifications)
                    .as("Non-completed swarm progress should evaluate escalation gate")
                    .anyMatch(n -> n.type() == NotificationType.DEPENDENCY);
        }
    }

}
