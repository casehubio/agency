package io.casehub.agency.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record WorldDefinition(
        @Nullable List<ActionDescriptorSpec> actions,
        @Nullable Map<String, EntitySpec> entities,
        @Nullable List<ObservationSectionSpec> sections,
        @Nullable List<ObservationFilterSpec> pipeline,
        @Nullable RendererSpec renderer) {}
