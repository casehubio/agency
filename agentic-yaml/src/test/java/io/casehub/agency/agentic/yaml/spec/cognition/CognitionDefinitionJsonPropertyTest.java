package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.yaml.jackson.YamlMappers;
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
                     retentionPerReflection: 0.5
                   """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json       = jsonMapper.writeValueAsString(definition);

        assertThat(json).contains("\"personalityEvolution\"");
        assertThat(json).doesNotContain("\"personality\"");
    }

    @Test
    void yamlDeserializationStillUsesPersonalityFieldName() throws Exception {
        var yaml = """
                   personality:
                     retentionPerReflection: 0.5
                     maxAuditAttempts: 3
                   """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);

        assertThat(definition.personality()).isNotNull();
        assertThat(definition.personality().retentionPerReflection()).isEqualTo(0.5);
        assertThat(definition.personality().maxAuditAttempts()).isEqualTo(3);
    }

    @Test
    void jsonRoundTripPreservesData() throws Exception {
        var yaml = """
                   drive:
                     changeThreshold: 0.1
                   personality:
                     retentionPerReflection: 0.5
                   """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json       = jsonMapper.writeValueAsString(definition);
        var fromJson   = jsonMapper.readValue(json, CognitionDefinition.class);

        assertThat(fromJson.drive()).isNotNull();
        assertThat(fromJson.drive().changeThreshold()).isEqualTo(0.1);
        assertThat(fromJson.personality()).isNotNull();
        assertThat(fromJson.personality().retentionPerReflection()).isEqualTo(0.5);
    }
}
