package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainConsistencyCheckerTest {

    DomainConsistencyChecker checker = new DomainConsistencyChecker();

    @Test
    void consistent_when_no_contradictions() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        var set = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(swarmA + ":system-health",
                        new SwarmConclusion(swarmA, "system-health", "node-7:healthy", 0.9), 90))
                .expand(Belief.of(swarmB + ":network",
                        new SwarmConclusion(swarmB, "network", "link-3:active", 0.8), 80));
        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void inconsistent_when_structural_negation_in_same_domain() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        var set = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(swarmA + ":system-health",
                        new SwarmConclusion(swarmA, "system-health", "node-7:healthy", 0.9), 90))
                .expand(Belief.of(swarmB + ":system-health",
                        new SwarmConclusion(swarmB, "system-health", "node-7:degraded", 0.7), 70));
        assertThat(checker.isConsistent(set)).isFalse();
    }

    @Test
    void consistent_when_same_entity_same_state() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        var set = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(swarmA + ":system-health",
                        new SwarmConclusion(swarmA, "system-health", "node-7:healthy", 0.9), 90))
                .expand(Belief.of(swarmB + ":system-health",
                        new SwarmConclusion(swarmB, "system-health", "node-7:healthy", 0.8), 80));
        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void consistent_when_different_entities_in_same_domain() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        var set = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(swarmA + ":system-health",
                        new SwarmConclusion(swarmA, "system-health", "node-7:healthy", 0.9), 90))
                .expand(Belief.of(swarmB + ":system-health",
                        new SwarmConclusion(swarmB, "system-health", "node-8:degraded", 0.8), 80));
        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void consistent_when_conclusion_has_no_colon() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();
        var set = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of(swarmA + ":system-health",
                        new SwarmConclusion(swarmA, "system-health", "all-clear", 0.9), 90))
                .expand(Belief.of(swarmB + ":system-health",
                        new SwarmConclusion(swarmB, "system-health", "alert", 0.7), 70));
        assertThat(checker.isConsistent(set)).isTrue();
    }
}
