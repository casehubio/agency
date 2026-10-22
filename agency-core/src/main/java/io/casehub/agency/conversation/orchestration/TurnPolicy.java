package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.conversation.ConversationState;

import java.util.List;

public interface TurnPolicy {
    List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants);
}
