package io.casehub.agency.agentic.yaml.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.agency.orchestration.execution.CapabilityCategory;
import io.casehub.agency.orchestration.execution.CapabilityType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class CapabilityCatalogGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<CapabilityType> YAML_BACKED = EnumSet.of(
            CapabilityType.SUPERVISOR, CapabilityType.VOTING, CapabilityType.DEBATE,
            CapabilityType.DECOMPOSITION, CapabilityType.SEQUENCE, CapabilityType.PARALLEL,
            CapabilityType.LOOP, CapabilityType.CONDITIONAL,
            CapabilityType.NEGOTIATION, CapabilityType.SUMMARISATION,
            CapabilityType.COGNITION, CapabilityType.PROMPT_OPTIMISATION,
            CapabilityType.MATCH, CapabilityType.TRY_CATCH
    );

    private CapabilityCatalogGenerator() {}

    public static String generateCatalog() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);

        ArrayNode categories = root.putArray("categories");
        for (CapabilityCategory cat : CapabilityCategory.values()) {
            ObjectNode catNode = categories.addObject();
            catNode.put("name", toKebab(cat.name()));
        }

        ArrayNode capabilities = root.putArray("capabilities");
        for (CapabilityType ct : CapabilityType.values()) {
            ObjectNode capNode = capabilities.addObject();
            capNode.put("name", toKebab(ct.name()));
            capNode.put("category", toKebab(ct.category().name()));
            capNode.put("hasYamlRoot", YAML_BACKED.contains(ct));
        }

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate catalog", e);
        }
    }

    private static String toKebab(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
