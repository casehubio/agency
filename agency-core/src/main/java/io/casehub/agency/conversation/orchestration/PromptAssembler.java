package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.summarisation.observation.PartitionedDrain;

@FunctionalInterface
public interface PromptAssembler {
    String assemble(AgentParticipant agent,
                    PartitionedDrain<String> drain,
                    ConversationState state);
}
