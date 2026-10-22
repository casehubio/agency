package io.casehub.agency.communication.channel;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.orchestration.execution.AgentInvoker;
import io.casehub.agency.deliberation.conversation.ConversationProjection;
import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.deliberation.conversation.orchestration.AgentParticipant;
import io.casehub.agency.deliberation.conversation.orchestration.PromptAssembler;
import io.casehub.agency.deliberation.conversation.orchestration.ResponseMessageBuilder;
import io.casehub.agency.deliberation.conversation.orchestration.TurnPolicy;
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
