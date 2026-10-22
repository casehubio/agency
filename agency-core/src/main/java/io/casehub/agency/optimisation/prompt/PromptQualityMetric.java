package io.casehub.agency.optimisation.prompt;

import java.util.List;

@FunctionalInterface
public interface PromptQualityMetric {
    double score(List<VariantOutcome> outcomes);
}
