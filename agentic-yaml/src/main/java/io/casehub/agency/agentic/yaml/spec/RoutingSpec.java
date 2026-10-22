package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = RoutingSpec.FirstMatch.class, name = "first-match"),
        @Type(value = RoutingSpec.RoundRobin.class, name = "round-robin"),
        @Type(value = RoutingSpec.Sequential.class, name = "sequential"),
        @Type(value = RoutingSpec.LlmSelected.class, name = "llm-selected"),
        @Type(value = RoutingSpec.SelectAll.class, name = "select-all")
})
public sealed interface RoutingSpec {

    record FirstMatch(@Nullable String guard) implements RoutingSpec {}

    record RoundRobin() implements RoutingSpec {}

    record Sequential() implements RoutingSpec {}

    record LlmSelected() implements RoutingSpec {}

    record SelectAll() implements RoutingSpec {}
}
