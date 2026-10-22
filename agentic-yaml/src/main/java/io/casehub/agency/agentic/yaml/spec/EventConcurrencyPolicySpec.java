package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = EventConcurrencyPolicySpec.Serialize.class, name = "serialize"),
        @Type(value = EventConcurrencyPolicySpec.Coalesce.class, name = "coalesce"),
        @Type(value = EventConcurrencyPolicySpec.CoalesceBySource.class, name = "coalesce-by-source")
})
public sealed interface EventConcurrencyPolicySpec {

    record Serialize() implements EventConcurrencyPolicySpec {}

    record Coalesce(@Nullable Duration window) implements EventConcurrencyPolicySpec {}

    record CoalesceBySource() implements EventConcurrencyPolicySpec {}
}
