package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import java.util.stream.Collectors;

/**
 * Detects contradictions between swarm conclusions via structural negation.
 *
 * <p>Conclusions must follow the {@code entity:state} convention — e.g.
 * {@code "node-7:healthy"} and {@code "node-7:degraded"} are a structural
 * negation (same entity, different state). Conclusions without a colon
 * separator are never considered contradictory.
 *
 * <p>This convention is the default. Domains that use different conclusion
 * formats (e.g. free-text descriptions, structured objects, or different
 * delimiters) should implement their own {@link ConsistencyChecker} and
 * pass it to {@link MultiSwarmTopologyBuilder#consistencyChecker}.
 *
 * @see ConsistencyChecker
 * @see MultiSwarmTopologyBuilder#consistencyChecker
 */
public class DomainConsistencyChecker implements ConsistencyChecker<SwarmConclusion> {

    @Override
    public boolean isConsistent(BeliefSet<SwarmConclusion> beliefs) {
        var byDomain = beliefs.all().stream()
                .collect(Collectors.groupingBy(b -> b.value().domain()));
        return byDomain.values().stream().noneMatch(group ->
                group.stream().anyMatch(a -> group.stream().anyMatch(b ->
                        !a.key().equals(b.key())
                                && structuralNegation(a.value().conclusion(), b.value().conclusion())
                ))
        );
    }

    private static boolean structuralNegation(String a, String b) {
        int colonA = a.indexOf(':');
        int colonB = b.indexOf(':');
        if (colonA < 0 || colonB < 0) return false;
        return a.substring(0, colonA).equals(b.substring(0, colonB))
                && !a.substring(colonA + 1).equals(b.substring(colonB + 1));
    }
}
