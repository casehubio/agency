package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AcceptancePolicySpec.Unanimous.class, name = "unanimous"),
        @Type(value = AcceptancePolicySpec.Majority.class, name = "majority"),
        @Type(value = AcceptancePolicySpec.Threshold.class, name = "threshold")
})
public sealed interface AcceptancePolicySpec {
    record Unanimous() implements AcceptancePolicySpec {}
    record Majority() implements AcceptancePolicySpec {}
    record Threshold(int minAcceptances) implements AcceptancePolicySpec {}
}
