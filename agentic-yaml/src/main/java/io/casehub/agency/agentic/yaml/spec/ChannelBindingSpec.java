package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

public record ChannelBindingSpec(
        @JsonAlias("channel-id") @Nullable String channelId,
        String semantic) {}
