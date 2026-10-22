package io.casehub.agency.prompt;

import java.util.List;

@FunctionalInterface
public interface PromptQualityMetric {
    double score(List<VariantOutcome> outcomes);
}
