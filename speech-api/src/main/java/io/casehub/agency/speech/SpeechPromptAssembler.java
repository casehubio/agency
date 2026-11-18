package io.casehub.agency.speech;

import io.casehub.eidos.api.AssembledPrompt;

import java.util.List;

@FunctionalInterface
public interface SpeechPromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);

    default AssembledPrompt assemble(String userMessage, List<ConversationTurn> history,
                                     @org.jspecify.annotations.Nullable PromptContext context) {
        return assemble(userMessage, history);
    }
}
