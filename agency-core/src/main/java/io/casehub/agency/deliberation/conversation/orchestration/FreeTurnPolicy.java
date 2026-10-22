package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.deliberation.conversation.ConversationState;

import java.util.List;

public class FreeTurnPolicy implements TurnPolicy {

    @Override
    public List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants) {
        return participants.stream()
                .filter(p -> !p.agentId().equals(context.senderId()))
                .toList();
    }
}
