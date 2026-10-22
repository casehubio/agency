package io.casehub.agency.agentic.yaml.spec;

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
            @Nullable CandidateSetStrategySpec candidateGroups,
            @Nullable CandidateSetStrategySpec candidateUsers,
            @Nullable String title,
            @Nullable String titleExpression,
            @Nullable Set<String> outcomes,
            @Nullable Integer claimDeadlineHours,
            @Nullable String scope,
            @Nullable String scopeExpression,
            @Nullable String priority,
            @Nullable String templateRef,
            @Nullable String payloadType,
            @Nullable QuorumConfigSpec quorum
    ) implements CallerConfigSpec {}

    record Llm(
            @Nullable String modelId,
            @Nullable String modelName,
            @Nullable String systemPrompt
    ) implements CallerConfigSpec {}

    record A2A(
            String endpoint,
            @Nullable String skill,
            boolean streaming
    ) implements CallerConfigSpec {}

    record Any() implements CallerConfigSpec {}
}
