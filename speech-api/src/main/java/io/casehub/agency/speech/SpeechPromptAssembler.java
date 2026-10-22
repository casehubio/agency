package io.casehub.agency.speech;

import java.util.List;

@FunctionalInterface
public interface SpeechPromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);
}
