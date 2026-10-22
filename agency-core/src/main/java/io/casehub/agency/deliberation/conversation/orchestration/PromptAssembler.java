package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.agency.perception.observation.PartitionedDrain;

@FunctionalInterface
public interface PromptAssembler {
    String assemble(AgentParticipant agent,
                    PartitionedDrain<String> drain,
                    ConversationState state);
}
