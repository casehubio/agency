package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.PromptContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GesturePromptSectionTest {

    @Test
    void contributesPromptableGesturesAndMoods() {
        var vocab = stubVocabulary(
                List.of(
                        new GestureEntry("nod", "agreement", true),
                        new GestureEntry("shrug", "uncertainty", true),
                        new GestureEntry("internal-only", "not for LLM", false)),
                List.of(
                        new MoodEntry("happy", "warm positive tone", true),
                        new MoodEntry("sleep", "drowsy", false)));

        var section = new GesturePromptSection(vocab);
        var text = section.contribute(new PromptContext("a", "t", null));

        assertThat(text).contains("[gesture:nod]").contains("agreement");
        assertThat(text).contains("[gesture:shrug]").contains("uncertainty");
        assertThat(text).doesNotContain("internal-only");
        assertThat(text).contains("[mood:happy]").contains("warm positive tone");
        assertThat(text).doesNotContain("sleep");
        assertThat(text).contains("at most one gesture per sentence");
    }

    @Test
    void returnsNullWhenNoPromptableEntries() {
        var vocab = stubVocabulary(
                List.of(new GestureEntry("hidden", "x", false)),
                List.of(new MoodEntry("hidden", "x", false)));

        var section = new GesturePromptSection(vocab);

        assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
    }

    @Test
    void worksWithGesturesOnlyNoMoods() {
        var vocab = stubVocabulary(
                List.of(new GestureEntry("wave", "greeting", true)),
                List.of());

        var section = new GesturePromptSection(vocab);
        var text = section.contribute(new PromptContext("a", "t", null));

        assertThat(text).contains("[gesture:wave]");
        assertThat(text).doesNotContain("[mood:");
    }

    @Test
    void worksWithMoodsOnlyNoGestures() {
        var vocab = stubVocabulary(
                List.of(),
                List.of(new MoodEntry("angry", "frustrated tone", true)));

        var section = new GesturePromptSection(vocab);
        var text = section.contribute(new PromptContext("a", "t", null));

        assertThat(text).doesNotContain("[gesture:");
        assertThat(text).contains("[mood:angry]").contains("frustrated tone");
    }

    private static PerformanceVocabulary stubVocabulary(List<GestureEntry> gestures,
                                                        List<MoodEntry> moods) {
        return new PerformanceVocabulary() {
            public Set<String> gestureTags() {
                return gestures.stream().map(GestureEntry::tag).collect(Collectors.toSet());
            }
            public boolean isValidGesture(String tag) {
                return gestures.stream().anyMatch(g -> g.tag().equals(tag));
            }
            public List<GestureEntry> gesturePromptEntries() { return gestures; }
            public Set<String> allClipNames() { return Set.of(); }
            public List<MoodEntry> moods() { return moods; }
            public boolean isValidMood(String mood) {
                return moods.stream().anyMatch(m -> m.name().equals(mood));
            }
        };
    }
}
