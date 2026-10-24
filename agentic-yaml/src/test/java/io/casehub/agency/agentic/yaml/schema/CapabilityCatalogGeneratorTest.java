package io.casehub.agency.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.orchestration.execution.CapabilityCategory;
import io.casehub.agency.orchestration.execution.CapabilityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCatalogGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generatesValidJsonWithVersion() throws Exception {
        var json = CapabilityCatalogGenerator.generateCatalog();
        var root = mapper.readTree(json);
        assertThat(root.has("version")).isTrue();
        assertThat(root.get("version").asInt()).isEqualTo(1);
    }

    @Test
    void containsAllCategories() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var categories = root.get("categories");
        assertThat(categories).hasSize(CapabilityCategory.values().length);
    }

    @Test
    void containsAllCapabilities() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var capabilities = root.get("capabilities");
        assertThat(capabilities).hasSize(CapabilityType.values().length);
    }

    @Test
    void capabilityHasRequiredFields() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        for (JsonNode cap : root.get("capabilities")) {
            assertThat(cap.has("name")).isTrue();
            assertThat(cap.has("category")).isTrue();
            assertThat(cap.has("hasYamlRoot")).isTrue();
        }
    }

    @Test
    void supervisorIsOrchestrationWithYamlRoot() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var supervisor = findCapability(root, "supervisor");
        assertThat(supervisor.get("category").asText()).isEqualTo("orchestration");
        assertThat(supervisor.get("hasYamlRoot").asBoolean()).isTrue();
    }

    @Test
    void choreographyIsOrchestrationWithoutYamlRoot() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var choreo = findCapability(root, "choreography");
        assertThat(choreo.get("category").asText()).isEqualTo("orchestration");
        assertThat(choreo.get("hasYamlRoot").asBoolean()).isFalse();
    }

    @Test
    void negotiationIsDeliberationWithYamlRoot() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var neg = findCapability(root, "negotiation");
        assertThat(neg.get("category").asText()).isEqualTo("deliberation");
        assertThat(neg.get("hasYamlRoot").asBoolean()).isTrue();
    }

    @Test
    void categoryNamesAreKebabCase() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        for (JsonNode cat : root.get("categories")) {
            var name = cat.get("name").asText();
            assertThat(name).matches("[a-z]+(-[a-z]+)*");
        }
    }

    @Test
    void capabilityNamesAreKebabCase() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        for (JsonNode cap : root.get("capabilities")) {
            var name = cap.get("name").asText();
            assertThat(name).matches("[a-z]+(-[a-z]+)*");
        }
    }

    private JsonNode findCapability(JsonNode root, String name) {
        for (JsonNode cap : root.get("capabilities")) {
            if (cap.get("name").asText().equals(name)) return cap;
        }
        throw new AssertionError("Capability not found: " + name);
    }
}
