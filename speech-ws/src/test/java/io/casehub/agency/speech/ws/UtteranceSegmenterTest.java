package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.ws.protocol.Cue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UtteranceSegmenterTest {

    private static UtteranceSegmenter segmenter;

    @BeforeAll
    static void setup() {
        segmenter = new UtteranceSegmenter(new TestVocabulary());
    }

    @Test
    void stripsGestureTagsFromCleanText() {
        var segs = segmenter.segment("Hello [gesture:greeting] everyone!");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).cleanText()).isEqualTo("Hello everyone!");
        assertThat(segs.get(0).cues()).hasSize(1);
        assertThat(segs.get(0).cues().get(0)).isInstanceOf(Cue.GestureCue.class);
    }

    @Test
    void forwardBindsTagToNextWord() {
        var segs = segmenter.segment("[gesture:greeting] Hello!");
        assertThat(segs.get(0).cues().get(0)).isInstanceOf(Cue.GestureCue.class);
        assertThat(((Cue.GestureCue) segs.get(0).cues().get(0)).tag()).isEqualTo("greeting");
    }

    @Test
    void trailingTagBindsToSentence() {
        var segs = segmenter.segment("Nice to meet you! [gesture:greeting]");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).cleanText()).isEqualTo("Nice to meet you!");
        assertThat(segs.get(0).cues()).hasSize(1);
    }

    @Test
    void dropsInvalidGestureTags() {
        var segs = segmenter.segment("Hello [gesture:nonexistent] world.");
        assertThat(segs.get(0).cues()).isEmpty();
        assertThat(segs.get(0).cleanText()).isEqualTo("Hello world.");
    }

    @Test
    void parsesMoodTags() {
        var segs = segmenter.segment("Great news! [mood:happy]");
        assertThat(segs.get(0).cleanText()).isEqualTo("Great news!");
        assertThat(segs.get(0).cues()).hasSize(1);
        assertThat(segs.get(0).cues().get(0)).isInstanceOf(Cue.MoodCue.class);
    }

    @Test
    void splitsSentences() {
        var segs = segmenter.segment("[gesture:greeting] Hello! How are you? Fine.");
        assertThat(segs).hasSize(3);
        assertThat(segs.get(0).cleanText()).isEqualTo("Hello!");
        assertThat(segs.get(1).cleanText()).isEqualTo("How are you?");
        assertThat(segs.get(2).cleanText()).isEqualTo("Fine.");
    }

    @Test
    void producesCanonicalMarkupForHistory() {
        var segs = segmenter.segment("Hello [gesture:greeting] everyone! [gesture:nonexistent]");
        assertThat(segs.get(0).canonicalMarkup()).isEqualTo("[gesture:greeting] Hello everyone!");
    }

    @Test
    void noEmptySegmentsFromPureMarkup() {
        var segs = segmenter.segment("[gesture:greeting]");
        assertThat(segs).isEmpty();
    }

    @Test
    void handlesMalformedTags() {
        var segs = segmenter.segment("Hello [gesture:truncat");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).cleanText()).isEqualTo("Hello");
        assertThat(segs.get(0).cues()).isEmpty();
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(segmenter.segment(null)).isEmpty();
        assertThat(segmenter.segment("")).isEmpty();
        assertThat(segmenter.segment("   ")).isEmpty();
    }

    @Test
    void multipleTagsInOneSentence() {
        var segs = segmenter.segment("[gesture:nod] Yes, [gesture:greeting] hello!");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).cues()).hasSize(2);
    }

    @Test
    void dropsInvalidMoodTag() {
        var segs = segmenter.segment("Hello! [mood:sleepy]");
        assertThat(segs.get(0).cues()).isEmpty();
    }

    @Test
    void wordCount() {
        var segs = segmenter.segment("Hello beautiful world!");
        assertThat(segs.get(0).wordCount()).isEqualTo(3);
    }

    static class TestVocabulary implements PerformanceVocabulary {
        private static final Set<String> GESTURES = Set.of("greeting", "nod", "shrug", "angry");
        private static final Set<String> MOODS = Set.of("happy", "sad", "neutral");

        @Override public Set<String> gestureTags() { return GESTURES; }
        @Override public boolean isValidGesture(String tag) { return GESTURES.contains(tag); }
        @Override public List<GestureEntry> gesturePromptEntries() { return List.of(); }
        @Override public Set<String> allClipNames() { return Set.of(); }
        @Override public List<MoodEntry> moods() { return List.of(); }
        @Override public boolean isValidMood(String mood) { return MOODS.contains(mood); }
    }
}
