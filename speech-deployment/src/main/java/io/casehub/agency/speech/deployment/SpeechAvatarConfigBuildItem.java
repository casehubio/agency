package io.casehub.agency.speech.deployment;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class SpeechAvatarConfigBuildItem extends SimpleBuildItem {

    private final @Nullable AvatarDefinition definition;

    public SpeechAvatarConfigBuildItem(@Nullable AvatarDefinition definition) {
        this.definition = definition;
    }

    public @Nullable AvatarDefinition definition() {
        return definition;
    }
}
