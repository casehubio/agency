package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicConflictMediatorTest {

    HeuristicConflictMediator mediator;
    UUID swarmA = UUID.randomUUID();
    UUID swarmB = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mediator = new HeuristicConflictMediator();
    }

    SwarmDescriptor descriptor(UUID swarmId, Set<String> caps) {
        return new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                caps, Set.of("agent-1"), null);
    }

    ConflictContext contextFor(NotificationType type, MultiSwarmSignal trigger) {
        var notification = new CrossSwarmNotification(swarmA, swarmB, type, trigger, 0.8);
        var beliefs = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of("a:domain", new SwarmConclusion(swarmA, "domain", "node-7:healthy", 0.9), 90))
                .expand(Belief.of("b:domain", new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        var contracted = List.of(
                Belief.of("b:domain", new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        var progress = Map.of(
                swarmA, new SwarmProgress(0.9, 0.8, 0.9, Instant.now()),
                swarmB, new SwarmProgress(0.5, 0.6, 0.5, Instant.now()));
        var claims = Map.<UUID, Set<String>>of();
        var descriptors = Map.of(
                swarmA, descriptor(swarmA, Set.of("analysis")),
                swarmB, descriptor(swarmB, Set.of("remediation")));
        return new ConflictContext(notification, beliefs, contracted, progress, claims, descriptors);
    }

    @Nested
    class ConflictType {
        @Test
        void retains_both_conclusions_with_disputed_tag() {
            var finding = new MultiSwarmSignal.Finding(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), "domain", "node-7:healthy", 0.9, Map.of());
            var result = mediator.mediate(contextFor(NotificationType.CONFLICT, finding));

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("disputed_tag");
            assertThat(applied.notifications()).hasSize(2);
        }
    }

    @Nested
    class DependencyType {
        @Test
        void defers_to_higher_consensus_score() {
            var progress = new MultiSwarmSignal.Progress(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(),
                    new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
            var result = mediator.mediate(contextFor(NotificationType.DEPENDENCY, progress));

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("consensus_delegation");
            assertThat(applied.notifications()).hasSize(1);
        }
    }

    @Nested
    class ResourceContentionType {
        @Test
        void first_claimer_wins() {
            var claim = new MultiSwarmSignal.ResourceClaim(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), Set.of("agent-1"), Set.of("analysis"));
            var result = mediator.mediate(contextFor(NotificationType.RESOURCE_CONTENTION, claim));

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("first_claimer_wins");
        }
    }

    @Nested
    class HandoffType {
        @Test
        void accepts_high_confidence_handoff() {
            var handoff = new MultiSwarmSignal.Handoff(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), "task-domain", Map.of(), Set.of("remediation"));
            var notification = new CrossSwarmNotification(swarmA, swarmB,
                    NotificationType.HANDOFF, handoff, 0.8);
            var ctx = new ConflictContext(notification, new BeliefSet<>(), List.of(),
                    Map.of(), Map.of(), Map.of(
                            swarmA, descriptor(swarmA, Set.of("analysis")),
                            swarmB, descriptor(swarmB, Set.of("remediation"))));
            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("handoff_accepted");
        }

        @Test
        void escalates_after_max_retries() {
            var handoff = new MultiSwarmSignal.Handoff(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), "task-domain", Map.of(), Set.of("remediation"));
            var notification = new CrossSwarmNotification(swarmA, swarmB,
                    NotificationType.HANDOFF, handoff, 0.3);
            var ctx = new ConflictContext(notification, new BeliefSet<>(), List.of(),
                    Map.of(), Map.of(), Map.of(
                            swarmA, descriptor(swarmA, Set.of("analysis")),
                            swarmB, descriptor(swarmB, Set.of("remediation"))));

            mediator.mediate(ctx);
            mediator.mediate(ctx);
            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.Escalated.class);
        }

        @Test
        void retries_on_low_confidence_before_max() {
            var handoff = new MultiSwarmSignal.Handoff(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), "task-domain", Map.of(), Set.of("remediation"));
            var notification = new CrossSwarmNotification(swarmA, swarmB,
                    NotificationType.HANDOFF, handoff, 0.3);
            var ctx = new ConflictContext(notification, new BeliefSet<>(), List.of(),
                    Map.of(), Map.of(), Map.of(
                            swarmA, descriptor(swarmA, Set.of("analysis")),
                            swarmB, descriptor(swarmB, Set.of("remediation"))));

            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("handoff_retry");
        }
    }
}
