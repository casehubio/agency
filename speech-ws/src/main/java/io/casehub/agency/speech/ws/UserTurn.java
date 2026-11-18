package io.casehub.agency.speech.ws;

import org.jspecify.annotations.Nullable;

public record UserTurn(
        String cleanText,
        @Nullable String speakerLabel,
        @Nullable String llmModel,
        @Nullable String ttsModel
) {}
