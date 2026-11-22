package io.casehub.agency.core.yaml.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.casehub.schema.generator.module.EnumInliningModule;
import io.casehub.schema.generator.module.SealedHierarchyModule;
import io.casehub.schema.generator.module.UnevaluatedPropertiesModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgencySchemaGenerator {

    private final SchemaGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgencySchemaGenerator() {
        var overrides = buildDiscriminatorOverrides();
        var configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new SealedHierarchyModule(overrides));
        configBuilder.with(new EnumInliningModule());
        configBuilder.with(new UnevaluatedPropertiesModule());
        this.generator = new SchemaGenerator(configBuilder.build());
    }

    private static Map<Class<?>, Map<Class<?>, String>> buildDiscriminatorOverrides() {
        var overrides = new HashMap<Class<?>, Map<Class<?>, String>>();
        for (var sealedType : List.of(
                io.casehub.agency.core.yaml.spec.RoutingSpec.class,
                io.casehub.agency.core.yaml.spec.TerminationSpec.class,
                io.casehub.agency.core.yaml.spec.AggregationSpec.class,
                io.casehub.agency.core.yaml.spec.ActivationSpec.class,
                io.casehub.agency.core.yaml.spec.DecompositionSpec.class,
                io.casehub.agency.core.yaml.spec.AgentRefSpec.class,
                io.casehub.agency.core.yaml.spec.PatternDefinition.class,
                io.casehub.agency.core.yaml.spec.AcceptancePolicySpec.class,
                io.casehub.agency.core.yaml.spec.TurnPolicySpec.class,
                io.casehub.agency.core.yaml.spec.EpistemicRuleSpec.class,
                io.casehub.agency.core.yaml.spec.ConvergencePolicySpec.class,
                io.casehub.agency.core.yaml.spec.ConflictResolutionSpec.class,
                io.casehub.agency.core.yaml.spec.TaskNodeSpec.class,
                io.casehub.agency.core.yaml.spec.PromptOptimiserSpec.class,
                io.casehub.agency.core.yaml.spec.DiversityStrategySpec.class,
                io.casehub.agency.core.yaml.spec.ConfidenceScorerSpec.class,
                io.casehub.agency.core.yaml.spec.ExecutionBackendSpec.class,
                io.casehub.agency.core.yaml.spec.EventConcurrencyPolicySpec.class,
                io.casehub.agency.core.yaml.spec.ExecutionListenerSpec.class,
                io.casehub.agency.core.yaml.spec.CoalitionEvaluatorSpec.class,
                io.casehub.agency.core.yaml.spec.RiskDecisionSpec.class,
                io.casehub.agency.core.yaml.spec.CandidateSetStrategySpec.class,
                io.casehub.agency.core.yaml.spec.VerifierStrategySpec.class,
                io.casehub.agency.core.yaml.spec.CallerConfigSpec.class)) {
            extractJacksonDiscriminators(sealedType, overrides);
        }
        return overrides;
    }

    private static void extractJacksonDiscriminators(Class<?> sealedType,
                                                      Map<Class<?>, Map<Class<?>, String>> overrides) {
        var annotation = sealedType.getAnnotation(JsonSubTypes.class);
        if (annotation == null) return;
        var subtypeMap = new HashMap<Class<?>, String>();
        for (var subType : annotation.value()) {
            subtypeMap.put(subType.value(), subType.name());
        }
        overrides.put(sealedType, subtypeMap);
    }

    public JsonNode generate(Class<?> rootType) {
        var generated = generator.generateSchema(rootType);
        try {
            var node = mapper.readTree(generated.toString());
            sortOneOfArrays(node);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to convert schema", e);
        }
    }

    private static void sortOneOfArrays(JsonNode node) {
        if (node.isObject()) {
            var obj   = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var oneOf = obj.get("oneOf");
            if (oneOf != null && oneOf.isArray()) {
                var arr    = (com.fasterxml.jackson.databind.node.ArrayNode) oneOf;
                var sorted = new java.util.ArrayList<JsonNode>();
                arr.forEach(sorted::add);
                sorted.sort(java.util.Comparator.comparing(AgencySchemaGenerator::oneOfSortKey));
                arr.removeAll();
                sorted.forEach(arr::add);
            }
            var fields = obj.fields();
            while (fields.hasNext()) {
                sortOneOfArrays(fields.next().getValue());
            }
        } else if (node.isArray()) {
            for (var element : node) {
                sortOneOfArrays(element);
            }
        }
    }

    private static String oneOfSortKey(JsonNode variant) {
        var props = variant.get("properties");
        if (props != null) {
            var type = props.get("type");
            if (type != null) {
                var constVal = type.get("const");
                if (constVal != null) {return constVal.asText();}
            }
        }
        var ref = variant.get("$ref");
        if (ref != null) {return ref.asText();}
        return variant.toString();
    }


}
