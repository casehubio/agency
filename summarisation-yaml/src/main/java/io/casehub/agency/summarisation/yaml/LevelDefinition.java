package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record LevelDefinition(
        String name,
        GroupingDefinition grouping,
        SummariserDefinition summariser,
        @Nullable EmitDefinition emit,
        @JsonProperty("aggregate-fields") @Nullable List<String> aggregateFields) {

    public LevelDefinition {
        if (aggregateFields == null) {
            aggregateFields = List.of();
        }
    }
}
