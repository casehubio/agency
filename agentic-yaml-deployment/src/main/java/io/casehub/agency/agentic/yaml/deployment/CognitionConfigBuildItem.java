package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.cognition.CognitionDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class CognitionConfigBuildItem extends SimpleBuildItem {

    private final @Nullable CognitionDefinition definition;

    public CognitionConfigBuildItem(@Nullable CognitionDefinition definition) {
        this.definition = definition;
    }

    public @Nullable CognitionDefinition definition() {
        return definition;
    }
}
