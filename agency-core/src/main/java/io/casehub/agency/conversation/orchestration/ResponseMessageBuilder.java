package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.AgentResult;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.qhorus.api.message.MessageView;

@FunctionalInterface
public interface ResponseMessageBuilder {
    MessageView build(AgentParticipant agent,
                      AgentResult result,
                      ConversationState currentState);
}
