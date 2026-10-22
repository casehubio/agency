package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.world.*;
import io.casehub.agency.perception.observation.affordance.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class WorldCompiler {

    private final ObservationFilterRegistry filterRegistry;

    public WorldCompiler(ObservationFilterRegistry filterRegistry) {
        this.filterRegistry = filterRegistry;
    }

    public CompiledWorld compile(WorldDefinition definition) {
        var entityMap = compileEntityMap(definition.entities());
        return new CompiledWorld(
                compileActions(definition.actions()),
                entityMap,
                compileSections(definition.sections(), entityMap),
                compilePipeline(definition.pipeline()),
                compileRenderer(definition.renderer()));
    }

    private Map<String, ObservableEntity> compileEntityMap(@Nullable Map<String, EntitySpec> specs) {
        if (specs == null) return Map.of();
        var result = new LinkedHashMap<String, ObservableEntity>();
        for (var entry : specs.entrySet()) {
            result.put(entry.getKey(), compileEntity(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private ObservableEntity compileEntity(String id, EntitySpec spec) {
        return new ObservableEntity(id, spec.displayName(), spec.description(), compileAffordances(spec.affordances()));
    }

    private ObservableEntity compileInlineEntity(InlineEntitySpec spec) {
        return new ObservableEntity(spec.id(), spec.displayName(), spec.description(), compileAffordances(spec.affordances()));
    }

    private List<Affordance> compileAffordances(@Nullable List<AffordanceSpec> specs) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> new Affordance(s.actionType(), s.label(), s.requiredItem(), s.acceptsItems() != null ? s.acceptsItems() : List.of()))
                .toList();
    }

    private List<ActionDescriptor> compileActions(@Nullable List<ActionDescriptorSpec> specs) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> new ActionDescriptor(s.type(), s.description(), s.parameterFormat()))
                .toList();
    }

    private List<ObservationSection> compileSections(@Nullable List<ObservationSectionSpec> specs, Map<String, ObservableEntity> entityMap) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> compileSection(s, entityMap))
                .toList();
    }

    private ObservationSection compileSection(ObservationSectionSpec spec, Map<String, ObservableEntity> entityMap) {
        ObservationSection base = switch (spec) {
            case ObservationSectionSpec.EntityGroupSpec g -> compileEntityGroup(g, entityMap);
            case ObservationSectionSpec.TextBlockSpec t -> new ObservationSection.TextBlock(t.header(), t.content());
            case ObservationSectionSpec.ItemListSpec i -> new ObservationSection.ItemList(i.header(), i.emptyMessage(), i.items() != null ? i.items() : List.of());
        };
        return maybeAnnotate(base, spec, entityMap);
    }

    private ObservationSection.EntityGroup compileEntityGroup(ObservationSectionSpec.EntityGroupSpec spec, Map<String, ObservableEntity> entityMap) {
        var entities = new ArrayList<ObservableEntity>();
        if (spec.entityRefs() != null) {
            for (var ref : spec.entityRefs()) {
                var entity = entityMap.get(ref);
                if (entity == null) {
                    throw new IllegalArgumentException("Unresolved entity ref '" + ref + "' in section '" + spec.header() + "'");
                }
                entities.add(entity);
            }
        }
        if (spec.entities() != null) {
            for (var inline : spec.entities()) {
                entities.add(compileInlineEntity(inline));
            }
        }
        return new ObservationSection.EntityGroup(spec.header(), spec.emptyMessage(), entities);
    }

    private ObservationSection maybeAnnotate(ObservationSection base, ObservationSectionSpec spec, Map<String, ObservableEntity> entityMap) {
        if (spec.requiredTags() == null && spec.resolutions() == null && spec.interpretiveFrame() == null) {
            return base;
        }
        Map<ResolutionTier, ObservationSection> compiledResolutions = Map.of();
        if (spec.resolutions() != null) {
            compiledResolutions = new LinkedHashMap<>();
            for (var entry : spec.resolutions().entrySet()) {
                compiledResolutions.put(entry.getKey(), compileSection(entry.getValue(), entityMap));
            }
            compiledResolutions = Collections.unmodifiableMap(compiledResolutions);
        }
        return new AnnotatedSection(base, spec.requiredTags() != null ? spec.requiredTags() : Set.of(), compiledResolutions, spec.interpretiveFrame());
    }

    private @Nullable ObservationPipeline compilePipeline(@Nullable List<ObservationFilterSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        var filters = specs.stream()
                .map(this::compileFilter)
                .toArray(ObservationFilter[]::new);
        return new ObservationPipeline(filters);
    }

    private ObservationFilter compileFilter(ObservationFilterSpec spec) {
        return switch (spec) {
            case ObservationFilterSpec.PerceptionSpec ignored -> filterRegistry.resolve("perception");
        };
    }

    private CompiledWorld.@Nullable RendererThresholds compileRenderer(@Nullable RendererSpec spec) {
        if (spec == null) return null;
        int verbatim = spec.verbatimThreshold() != null ? spec.verbatimThreshold() : 5;
        Integer grouped = spec.groupedThreshold();
        if (grouped != null && grouped <= verbatim) {
            throw new IllegalArgumentException("groupedThreshold (" + grouped + ") must be > verbatimThreshold (" + verbatim + ")");
        }
        return new CompiledWorld.RendererThresholds(verbatim, grouped);
    }
}
