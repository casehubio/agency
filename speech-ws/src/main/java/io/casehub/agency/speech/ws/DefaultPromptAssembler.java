package io.casehub.agency.speech.ws;

import io.casehub.eidos.api.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.SpeechPromptAssembler;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultPromptAssembler implements SpeechPromptAssembler {

    private static final String FALLBACK_SYSTEM_PROMPT =
            "You are a conversational assistant. Respond naturally and concisely.";

    private final String systemPrompt;
    private final @Nullable GesturePromptSection gestureSection;

    public DefaultPromptAssembler(@Nullable String systemPrompt) {
        this(systemPrompt, null);
    }

    public DefaultPromptAssembler(@Nullable String systemPrompt,
                                  @Nullable GesturePromptSection gestureSection) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : FALLBACK_SYSTEM_PROMPT;
        this.gestureSection = gestureSection;
    }

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        var sb = new StringBuilder();
        var speakers = new java.util.LinkedHashSet<String>();
        for (ConversationTurn turn : history) {
            String label;
            if (turn.speaker() != null) {
                label = turn.speaker() + " (User)";
                speakers.add(turn.speaker());
            } else {
                label = turn.role().equals("user") ? "User" : "Assistant";
            }
            sb.append(label).append(": ").append(turn.text()).append("\n");
        }
        sb.append("User: ").append(userMessage);

        String effectiveSystemPrompt = systemPrompt;
        if (!speakers.isEmpty()) {
            effectiveSystemPrompt = systemPrompt + "\nSpeaking with: "
                    + String.join(", ", speakers) + ".";
        }

        if (gestureSection != null) {
            String gestureText = gestureSection.render();
            if (gestureText != null) {
                effectiveSystemPrompt = effectiveSystemPrompt + "\n\n" + gestureText;
            }
        }

        return new AssembledPrompt(effectiveSystemPrompt, sb.toString());
    }
}
