package io.casehub.agency.speech.sherpa;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public record ExecutionProviderConfig(
        String preferred,
        @Nullable Integer deviceId,
        Map<String, String> stageOverrides
) {}
