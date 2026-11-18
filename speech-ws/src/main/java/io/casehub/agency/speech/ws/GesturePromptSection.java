package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.PromptSection;
import org.jspecify.annotations.Nullable;

public class GesturePromptSection implements PromptSection {

    private final PerformanceVocabulary vocabulary;

    public GesturePromptSection(PerformanceVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var gestures = vocabulary.gesturePromptEntries().stream()
                .filter(GestureEntry::promptable).toList();
        var moods = vocabulary.moods().stream()
                .filter(MoodEntry::promptable).toList();

        if (gestures.isEmpty() && moods.isEmpty()) return null;

        var sb = new StringBuilder("You may annotate your response with tags.\n");

        if (!gestures.isEmpty()) {
            sb.append("\nGestures (place before the associated word):\n");
            for (var g : gestures) {
                sb.append("[gesture:").append(g.tag()).append("] — ")
                  .append(g.description()).append("\n");
            }
        }

        if (!moods.isEmpty()) {
            sb.append("\nMoods (place at sentence start):\n");
            for (var m : moods) {
                sb.append("[mood:").append(m.name()).append("] — ")
                  .append(m.description()).append("\n");
            }
        }

        sb.append("\nRules: at most one gesture per sentence. Only use when it fits naturally.");
        return sb.toString();
    }
}
