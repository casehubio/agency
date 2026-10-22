package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupingDefinition.Windowed.class, name = "windowed"),
        @JsonSubTypes.Type(value = GroupingDefinition.Keyed.class, name = "keyed")
})
public sealed interface GroupingDefinition {

    record Windowed(@Nullable Long age, @Nullable Integer count) implements GroupingDefinition {}

    record Keyed(
            @JsonProperty("key") String keyExpression,
            @JsonProperty("completion") String completionExpression,
            @JsonProperty("stale-timeout") long staleTimeout) implements GroupingDefinition {}
}
