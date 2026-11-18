package io.casehub.agency.speech.ws;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.avatar")
public interface AvatarConfig {

    @WithDefault("16000")
    int sampleRate();

    @WithDefault("2")
    int maxDestructiveness();

    Optional<String> systemPrompt();

    Optional<String> agentId();

    Optional<String> tenantId();

    @WithDefault("30")
    int proactiveTickIntervalSeconds();

    @WithDefault("false")
    boolean attentionEnabled();

    @WithDefault("30")
    int attentionSubtleSeconds();

    @WithDefault("75")
    int attentionStrongSeconds();

    @WithDefault("0.6")
    double minPlaybackRate();
}
