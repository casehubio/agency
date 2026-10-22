package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.qhorus.api.message.MessageView;

@FunctionalInterface
public interface ResponseMessageBuilder {
    MessageView build(AgentParticipant agent,
                      AgentResult result,
                      ConversationState currentState);
}
