package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PromptOptimiserSpec.FewShot.class, name = "few-shot"),
        @Type(value = PromptOptimiserSpec.Instruction.class, name = "instruction")
})
public sealed interface PromptOptimiserSpec {

    record FewShot(@Nullable DiversityStrategySpec diversity) implements PromptOptimiserSpec {}

    record Instruction() implements PromptOptimiserSpec {}
}
