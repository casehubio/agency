package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionDefinitionJsonPropertyTest {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    @Test
    void personalitySerializesAsPersonalityEvolutionInJson() throws Exception {
        var yaml = """
                personality:
                  decayFactor: 0.95
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json = jsonMapper.writeValueAsString(definition);

        assertThat(json).contains("\"personalityEvolution\"");
        assertThat(json).doesNotContain("\"personality\"");
    }

    @Test
    void yamlDeserializationStillUsesPersonalityFieldName() throws Exception {
        var yaml = """
                personality:
                  decayFactor: 0.95
                  l2Ceiling: 0.3
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);

        assertThat(definition.personality()).isNotNull();
        assertThat(definition.personality().decayFactor()).isEqualTo(0.95);
        assertThat(definition.personality().l2Ceiling()).isEqualTo(0.3);
    }

    @Test
    void jsonRoundTripPreservesData() throws Exception {
        var yaml = """
                drive:
                  changeThreshold: 0.1
                personality:
                  decayFactor: 0.8
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json = jsonMapper.writeValueAsString(definition);
        var fromJson = jsonMapper.readValue(json, CognitionDefinition.class);

        assertThat(fromJson.drive()).isNotNull();
        assertThat(fromJson.drive().changeThreshold()).isEqualTo(0.1);
        assertThat(fromJson.personality()).isNotNull();
        assertThat(fromJson.personality().decayFactor()).isEqualTo(0.8);
    }
}
