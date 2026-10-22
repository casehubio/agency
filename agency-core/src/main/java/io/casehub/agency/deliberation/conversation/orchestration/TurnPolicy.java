package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.deliberation.conversation.ConversationState;

import java.util.List;

public interface TurnPolicy {
    List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants);
}
