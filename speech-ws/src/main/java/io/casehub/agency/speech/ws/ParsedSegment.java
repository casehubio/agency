package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.ws.protocol.Cue;

import java.util.List;

public record ParsedSegment(
        String cleanText,
        List<Cue> cues,
        String canonicalMarkup
) {
    public ParsedSegment {
        cues = List.copyOf(cues);
    }

    public int wordCount() {
        return cleanText.isBlank() ? 0 : cleanText.trim().split("\\s+").length;
    }
}
