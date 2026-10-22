package io.casehub.agency.agentic.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ObservationFilterSpec.PerceptionSpec.class, name = "perception")
})
public sealed interface ObservationFilterSpec {

    record PerceptionSpec() implements ObservationFilterSpec {}
}
