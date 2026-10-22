package io.casehub.agency.summarisation;

import org.jspecify.annotations.Nullable;

public record LevelEvent<E>(E payload, long timestamp, EventLevel level, @Nullable String tenancyId) {}
