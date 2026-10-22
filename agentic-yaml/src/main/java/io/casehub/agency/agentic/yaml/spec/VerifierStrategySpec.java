package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = VerifierStrategySpec.LlmEvaluation.class, name = "llm-evaluation"),
        @Type(value = VerifierStrategySpec.SchemaValidation.class, name = "schema-validation")
})
public sealed interface VerifierStrategySpec {
    record LlmEvaluation() implements VerifierStrategySpec {}
    record SchemaValidation() implements VerifierStrategySpec {}
}
