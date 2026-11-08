package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.agency.deliberation.belief.ConsistencyChecker;
import java.util.stream.Collectors;

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
