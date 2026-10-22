package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ConflictResolutionSpec.Priority.class, name = "priority"),
        @Type(value = ConflictResolutionSpec.Specificity.class, name = "specificity"),
        @Type(value = ConflictResolutionSpec.Recency.class, name = "recency"),
        @Type(value = ConflictResolutionSpec.MostRestrictive.class, name = "most-restrictive"),
        @Type(value = ConflictResolutionSpec.Escalation.class, name = "escalation")
})
public sealed interface ConflictResolutionSpec {
    record Priority() implements ConflictResolutionSpec {}
    record Specificity() implements ConflictResolutionSpec {}
    record Recency() implements ConflictResolutionSpec {}
    record MostRestrictive() implements ConflictResolutionSpec {}
    record Escalation(String escalationDecision) implements ConflictResolutionSpec {}
}
