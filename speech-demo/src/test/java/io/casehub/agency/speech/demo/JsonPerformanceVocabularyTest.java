package io.casehub.agency.speech.demo;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPerformanceVocabularyTest {

    private static JsonPerformanceVocabulary vocab;

    @BeforeAll
    static void loadVocab() {
        var stream = JsonPerformanceVocabularyTest.class.getClassLoader()
                .getResourceAsStream("META-INF/resources/animations/avatar-performance.json");
        assertThat(stream).as("vocabulary JSON must be on classpath").isNotNull();
        vocab = new JsonPerformanceVocabulary(stream);
    }

    @Test
    void loadsAllGesturesFromJson() {
        assertThat(vocab.gestureTags()).contains("nod", "shrug", "greeting", "angry", "victory");
        assertThat(vocab.isValidGesture("nod")).isTrue();
        assertThat(vocab.isValidGesture("nonexistent")).isFalse();
    }

    @Test
    void promptableEntriesHaveDescriptions() {
        for (var entry : vocab.gesturePromptEntries()) {
            if (entry.promptable()) {
                assertThat(entry.description()).as("gesture %s", entry.tag()).isNotBlank();
            }
        }
    }

    @Test
    void allMoodsLoadWithDescriptions() {
        assertThat(vocab.moods()).extracting(MoodEntry::name)
                .contains("happy", "sad", "angry", "neutral", "fear", "love", "disgust");
        assertThat(vocab.isValidMood("happy")).isTrue();
        assertThat(vocab.isValidMood("sleepy")).isFalse();
    }

    @Test
    void allClipNamesPresent() {
        var clips = vocab.allClipNames();
        assertThat(clips).contains("idle-subtle", "head-nod", "shrug", "greeting", "death-a");
        assertThat(clips.size()).isGreaterThan(60);
    }

    @Test
    void gestureTagCount() {
        assertThat(vocab.gestureTags().size()).isGreaterThan(40);
    }

    @Test
    void nonPromptableGesturesExist() {
        var nonPromptable = vocab.gesturePromptEntries().stream()
                .filter(e -> !e.promptable())
                .map(GestureEntry::tag)
                .toList();
        assertThat(nonPromptable).contains("attention", "happy", "explaining");
    }

    @Test
    void sleepMoodNotPromptable() {
        var sleep = vocab.moods().stream()
                .filter(m -> m.name().equals("sleep"))
                .findFirst();
        assertThat(sleep).isPresent();
        assertThat(sleep.get().promptable()).isFalse();
    }
}
