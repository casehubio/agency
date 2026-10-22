package io.casehub.agency.agentic.yaml.deployment;

import io.casehub.agency.agentic.yaml.spec.PatternSpec;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class AgenticPatternsBuildItem extends SimpleBuildItem {
    private final List<PatternSpec> patterns;

    public AgenticPatternsBuildItem(List<PatternSpec> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public List<PatternSpec> patterns() { return patterns; }
}
