package io.casehub.agency.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;

public record ChannelBindingSpec(
        @Nullable String channelId,
        String semantic) {}
