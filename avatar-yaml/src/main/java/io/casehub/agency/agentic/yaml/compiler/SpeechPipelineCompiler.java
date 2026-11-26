package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.SpeechPipelineSpec;
import io.casehub.agency.core.yaml.spec.StepSpec;
import io.casehub.connectors.voice.spi.TextFilter;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class SpeechPipelineCompiler {

    public CompiledSpeechPipeline compile(SpeechPipelineSpec spec, TextFilterRegistry registry) {
        return new CompiledSpeechPipeline(
                compileChain(spec.postStt(), registry),
                compileChain(spec.postLlm(), registry),
                compileChain(spec.postSegmenter(), registry));
    }

    public CompiledSpeechPipeline compileWithFallback(
            SpeechPipelineSpec spec, TextFilterRegistry registry, FilterChain postSttFallback) {
        return new CompiledSpeechPipeline(
                spec.postStt() != null ? compileChain(spec.postStt(), registry) : postSttFallback,
                compileChain(spec.postLlm(), registry),
                compileChain(spec.postSegmenter(), registry));
    }

    private FilterChain compileChain(@Nullable List<StepSpec> steps, TextFilterRegistry registry) {
        if (steps == null || steps.isEmpty()) return FilterChain.EMPTY;
        TextFilter[] filters = steps.stream()
                .map(step -> registry.create(step.name(), step.config()))
                .toArray(TextFilter[]::new);
        return FilterChain.of(filters);
    }
}
