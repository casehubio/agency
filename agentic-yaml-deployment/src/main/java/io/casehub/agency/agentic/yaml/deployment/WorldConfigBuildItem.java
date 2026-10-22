package io.casehub.agency.agentic.yaml.deployment;

import io.casehub.agency.agentic.yaml.spec.world.WorldDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class WorldConfigBuildItem extends SimpleBuildItem {

    private final @Nullable WorldDefinition definition;

    public WorldConfigBuildItem(@Nullable WorldDefinition definition) {
        this.definition = definition;
    }

    public @Nullable WorldDefinition definition() {
        return definition;
    }
}
