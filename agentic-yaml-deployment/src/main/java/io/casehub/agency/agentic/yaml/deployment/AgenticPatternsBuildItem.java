package io.casehub.agency.core.yaml.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class AgenticPatternsBuildItem extends SimpleBuildItem {
    private final List<io.casehub.agency.core.yaml.spec.PatternDefinition> patterns;

    public AgenticPatternsBuildItem(List<io.casehub.agency.core.yaml.spec.PatternDefinition> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public List<io.casehub.agency.core.yaml.spec.PatternDefinition> patterns() { return patterns; }
}
