package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import org.jspecify.annotations.Nullable;

public class GesturePromptSection {

    private final PerformanceVocabulary vocabulary;

    public GesturePromptSection(PerformanceVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public @Nullable String render() {
        var gestures = vocabulary.gesturePromptEntries().stream()
                                 .filter(GestureEntry::promptable).toList();
        var moods = vocabulary.moods().stream()
                              .filter(MoodEntry::promptable).toList();

        if (gestures.isEmpty() && moods.isEmpty()) {return null;}

        var sb = new StringBuilder();
        sb.append("Embed gesture and mood tags naturally throughout your response, ");
        sb.append("the way a person gestures while speaking. ");
        sb.append("Place each tag before the word it accompanies.\n");

        if (!gestures.isEmpty()) {
            sb.append("\nAvailable gestures:\n");
            for (var g : gestures) {
                sb.append("[gesture:").append(g.tag()).append("] — ")
                  .append(g.description()).append("\n");
            }
        }

        if (!moods.isEmpty()) {
            sb.append("\nAvailable moods (set emotional tone at a shift in feeling):\n");
            for (var m : moods) {
                sb.append("[mood:").append(m.name()).append("] — ")
                  .append(m.description()).append("\n");
            }
        }

        sb.append("\nGuidelines:\n");
        if (!gestures.isEmpty()) {
            sb.append("- Conversational gestures (nod, shrug, point, emphasis) can appear frequently\n");
            sb.append("- Reactive gestures (clapping, facepalm, shocked) should match genuine emotion\n");
            sb.append("- Dramatic gestures (backflip, death, zombie) are for playful contexts only\n");
        }
        if (!moods.isEmpty()) {
            sb.append("- Mood tags set the emotional tone — place at the start of a shift in feeling\n");
        }
        sb.append("- Not every sentence needs a tag — silence between tags is natural\n");

        if (!gestures.isEmpty() && !moods.isEmpty()) {
            sb.append("\nExamples:\n");
            sb.append("\"[gesture:greeting] Hey there! [gesture:nod] That's a great question. ");
            sb.append("[mood:happy] I'd love to help you with that.\"\n");
            sb.append("\"[gesture:thinking] Hmm, let me think about that. ");
            sb.append("[gesture:aha] Actually, [gesture:point] the key thing is...\"");
        } else if (!gestures.isEmpty()) {
            sb.append("\nExample:\n");
            sb.append("\"[gesture:greeting] Hey there! [gesture:nod] That's a great question.\"");
        } else {
            sb.append("\nExample:\n");
            sb.append("\"[mood:happy] I'd love to help! [mood:sad] I'm sorry to hear that.\"");
        }

        return sb.toString();
    }
}
