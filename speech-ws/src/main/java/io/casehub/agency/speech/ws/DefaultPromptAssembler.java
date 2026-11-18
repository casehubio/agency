package io.casehub.agency.speech.ws;

import io.casehub.eidos.api.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.PromptSection;
import io.casehub.agency.speech.SpeechPromptAssembler;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultPromptAssembler implements SpeechPromptAssembler {

    private static final String FALLBACK_SYSTEM_PROMPT =
            "You are a conversational assistant. Respond naturally and concisely.";

    private final String systemPrompt;
    private final List<PromptSection> sections;

    public DefaultPromptAssembler(@Nullable String systemPrompt) {
        this(systemPrompt, List.of());
    }

    public DefaultPromptAssembler(@Nullable String systemPrompt, List<PromptSection> sections) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : FALLBACK_SYSTEM_PROMPT;
        this.sections = List.copyOf(sections);
    }

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        return assemble(userMessage, history, null);
    }

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history,
                                    @Nullable PromptContext context) {
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

        if (context != null && !sections.isEmpty()) {
            var sectionBuilder = new StringBuilder(effectiveSystemPrompt);
            for (var section : sections) {
                String text = section.contribute(context);
                if (text != null) {
                    sectionBuilder.append("\n\n").append(text);
                }
            }
            effectiveSystemPrompt = sectionBuilder.toString();
        }

        return new AssembledPrompt(effectiveSystemPrompt, sb.toString());
    }
}
