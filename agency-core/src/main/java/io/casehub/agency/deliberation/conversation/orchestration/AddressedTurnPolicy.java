package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.deliberation.conversation.ConversationState;

import java.util.List;

public class AddressedTurnPolicy implements TurnPolicy {

    @Override
    public List<AgentParticipant> nextResponders(
            ConversationState state,
            TurnContext context,
            List<AgentParticipant> participants) {
        if (context.targetId() == null) return List.of();
        return participants.stream()
                .filter(p -> p.role().equals(context.targetId()))
                .toList();
    }
}
