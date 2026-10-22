package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = TurnPolicySpec.RoundRobin.class, name = "round-robin"),
        @Type(value = TurnPolicySpec.Addressed.class, name = "addressed"),
        @Type(value = TurnPolicySpec.PointAddressed.class, name = "point-addressed"),
        @Type(value = TurnPolicySpec.Free.class, name = "free")
})
public sealed interface TurnPolicySpec {
    record RoundRobin() implements TurnPolicySpec {}
    record Addressed() implements TurnPolicySpec {}
    record PointAddressed() implements TurnPolicySpec {}
    record Free() implements TurnPolicySpec {}
}
