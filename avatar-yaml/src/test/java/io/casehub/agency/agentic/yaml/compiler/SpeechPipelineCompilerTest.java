package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.SpeechPipelineSpec;
import io.casehub.agency.core.yaml.spec.StepSpec;
import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeechPipelineCompilerTest {

    private static TextFilter suffix(String s) {
        return new TextFilter() {
            @Override public String apply(String t) { return t + s; }
            @Override public String name() { return "suffix-" + s; }
            @Override public int destructiveness() { return 0; }
        };
    }

    private TextFilterRegistry registry() {
        var r = new TextFilterRegistry();
        r.register("upper", config -> new TextFilter() {
            @Override public String apply(String t) { return t.toUpperCase(); }
            @Override public String name() { return "upper"; }
            @Override public int destructiveness() { return 0; }
        });
        r.register("exclaim", config -> suffix("!"));
        return r;
    }

    @Test
    void compilesExplicitPostSttChain() {
        var spec = new SpeechPipelineSpec(
                List.of(new StepSpec("upper", Map.of()), new StepSpec("exclaim", Map.of())),
                null, null);
        var pipeline = new SpeechPipelineCompiler().compile(spec, registry());
        assertThat(pipeline.postSttChain().apply("hello")).isEqualTo("HELLO!");
        assertThat(pipeline.postLlmChain().apply("hello")).isEqualTo("hello");
        assertThat(pipeline.postSegmenterChain().apply("hello")).isEqualTo("hello");
    }

    @Test
    void compilesPartialPipeline() {
        var spec = new SpeechPipelineSpec(
                null,
                List.of(new StepSpec("exclaim", Map.of())),
                null);
        var pipeline = new SpeechPipelineCompiler().compile(spec, registry());
        assertThat(pipeline.postSttChain().apply("hello")).isEqualTo("hello");
        assertThat(pipeline.postLlmChain().apply("hello")).isEqualTo("hello!");
    }

    @Test
    void unknownStepNameThrows() {
        var spec = new SpeechPipelineSpec(
                List.of(new StepSpec("no-such-filter", Map.of())),
                null, null);
        assertThatThrownBy(() -> new SpeechPipelineCompiler().compile(spec, registry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-filter");
    }

    @Test
    void compileWithFallbackUsesDefaultPostSttWhenNull() {
        var fallback = FilterChain.of(suffix("-fallback"));
        var spec = new SpeechPipelineSpec(null, List.of(new StepSpec("exclaim", Map.of())), null);
        var pipeline = new SpeechPipelineCompiler().compileWithFallback(spec, registry(), fallback);
        assertThat(pipeline.postSttChain().apply("hi")).isEqualTo("hi-fallback");
        assertThat(pipeline.postLlmChain().apply("hi")).isEqualTo("hi!");
    }

    @Test
    void compileWithFallbackOverridesWhenPostSttPresent() {
        var fallback = FilterChain.of(suffix("-fallback"));
        var spec = new SpeechPipelineSpec(
                List.of(new StepSpec("upper", Map.of())),
                null, null);
        var pipeline = new SpeechPipelineCompiler().compileWithFallback(spec, registry(), fallback);
        assertThat(pipeline.postSttChain().apply("hi")).isEqualTo("HI");
    }

    @Test
    void emptyPipelineIsIdentity() {
        assertThat(CompiledSpeechPipeline.EMPTY.postSttChain().apply("hi")).isEqualTo("hi");
        assertThat(CompiledSpeechPipeline.EMPTY.postLlmChain().apply("hi")).isEqualTo("hi");
        assertThat(CompiledSpeechPipeline.EMPTY.postSegmenterChain().apply("hi")).isEqualTo("hi");
    }
}
