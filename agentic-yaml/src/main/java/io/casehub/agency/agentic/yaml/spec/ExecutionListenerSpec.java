package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ExecutionListenerSpec.EventLog.class, name = "event-log"),
        @Type(value = ExecutionListenerSpec.Ledger.class, name = "ledger"),
        @Type(value = ExecutionListenerSpec.Metrics.class, name = "metrics"),
        @Type(value = ExecutionListenerSpec.Checkpointing.class, name = "checkpointing")
})
public sealed interface ExecutionListenerSpec {

    record EventLog() implements ExecutionListenerSpec {}

    record Ledger(@Nullable String supervisorActorId) implements ExecutionListenerSpec {}

    record Metrics() implements ExecutionListenerSpec {}

    record Checkpointing() implements ExecutionListenerSpec {}
}
