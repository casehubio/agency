package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AgentRefSpec.Worker.class, name = "worker"),
        @Type(value = AgentRefSpec.Channel.class, name = "channel"),
        @Type(value = AgentRefSpec.Human.class, name = "human"),
        @Type(value = AgentRefSpec.External.class, name = "external"),
        @Type(value = AgentRefSpec.Composed.class, name = "composed")
})
public sealed interface AgentRefSpec {

    String name();
    @Nullable String description();
    @Nullable List<String> capabilities();
    @Nullable String model();

    record Worker(String name, @Nullable String description,
                  @Nullable List<String> capabilities,
                  @Nullable String model) implements AgentRefSpec {}

    record Channel(String name, String channelId, @Nullable String description,
                   @Nullable List<String> capabilities,
                   @Nullable String model) implements AgentRefSpec {}

    record Human(String name, @Nullable String description,
                 @Nullable List<String> capabilities,
                 @Nullable String model) implements AgentRefSpec {}

    record External(String name, @Nullable String description,
                    @Nullable List<String> capabilities,
                    @Nullable String model) implements AgentRefSpec {}

    record Composed(String name, @Nullable String description,
                    @Nullable List<String> capabilities,
                    @Nullable String model,
                    PatternSpec pattern) implements AgentRefSpec {}
}
