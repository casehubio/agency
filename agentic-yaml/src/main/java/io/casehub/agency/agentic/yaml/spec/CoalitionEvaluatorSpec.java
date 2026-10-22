package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = CoalitionEvaluatorSpec.CapabilityCoverage.class, name = "capability-coverage")
})
public sealed interface CoalitionEvaluatorSpec {

    record CapabilityCoverage() implements CoalitionEvaluatorSpec {}
}
