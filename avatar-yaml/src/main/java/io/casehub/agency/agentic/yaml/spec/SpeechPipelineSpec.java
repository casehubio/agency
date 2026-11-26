package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record SpeechPipelineSpec(
        @Nullable List<StepSpec> postStt,
        @Nullable List<StepSpec> postLlm,
        @Nullable List<StepSpec> postSegmenter) {

    @JsonCreator
    public SpeechPipelineSpec(
            @JsonProperty("postStt") @JsonAlias("post-stt") @Nullable List<StepSpec> postStt,
            @JsonProperty("postLlm") @JsonAlias("post-llm") @Nullable List<StepSpec> postLlm,
            @JsonProperty("postSegmenter") @JsonAlias("post-segmenter") @Nullable List<StepSpec> postSegmenter) {
        this.postStt = postStt;
        this.postLlm = postLlm;
        this.postSegmenter = postSegmenter;
    }
}
