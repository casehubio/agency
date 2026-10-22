package io.casehub.agency.summarisation.yaml.runtime;

import io.casehub.agency.summarisation.yaml.CompiledPipeline;
import io.casehub.agency.summarisation.yaml.PipelineCompiler;
import io.casehub.agency.summarisation.yaml.PipelineDefinition;
import io.casehub.agency.summarisation.yaml.SummariserFactory;
import io.casehub.agency.summarisation.yaml.SummariserRegistry;
import io.casehub.agency.summarisation.yaml.builtin.CountSummariser;
import io.casehub.agency.summarisation.yaml.builtin.FieldExtractSummariser;
import io.casehub.agency.summarisation.yaml.builtin.PhaseDetectSummariser;
import io.casehub.agency.summarisation.yaml.builtin.ThresholdClassifySummariser;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.quarkus.runtime.annotations.Recorder;

import java.util.Map;

@Recorder
public class SummarisationRecorder {

    @SuppressWarnings("unchecked")
    public SummariserRegistry createRegistry(ExpressionEngine expressionEngine) {
        var registry = new SummariserRegistry();
        registry.register("threshold-classify", (SummariserFactory)
                                                        config -> ThresholdClassifySummariser.create(config, expressionEngine));
        registry.register("phase-detect", (SummariserFactory)
                                                  config -> PhaseDetectSummariser.create(config, java.util.List.of()));
        registry.register("count", (SummariserFactory) config -> CountSummariser.create(config));
        registry.register("field-extract", (SummariserFactory)
                                                   config -> FieldExtractSummariser.create(config, expr -> {
                                                       var compiled = expressionEngine.compile(expr,
                                                                                               com.fasterxml.jackson.databind.JsonNode.class, Object.class);
                                                       return node -> {
                                                           var result = compiled.eval(node);
                                                           if (result instanceof java.util.List<?> l) {
                                                               return l.stream()
                                                                       .map(o -> (com.fasterxml.jackson.databind.JsonNode) o)
                                                                       .toList();
                                                           }
                                                           return result instanceof com.fasterxml.jackson.databind.JsonNode jn
                                                                  ? java.util.List.of(jn) : java.util.List.of();
                                                       };
                                                   }));
        registry.register("verbatim", (SummariserFactory) config -> {
            var expr     = (String) config.getOrDefault("expression", "toString()");
            var compiled = expressionEngine.compile(expr, Object.class, Object.class);
            return new io.casehub.agency.summarisation.VerbatimContentSummariser<>(
                    item -> String.valueOf(compiled.eval(item))).asSummariser();
        });
        return registry;
    }

    @SuppressWarnings("unchecked")
    public CompiledPipeline<Map<String, Object>> compilePipeline(
            PipelineDefinition definition,
            SummariserRegistry registry,
            ExpressionEngine expressionEngine) {
        return new PipelineCompiler().compile(definition, registry, null, expressionEngine);
    }
}
