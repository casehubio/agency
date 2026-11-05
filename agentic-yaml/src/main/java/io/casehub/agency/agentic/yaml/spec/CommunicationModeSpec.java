package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = CommunicationModeSpec.Stigmergy.class, name = "stigmergy"),
        @Type(value = CommunicationModeSpec.DirectMessaging.class, name = "direct-messaging"),
        @Type(value = CommunicationModeSpec.Broadcast.class, name = "broadcast"),
        @Type(value = CommunicationModeSpec.GroupChannel.class, name = "group-channel")
})
public sealed interface CommunicationModeSpec {

    record Stigmergy() implements CommunicationModeSpec {}

    record DirectMessaging() implements CommunicationModeSpec {}

    record Broadcast() implements CommunicationModeSpec {}

    record GroupChannel() implements CommunicationModeSpec {}
}
