package io.casehub.agency.orchestration.loop;

import com.fasterxml.jackson.databind.JsonNode;

public record ContextWrite(String key, JsonNode value) {}
