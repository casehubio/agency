package io.casehub.agency.core.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.agency.perception.observation.affordance.ResolutionTier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ObservationSectionSpec.EntityGroupSpec.class, name = "entity-group"),
        @Type(value = ObservationSectionSpec.TextBlockSpec.class, name = "text"),
        @Type(value = ObservationSectionSpec.ItemListSpec.class, name = "item-list")
})
public sealed interface ObservationSectionSpec {

    String header();

    @Nullable Set<String> requiredTags();

    @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions();

    @Nullable String interpretiveFrame();

    record EntityGroupSpec(
            String header,
            @Nullable String emptyMessage,
            @Nullable List<String> entityRefs,
            @Nullable List<InlineEntitySpec> entities,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record TextBlockSpec(
            String header,
            String content,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record ItemListSpec(
            String header,
            @Nullable String emptyMessage,
            @Nullable List<String> items,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}
}
