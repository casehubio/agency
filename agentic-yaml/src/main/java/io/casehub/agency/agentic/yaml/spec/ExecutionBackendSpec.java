package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.Objects;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ExecutionBackendSpec.Reactive.class, name = "reactive"),
        @Type(value = ExecutionBackendSpec.Choreographed.class, name = "choreographed"),
        @Type(value = ExecutionBackendSpec.EngineHosted.class, name = "engine-hosted")
})
public sealed interface ExecutionBackendSpec {

    record Reactive() implements ExecutionBackendSpec {}

    record EngineHosted() implements ExecutionBackendSpec {}

    record Choreographed(EventConcurrencyPolicySpec policy) implements ExecutionBackendSpec {
        public Choreographed {
            Objects.requireNonNull(policy, "policy");
        }
    }
}
