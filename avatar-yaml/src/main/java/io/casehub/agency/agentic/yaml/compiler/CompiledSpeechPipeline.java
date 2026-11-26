package io.casehub.agency.core.yaml.compiler;

public record CompiledSpeechPipeline(
        FilterChain postSttChain,
        FilterChain postLlmChain,
        FilterChain postSegmenterChain) {

    public static final CompiledSpeechPipeline EMPTY =
            new CompiledSpeechPipeline(FilterChain.EMPTY, FilterChain.EMPTY, FilterChain.EMPTY);
}
