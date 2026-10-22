package io.casehub.agency.agentic.channel;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.model.AgentInvoker;
import io.casehub.agency.conversation.ConversationProjection;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.conversation.orchestration.AgentParticipant;
import io.casehub.agency.conversation.orchestration.PromptAssembler;
import io.casehub.agency.conversation.orchestration.ResponseMessageBuilder;
import io.casehub.agency.conversation.orchestration.TurnPolicy;
import io.casehub.qhorus.api.message.MessageView;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public record ConversationConfig<T>(
        PromptAssembler promptAssembler,
        ResponseMessageBuilder responseBuilder,
        Function<T, MessageView> triggerMapper,
        @Nullable Supplier<ConversationProjection> projectionFactory,
        @Nullable AgentInvoker<String> conversationInvoker,
        @Nullable Function<AgentRef, AgentParticipant> participantMapper,
        @Nullable TurnPolicy turnPolicyOverride,
        @Nullable TerminationCondition<ConversationState> terminationOverride
) {
    public ConversationConfig {
        Objects.requireNonNull(promptAssembler, "promptAssembler");
        Objects.requireNonNull(responseBuilder, "responseBuilder");
        Objects.requireNonNull(triggerMapper, "triggerMapper");
    }
}
