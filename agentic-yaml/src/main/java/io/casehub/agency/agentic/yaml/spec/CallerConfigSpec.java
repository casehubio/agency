package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = CallerConfigSpec.Human.class, name = "human"),
        @Type(value = CallerConfigSpec.Llm.class, name = "llm"),
        @Type(value = CallerConfigSpec.A2A.class, name = "a2a"),
        @Type(value = CallerConfigSpec.Any.class, name = "any")
})
public sealed interface CallerConfigSpec {

    record Human(
            @JsonAlias("candidate-groups") @Nullable CandidateSetStrategySpec candidateGroups,
            @JsonAlias("candidate-users") @Nullable CandidateSetStrategySpec candidateUsers,
            @Nullable String title,
            @JsonAlias("title-expression") @Nullable String titleExpression,
            @Nullable Set<String> outcomes,
            @JsonAlias("claim-deadline-hours") @Nullable Integer claimDeadlineHours,
            @Nullable String scope,
            @JsonAlias("scope-expression") @Nullable String scopeExpression,
            @Nullable String priority,
            @JsonAlias("template-ref") @Nullable String templateRef,
            @JsonAlias("payload-type") @Nullable String payloadType,
            @Nullable QuorumConfigSpec quorum
    ) implements CallerConfigSpec {}

    record Llm(
            @JsonAlias("model-id") @Nullable String modelId,
            @JsonAlias("model-name") @Nullable String modelName,
            @JsonAlias("system-prompt") @Nullable String systemPrompt
    ) implements CallerConfigSpec {}

    record A2A(
            String endpoint,
            @Nullable String skill,
            boolean streaming
    ) implements CallerConfigSpec {}

    record Any() implements CallerConfigSpec {}
}
