package io.casehub.agency.speech;

import io.casehub.eidos.api.AssembledPrompt;

import java.util.List;

@FunctionalInterface
public interface SpeechPromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);
}
