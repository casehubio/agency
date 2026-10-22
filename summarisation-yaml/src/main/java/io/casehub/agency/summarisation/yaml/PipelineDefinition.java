package io.casehub.agency.summarisation.yaml;

import java.util.List;

public record PipelineDefinition(
        String name,
        SourceDefinition source,
        List<LevelDefinition> levels,
        @com.fasterxml.jackson.annotation.JsonProperty("tick-interval") @org.jspecify.annotations.Nullable Long tickInterval) {}
