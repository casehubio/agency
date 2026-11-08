package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import io.casehub.eidos.api.VocabularyRegistry;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detects contradictions between swarm conclusions using vocabulary-aware
 * equivalence from eidos {@link VocabularyRegistry}.
 *
 * <p>Extends the {@code entity:state} convention from {@link DomainConsistencyChecker}
 * with two additional compatibility checks:
 * <ul>
 *   <li><b>Synonyms:</b> two states that resolve to the same {@code VocabularyTerm}
 *       are compatible (e.g. "healthy" and "operational" as aliases).</li>
 *   <li><b>Hierarchy:</b> if one state subsumes the other in the vocabulary's
 *       term hierarchy, they are compatible (e.g. "degraded" subsumes
 *       "partially-degraded").</li>
 * </ul>
 *
 * <p>When a state value cannot be resolved in the vocabulary, falls back to
 * string equality — same behavior as {@link DomainConsistencyChecker}.
 *
 * @see DomainConsistencyChecker
 * @see VocabularyRegistry
 */
public class VocabularyConsistencyChecker implements ConsistencyChecker<SwarmConclusion> {

    private final VocabularyRegistry registry;
    private final String vocabUri;

    public VocabularyConsistencyChecker(VocabularyRegistry registry, String vocabUri) {
        this.registry = Objects.requireNonNull(registry);
        this.vocabUri = Objects.requireNonNull(vocabUri);
    }

    @Override
    public boolean isConsistent(BeliefSet<SwarmConclusion> beliefs) {
        var byDomain = beliefs.all().stream()
                .collect(Collectors.groupingBy(b -> b.value().domain()));
        return byDomain.values().stream().noneMatch(group ->
                group.stream().anyMatch(a -> group.stream().anyMatch(b ->
                        !a.key().equals(b.key())
                                && contradicts(a.value().conclusion(), b.value().conclusion())
                ))
        );
    }

    private boolean contradicts(String a, String b) {
        int colonA = a.indexOf(':');
        int colonB = b.indexOf(':');
        if (colonA < 0 || colonB < 0) return false;

        String entityA = a.substring(0, colonA);
        String entityB = b.substring(0, colonB);
        if (!entityA.equals(entityB)) return false;

        String stateA = a.substring(colonA + 1);
        String stateB = b.substring(colonB + 1);
        if (stateA.equals(stateB)) return false;

        var termA = registry.resolve(vocabUri, stateA);
        var termB = registry.resolve(vocabUri, stateB);

        if (termA.isPresent() && termB.isPresent()) {
            if (termA.get().value().equals(termB.get().value())) return false;
            if (registry.subsumes(vocabUri, stateA, stateB)) return false;
            if (registry.subsumes(vocabUri, stateB, stateA)) return false;
            return true;
        }

        return true;
    }
}
