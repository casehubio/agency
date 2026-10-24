package io.casehub.agency.core.yaml.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDriftTest {

    private static final String BASELINE_RESOURCE = "/schema/pattern-spec-schema.json";
    private static final Path BASELINE_SOURCE_PATH =
            Path.of("src/test/resources/schema/pattern-spec-schema.json");

    @Test
    void patternSpecSchemaMatchesBaseline() throws IOException {
        var generator = new AgencySchemaGenerator();
        var schema = generator.generate(io.casehub.agency.core.yaml.spec.PatternDefinition.class);
        var mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        var actual = mapper.writeValueAsString(schema);

        if (System.getProperty("schema.update") != null) {
            Files.createDirectories(BASELINE_SOURCE_PATH.getParent());
            Files.writeString(BASELINE_SOURCE_PATH, actual, StandardCharsets.UTF_8);
            return;
        }

        try (var stream = getClass().getResourceAsStream(BASELINE_RESOURCE)) {
            assertThat(stream)
                    .as("Baseline not found — run with -Dschema.update to generate")
                    .isNotNull();
            var baseline = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actual).isEqualTo(baseline);
        }
    }
}
