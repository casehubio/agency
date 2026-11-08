package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyConsistencyCheckerTest {

    static final String VOCAB_URI = "urn:casehub:health-status";

    @Test
    void consistent_when_empty() {
        var checker = checker(Map.of(), Set.of());
        assertThat(checker.isConsistent(new BeliefSet<>())).isTrue();
    }

    @Test
    void consistent_when_synonyms_resolve_to_same_term() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(
                Map.of("healthy", "healthy", "operational", "healthy"),
                Set.of());

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "node-7:healthy", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "node-7:operational", 0.8), 80));

        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void consistent_when_one_subsumes_other() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(
                Map.of("degraded", "degraded", "partially-degraded", "partially-degraded"),
                Set.of("degraded->partially-degraded"));

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "node-7:degraded", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "node-7:partially-degraded", 0.8), 80));

        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void inconsistent_when_different_terms_no_subsumption() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(
                Map.of("healthy", "healthy", "degraded", "degraded"),
                Set.of());

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "node-7:healthy", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "node-7:degraded", 0.8), 80));

        assertThat(checker.isConsistent(set)).isFalse();
    }

    @Test
    void falls_back_to_string_inequality_when_unresolvable() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(Map.of(), Set.of());

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "node-7:healthy", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "node-7:degraded", 0.8), 80));

        assertThat(checker.isConsistent(set)).isFalse();
    }

    @Test
    void consistent_when_unresolvable_but_same_string() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(Map.of(), Set.of());

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "node-7:healthy", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "node-7:healthy", 0.8), 80));

        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void consistent_when_no_colon_in_conclusion() {
        var checker = checker(Map.of(), Set.of());
        var swarmA  = UUID.randomUUID();
        var swarmB  = UUID.randomUUID();

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":health",
                                            new SwarmConclusion(swarmA, "health", "all-clear", 0.9), 90))
                          .expand(Belief.of(swarmB + ":health",
                                            new SwarmConclusion(swarmB, "health", "alert", 0.7), 70));

        assertThat(checker.isConsistent(set)).isTrue();
    }

    @Test
    void consistent_across_different_domains() {
        var swarmA = UUID.randomUUID();
        var swarmB = UUID.randomUUID();

        var checker = checker(
                Map.of("healthy", "healthy", "degraded", "degraded"),
                Set.of());

        var set = new BeliefSet<SwarmConclusion>()
                          .expand(Belief.of(swarmA + ":system-health",
                                            new SwarmConclusion(swarmA, "system-health", "node-7:healthy", 0.9), 90))
                          .expand(Belief.of(swarmB + ":network",
                                            new SwarmConclusion(swarmB, "network", "node-7:degraded", 0.8), 80));

        assertThat(checker.isConsistent(set)).isTrue();
    }

    /**
     * Creates a checker with a stub registry.
     *
     * @param termMap      maps raw value to its canonical term value (synonyms share the same canonical)
     * @param subsumptions "general->specific" strings encoding subsumption relationships
     */
    private VocabularyConsistencyChecker checker(Map<String, String> termMap, Set<String> subsumptions) {
        return new VocabularyConsistencyChecker(
                new StubVocabularyRegistry(VOCAB_URI, termMap, subsumptions), VOCAB_URI);
    }
}
