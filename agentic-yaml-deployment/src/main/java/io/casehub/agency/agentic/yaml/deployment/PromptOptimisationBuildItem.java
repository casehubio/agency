package io.casehub.agency.agentic.yaml.deployment;

import io.casehub.agency.agentic.yaml.spec.PromptOptimisationDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class PromptOptimisationBuildItem extends SimpleBuildItem {

    private final @Nullable PromptOptimisationDefinition definition;

    public PromptOptimisationBuildItem(
            @Nullable PromptOptimisationDefinition definition) {
        this.definition = definition;
    }

    public @Nullable PromptOptimisationDefinition definition() {
        return definition;
    }
}
