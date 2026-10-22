package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmitDefinition(@JsonProperty("cloud-event-type") String cloudEventType) {}
