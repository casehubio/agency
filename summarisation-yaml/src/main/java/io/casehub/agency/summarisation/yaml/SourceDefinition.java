package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record SourceDefinition(
        @Nullable String type,
        @JsonProperty("cloud-event-type") @Nullable String cloudEventType,
        @JsonProperty("type-prefix") @Nullable String typePrefix) {}
