package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechPipelineSpecTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void deserializesPostSttSteps() throws Exception {
        String yaml = """
                post-stt:
                  - name: filler-removal
                  - name: custom-filter
                    config:
                      dictionary: medical
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt()).hasSize(2);
        assertThat(spec.postStt().get(0).name()).isEqualTo("filler-removal");
        assertThat(spec.postStt().get(1).name()).isEqualTo("custom-filter");
        assertThat(spec.postStt().get(1).config()).containsEntry("dictionary", "medical");
    }

    @Test
    void nullInsertionPointsForPartialPipeline() throws Exception {
        String yaml = """
                post-llm:
                  - name: profanity-filter
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt()).isNull();
        assertThat(spec.postLlm()).hasSize(1);
        assertThat(spec.postSegmenter()).isNull();
    }

    @Test
    void allThreeInsertionPoints() throws Exception {
        String yaml = """
                post-stt:
                  - name: cleanup
                post-llm:
                  - name: filter
                post-segmenter:
                  - name: marker
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt()).hasSize(1);
        assertThat(spec.postLlm()).hasSize(1);
        assertThat(spec.postSegmenter()).hasSize(1);
    }

    @Test
    void emptyConfigDefaultsToEmptyMap() throws Exception {
        String yaml = """
                post-stt:
                  - name: simple
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt().get(0).config()).isEmpty();
    }
}
