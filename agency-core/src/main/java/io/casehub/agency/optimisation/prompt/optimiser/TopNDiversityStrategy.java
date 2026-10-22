package io.casehub.agency.optimisation.prompt.optimiser;

import io.casehub.agency.optimisation.prompt.DiversityStrategy;
import io.casehub.agency.optimisation.prompt.ExampleCandidate;

import java.util.List;

public class TopNDiversityStrategy implements DiversityStrategy {

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        return shortlist.stream().limit(maxExamples).toList();
    }
}
