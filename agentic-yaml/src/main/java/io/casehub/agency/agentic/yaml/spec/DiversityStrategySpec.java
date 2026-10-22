package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = DiversityStrategySpec.TopN.class, name = "top-n"),
        @Type(value = DiversityStrategySpec.OutcomeAware.class, name = "outcome-aware")
})
public sealed interface DiversityStrategySpec {

    record TopN() implements DiversityStrategySpec {}

    record OutcomeAware(double weight) implements DiversityStrategySpec {
        public OutcomeAware {
            if (weight < 0 || weight > 1)
                throw new IllegalArgumentException("weight must be in [0, 1]");
        }
    }
}
