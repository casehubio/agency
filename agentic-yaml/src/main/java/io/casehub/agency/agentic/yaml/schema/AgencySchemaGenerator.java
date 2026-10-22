package io.casehub.agency.agentic.yaml.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.casehub.schema.generator.module.EnumInliningModule;
import io.casehub.schema.generator.module.SealedHierarchyModule;
import io.casehub.schema.generator.module.UnevaluatedPropertiesModule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgencySchemaGenerator {

    private final SchemaGenerator generator;

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
                io.casehub.agency.agentic.yaml.spec.RoutingSpec.class,
                io.casehub.agency.agentic.yaml.spec.TerminationSpec.class,
                io.casehub.agency.agentic.yaml.spec.AggregationSpec.class,
                io.casehub.agency.agentic.yaml.spec.ActivationSpec.class,
                io.casehub.agency.agentic.yaml.spec.DecompositionSpec.class,
                io.casehub.agency.agentic.yaml.spec.AgentRefSpec.class,
                io.casehub.agency.agentic.yaml.spec.PatternSpec.class,
                io.casehub.agency.agentic.yaml.spec.AcceptancePolicySpec.class,
                io.casehub.agency.agentic.yaml.spec.TurnPolicySpec.class,
                io.casehub.agency.agentic.yaml.spec.EpistemicRuleSpec.class,
                io.casehub.agency.agentic.yaml.spec.ConvergencePolicySpec.class,
                io.casehub.agency.agentic.yaml.spec.ConflictResolutionSpec.class,
                io.casehub.agency.agentic.yaml.spec.TaskNodeSpec.class,
                io.casehub.agency.agentic.yaml.spec.PromptOptimiserSpec.class,
                io.casehub.agency.agentic.yaml.spec.DiversityStrategySpec.class,
                io.casehub.agency.agentic.yaml.spec.ConfidenceScorerSpec.class,
                io.casehub.agency.agentic.yaml.spec.ExecutionBackendSpec.class,
                io.casehub.agency.agentic.yaml.spec.EventConcurrencyPolicySpec.class,
                io.casehub.agency.agentic.yaml.spec.ExecutionListenerSpec.class,
                io.casehub.agency.agentic.yaml.spec.CoalitionEvaluatorSpec.class,
                io.casehub.agency.agentic.yaml.spec.RiskDecisionSpec.class,
                io.casehub.agency.agentic.yaml.spec.CandidateSetStrategySpec.class,
                io.casehub.agency.agentic.yaml.spec.VerifierStrategySpec.class,
                io.casehub.agency.agentic.yaml.spec.CallerConfigSpec.class)) {
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
        return generator.generateSchema(rootType);
    }
}
